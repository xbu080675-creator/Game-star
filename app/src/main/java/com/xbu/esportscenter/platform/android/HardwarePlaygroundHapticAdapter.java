package com.xbu.esportscenter.platform.android;

import android.content.Context;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

/**
 * Three short physical material samples used by the first-boot Hardware Playground.
 *
 * Audio is intentionally absent. Every response from this adapter goes only to Android's physical
 * vibrator service so Bluetooth/headphone routing can never be mistaken for tactile feedback.
 */
public final class HardwarePlaygroundHapticAdapter {
    private static final String TAG = "[GSB-PLAY-HAPTIC]";
    private final Vibrator vibrator;

    public HardwarePlaygroundHapticAdapter(Context context) {
        Context app = context.getApplicationContext();
        Vibrator resolved = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager = app.getSystemService(VibratorManager.class);
                if (manager != null) resolved = manager.getDefaultVibrator();
            } else {
                resolved = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
            }
        } catch (Throwable t) {
            Log.w(TAG, "GSB-PLAY-HAPTIC-LOOKUP-FAILED", t);
        }
        vibrator = resolved;
        if (vibrator == null) {
            Log.w(TAG, "GSB-PLAY-HAPTIC-NO-VIBRATOR");
        }
    }

    public void playSample(int sample) {
        int safe = Math.max(0, Math.min(2, sample));
        VibrationEffect effect;
        boolean amplitude = hasAmplitudeControl();

        if (safe == 0) {
            // A crisp mechanical detent.
            effect = amplitude
                    ? VibrationEffect.createOneShot(28L, 165)
                    : VibrationEffect.createOneShot(28L, VibrationEffect.DEFAULT_AMPLITUDE);
        } else if (safe == 1) {
            // Ratchet / gear teeth: several light, separated impacts.
            effect = amplitude
                    ? VibrationEffect.createWaveform(
                            new long[]{0, 15, 19, 17, 18, 21, 16, 29},
                            new int[]{0, 75, 0, 105, 0, 140, 0, 185},
                            -1
                    )
                    : VibrationEffect.createWaveform(
                            new long[]{0, 15, 19, 17, 18, 21, 16, 29},
                            -1
                    );
        } else {
            // A heavier latch closing into the chassis.
            effect = amplitude
                    ? VibrationEffect.createWaveform(
                            new long[]{0, 18, 22, 42, 14, 74},
                            new int[]{0, 95, 0, 170, 0, 245},
                            -1
                    )
                    : VibrationEffect.createWaveform(
                            new long[]{0, 18, 22, 42, 14, 74},
                            -1
                    );
        }
        vibrate(effect, "sample-" + safe);
    }

    public void cancel() {
        if (vibrator == null) return;
        try {
            vibrator.cancel();
        } catch (Throwable t) {
            Log.w(TAG, "GSB-PLAY-HAPTIC-CANCEL-FAILED", t);
        }
    }

    private boolean hasAmplitudeControl() {
        try {
            return vibrator != null && vibrator.hasAmplitudeControl();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void vibrate(VibrationEffect effect, String reason) {
        Vibrator target = vibrator;
        if (target == null) {
            Log.w(TAG, "GSB-PLAY-HAPTIC-NO-VIBRATOR reason=" + reason);
            return;
        }
        try {
            if (!target.hasVibrator()) {
                Log.w(TAG, "GSB-PLAY-HAPTIC-NO-PHYSICAL-MOTOR reason=" + reason);
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                target.vibrate(
                        effect,
                        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK)
                );
            } else {
                target.vibrate(effect);
            }
            Log.i(TAG, "physical sample played reason=" + reason
                    + " amplitudeControl=" + target.hasAmplitudeControl());
        } catch (Throwable t) {
            Log.w(TAG, "GSB-PLAY-HAPTIC-VIBRATE-FAILED reason=" + reason, t);
        }
    }
}
