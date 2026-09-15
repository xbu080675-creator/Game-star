package com.xbu.esportscenter.platform.android;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.util.Log;

/**
 * Native motion source for the first-boot Hardware Playground.
 *
 * This adapter exposes only semantic orientation values to the presentation layer. It owns no
 * WebView, no privileged API and no vendor-specific control surface.
 */
public final class HardwarePlaygroundMotionAdapter implements SensorEventListener {
    private static final String TAG = "[GSB-PLAYGROUND]";
    private static final long MIN_DISPATCH_MS = 28L;

    public interface Listener {
        void onMotion(float pitchDegrees, float rollDegrees);
        void onUnavailable(String code);
    }

    private final SensorManager manager;
    private final Sensor sensor;
    private final Listener listener;
    private final float[] rotation = new float[9];
    private final float[] orientation = new float[3];

    private boolean running;
    private long lastDispatchMs;

    public HardwarePlaygroundMotionAdapter(Context context, Listener listener) {
        Context app = context.getApplicationContext();
        manager = (SensorManager) app.getSystemService(Context.SENSOR_SERVICE);
        this.listener = listener;

        Sensor resolved = null;
        if (manager != null) {
            resolved = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (resolved == null) {
                resolved = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            }
        }
        sensor = resolved;

        if (sensor == null) {
            Log.w(TAG, "GSB-PLAYGROUND-MOTION-UNAVAILABLE");
        } else {
            Log.i(TAG, "motion sensor ready type=" + sensor.getType() + " name=" + sensor.getName());
        }
    }

    public boolean isAvailable() {
        return manager != null && sensor != null;
    }

    public boolean start() {
        if (!isAvailable()) {
            if (listener != null) listener.onUnavailable("GSB-PLAYGROUND-MOTION-UNAVAILABLE");
            return false;
        }
        if (running) return true;
        running = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        lastDispatchMs = 0L;
        if (!running) {
            Log.w(TAG, "GSB-PLAYGROUND-MOTION-REGISTER-FAILED");
            if (listener != null) listener.onUnavailable("GSB-PLAYGROUND-MOTION-REGISTER-FAILED");
        } else {
            Log.i(TAG, "motion sampling started");
        }
        return running;
    }

    public void stop() {
        if (!running || manager == null) return;
        manager.unregisterListener(this);
        running = false;
        lastDispatchMs = 0L;
        Log.i(TAG, "motion sampling stopped");
    }

    public void shutdown() {
        stop();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!running || event == null || event.sensor == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastDispatchMs < MIN_DISPATCH_MS) return;
        lastDispatchMs = now;

        try {
            SensorManager.getRotationMatrixFromVector(rotation, event.values);
            SensorManager.getOrientation(rotation, orientation);
            float pitch = (float) Math.toDegrees(orientation[1]);
            float roll = (float) Math.toDegrees(orientation[2]);
            if (listener != null) listener.onMotion(pitch, roll);
        } catch (Throwable t) {
            Log.w(TAG, "GSB-PLAYGROUND-MOTION-DECODE-FAILED", t);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Orientation challenge is relative to the first sample, so no accuracy UI is needed.
    }
}
