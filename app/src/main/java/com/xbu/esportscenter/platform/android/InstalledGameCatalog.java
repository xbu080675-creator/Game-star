package com.xbu.esportscenter.platform.android;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only Android launcher/game catalog.
 *
 * Performance rule: launcher discovery and launch validation must never rasterize every installed
 * application icon. Only auto-detected games receive an icon in the initial catalog. Manually
 * selected launcher apps may request their own real launcher icon lazily through iconDataUrl().
 */
public final class InstalledGameCatalog {
    // Handheld HOME treats the launcher icon as the primary software tile artwork. 192px keeps the
    // enlarged tile crisp while avoiding full-resolution drawable payloads in the WebView bridge.
    private static final int ICON_SIZE = 192;

    private final Context appContext;
    private final PackageManager packageManager;
    private final Map<String, String> iconCache = new ConcurrentHashMap<>();
    private volatile String cachedJson;
    private volatile Set<String> cachedLaunchablePackages;

    public InstalledGameCatalog(Context context) {
        appContext = context.getApplicationContext();
        packageManager = appContext.getPackageManager();
    }

    public String catalogJson() {
        String cached = cachedJson;
        if (cached != null) return cached;

        synchronized (this) {
            if (cachedJson != null) return cachedJson;
            cachedJson = buildCatalogJson();
            return cachedJson;
        }
    }

    public synchronized void invalidate() {
        cachedJson = null;
        cachedLaunchablePackages = null;
        iconCache.clear();
    }

    /**
     * Returns the package's actual Android launcher icon on demand.
     *
     * This is deliberately lazy: generic launcher discovery stays metadata-only so boot does not
     * rasterize dozens or hundreds of application icons. Only software the user actually promotes
     * into Game Star Box HOME pays the icon encoding cost.
     */
    public String iconDataUrl(String packageName) {
        if (packageName == null || packageName.isBlank()) return "";
        if (appContext.getPackageName().equals(packageName)) return "";

        String cached = iconCache.get(packageName);
        if (cached != null) return cached;
        if (!isVisibleLauncherPackage(packageName)) return "";

        try {
            Intent launch = packageManager.getLaunchIntentForPackage(packageName);
            if (launch == null) return "";
            ResolveInfo info = packageManager.resolveActivity(launch, 0);
            Drawable drawable = info == null
                    ? packageManager.getApplicationIcon(packageName)
                    : info.loadIcon(packageManager);
            String encoded = encodeDrawable(drawable);
            if (!encoded.isBlank()) iconCache.put(packageName, encoded);
            return encoded;
        } catch (Throwable ignored) {
            return "";
        }
    }

    public Intent createValidatedLaunchIntent(String packageName) {
        if (packageName == null || packageName.isBlank()) return null;
        if (appContext.getPackageName().equals(packageName)) return null;
        if (!isVisibleLauncherPackage(packageName)) return null;

        try {
            Intent launch = packageManager.getLaunchIntentForPackage(packageName);
            if (launch == null) return null;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            return launch;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String buildCatalogJson() {
        JSONObject root = new JSONObject();
        JSONArray games = new JSONArray();
        JSONArray launchables = new JSONArray();
        try {
            List<Entry> entries = queryEntries();
            Set<String> allowList = new HashSet<>();
            for (Entry entry : entries) {
                allowList.add(entry.packageName);
                // Generic launcher candidates deliberately omit icon payloads. Encoding dozens or
                // hundreds of PNGs synchronously caused severe WebView stalls on real devices.
                launchables.put(entry.toJson(false));
                if (entry.game) {
                    games.put(entry.toJson(true));
                    if (!entry.iconDataUrl.isBlank()) {
                        iconCache.put(entry.packageName, entry.iconDataUrl);
                    }
                }
            }
            cachedLaunchablePackages = allowList;
            root.put("autoGames", games);
            root.put("launchables", launchables);
            root.put("error", JSONObject.NULL);
        } catch (Throwable t) {
            try {
                root.put("autoGames", games);
                root.put("launchables", launchables);
                root.put("error", "GSB-GAME-CATALOG-READ-FAILED");
            } catch (Throwable ignored) {
            }
        }
        return root.toString();
    }

    private List<Entry> queryEntries() {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved;
        try {
            resolved = packageManager.queryIntentActivities(query, 0);
        } catch (RuntimeException e) {
            resolved = new ArrayList<>();
        }

        Map<String, Entry> unique = new LinkedHashMap<>();
        for (ResolveInfo info : resolved) {
            if (info == null || info.activityInfo == null || info.activityInfo.applicationInfo == null) continue;
            ApplicationInfo app = info.activityInfo.applicationInfo;
            String packageName = app.packageName;
            if (packageName == null || packageName.isBlank() || appContext.getPackageName().equals(packageName)) continue;
            if (unique.containsKey(packageName)) continue;

            String label;
            try {
                CharSequence raw = info.loadLabel(packageManager);
                label = raw == null ? packageName : raw.toString().trim();
            } catch (RuntimeException e) {
                label = packageName;
            }
            if (label.isBlank()) label = packageName;

            boolean game = isGame(app);
            // Only real games need artwork on the immediately visible home/library surface.
            String icon = game ? encodeIcon(info) : "";
            unique.put(packageName, new Entry(label, packageName, game, icon));
        }

        List<Entry> entries = new ArrayList<>(unique.values());
        entries.sort(Comparator.comparing((Entry e) -> !e.game)
                .thenComparing(e -> e.label, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private boolean isVisibleLauncherPackage(String packageName) {
        Set<String> cached = cachedLaunchablePackages;
        if (cached != null) return cached.contains(packageName);

        // Validation fallback intentionally performs a metadata-only launcher query. It must not
        // call queryEntries(), because queryEntries() may render game icons for catalog display.
        Set<String> allowList = queryLaunchablePackages();
        cachedLaunchablePackages = allowList;
        return allowList.contains(packageName);
    }

    private Set<String> queryLaunchablePackages() {
        Set<String> packages = new HashSet<>();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        try {
            List<ResolveInfo> resolved = packageManager.queryIntentActivities(query, 0);
            for (ResolveInfo info : resolved) {
                if (info == null || info.activityInfo == null) continue;
                String packageName = info.activityInfo.packageName;
                if (packageName == null || packageName.isBlank()) continue;
                if (appContext.getPackageName().equals(packageName)) continue;
                packages.add(packageName);
            }
        } catch (RuntimeException ignored) {
        }
        return packages;
    }

    @SuppressWarnings("deprecation")
    private boolean isGame(ApplicationInfo app) {
        if (android.os.Build.VERSION.SDK_INT >= 26 && app.category == ApplicationInfo.CATEGORY_GAME) {
            return true;
        }
        return (app.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
    }

    private String encodeIcon(ResolveInfo info) {
        try {
            return encodeDrawable(info.loadIcon(packageManager));
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String encodeDrawable(Drawable drawable) {
        if (drawable == null) return "";
        Bitmap bitmap = null;
        ByteArrayOutputStream out = null;
        try {
            bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, ICON_SIZE, ICON_SIZE);
            drawable.draw(canvas);
            out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 82, out);
            return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable ignored) {
            return "";
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            if (out != null) {
                try { out.close(); } catch (Throwable ignored) { }
            }
        }
    }

    private static final class Entry {
        final String label;
        final String packageName;
        final boolean game;
        final String iconDataUrl;

        Entry(String label, String packageName, boolean game, String iconDataUrl) {
            this.label = label;
            this.packageName = packageName;
            this.game = game;
            this.iconDataUrl = iconDataUrl == null ? "" : iconDataUrl;
        }

        JSONObject toJson(boolean includeIcon) {
            JSONObject json = new JSONObject();
            try {
                json.put("label", label);
                json.put("packageName", packageName);
                json.put("game", game);
                json.put("icon", includeIcon ? iconDataUrl : "");
            } catch (Throwable ignored) {
            }
            return json;
        }
    }
}
