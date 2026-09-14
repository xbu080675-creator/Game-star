package com.xbu.esportscenter.privileged;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.xbu.esportscenter.BuildConfig;
import com.xbu.esportscenter.core.boot.ShoulderBootInputHub;
import com.xbu.esportscenter.core.boot.ShoulderBootStateMachine;
import com.xbu.esportscenter.platform.android.HardwareAwakeningMotorDriver;

import java.util.concurrent.atomic.AtomicBoolean;

import rikka.shizuku.Shizuku;

/**
 * Narrow application-side adapter for REDMAGIC shoulder calibration.
 *
 * No command, Settings key/value, device path or key code enters from WebView/UI. The privileged
 * service owns the fixed calibration scene activation and returns only LEFT/RIGHT DOWN/UP input.
 */
public final class RedMagicShoulderPrivilegedAdapter {
    private static final String TAG = "[GSB-SHOULDER]";
    private static final int REQUEST_CODE = 0x4754;
    private static final long CALIBRATION_POLL_MS = 300L;
    private static final long BINDER_RETRY_MS = 350L;
    private static final int MAX_BINDER_RETRIES = 12;
    private static final String PREFS_BOOT = "gsb.boot.profile";
    private static final String PREF_SHOULDER_CALIBRATED = "shoulder_calibrated_v1";

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Shizuku.UserServiceArgs serviceArgs;
    private final AtomicBoolean bindRequested = new AtomicBoolean(false);
    private final AtomicBoolean serviceBound = new AtomicBoolean(false);
    private final HardwareAwakeningMotorDriver motorDriver;

    private volatile IRedMagicShoulderReader remote;
    private volatile boolean active;
    private volatile boolean destroyed;
    private volatile boolean permissionRequestInFlight;
    private int binderRetryCount;

    private final IRedMagicShoulderCallback callback = new IRedMagicShoulderCallback.Stub() {
        @Override
        public void onTriggerEvent(int side, boolean down) {
            if (!active || destroyed) return;
            ShoulderBootStateMachine.Side semantic;
            if (side == 0) semantic = ShoulderBootStateMachine.Side.LEFT;
            else if (side == 1) semantic = ShoulderBootStateMachine.Side.RIGHT;
            else return;

            long now = SystemClock.uptimeMillis();
            motorDriver.onShoulderInput(semantic, down, now);
            boolean delivered = ShoulderBootInputHub.publish(
                    semantic,
                    down,
                    now
            );
            if (delivered) {
                Log.d(TAG, "semantic input " + semantic + (down ? " DOWN" : " UP"));
            }
        }

        @Override
        public void onStatus(String code) {
            if (code == null) return;
            Log.i(TAG, "reader status=" + sanitizeCode(code));
        }
    };

    private final Runnable binderRetry = new Runnable() {
        @Override
        public void run() {
            if (!active || destroyed || remote != null || bindRequested.get()) return;
            continueStart();
        }
    };

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        binderRetryCount = 0;
        if (active && !destroyed) continueStart();
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        remote = null;
        bindRequested.set(false);
        serviceBound.set(false);
        motorDriver.cancel();
        Log.w(TAG, "GSB-SHOULDER-BINDER-DEAD");
        if (active && !destroyed) scheduleBinderRetry();
    };

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            (requestCode, grantResult) -> {
                if (requestCode != REQUEST_CODE) return;
                permissionRequestInFlight = false;
                if (!active || destroyed) return;
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Shizuku shoulder calibration permission granted");
                    continueStart();
                } else {
                    Log.w(TAG, "GSB-SHOULDER-SHIZUKU-DENIED");
                }
            };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            bindRequested.set(true);
            serviceBound.set(true);
            if (!active || destroyed || binder == null || !binder.pingBinder()) {
                unbindService(true);
                return;
            }
            remote = IRedMagicShoulderReader.Stub.asInterface(binder);
            try {
                int sceneResult = remote.enableCalibrationScene();
                if (sceneResult != 0) {
                    Log.w(TAG, "GSB-SHOULDER-SCENE-ACTIVATE-FAILED result=" + sceneResult);
                    stopAndRestoreRemote();
                    unbindService(true);
                    return;
                }
                Log.i(TAG, "temporary REDMAGIC shoulder scene active");
                motorDriver.onLinkReady();

                String devices = remote.detectDevices();
                if (devices == null || devices.trim().isEmpty()) {
                    Log.w(TAG, "GSB-SHOULDER-SAR-NOT-FOUND");
                } else {
                    Log.i(TAG, "trusted SAR nodes ready count=" + devices.split(",").length);
                }
                remote.startReading(callback);
            } catch (Throwable t) {
                Log.w(TAG, "GSB-SHOULDER-READER-START-FAILED", t);
                motorDriver.cancel();
                stopAndRestoreRemote();
                unbindService(true);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            remote = null;
            bindRequested.set(false);
            serviceBound.set(false);
            motorDriver.cancel();
            if (active && !destroyed) {
                Log.w(TAG, "GSB-SHOULDER-BINDER-DISCONNECTED");
                scheduleBinderRetry();
            }
        }
    };

    private final Runnable calibrationWatch = new Runnable() {
        @Override
        public void run() {
            if (!active || destroyed) return;
            if (isCalibrated()) {
                Log.i(TAG, "calibration complete; restoring REDMAGIC scene and stopping reader");
                deactivate();
                return;
            }
            main.postDelayed(this, CALIBRATION_POLL_MS);
        }
    };

    public RedMagicShoulderPrivilegedAdapter(Context context) {
        this.context = context.getApplicationContext();
        motorDriver = new HardwareAwakeningMotorDriver(this.context);
        serviceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(this.context, RedMagicShoulderReaderService.class)
        )
                .daemon(false)
                .processNameSuffix("gsb_shoulder")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE);

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
    }

    public void activate() {
        if (destroyed || isCalibrated()) return;
        if (active) return;
        active = true;
        binderRetryCount = 0;
        main.removeCallbacks(calibrationWatch);
        main.removeCallbacks(binderRetry);
        main.post(calibrationWatch);
        continueStart();
    }

    public void deactivate() {
        motorDriver.cancel();
        if (!active && remote == null && !bindRequested.get() && !serviceBound.get()) return;
        active = false;
        permissionRequestInFlight = false;
        binderRetryCount = 0;
        main.removeCallbacks(calibrationWatch);
        main.removeCallbacks(binderRetry);
        stopAndRestoreRemote();
        unbindService(true);
    }

    private void continueStart() {
        if (!active || destroyed || isCalibrated()) return;
        try {
            if (!Shizuku.pingBinder()) {
                if (binderRetryCount < MAX_BINDER_RETRIES) {
                    binderRetryCount++;
                    Log.i(TAG, "waiting for Shizuku Binder attempt=" + binderRetryCount);
                    scheduleBinderRetry();
                } else {
                    Log.w(TAG, "GSB-SHOULDER-SHIZUKU-UNAVAILABLE");
                }
                return;
            }
            binderRetryCount = 0;
            main.removeCallbacks(binderRetry);

            if (Shizuku.isPreV11()) {
                Log.w(TAG, "GSB-SHOULDER-SHIZUKU-UNSUPPORTED");
                return;
            }
            int permission = Shizuku.checkSelfPermission();
            if (permission != PackageManager.PERMISSION_GRANTED) {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    Log.w(TAG, "GSB-SHOULDER-SHIZUKU-DENIED");
                    return;
                }
                if (!permissionRequestInFlight) {
                    permissionRequestInFlight = true;
                    Log.i(TAG, "requesting Shizuku permission for REDMAGIC shoulder calibration");
                    Shizuku.requestPermission(REQUEST_CODE);
                }
                return;
            }
            Log.i(TAG, "Shizuku permission already granted; no prompt required");
            if (bindRequested.get() || serviceBound.get() || remote != null) return;
            Log.i(TAG, "binding REDMAGIC shoulder calibration UserService");
            bindRequested.set(true);
            try {
                Shizuku.bindUserService(serviceArgs, serviceConnection);
            } catch (Throwable t) {
                bindRequested.set(false);
                throw t;
            }
        } catch (Throwable t) {
            Log.w(TAG, "GSB-SHOULDER-SHIZUKU-CONNECT-FAILED", t);
        }
    }

    private void scheduleBinderRetry() {
        if (!active || destroyed) return;
        main.removeCallbacks(binderRetry);
        main.postDelayed(binderRetry, BINDER_RETRY_MS);
    }

    private void stopAndRestoreRemote() {
        IRedMagicShoulderReader reader = remote;
        if (reader == null) return;
        try {
            reader.stopReading();
        } catch (Throwable t) {
            Log.w(TAG, "shoulder reader stop failed", t);
        }
        try {
            int result = reader.restoreCalibrationScene();
            if (result != 0) {
                Log.w(TAG, "GSB-SHOULDER-SCENE-RESTORE-FAILED result=" + result);
            }
        } catch (Throwable t) {
            Log.w(TAG, "GSB-SHOULDER-SCENE-RESTORE-FAILED", t);
        }
    }

    private boolean isCalibrated() {
        return context.getSharedPreferences(PREFS_BOOT, Context.MODE_PRIVATE)
                .getBoolean(PREF_SHOULDER_CALIBRATED, false);
    }

    private void unbindService(boolean remove) {
        boolean hadRequest = bindRequested.getAndSet(false);
        boolean hadBound = serviceBound.getAndSet(false);
        if (!hadRequest && !hadBound && remote == null) return;
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.unbindUserService(serviceArgs, serviceConnection, remove);
            }
        } catch (Throwable t) {
            Log.w(TAG, "shoulder calibration UserService unbind skipped", t);
        } finally {
            remote = null;
        }
    }

    public void shutdown() {
        if (destroyed) return;
        deactivate();
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
    }

    private static String sanitizeCode(String code) {
        String safe = code.replaceAll("[^A-Z0-9._-]", "");
        return safe.length() > 96 ? safe.substring(0, 96) : safe;
    }
}
