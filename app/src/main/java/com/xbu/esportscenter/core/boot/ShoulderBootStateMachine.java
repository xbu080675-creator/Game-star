package com.xbu.esportscenter.core.boot;

/**
 * Platform-neutral first-boot hardware-awakening state machine.
 *
 * Core owns only semantic shoulder input and hold timing. Android/REDMAGIC adapters translate
 * physical input. Completing left/right calibration enters BOTH_HOLD, but the final L+R ignition
 * grip is disabled until the ordered Hardware Playground reaches FINAL_GRIP. Enabling the final
 * grip requires a fresh release-and-press if either shoulder was already held, so early input can
 * never pre-arm ignition.
 */
public final class ShoulderBootStateMachine implements ShoulderBootInputHub.Sink {
    public enum Side { LEFT, RIGHT }

    public enum Phase {
        LEFT_TAP,
        LEFT_HOLD,
        RIGHT_TAP,
        RIGHT_HOLD,
        BOTH_HOLD,
        ARMED,
        IGNITING,
        COMPLETE
    }

    public static final long TAP_MAX_MS = 700L;
    public static final long CALIBRATION_HOLD_MS = 1200L;
    public static final long IGNITION_HOLD_MS = 1450L;

    public static final class Snapshot {
        public final Phase phase;
        public final boolean leftDown;
        public final boolean rightDown;
        public final int progress;
        public final int level;
        public final boolean leftCalibrated;
        public final boolean rightCalibrated;
        public final boolean finalGripEnabled;
        public final boolean freshBothRequired;

        Snapshot(
                Phase phase,
                boolean leftDown,
                boolean rightDown,
                int progress,
                int level,
                boolean leftCalibrated,
                boolean rightCalibrated,
                boolean finalGripEnabled,
                boolean freshBothRequired
        ) {
            this.phase = phase;
            this.leftDown = leftDown;
            this.rightDown = rightDown;
            this.progress = progress;
            this.level = level;
            this.leftCalibrated = leftCalibrated;
            this.rightCalibrated = rightCalibrated;
            this.finalGripEnabled = finalGripEnabled;
            this.freshBothRequired = freshBothRequired;
        }
    }

    private Phase phase = Phase.LEFT_TAP;
    private boolean leftDown;
    private boolean rightDown;
    private boolean leftCalibrated;
    private boolean rightCalibrated;
    private boolean finalGripEnabled;
    private boolean freshBothRequired;
    private long leftDownAt;
    private long rightDownAt;
    private long bothDownAt;
    private int progress;
    private int level;
    private boolean holdSatisfied;

    public ShoulderBootStateMachine() {
        ShoulderBootInputHub.attach(this);
    }

    @Override
    public void onShoulderInput(Side side, boolean down, long nowMs) {
        onInput(side, down, nowMs);
    }

    public synchronized Snapshot snapshot() {
        return snapshotLocked();
    }

    public synchronized Snapshot onInput(Side side, boolean down, long nowMs) {
        if (phase == Phase.COMPLETE || phase == Phase.IGNITING || phase == Phase.ARMED) {
            if (side == Side.LEFT) leftDown = down;
            else rightDown = down;
            return snapshotLocked();
        }

        if (side == Side.LEFT) {
            if (leftDown == down) return snapshotLocked();
            leftDown = down;
            if (down) leftDownAt = nowMs;
        } else {
            if (rightDown == down) return snapshotLocked();
            rightDown = down;
            if (down) rightDownAt = nowMs;
        }

        switch (phase) {
            case LEFT_TAP:
                if (side == Side.LEFT && !down) {
                    long held = Math.max(0L, nowMs - leftDownAt);
                    if (held <= TAP_MAX_MS) {
                        phase = Phase.LEFT_HOLD;
                        resetProgressLocked();
                    }
                }
                break;
            case LEFT_HOLD:
                if (side == Side.LEFT && !down) {
                    if (holdSatisfied) {
                        leftCalibrated = true;
                        phase = Phase.RIGHT_TAP;
                    }
                    resetProgressLocked();
                }
                break;
            case RIGHT_TAP:
                if (side == Side.RIGHT && !down) {
                    long held = Math.max(0L, nowMs - rightDownAt);
                    if (held <= TAP_MAX_MS) {
                        phase = Phase.RIGHT_HOLD;
                        resetProgressLocked();
                    }
                }
                break;
            case RIGHT_HOLD:
                if (side == Side.RIGHT && !down) {
                    if (holdSatisfied) {
                        rightCalibrated = true;
                        phase = Phase.BOTH_HOLD;
                    }
                    resetProgressLocked();
                }
                break;
            case BOTH_HOLD:
                updateFinalGripInputLocked(nowMs);
                break;
            default:
                break;
        }
        return snapshotLocked();
    }

    public synchronized Snapshot tick(long nowMs) {
        switch (phase) {
            case LEFT_HOLD:
                updateSingleHoldLocked(leftDown, leftDownAt, nowMs, CALIBRATION_HOLD_MS);
                break;
            case RIGHT_HOLD:
                updateSingleHoldLocked(rightDown, rightDownAt, nowMs, CALIBRATION_HOLD_MS);
                break;
            case BOTH_HOLD:
                if (!finalGripEnabled || freshBothRequired) {
                    if (freshBothRequired && !leftDown && !rightDown) {
                        freshBothRequired = false;
                    }
                    resetProgressLocked();
                    bothDownAt = 0L;
                    break;
                }
                if (leftDown && rightDown) {
                    if (bothDownAt == 0L) bothDownAt = nowMs;
                    updateProgressLocked(nowMs - bothDownAt, IGNITION_HOLD_MS);
                    if (progress >= 100) {
                        phase = Phase.ARMED;
                        progress = 100;
                        level = 4;
                        leftCalibrated = true;
                        rightCalibrated = true;
                    }
                } else {
                    resetProgressLocked();
                    bothDownAt = 0L;
                }
                break;
            default:
                break;
        }
        return snapshotLocked();
    }

    /**
     * Enables the final ignition grip only after Core playground progression reaches FINAL_GRIP.
     * If either shoulder is already held, both must be released before a new hold may count.
     */
    public synchronized Snapshot setFinalGripEnabled(boolean enabled) {
        if (phase == Phase.COMPLETE || phase == Phase.IGNITING || phase == Phase.ARMED) {
            return snapshotLocked();
        }
        if (enabled == finalGripEnabled) return snapshotLocked();
        finalGripEnabled = enabled;
        bothDownAt = 0L;
        resetProgressLocked();
        freshBothRequired = enabled && (leftDown || rightDown);
        return snapshotLocked();
    }

    /**
     * Lifecycle-safe cancel: dropping the Activity must never count as a successful release.
     * ARMED is latched because the user already completed the physical ritual.
     */
    public synchronized Snapshot cancelActivePresses() {
        leftDown = false;
        rightDown = false;
        leftDownAt = 0L;
        rightDownAt = 0L;
        bothDownAt = 0L;
        freshBothRequired = false;
        if (phase != Phase.ARMED && phase != Phase.IGNITING && phase != Phase.COMPLETE) {
            resetProgressLocked();
        }
        return snapshotLocked();
    }

    /** Subsequent boots explicitly bypass the interactive playground and wait at ARMED. */
    public synchronized Snapshot startFastArmed() {
        leftCalibrated = true;
        rightCalibrated = true;
        finalGripEnabled = true;
        freshBothRequired = false;
        leftDown = false;
        rightDown = false;
        progress = 100;
        level = 4;
        phase = Phase.ARMED;
        return snapshotLocked();
    }

    /** Only the platform-neutral IgnitionGate owner may release ARMED -> IGNITING. */
    public synchronized Snapshot releaseIgnition() {
        if (phase != Phase.ARMED) return snapshotLocked();
        phase = Phase.IGNITING;
        progress = 100;
        level = 4;
        return snapshotLocked();
    }

    public synchronized Snapshot complete() {
        phase = Phase.COMPLETE;
        leftDown = false;
        rightDown = false;
        progress = 100;
        level = 0;
        finalGripEnabled = false;
        freshBothRequired = false;
        ShoulderBootInputHub.detach(this);
        return snapshotLocked();
    }

    private void updateFinalGripInputLocked(long nowMs) {
        if (!finalGripEnabled) {
            bothDownAt = 0L;
            resetProgressLocked();
            return;
        }
        if (freshBothRequired) {
            if (!leftDown && !rightDown) freshBothRequired = false;
            bothDownAt = 0L;
            resetProgressLocked();
            return;
        }
        if (leftDown && rightDown) {
            if (bothDownAt == 0L) bothDownAt = nowMs;
        } else {
            bothDownAt = 0L;
            resetProgressLocked();
        }
    }

    private void updateSingleHoldLocked(boolean down, long downAt, long nowMs, long targetMs) {
        if (!down) {
            resetProgressLocked();
            return;
        }
        updateProgressLocked(nowMs - downAt, targetMs);
        holdSatisfied = progress >= 100;
    }

    private void updateProgressLocked(long elapsed, long targetMs) {
        int next = (int) Math.max(0L, Math.min(100L, elapsed * 100L / targetMs));
        progress = next;
        if (next <= 0) level = 0;
        else if (next < 25) level = 1;
        else if (next < 50) level = 2;
        else if (next < 75) level = 3;
        else level = 4;
        if (next >= 100) holdSatisfied = true;
    }

    private void resetProgressLocked() {
        progress = 0;
        level = 0;
        holdSatisfied = false;
    }

    private Snapshot snapshotLocked() {
        return new Snapshot(
                phase,
                leftDown,
                rightDown,
                progress,
                level,
                leftCalibrated,
                rightCalibrated,
                finalGripEnabled,
                freshBothRequired
        );
    }
}
