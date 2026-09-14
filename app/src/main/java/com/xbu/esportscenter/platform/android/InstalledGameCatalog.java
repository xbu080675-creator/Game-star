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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only Android launcher/game catalog.
 *
 * It only enumerates packages that expose a normal MAIN/LAUNCHER entry. Game classification uses
 * Android's ApplicationInfo game category/flag. Manual additions are selected by the user in the
 * local UI from this same launchable allow-list.
 */
public final class InstalledGameCatalog {
    private static final int ICON_SIZE = 128;

    private final Context appContext;
    private final PackageManager packageManager;
    private volatile String cachedJson;

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
            for (Entry entry : entries) {
                JSONObject item = entry.toJson();
                launchables.put(item);
                if (entry.game) games.put(entry.toJson());
            }
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
            String icon = encodeIcon(info);
            unique.put(packageName, new Entry(label, packageName, game, icon));
        }

        List<Entry> entries = new ArrayList<>(unique.values());
        entries.sort(Comparator.comparing((Entry e) -> !e.game)
                .thenComparing(e -> e.label, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private boolean isVisibleLauncherPackage(String packageName) {
        for (Entry entry : queryEntries()) {
            if (packageName.equals(entry.packageName)) return true;
        }
        return false;
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
            Drawable drawable = info.loadIcon(packageManager);
            if (drawable == null) return "";
            Bitmap bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, ICON_SIZE, ICON_SIZE);
            drawable.draw(canvas);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 88, out);
            bitmap.recycle();
            return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable ignored) {
            return "";
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

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("label", label);
                json.put("packageName", packageName);
                json.put("game", game);
                json.put("icon", iconDataUrl);
            } catch (Throwable ignored) {
            }
            return json;
        }
    }
}
