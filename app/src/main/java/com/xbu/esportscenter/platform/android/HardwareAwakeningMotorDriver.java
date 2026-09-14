package com.xbu.esportscenter.platform.android;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import com.xbu.esportscenter.core.boot.ShoulderBootStateMachine;

/**
 * Physical motor driver for Hardware Awakening.
 *
 * This class deliberately owns no audio. Audible boot design must never be used as a substitute
 * for tactile feedback. The only output of this adapter is the Android vibrator service.
 *
 * REDMAGIC shoulder input is semantic by the time it enters here: LEFT/RIGHT + DOWN/UP only.
 * No Shizuku, device path, Settings or vendor command leaks into this adapter.
 */
public final class HardwareAwakeningMotorDriver {
    private static final String TAG = "[GSB-HAPTIC]";
    private static final long TAP_MAX_MS = ShoulderBootStateMachine.TAP_MAX_MS;
    private static final long HOLD_MS = ShoulderBootStateMachine.CALIBRATION_HOLD_MS;
    private static final long BOTH_HOLD_MS = ShoulderBootStateMachine.IGNITION_HOLD_MS;

    private enum Step {
        LEFT_TAP,
        LEFT_HOLD,
        RIGHT_TAP,
        RIGHT_HOLD,
        BOTH_HOLD,
        ARMED
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Vibrator vibrator;

    private Step step = Step.LEFT_TAP;
    private boolean leftDown;
    private boolean rightDown;
    private long leftDownAt;
    private long rightDownAt;
    private long bothDownAt;
    private int lastLevel;
    private boolean running;

    private final Runnable holdTick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long now = SystemClock.uptimeMillis();
            if (step == Step.LEFT_HOLD && leftDown) {
                updateHold(now - leftDownAt, HOLD_MS, false);
            } else if (step == Step.RIGHT_HOLD && rightDown) {
                updateHold(now - rightDownAt, HOLD_MS, false);
            } else if (step == Step.BOTH_HOLD && leftDown && rightDown) {
                if (bothDownAt == 0L) bothDownAt = now;
                long elapsed = Math.max(0L, now - bothDownAt);
                updateHold(elapsed, BOTH_HOLD_MS, true);
                if (elapsed >= BOTH_HOLD_MS) {
                    step = Step.ARMED;
                    stopRepeatingMotor();
                    playIgnition();
                    Log.i(TAG, "hardware sequence ARMED motor-side");
                    return;
                }
            } else {
                stopRepeatingMotor();
            }
            main.postDelayed(this, 70L);
        }
    };

    public HardwareAwakeningMotorDriver(Context context) {
        Context app = context.getApplicationContext();
        Vibrator resolved = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager = app.getSystemService(VibratorManager.class);
                if (manager != null) {
                    resolved = manager.getDefaultVibrator();
                    try {
                        int[] ids = manager.getVibratorIds();
                        Log.i(TAG, "VibratorManager ids=" + ids.length);
                    } catch (Throwable t) {
                        Log.w(TAG, "GSB-HAPTIC-VIBRATOR-ID-PROBE-FAILED", t);
                    }
                }
            } else {
                resolved = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
            }
        } catch (Throwable t) {
            Log.w(TAG, "GSB-HAPTIC-VIBRATOR-LOOKUP-FAILED", t);
        }
        vibrator = resolved;
        if (vibrator == null) {
            Log.w(TAG, "GSB-HAPTIC-NO-VIBRATOR-SERVICE");
        } else {
            try {
                Log.i(TAG, "motor ready hasVibrator=" + vibrator.hasVibrator()
                        + " amplitudeControl=" + vibrator.hasAmplitudeControl());
            } catch (Throwable t) {
                Log.w(TAG, "GSB-HAPTIC-CAPABILITY-PROBE-FAILED", t);
            }
        }
    }

    /** Small physical confirmation when the REDMAGIC SAR scene becomes available. */
    public void onLinkReady() {
        running = true;
        step = Step.LEFT_TAP;
        leftDown = false;
        rightDown = false;
        lastLevel = 0;
        bothDownAt = 0L;
        main.removeCallbacks(holdTick);
        playOneShot(34L, 120, "sar-link-ready");
    }

    public void onShoulderInput(ShoulderBootStateMachine.Side side, boolean down, long nowMs) {
        if (!running || side == null) return;

        if (side == ShoulderBootStateMachine.Side.LEFT) {
            if (leftDown == down) return;
            leftDown = down;
            if (down) leftDownAt = nowMs;
        } else {
            if (rightDown == down) return;
            rightDown = down;
            if (down) rightDownAt = nowMs;
        }

        switch (step) {
            case LEFT_TAP:
                if (side == ShoulderBootStateMachine.Side.LEFT && down) {
                    playClick("left-tap-down");
                } else if (side == ShoulderBootStateMachine.Side.LEFT && !down
                        && Math.max(0L, nowMs - leftDownAt) <= TAP_MAX_MS) {
                    step = Step.LEFT_HOLD;
                    lastLevel = 0;
                    playOneShot(28L, 135, "left-module-awake");
                }
                break;
            case LEFT_HOLD:
                if (side == ShoulderBootStateMachine.Side.LEFT && down) {
                    lastLevel = 0;
                    startHoldTicker();
                } else if (side == ShoulderBootStateMachine.Side.LEFT && !down) {
                    long held = Math.max(0L, nowMs - leftDownAt);
                    stopRepeatingMotor();
                    if (held >= HOLD_MS) {
                        step = Step.RIGHT_TAP;
                        playOneShot(52L, 205, "left-module-locked");
                    }
                }
                break;
            case RIGHT_TAP:
                if (side == ShoulderBootStateMachine.Side.RIGHT && down) {
                    playClick("right-tap-down");
                } else if (side == ShoulderBootStateMachine.Side.RIGHT && !down
                        && Math.max(0L, nowMs - rightDownAt) <= TAP_MAX_MS) {
                    step = Step.RIGHT_HOLD;
                    lastLevel = 0;
                    playOneShot(28L, 135, "right-module-awake");
                }
                break;
            case RIGHT_HOLD:
                if (side == ShoulderBootStateMachine.Side.RIGHT && down) {
                    lastLevel = 0;
                    startHoldTicker();
                } else if (side == ShoulderBootStateMachine.Side.RIGHT && !down) {
                    long held = Math.max(0L, nowMs - rightDownAt);
                    stopRepeatingMotor();
                    if (held >= HOLD_MS) {
                        step = Step.BOTH_HOLD;
                        bothDownAt = 0L;
                        playOneShot(52L, 205, "right-module-locked");
                    }
                }
                break;
            case BOTH_HOLD:
                if (leftDown && rightDown) {
                    bothDownAt = nowMs;
                    lastLevel = 0;
                    startHoldTicker();
                } else if (!down) {
                    bothDownAt = 0L;
                    lastLevel = 0;
                    stopRepeatingMotor();
                }
                break;
            case ARMED:
                break;
        }
    }

    /** Strong physical ignition waveform. No speaker/audio path is involved. */
    public void playIgnition() {
        if (!running) return;
        VibrationEffect effect;
        if (hasAmplitudeControl()) {
            effect = VibrationEffect.createWaveform(
                    new long[]{0, 70, 24, 90, 18, 120, 12, 165, 8, 230},
                    new int[]{0, 95, 0, 135, 0, 175, 0, 220, 0, 255},
                    -1
            );
        } else {
            effect = VibrationEffect.createWaveform(
                    new long[]{0, 70, 24, 90, 18, 120, 12, 165, 8, 230},
                    -1
            );
        }
        vibrate(effect, "ignition");
    }

    public void cancel() {
        running = false;
        main.removeCallbacks(holdTick);
        stopRepeatingMotor();
        leftDown = false;
        rightDown = false;
        bothDownAt = 0L;
        lastLevel = 0;
    }

    private void startHoldTicker() {
        main.removeCallbacks(holdTick);
        main.post(holdTick);
    }

    private void updateHold(long elapsed, long targetMs, boolean both) {
        int level;
        long clamped = Math.max(0L, Math.min(targetMs, elapsed));
        int progress = (int) (clamped * 100L / Math.max(1L, targetMs));
        if (progress < 25) level = 1;
        else if (progress < 50) level = 2;
        else if (progress < 75) level = 3;
        else level = 4;
        if (level == lastLevel) return;
        lastLevel = level;
        startRepeatingLevel(level, both);
    }

    private void startRepeatingLevel(int level, boolean both) {
        int safe = Math.max(1, Math.min(4, level));
        long[] on = both
                ? new long[]{24L, 31L, 40L, 52L}
                : new long[]{18L, 25L, 33L, 43L};
        long[] off = both
                ? new long[]{60L, 38L, 22L, 10L}
                : new long[]{76L, 50L, 30L, 16L};
        int[] amp = both
                ? new int[]{82, 130, 185, 235}
                : new int[]{60, 105, 155, 210};

        VibrationEffect effect;
        if (hasAmplitudeControl()) {
            effect = VibrationEffect.createWaveform(
                    new long[]{0L, on[safe - 1], off[safe - 1]},
                    new int[]{0, amp[safe - 1], 0},
                    1
            );
        } else {
            effect = VibrationEffect.createWaveform(
                    new long[]{0L, on[safe - 1], off[safe - 1]},
                    1
            );
        }
        vibrate(effect, (both ? "both" : "module") + "-hold-level-" + safe);
    }

    private void playClick(String reason) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && vibrator != null
                    && vibrator.areAllEffectsSupported(VibrationEffect.EFFECT_CLICK)
                    == Vibrator.VIBRATION_EFFECT_SUPPORT_YES) {
                vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK), reason);
                return;
            }
        } catch (Throwable t) {
            Log.w(TAG, "GSB-HAPTIC-EFFECT-PROBE-FAILED", t);
        }
        playOneShot(24L, 145, reason);
    }

    private void playOneShot(long durationMs, int amplitude, String reason) {
        VibrationEffect effect = hasAmplitudeControl()
                ? VibrationEffect.createOneShot(durationMs, Math.max(1, Math.min(255, amplitude)))
                : VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE);
        vibrate(effect, reason);
    }

    private boolean hasAmplitudeControl() {
        try {
            return vibrator != null && vibrator.hasAmplitudeControl();
        } catch (Throwable t) {
            return false;
        }
    }

    private void vibrate(VibrationEffect effect, String reason) {
        Vibrator target = vibrator;
        if (target == null) {
            Log.w(TAG, "GSB-HAPTIC-NO-VIBRATOR-SERVICE reason=" + reason);
            return;
        }
        try {
            if (!target.hasVibrator()) {
                Log.w(TAG, "GSB-HAPTIC-NO-PHYSICAL-MOTOR reason=" + reason);
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
            Log.i(TAG, "motor vibrate reason=" + reason
                    + " amplitudeControl=" + target.hasAmplitudeControl());
        } catch (Throwable t) {
            Log.w(TAG, "GSB-HAPTIC-VIBRATE-FAILED reason=" + reason, t);
        }
    }

    private void stopRepeatingMotor() {
        Vibrator target = vibrator;
        if (target == null) return;
        try {
            target.cancel();
        } catch (Throwable t) {
            Log.w(TAG, "GSB-HAPTIC-CANCEL-FAILED", t);
        }
    }
}
