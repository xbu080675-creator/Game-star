package com.xbu.esportscenter;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.xbu.esportscenter.core.boot.BootOrchestrator;
import com.xbu.esportscenter.core.boot.BootPhase;
import com.xbu.esportscenter.core.boot.BootSnapshot;
import com.xbu.esportscenter.core.capability.CapabilityRegistry;
import com.xbu.esportscenter.core.session.GameSessionManager;
import com.xbu.esportscenter.core.session.GameSessionState;
import com.xbu.esportscenter.platform.android.AndroidGameSessionLauncher;
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
    private GameSessionManager sessions;
    private InstalledGameCatalog gameCatalog;
    private AndroidGameSessionLauncher gameLauncher;
    private WebView bootWebView;
    private final BootOrchestrator.Listener bootListener = this::dispatchBootSnapshot;
    private final CapabilityRegistry.Listener capabilityListener = this::dispatchCapabilitySnapshot;
    private final GameSessionManager.Listener sessionListener = (previous, current, gameId) ->
            dispatchSessionSnapshot(current, gameId);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GameStarBoxApplication app = (GameStarBoxApplication) getApplication();
        boot = app.getBootOrchestrator();
        capabilities = app.getCapabilities();
        sessions = app.getGameSessions();
        gameCatalog = new InstalledGameCatalog(this);
        gameLauncher = new AndroidGameSessionLauncher(this, gameCatalog, sessions);
        boot.addListener(bootListener);
        capabilities.addListener(capabilityListener);
        sessions.addListener(sessionListener);

        bootWebView = findWebView(getWindow().getDecorView());
        if (bootWebView != null) {
            bootWebView.addJavascriptInterface(new BootBridge(), "GSBBoot");
            bootWebView.addJavascriptInterface(new CapabilityBridge(), "GSBCapabilities");
            bootWebView.addJavascriptInterface(new GameBridge(), "GSBGames");
            bootWebView.addJavascriptInterface(new SessionBridge(), "GSBSession");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameLauncher != null) gameLauncher.onHostResumed();
    }

    @Override
    protected void onStop() {
        if (gameLauncher != null) gameLauncher.onHostStopped();
        super.onStop();
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
            return gameLauncher != null && gameLauncher.requestLaunch(packageName);
        }
    }

    private final class SessionBridge {
        @JavascriptInterface
        public String state() {
            GameSessionManager manager = sessions;
            if (manager == null) return "{}";
            return toJson(manager.getState(), manager.getGameId());
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

    private void dispatchSessionSnapshot(GameSessionState state, String gameId) {
        if (bootWebView == null) return;
        String json = toJson(state, gameId);
        runOnUiThread(() -> {
            if (bootWebView != null) {
                bootWebView.evaluateJavascript(
                        "window.onGSBSessionState&&window.onGSBSessionState(" + json + ");",
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

    private static String toJson(GameSessionState state, String gameId) {
        JSONObject root = new JSONObject();
        try {
            root.put("state", state == null ? GameSessionState.IDLE.name() : state.name());
            root.put("gameId", gameId == null ? "" : gameId);
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
        if (sessions != null) {
            sessions.removeListener(sessionListener);
            sessions = null;
        }
        gameLauncher = null;
        gameCatalog = null;
        bootWebView = null;
        super.onDestroy();
    }
}
