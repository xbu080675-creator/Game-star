package com.xbu.esportscenter;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.xbu.esportscenter.core.boot.BootOrchestrator;
import com.xbu.esportscenter.core.boot.BootPhase;
import com.xbu.esportscenter.core.boot.BootSnapshot;
import com.xbu.esportscenter.core.boot.ShoulderBootStateMachine;
import com.xbu.esportscenter.core.capability.CapabilityRegistry;
import com.xbu.esportscenter.core.session.GameSessionManager;
import com.xbu.esportscenter.core.session.GameSessionState;
import com.xbu.esportscenter.platform.android.AndroidGameSessionLauncher;
import com.xbu.esportscenter.platform.android.AndroidShoulderKeyAdapter;
import com.xbu.esportscenter.platform.android.InstalledGameCatalog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/**
 * Thin launcher activity that exposes typed, narrow runtime state to the local WebView.
 * No generic shell, Settings write, Shizuku execution or arbitrary package launch surface exists.
 */
public final class BootMainActivity extends MainActivity {
    private static final String TAG_BOOT = "[GSB-BOOT]";
    private static final String PREFS_BOOT = "gsb.boot.profile";
    private static final String PREF_SHOULDER_CALIBRATED = "shoulder_calibrated_v1";

    private BootOrchestrator boot;
    private CapabilityRegistry capabilities;
    private GameSessionManager sessions;
    private InstalledGameCatalog gameCatalog;
    private AndroidGameSessionLauncher gameLauncher;
    private WebView bootWebView;
    private boolean runtimeListenersAttached;

    private final Handler bootHandler = new Handler(Looper.getMainLooper());
    private ShoulderBootStateMachine shoulderBoot;
    private AndroidShoulderKeyAdapter shoulderKeyAdapter;
    private WebView shoulderBootView;
    private boolean shoulderBootActive;
    private boolean shoulderBootGateOpen;
    private boolean shoulderBootFastMode;
    private ShoulderBootStateMachine.Phase lastHapticPhase;
    private int lastHapticLevel = -1;
    private boolean ignitionHapticStarted;
    private int legacyBootGuardAttempts;

    private final BootOrchestrator.Listener bootListener = this::dispatchBootSnapshot;
    private final CapabilityRegistry.Listener capabilityListener = this::dispatchCapabilitySnapshot;
    private final GameSessionManager.Listener sessionListener = (previous, current, gameId) ->
            dispatchSessionSnapshot(current, gameId);

    private final Runnable shoulderTick = new Runnable() {
        @Override
        public void run() {
            if (!shoulderBootActive || shoulderBoot == null) return;
            ShoulderBootStateMachine.Snapshot snapshot =
                    shoulderBoot.tick(android.os.SystemClock.uptimeMillis());
            dispatchShoulderSnapshot(snapshot);
            applyBootHaptics(snapshot);
            bootHandler.postDelayed(this, 40L);
        }
    };

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

        bootWebView = findWebView(getWindow().getDecorView());
        if (bootWebView != null) {
            bootWebView.addJavascriptInterface(new BootBridge(), "GSBBoot");
            bootWebView.addJavascriptInterface(new RuntimeBridge(), "GSBRuntime");
            bootWebView.addJavascriptInterface(new CapabilityBridge(), "GSBCapabilities");
            bootWebView.addJavascriptInterface(new GameBridge(), "GSBGames");
            bootWebView.addJavascriptInterface(new SessionBridge(), "GSBSession");

            // The legacy WebView boot animation remains in index.html for compatibility, but is
            // visually and audibly suppressed while the new hardware-first boot surface is active.
            bootWebView.getSettings().setMediaPlaybackRequiresUserGesture(true);
            bootWebView.setVisibility(View.INVISIBLE);
        }

        shoulderBoot = new ShoulderBootStateMachine();
        shoulderKeyAdapter = new AndroidShoulderKeyAdapter();
        shoulderBootFastMode = getSharedPreferences(PREFS_BOOT, MODE_PRIVATE)
                .getBoolean(PREF_SHOULDER_CALIBRATED, false);
        shoulderBootActive = true;
        shoulderBootGateOpen = false;
        installShoulderBootSurface();
        suppressLegacyBootWhenReady();
        bootHandler.post(shoulderTick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameLauncher != null) gameLauncher.onHostResumed();
        if (shoulderBootActive && shoulderBoot != null) {
            bootHandler.removeCallbacks(shoulderTick);
            bootHandler.post(shoulderTick);
        }
    }

    @Override
    protected void onStop() {
        if (gameLauncher != null) gameLauncher.onHostStopped();
        if (shoulderBootActive) cancelBootVibration();
        super.onStop();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (shoulderBootActive && shoulderKeyAdapter != null && event != null) {
            ShoulderBootStateMachine.Side side = shoulderKeyAdapter.map(event);
            if (side != null) {
                if (event.getRepeatCount() == 0) {
                    boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
                    if (down || event.getAction() == KeyEvent.ACTION_UP) {
                        onShoulderInput(side, down, true);
                    }
                }
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                String device = event.getDevice() == null ? "unknown" : event.getDevice().getName();
                Log.d(TAG_BOOT, "raw key during shoulder calibration code=" + event.getKeyCode()
                        + " source=" + event.getSource() + " device=" + device);
            }
        }
        return super.dispatchKeyEvent(event);
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

    private void installShoulderBootSurface() {
        shoulderBootView = new WebView(this);
        shoulderBootView.setBackgroundColor(Color.BLACK);
        shoulderBootView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings settings = shoulderBootView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        shoulderBootView.addJavascriptInterface(new ShoulderBootBridge(), "GSBShoulderBoot");
        shoulderBootView.setWebViewClient(new WebViewClient());

        addContentView(
                shoulderBootView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );
        shoulderBootView.loadUrl("file:///android_asset/shoulder-boot.html");
    }

    private void suppressLegacyBootWhenReady() {
        WebView view = bootWebView;
        if (view == null || !shoulderBootActive || legacyBootGuardAttempts >= 160) return;
        legacyBootGuardAttempts++;
        view.evaluateJavascript(
                "(function(){var b=document.getElementById('boot');"
                        + "if(!b)return false;b.classList.add('done');"
                        + "var a=document.getElementById('bootAudio');"
                        + "if(a){try{a.pause();a.currentTime=0}catch(e){}}return true;})();",
                value -> {
                    if ("true".equals(value)) return;
                    bootHandler.postDelayed(this::suppressLegacyBootWhenReady, 35L);
                }
        );
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

    /** Activates non-boot UI listeners only after the external hardware boot gate opens. */
    private final class RuntimeBridge {
        @JavascriptInterface
        public boolean bootGateOpen() {
            return shoulderBootGateOpen;
        }

        @JavascriptInterface
        public void ready() {
            if (!shoulderBootGateOpen) return;
            runOnUiThread(BootMainActivity.this::attachRuntimeListeners);
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

    private final class ShoulderBootBridge {
        @JavascriptInterface
        public String mode() {
            return shoulderBootFastMode ? "FAST" : "INTERACTIVE";
        }

        @JavascriptInterface
        public void ready() {
            runOnUiThread(() -> {
                if (shoulderBoot != null) dispatchShoulderSnapshot(shoulderBoot.snapshot());
            });
        }

        /** Touch fallback is intentionally scoped to this local first-boot surface. */
        @JavascriptInterface
        public void touch(String sideName, boolean down) {
            ShoulderBootStateMachine.Side side = parseShoulderSide(sideName);
            if (side == null) return;
            runOnUiThread(() -> onShoulderInput(side, down, false));
        }

        @JavascriptInterface
        public void startFast() {
            runOnUiThread(() -> {
                if (!shoulderBootActive || shoulderBoot == null) return;
                ShoulderBootStateMachine.Snapshot snapshot = shoulderBoot.startFastIgnition();
                dispatchShoulderSnapshot(snapshot);
                applyBootHaptics(snapshot);
            });
        }

        @JavascriptInterface
        public void visualComplete() {
            runOnUiThread(BootMainActivity.this::finishShoulderBoot);
        }
    }

    private static ShoulderBootStateMachine.Side parseShoulderSide(String value) {
        if ("LEFT".equalsIgnoreCase(value)) return ShoulderBootStateMachine.Side.LEFT;
        if ("RIGHT".equalsIgnoreCase(value)) return ShoulderBootStateMachine.Side.RIGHT;
        return null;
    }

    private void onShoulderInput(
            ShoulderBootStateMachine.Side side,
            boolean down,
            boolean hardwareEvent
    ) {
        if (!shoulderBootActive || shoulderBoot == null) return;
        ShoulderBootStateMachine.Snapshot snapshot = shoulderBoot.onInput(
                side,
                down,
                android.os.SystemClock.uptimeMillis()
        );
        if (hardwareEvent) {
            Log.d(TAG_BOOT, "shoulder input side=" + side + " down=" + down
                    + " phase=" + snapshot.phase);
        }
        dispatchShoulderSnapshot(snapshot);
        applyBootHaptics(snapshot);
    }

    private void dispatchShoulderSnapshot(ShoulderBootStateMachine.Snapshot snapshot) {
        WebView view = shoulderBootView;
        if (view == null || snapshot == null) return;
        String json = toJson(snapshot);
        runOnUiThread(() -> {
            if (shoulderBootView != null) {
                shoulderBootView.evaluateJavascript(
                        "window.onGSBShoulderState&&window.onGSBShoulderState(" + json + ");",
                        null
                );
            }
        });
    }

    private void applyBootHaptics(ShoulderBootStateMachine.Snapshot snapshot) {
        if (snapshot == null || !shoulderBootActive) return;
        boolean phaseChanged = snapshot.phase != lastHapticPhase;
        boolean levelChanged = snapshot.level != lastHapticLevel;

        if (phaseChanged && (snapshot.phase == ShoulderBootStateMachine.Phase.LEFT_HOLD
                || snapshot.phase == ShoulderBootStateMachine.Phase.RIGHT_HOLD
                || snapshot.phase == ShoulderBootStateMachine.Phase.RIGHT_TAP
                || snapshot.phase == ShoulderBootStateMachine.Phase.BOTH_HOLD)) {
            emitBootClick();
        }

        boolean holding = snapshot.phase == ShoulderBootStateMachine.Phase.LEFT_HOLD
                || snapshot.phase == ShoulderBootStateMachine.Phase.RIGHT_HOLD
                || snapshot.phase == ShoulderBootStateMachine.Phase.BOTH_HOLD;

        if (snapshot.phase == ShoulderBootStateMachine.Phase.IGNITING) {
            if (!ignitionHapticStarted) {
                ignitionHapticStarted = true;
                startIgnitionVibration();
            }
        } else if (holding) {
            if (snapshot.level <= 0) {
                cancelBootVibration();
            } else if (levelChanged) {
                startHoldVibration(snapshot.level);
            }
        } else if (snapshot.phase != ShoulderBootStateMachine.Phase.COMPLETE) {
            cancelBootVibration();
        }

        lastHapticPhase = snapshot.phase;
        lastHapticLevel = snapshot.level;
    }

    private boolean bootHapticsEnabled() {
        try {
            return Settings.System.getInt(
                    getContentResolver(),
                    Settings.System.HAPTIC_FEEDBACK_ENABLED,
                    1
            ) == 1;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private Vibrator bootVibrator() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager = getSystemService(VibratorManager.class);
                return manager == null ? null : manager.getDefaultVibrator();
            }
            return (Vibrator) getSystemService(VIBRATOR_SERVICE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void emitBootClick() {
        if (!bootHapticsEnabled()) return;
        Vibrator vibrator = bootVibrator();
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
        } catch (Throwable ignored) {
        }
    }

    private void startHoldVibration(int level) {
        if (!bootHapticsEnabled()) return;
        Vibrator vibrator = bootVibrator();
        if (vibrator == null || !vibrator.hasVibrator()) return;
        int safe = Math.max(1, Math.min(4, level));
        long[] on = {22L, 30L, 40L, 54L};
        long[] off = {72L, 48L, 28L, 14L};
        int[] amp = {58, 104, 166, 225};
        try {
            if (vibrator.hasAmplitudeControl()) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{0L, on[safe - 1], off[safe - 1]},
                        new int[]{0, amp[safe - 1], 0},
                        1
                ));
            } else {
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{0L, on[safe - 1], off[safe - 1]},
                        1
                ));
            }
        } catch (Throwable ignored) {
        }
    }

    private void startIgnitionVibration() {
        if (!bootHapticsEnabled()) return;
        Vibrator vibrator = bootVibrator();
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (vibrator.hasAmplitudeControl()) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{0, 90, 24, 110, 18, 140, 12, 190, 8, 270},
                        new int[]{0, 110, 0, 145, 0, 185, 0, 225, 0, 255},
                        -1
                ));
            } else {
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{0, 90, 24, 110, 18, 140, 12, 190, 8, 270},
                        -1
                ));
            }
        } catch (Throwable ignored) {
        }
    }

    private void cancelBootVibration() {
        Vibrator vibrator = bootVibrator();
        if (vibrator == null) return;
        try {
            vibrator.cancel();
        } catch (Throwable ignored) {
        }
    }

    private void finishShoulderBoot() {
        if (!shoulderBootActive) return;
        shoulderBootActive = false;
        shoulderBootGateOpen = true;
        bootHandler.removeCallbacks(shoulderTick);
        cancelBootVibration();
        if (shoulderBoot != null) shoulderBoot.complete();

        if (!shoulderBootFastMode) {
            getSharedPreferences(PREFS_BOOT, MODE_PRIVATE)
                    .edit()
                    .putBoolean(PREF_SHOULDER_CALIBRATED, true)
                    .apply();
        }

        WebView main = bootWebView;
        if (main != null) {
            main.evaluateJavascript(
                    "(function(){var b=document.getElementById('boot');if(b)b.classList.add('done');"
                            + "var a=document.getElementById('bootAudio');if(a){try{a.pause();a.currentTime=0}catch(e){}}})();",
                    null
            );
            main.getSettings().setMediaPlaybackRequiresUserGesture(false);
            main.setAlpha(0f);
            main.setVisibility(View.VISIBLE);
            main.animate().alpha(1f).setDuration(240L).start();
        }

        WebView overlay = shoulderBootView;
        if (overlay != null) {
            overlay.animate()
                    .alpha(0f)
                    .setDuration(220L)
                    .withEndAction(() -> {
                        if (shoulderBootView == null) return;
                        ViewGroup parent = (ViewGroup) shoulderBootView.getParent();
                        if (parent != null) parent.removeView(shoulderBootView);
                        shoulderBootView.destroy();
                        shoulderBootView = null;
                    })
                    .start();
        }
        Log.i(TAG_BOOT, "hardware boot gate opened mode="
                + (shoulderBootFastMode ? "FAST" : "INTERACTIVE"));
    }

    private void attachRuntimeListeners() {
        if (runtimeListenersAttached) return;
        runtimeListenersAttached = true;
        if (capabilities != null) capabilities.addListener(capabilityListener);
        if (sessions != null) sessions.addListener(sessionListener);
        if (capabilities != null) dispatchCapabilitySnapshot(capabilities.snapshot());
        if (sessions != null) dispatchSessionSnapshot(sessions.getState(), sessions.getGameId());
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
        if (!runtimeListenersAttached || bootWebView == null) return;
        String json = toJson(snapshot);
        runOnUiThread(() -> {
            if (runtimeListenersAttached && bootWebView != null) {
                bootWebView.evaluateJavascript(
                        "window.onGSBCapabilityState&&window.onGSBCapabilityState(" + json + ");",
                        null
                );
            }
        });
    }

    private void dispatchSessionSnapshot(GameSessionState state, String gameId) {
        if (!runtimeListenersAttached || bootWebView == null) return;
        String json = toJson(state, gameId);
        runOnUiThread(() -> {
            if (runtimeListenersAttached && bootWebView != null) {
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

    private static String toJson(ShoulderBootStateMachine.Snapshot snapshot) {
        JSONObject root = new JSONObject();
        try {
            root.put("phase", snapshot.phase.name());
            root.put("leftDown", snapshot.leftDown);
            root.put("rightDown", snapshot.rightDown);
            root.put("progress", snapshot.progress);
            root.put("level", snapshot.level);
            root.put("leftCalibrated", snapshot.leftCalibrated);
            root.put("rightCalibrated", snapshot.rightCalibrated);
        } catch (Throwable ignored) {
        }
        return root.toString();
    }

    @Override
    protected void onDestroy() {
        bootHandler.removeCallbacksAndMessages(null);
        cancelBootVibration();

        if (shoulderBootView != null) {
            ViewGroup parent = (ViewGroup) shoulderBootView.getParent();
            if (parent != null) parent.removeView(shoulderBootView);
            shoulderBootView.destroy();
            shoulderBootView = null;
        }
        shoulderBoot = null;
        shoulderKeyAdapter = null;
        shoulderBootActive = false;

        if (boot != null) {
            boot.removeListener(bootListener);
            boot = null;
        }
        if (capabilities != null) {
            if (runtimeListenersAttached) capabilities.removeListener(capabilityListener);
            capabilities = null;
        }
        if (sessions != null) {
            if (runtimeListenersAttached) sessions.removeListener(sessionListener);
            sessions = null;
        }
        runtimeListenersAttached = false;
        gameLauncher = null;
        gameCatalog = null;
        bootWebView = null;
        super.onDestroy();
    }
}
