package com.xbu.esportscenter;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.xbu.esportscenter.core.boot.BootOrchestrator;
import com.xbu.esportscenter.core.boot.BootPhase;
import com.xbu.esportscenter.core.boot.BootSnapshot;
import com.xbu.esportscenter.core.capability.CapabilityRegistry;
import com.xbu.esportscenter.platform.android.InstalledGameCatalog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/**
 * Thin launcher activity that exposes typed, narrow runtime state to the local WebView.
 * No generic shell, Settings write, Shizuku execution or arbitrary package launch surface exists.
 */
public final class BootMainActivity extends MainActivity {
    private BootOrchestrator boot;
    private CapabilityRegistry capabilities;
    private InstalledGameCatalog gameCatalog;
    private WebView bootWebView;
    private final BootOrchestrator.Listener bootListener = this::dispatchBootSnapshot;
    private final CapabilityRegistry.Listener capabilityListener = this::dispatchCapabilitySnapshot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GameStarBoxApplication app = (GameStarBoxApplication) getApplication();
        boot = app.getBootOrchestrator();
        capabilities = app.getCapabilities();
        gameCatalog = new InstalledGameCatalog(this);
        boot.addListener(bootListener);
        capabilities.addListener(capabilityListener);

        bootWebView = findWebView(getWindow().getDecorView());
        if (bootWebView != null) {
            bootWebView.addJavascriptInterface(new BootBridge(), "GSBBoot");
            bootWebView.addJavascriptInterface(new CapabilityBridge(), "GSBCapabilities");
            bootWebView.addJavascriptInterface(new GameBridge(), "GSBGames");
        }
    }

    private WebView findWebView(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            WebView found = findWebView(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private final class BootBridge {
        @JavascriptInterface
        public String state() {
            return boot == null ? "{}" : toJson(boot.snapshot());
        }

        @JavascriptInterface
        public void webViewReady() {
            if (boot != null) boot.complete(BootPhase.WEBVIEW_READY);
        }
    }

    private final class CapabilityBridge {
        @JavascriptInterface
        public String state() {
            return capabilities == null ? "{}" : toJson(capabilities.snapshot());
        }
    }

    private final class GameBridge {
        @JavascriptInterface
        public String catalog() {
            return gameCatalog == null ? "{\"autoGames\":[],\"launchables\":[]}" : gameCatalog.catalogJson();
        }

        @JavascriptInterface
        public boolean launch(String packageName) {
            if (gameCatalog == null) return false;
            Intent launch = gameCatalog.createValidatedLaunchIntent(packageName);
            if (launch == null) return false;
            runOnUiThread(() -> {
                try {
                    startActivity(launch);
                } catch (RuntimeException ignored) {
                }
            });
            return true;
        }
    }

    private void dispatchBootSnapshot(BootSnapshot snapshot) {
        WebView view = bootWebView;
        if (view == null) return;
        String json = toJson(snapshot);
        runOnUiThread(() -> {
            if (bootWebView != null) {
                bootWebView.evaluateJavascript(
                        "window.onGSBBootState&&window.onGSBBootState(" + json + ");",
                        null
                );
            }
        });
    }

    private void dispatchCapabilitySnapshot(Map<String, CapabilityRegistry.Entry> snapshot) {
        WebView view = bootWebView;
        if (view == null) return;
        String json = toJson(snapshot);
        runOnUiThread(() -> {
            if (bootWebView != null) {
                bootWebView.evaluateJavascript(
                        "window.onGSBCapabilityState&&window.onGSBCapabilityState(" + json + ");",
                        null
                );
            }
        });
    }

    private static String toJson(BootSnapshot snapshot) {
        JSONObject o = new JSONObject();
        JSONArray completed = new JSONArray();
        try {
            o.put("phase", snapshot.phase.name());
            o.put("overallProgress", snapshot.overallProgress);
            o.put("blockingReady", snapshot.blockingReady);
            for (BootPhase phase : snapshot.completed) completed.put(phase.name());
            o.put("completed", completed);
        } catch (Throwable ignored) {
        }
        return o.toString();
    }

    private static String toJson(Map<String, CapabilityRegistry.Entry> snapshot) {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, CapabilityRegistry.Entry> item : snapshot.entrySet()) {
                CapabilityRegistry.Entry entry = item.getValue();
                JSONObject value = new JSONObject();
                value.put("availability", entry.availability.name());
                value.put("detailCode", entry.detailCode);
                root.put(item.getKey(), value);
            }
        } catch (Throwable ignored) {
        }
        return root.toString();
    }

    @Override
    protected void onDestroy() {
        if (boot != null) {
            boot.removeListener(bootListener);
            boot = null;
        }
        if (capabilities != null) {
            capabilities.removeListener(capabilityListener);
            capabilities = null;
        }
        gameCatalog = null;
        bootWebView = null;
        super.onDestroy();
    }
}
