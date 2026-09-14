package com.xbu.esportscenter;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.xbu.esportscenter.core.boot.BootOrchestrator;
import com.xbu.esportscenter.core.boot.BootPhase;
import com.xbu.esportscenter.core.boot.BootSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Thin launcher activity that exposes the platform-neutral BootOrchestrator to the local WebView.
 * No privileged or vendor-specific API is exposed to JavaScript.
 */
public final class BootMainActivity extends MainActivity {
    private BootOrchestrator boot;
    private WebView bootWebView;
    private final BootOrchestrator.Listener bootListener = this::dispatchBootSnapshot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GameStarBoxApplication app = (GameStarBoxApplication) getApplication();
        boot = app.getBootOrchestrator();
        boot.addListener(bootListener);

        bootWebView = findWebView(getWindow().getDecorView());
        if (bootWebView != null) {
            bootWebView.addJavascriptInterface(new BootBridge(), "GSBBoot");
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

    @Override
    protected void onDestroy() {
        if (boot != null) {
            boot.removeListener(bootListener);
            boot = null;
        }
        bootWebView = null;
        super.onDestroy();
    }
}
