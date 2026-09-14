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

import java.util.concurrent.atomic.AtomicBoolean;

import rikka.shizuku.Shizuku;

/**
 * Narrow application-side adapter for read-only REDMAGIC shoulder SAR input.
 *
 * No command, device path or key code enters from WebView/UI. The privileged service discovers
 * and validates Nubia SAR nodes itself and returns only LEFT/RIGHT DOWN/UP semantics.
 */
public final class RedMagicShoulderPrivilegedAdapter {
    private static final String TAG = "[GSB-SHOULDER]";
    private static final int REQUEST_CODE = 0x4754;
    private static final long CALIBRATION_POLL_MS = 300L;
    private static final String PREFS_BOOT = "gsb.boot.profile";
    private static final String PREF_SHOULDER_CALIBRATED = "shoulder_calibrated_v1";

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Shizuku.UserServiceArgs serviceArgs;
    private final AtomicBoolean bindRequested = new AtomicBoolean(false);
    private final AtomicBoolean serviceBound = new AtomicBoolean(false);

    private volatile IRedMagicShoulderReader remote;
    private volatile boolean active;
    private volatile boolean destroyed;
    private volatile boolean permissionRequestInFlight;

    private final IRedMagicShoulderCallback callback = new IRedMagicShoulderCallback.Stub() {
        @Override
        public void onTriggerEvent(int side, boolean down) {
            if (!active || destroyed) return;
            ShoulderBootStateMachine.Side semantic;
            if (side == 0) semantic = ShoulderBootStateMachine.Side.LEFT;
            else if (side == 1) semantic = ShoulderBootStateMachine.Side.RIGHT;
            else return;

            boolean delivered = ShoulderBootInputHub.publish(
                    semantic,
                    down,
                    SystemClock.uptimeMillis()
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

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        if (active && !destroyed) continueStart();
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        remote = null;
        bindRequested.set(false);
        serviceBound.set(false);
        Log.w(TAG, "GSB-SHOULDER-BINDER-DEAD");
    };

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            (requestCode, grantResult) -> {
                if (requestCode != REQUEST_CODE) return;
                permissionRequestInFlight = false;
                if (!active || destroyed) return;
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Shizuku shoulder-read permission granted");
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
                String devices = remote.detectDevices();
                if (devices == null || devices.trim().isEmpty()) {
                    Log.w(TAG, "GSB-SHOULDER-SAR-NOT-FOUND");
                } else {
                    Log.i(TAG, "trusted SAR nodes ready count=" + devices.split(",").length);
                }
                remote.startReading(callback);
            } catch (Throwable t) {
                Log.w(TAG, "GSB-SHOULDER-READER-START-FAILED", t);
                unbindService(true);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            remote = null;
            bindRequested.set(false);
            serviceBound.set(false);
            if (active && !destroyed) Log.w(TAG, "GSB-SHOULDER-BINDER-DISCONNECTED");
        }
    };

    private final Runnable calibrationWatch = new Runnable() {
        @Override
        public void run() {
            if (!active || destroyed) return;
            if (isCalibrated()) {
                Log.i(TAG, "calibration complete; stopping privileged shoulder reader");
                deactivate();
                return;
            }
            main.postDelayed(this, CALIBRATION_POLL_MS);
        }
    };

    public RedMagicShoulderPrivilegedAdapter(Context context) {
        this.context = context.getApplicationContext();
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
        main.removeCallbacks(calibrationWatch);
        main.post(calibrationWatch);
        continueStart();
    }

    public void deactivate() {
        if (!active && remote == null && !bindRequested.get() && !serviceBound.get()) return;
        active = false;
        permissionRequestInFlight = false;
        main.removeCallbacks(calibrationWatch);
        IRedMagicShoulderReader reader = remote;
        if (reader != null) {
            try {
                reader.stopReading();
            } catch (Throwable ignored) {
            }
        }
        unbindService(true);
    }

    private void continueStart() {
        if (!active || destroyed || isCalibrated()) return;
        try {
            if (!Shizuku.pingBinder()) {
                Log.i(TAG, "GSB-SHOULDER-SHIZUKU-UNAVAILABLE");
                return;
            }
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
                    Log.i(TAG, "requesting Shizuku permission for shoulder calibration reader");
                    Shizuku.requestPermission(REQUEST_CODE);
                }
                return;
            }
            if (bindRequested.get() || serviceBound.get() || remote != null) return;
            Log.i(TAG, "binding read-only REDMAGIC shoulder UserService");
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
            Log.w(TAG, "shoulder reader unbind skipped", t);
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
