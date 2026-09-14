package com.xbu.esportscenter.platform.android;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Typed asset adapter for Hardware Playground v8 model-render outputs.
 *
 * The presentation continues to request stable logical v7 asset URLs. When a complete, validated
 * high-render pack is present and explicitly marked productionReady, this adapter substitutes the
 * packaged raster render output at the WebView resource boundary. Any incomplete or invalid pack
 * fails closed to the existing v7 prototype assets; mixed visual packs are never allowed.
 *
 * This adapter does not render models, does not invoke a DCC tool, and does not download assets.
 */
public final class HardwareModelAssetAdapter extends WebViewClient {
    private static final String TAG = "[GSB-MODEL]";
    private static final String MANIFEST_PATH = "hardware-model/model-manifest.json";
    private static final String ASSET_URL_PREFIX = "/android_asset/";
    private static final int SUPPORTED_SCHEMA = 1;

    private final AssetManager assets;
    private final Map<String, RenderEntry> replacements;
    private final boolean productionPackReady;

    public HardwareModelAssetAdapter(Context context) {
        assets = context.getApplicationContext().getAssets();
        LoadResult result = loadManifest();
        replacements = result.replacements;
        productionPackReady = result.ready;
        Log.i(TAG, productionPackReady
                ? "high-render model pack active entries=" + replacements.size()
                : "high-render model pack unavailable; using prototype fallback");
    }

    public boolean isProductionPackReady() {
        return productionPackReady;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        if (request == null || request.getUrl() == null) return null;
        return intercept(request.getUrl());
    }

    @Override
    @SuppressWarnings("deprecation")
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
        if (url == null) return null;
        try {
            return intercept(Uri.parse(url));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private WebResourceResponse intercept(Uri uri) {
        if (!productionPackReady || uri == null) return null;
        String path = uri.getPath();
        if (path == null || !path.startsWith(ASSET_URL_PREFIX)) return null;
        String logicalPath = path.substring(ASSET_URL_PREFIX.length());
        RenderEntry entry = replacements.get(logicalPath);
        if (entry == null) return null;
        try {
            InputStream stream = assets.open(entry.renderPath, AssetManager.ACCESS_STREAMING);
            return new WebResourceResponse(entry.mimeType, null, stream);
        } catch (Throwable t) {
            // Validation happens atomically during construction. A later read failure is still
            // treated as fail-closed for this request so WebView can load the original v7 URL.
            Log.e(TAG, "GSB-MODEL-ASSET-READ-FAILED logical=" + logicalPath, t);
            return null;
        }
    }

    private LoadResult loadManifest() {
        try {
            String json = readText(MANIFEST_PATH);
            JSONObject root = new JSONObject(json);
            if (root.optInt("schemaVersion", -1) != SUPPORTED_SCHEMA) {
                Log.e(TAG, "GSB-MODEL-MANIFEST-SCHEMA-UNSUPPORTED");
                return LoadResult.fallback();
            }

            JSONObject pack = root.optJSONObject("pack");
            JSONArray entries = root.optJSONArray("assets");
            if (pack == null || entries == null || entries.length() == 0) {
                Log.e(TAG, "GSB-MODEL-MANIFEST-INVALID");
                return LoadResult.fallback();
            }
            if (!pack.optBoolean("productionReady", false)) {
                return LoadResult.fallback();
            }

            Map<String, RenderEntry> candidate = new HashMap<>();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject item = entries.optJSONObject(i);
                if (item == null) return failIncomplete("entry-null");
                String logicalRequest = safeRelative(item.optString("logicalRequest", ""));
                String renderPath = safeRelative(item.optString("renderPath", ""));
                String mimeType = item.optString("mimeType", "");
                boolean required = item.optBoolean("required", true);
                if (!required) continue;
                if (logicalRequest.isEmpty() || renderPath.isEmpty() || !allowedMime(mimeType)) {
                    return failIncomplete("entry-invalid");
                }
                if (renderPath.endsWith(".svg") || renderPath.contains("hardware-v7/")) {
                    Log.e(TAG, "GSB-MODEL-PRODUCTION-VECTOR-FORBIDDEN path=" + renderPath);
                    return LoadResult.fallback();
                }
                // Atomic validation: every required render output must exist before any substitution
                // is enabled. This prevents half-model / half-prototype presentation states.
                try (InputStream ignored = assets.open(renderPath, AssetManager.ACCESS_STREAMING)) {
                    // existence check only
                }
                candidate.put(logicalRequest, new RenderEntry(renderPath, mimeType));
            }
            if (candidate.isEmpty()) return failIncomplete("no-required-assets");
            return new LoadResult(true, Collections.unmodifiableMap(candidate));
        } catch (Throwable t) {
            Log.e(TAG, "GSB-MODEL-MANIFEST-READ-FAILED", t);
            return LoadResult.fallback();
        }
    }

    private LoadResult failIncomplete(String detail) {
        Log.e(TAG, "GSB-MODEL-PACK-INCOMPLETE detail=" + detail);
        return LoadResult.fallback();
    }

    private String readText(String path) throws Exception {
        try (InputStream stream = assets.open(path, AssetManager.ACCESS_BUFFER)) {
            byte[] bytes = new byte[stream.available()];
            int total = 0;
            while (total < bytes.length) {
                int read = stream.read(bytes, total, bytes.length - total);
                if (read < 0) break;
                total += read;
            }
            return new String(bytes, 0, total, StandardCharsets.UTF_8);
        }
    }

    private static boolean allowedMime(String value) {
        return "image/webp".equals(value) || "image/png".equals(value) || "image/avif".equals(value);
    }

    private static String safeRelative(String value) {
        if (value == null) return "";
        String safe = value.trim().replace('\\', '/');
        if (safe.startsWith("/") || safe.contains("../") || safe.contains("://")) return "";
        return safe;
    }

    private static final class RenderEntry {
        final String renderPath;
        final String mimeType;

        RenderEntry(String renderPath, String mimeType) {
            this.renderPath = renderPath;
            this.mimeType = mimeType;
        }
    }

    private static final class LoadResult {
        final boolean ready;
        final Map<String, RenderEntry> replacements;

        LoadResult(boolean ready, Map<String, RenderEntry> replacements) {
            this.ready = ready;
            this.replacements = replacements;
        }

        static LoadResult fallback() {
            return new LoadResult(false, Collections.emptyMap());
        }
    }
}
