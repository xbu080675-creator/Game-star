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
import com.xbu.esportscenter.core.boot.FirstBootProvisioningCoordinator;
import com.xbu.esportscenter.core.boot.IgnitionGate;
import com.xbu.esportscenter.core.boot.ShoulderBootStateMachine;
import com.xbu.esportscenter.core.capability.CapabilityRegistry;
import com.xbu.esportscenter.core.session.GameSessionManager;
import com.xbu.esportscenter.core.session.GameSessionState;
import com.xbu.esportscenter.platform.android.AndroidGameSessionLauncher;
import com.xbu.esportscenter.platform.android.AndroidShoulderKeyAdapter;
import com.xbu.esportscenter.platform.android.HardwarePlaygroundHapticAdapter;
import com.xbu.esportscenter.platform.android.HardwarePlaygroundMotionAdapter;
import com.xbu.esportscenter.platform.android.InstalledGameCatalog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin launcher activity that exposes typed, narrow runtime state to the local WebView.
 * First-boot hardware awakening and hidden first-surface provisioning run in parallel.
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

    private FirstBootProvisioningCoordinator provisioning;
    private IgnitionGate ignitionGate;
    private ExecutorService firstBootExecutor;

    private final Handler bootHandler = new Handler(Looper.getMainLooper());
    private ShoulderBootStateMachine shoulderBoot;
    private AndroidShoulderKeyAdapter shoulderKeyAdapter;
    private HardwarePlaygroundMotionAdapter playgroundMotion;
    private HardwarePlaygroundHapticAdapter playgroundHaptics;
    private WebView shoulderBootView;
    private boolean shoulderBootActive;
    private boolean shoulderBootGateOpen;
    private boolean shoulderBootFastMode;
    private boolean hardwareReadyLatched;
    private ShoulderBootStateMachine.Phase lastHapticPhase;
    private int lastHapticLevel = -1;
    private boolean ignitionHapticStarted;
    private int legacyBootGuardAttempts;

    private final BootOrchestrator.Listener bootListener = snapshot -> {
        dispatchBootSnapshot(snapshot);
        FirstBootProvisioningCoordinator current = provisioning;
        if (current != null && snapshot != null && snapshot.blockingReady) {
            current.complete(FirstBootProvisioningCoordinator.Phase.CORE_RUNTIME);
        }
    };

    private final FirstBootProvisioningCoordinator.Listener provisioningListener = snapshot -> {
        IgnitionGate gate = ignitionGate;
        if (gate != null && snapshot != null) gate.setProvisioningReady(snapshot.blockingReady);
        dispatchProvisioningSnapshot(snapshot);
        runOnUiThread(this::maybeReleaseIgnition);
    };

    private final CapabilityRegistry.Listener capabilityListener = this::dispatchCapabilitySnapshot;
    private final GameSessionManager.Listener sessionListener = (previous, current, gameId) ->
            dispatchSessionSnapshot(current, gameId);

    private final Runnable shoulderTick = new Runnable() {
        @Override
        public void run() {
            if (!shoulderBootActive || shoulderBoot == null) return;
            ShoulderBootStateMachine.Snapshot snapshot =
                    shoulderBoot.tick(android.os.SystemClock.uptimeMillis());
            handleShoulderSnapshot(snapshot);
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
        playgroundHaptics = new HardwarePlaygroundHapticAdapter(this);
        playgroundMotion = new HardwarePlaygroundMotionAdapter(
                this,
                new HardwarePlaygroundMotionAdapter.Listener() {
                    @Override
                    public void onMotion(float pitchDegrees, float rollDegrees) {
                        dispatchPlaygroundMotion(pitchDegrees, rollDegrees);
                    }

                    @Override
                    public void onUnavailable(String code) {
                        dispatchPlaygroundMotionUnavailable(code);
                    }
                }
        );

        provisioning = new FirstBootProvisioningCoordinator();
        ignitionGate = new IgnitionGate();
        firstBootExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "GSB-FirstBoot-Provisioning");
            thread.setDaemon(true);
            return thread;
        });
        provisioning.addListener(provisioningListener);
        boot.addListener(bootListener);

        BootSnapshot initialBoot = boot.snapshot();
        if (initialBoot.blockingReady) {
            provisioning.complete(FirstBootProvisioningCoordinator.Phase.CORE_RUNTIME);
        }

        bootWebView = findWebView(getWindow().getDecorView());
        if (bootWebView != null) {
            bootWebView.addJavascriptInterface(new BootBridge(), "GSBBoot");
            bootWebView.addJavascriptInterface(new RuntimeBridge(), "GSBRuntime");
            bootWebView.addJavascriptInterface(new ProvisioningBridge(), "GSBProvisioning");
            bootWebView.addJavascriptInterface(new CapabilityBridge(), "GSBCapabilities");
            bootWebView.addJavascriptInterface(new GameBridge(), "GSBGames");
            bootWebView.addJavascriptInterface(new SessionBridge(), "GSBSession");

            // The main surface hydrates while hidden behind the hardware-awakening layer.
            bootWebView.getSettings().setMediaPlaybackRequiresUserGesture(true);
            bootWebView.setVisibility(View.INVISIBLE);
        }

        // Runtime listeners are safe to attach before the surface is shown. Hidden hydration is the
        // point of first boot: when ignition happens, Quick Menu/session state is already current.
        attachRuntimeListeners();
        startGameCatalogPrewarm();

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
        if (shoulderBootView != null) shoulderBootView.onResume();
        if (shoulderBootActive && shoulderBoot != null) {
            handleShoulderSnapshot(shoulderBoot.snapshot());
            bootHandler.removeCallbacks(shoulderTick);
            bootHandler.post(shoulderTick);
        }
    }

    @Override
    protected void onPause() {
        if (playgroundMotion != null) playgroundMotion.stop();
        if (playgroundHaptics != null) playgroundHaptics.cancel();
        if (shoulderBootActive) {
            bootHandler.removeCallbacks(shoulderTick);
            cancelBootVibration();
            if (shoulderBoot != null) {
                ShoulderBootStateMachine.Snapshot snapshot = shoulderBoot.cancelActivePresses();
                dispatchShoulderSnapshot(snapshot);
                lastHapticLevel = -1;
            }
        }
        if (shoulderBootView != null) shoulderBootView.onPause();
        super.onPause();
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
                Log.d(TAG_BOOT, "raw key during hardware awakening code=" + event.getKeyCode()
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
        shoulderBootView.addJavascriptInterface(new HardwarePlaygroundBridge(), "GSBPlayground");
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

    private void startGameCatalogPrewarm() {
        ExecutorService executor = firstBootExecutor;
        InstalledGameCatalog catalog = gameCatalog;
        if (executor == null || catalog == null) return;
        executor.execute(() -> {
            long start = android.os.SystemClock.elapsedRealtime();
            try {
                catalog.catalogJson();
                FirstBootProvisioningCoordinator current = provisioning;
                if (current != null) {
                    current.complete(FirstBootProvisioningCoordinator.Phase.GAME_CATALOG);
                }
                Log.i(TAG_BOOT, "first-boot game catalog ready ms="
                        + (android.os.SystemClock.elapsedRealtime() - start));
            } catch (Throwable t) {
                // InstalledGameCatalog already returns a safe error payload for normal failures.
                // An unexpected failure is logged and does not widen into package/shell fallback.
                Log.w(TAG_BOOT, "GSB-BOOT-GAME-CATALOG-PREWARM-FAILED", t);
            }
        });
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

    private final class RuntimeBridge {
        @JavascriptInterface
        public boolean bootGateOpen() {
            return shoulderBootGateOpen;
        }

        @JavascriptInterface
        public void ready() {
            runOnUiThread(BootMainActivity.this::attachRuntimeListeners);
        }
    }

    private final class ProvisioningBridge {
        @JavascriptInterface
        public boolean catalogReady() {
            FirstBootProvisioningCoordinator current = provisioning;
            return current != null
                    && current.isComplete(FirstBootProvisioningCoordinator.Phase.GAME_CATALOG);
        }

        @JavascriptInterface
        public String state() {
            FirstBootProvisioningCoordinator current = provisioning;
            return current == null ? "{}" : toJson(current.snapshot());
        }

        @JavascriptInterface
        public void mainSurfaceReady() {
            runOnUiThread(() -> {
                FirstBootProvisioningCoordinator current = provisioning;
                if (current == null) return;
                current.complete(FirstBootProvisioningCoordinator.Phase.MAIN_SURFACE);
                Log.i(TAG_BOOT, "hidden main surface hydrated");
            });
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

    private final class HardwarePlaygroundBridge {
        @JavascriptInterface
        public boolean motionAvailable() {
            HardwarePlaygroundMotionAdapter adapter = playgroundMotion;
            return adapter != null && adapter.isAvailable();
        }

        @JavascriptInterface
        public void startMotion() {
            runOnUiThread(() -> {
                HardwarePlaygroundMotionAdapter adapter = playgroundMotion;
                if (adapter != null) adapter.start();
            });
        }

        @JavascriptInterface
        public void stopMotion() {
            runOnUiThread(() -> {
                HardwarePlaygroundMotionAdapter adapter = playgroundMotion;
                if (adapter != null) adapter.stop();
            });
        }

        @JavascriptInterface
        public void haptic(int sample) {
            int safe = Math.max(0, Math.min(2, sample));
            runOnUiThread(() -> {
                HardwarePlaygroundHapticAdapter adapter = playgroundHaptics;
                if (adapter != null) adapter.playSample(safe);
            });
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
                if (shoulderBoot != null) handleShoulderSnapshot(shoulderBoot.snapshot());
                FirstBootProvisioningCoordinator current = provisioning;
                if (current != null) dispatchProvisioningSnapshot(current.snapshot());
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
                ShoulderBootStateMachine.Snapshot snapshot = shoulderBoot.startFastArmed();
                handleShoulderSnapshot(snapshot);
            });
        }

        @JavascriptInterface
        public void visualComplete() {
            runOnUiThread(() -> {
                if (shoulderBoot == null
                        || shoulderBoot.snapshot().phase != ShoulderBootStateMachine.Phase.IGNITING) {
                    return;
                }
                finishShoulderBoot();
            });
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
        handleShoulderSnapshot(snapshot);
    }

    private void handleShoulderSnapshot(ShoulderBootStateMachine.Snapshot snapshot) {
        dispatchShoulderSnapshot(snapshot);
        applyBootHaptics(snapshot);
        if (snapshot != null && snapshot.phase == ShoulderBootStateMachine.Phase.ARMED) {
            markHardwareSequenceReady();
        }
    }

    private void markHardwareSequenceReady() {
        if (hardwareReadyLatched) {
            maybeReleaseIgnition();
            return;
        }
        hardwareReadyLatched = true;
        IgnitionGate gate = ignitionGate;
        if (gate != null) gate.setHardwareReady(true);
        Log.i(TAG_BOOT, "hardware awakening armed; waiting for first surface readiness");
        maybeReleaseIgnition();
    }

    private void maybeReleaseIgnition() {
        if (!shoulderBootActive || shoulderBoot == null || ignitionGate == null) return;
        IgnitionGate.Snapshot gate = ignitionGate.snapshot();
        if (!gate.open) return;
        if (shoulderBoot.snapshot().phase != ShoulderBootStateMachine.Phase.ARMED) return;

        ShoulderBootStateMachine.Snapshot snapshot = shoulderBoot.releaseIgnition();
        dispatchShoulderSnapshot(snapshot);
        applyBootHaptics(snapshot);
        Log.i(TAG_BOOT, "ignition released: hardwareReady=true provisioningReady=true");
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

    private void dispatchProvisioningSnapshot(FirstBootProvisioningCoordinator.Snapshot snapshot) {
        if (snapshot == null) return;
        String json = toJson(snapshot);
        runOnUiThread(() -> {
            if (shoulderBootView != null) {
                shoulderBootView.evaluateJavascript(
                        "window.onGSBProvisioningState&&window.onGSBProvisioningState(" + json + ");",
                        null
                );
            }
        });
    }

    private void dispatchPlaygroundMotion(float pitchDegrees, float rollDegrees) {
        runOnUiThread(() -> {
            WebView view = shoulderBootView;
            if (view == null) return;
            view.evaluateJavascript(
                    "window.onGSBMotion&&window.onGSBMotion("
                            + Float.toString(pitchDegrees) + ","
                            + Float.toString(rollDegrees) + ");",
                    null
            );
        });
    }

    private void dispatchPlaygroundMotionUnavailable(String code) {
        String safe = code == null ? "GSB-PLAYGROUND-MOTION-UNAVAILABLE"
                : code.replaceAll("[^A-Z0-9._-]", "");
        runOnUiThread(() -> {
            WebView view = shoulderBootView;
            if (view == null) return;
            view.evaluateJavascript(
                    "window.onGSBMotionUnavailable&&window.onGSBMotionUnavailable('" + safe + "');",
                    null
            );
        });
    }

    private void applyBootHaptics(ShoulderBootStateMachine.Snapshot snapshot) {
        if (snapshot == null || !shoulderBootActive) return;
        boolean phaseChanged = snapshot.phase != lastHapticPhase;
        boolean levelChanged = snapshot.level != lastHapticLevel;

        if (phaseChanged && (snapshot.phase == ShoulderBootStateMachine.Phase.LEFT_HOLD
                || snapshot.phase == ShoulderBootStateMachine.Phase.RIGHT_HOLD
                || snapshot.phase == ShoulderBootStateMachine.Phase.RIGHT_TAP
                || snapshot.phase == ShoulderBootStateMachine.Phase.BOTH_HOLD
                || snapshot.phase == ShoulderBootStateMachine.Phase.ARMED)) {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(28L, 145));
            }
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
        if (playgroundMotion != null) playgroundMotion.stop();
        if (playgroundHaptics != null) playgroundHaptics.cancel();
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
            main.animate().alpha(1f).setDuration(220L).start();
        }

        WebView overlay = shoulderBootView;
        if (overlay != null) {
            overlay.animate()
                    .alpha(0f)
                    .setDuration(180L)
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

    private static String toJson(FirstBootProvisioningCoordinator.Snapshot snapshot) {
        JSONObject root = new JSONObject();
        JSONArray completed = new JSONArray();
        try {
            root.put("overallProgress", snapshot.overallProgress);
            root.put("blockingReady", snapshot.blockingReady);
            for (FirstBootProvisioningCoordinator.Phase phase : snapshot.completed) {
                completed.put(phase.name());
            }
            root.put("completed", completed);
        } catch (Throwable ignored) {
        }
        return root.toString();
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
        if (playgroundMotion != null) {
            playgroundMotion.shutdown();
            playgroundMotion = null;
        }
        if (playgroundHaptics != null) {
            playgroundHaptics.cancel();
            playgroundHaptics = null;
        }

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
        if (provisioning != null) {
            provisioning.removeListener(provisioningListener);
            provisioning = null;
        }
        ignitionGate = null;
        if (firstBootExecutor != null) {
            firstBootExecutor.shutdownNow();
            firstBootExecutor = null;
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
