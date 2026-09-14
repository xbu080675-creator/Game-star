package com.xbu.esportscenter.core.boot;

/**
 * Platform-neutral first-boot shoulder calibration and ignition state machine.
 * Core owns only semantic input/hold timing. Android/REDMAGIC adapters translate hardware input.
 */
public final class ShoulderBootStateMachine {
    public enum Side { LEFT, RIGHT }

    public enum Phase {
        LEFT_TAP,
        LEFT_HOLD,
        RIGHT_TAP,
        RIGHT_HOLD,
        BOTH_HOLD,
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

        Snapshot(
                Phase phase,
                boolean leftDown,
                boolean rightDown,
                int progress,
                int level,
                boolean leftCalibrated,
                boolean rightCalibrated
        ) {
            this.phase = phase;
            this.leftDown = leftDown;
            this.rightDown = rightDown;
            this.progress = progress;
            this.level = level;
            this.leftCalibrated = leftCalibrated;
            this.rightCalibrated = rightCalibrated;
        }
    }

    private Phase phase = Phase.LEFT_TAP;
    private boolean leftDown;
    private boolean rightDown;
    private boolean leftCalibrated;
    private boolean rightCalibrated;
    private long leftDownAt;
    private long rightDownAt;
    private long bothDownAt;
    private int progress;
    private int level;
    private boolean holdSatisfied;

    public synchronized Snapshot snapshot() {
        return snapshotLocked();
    }

    public synchronized Snapshot onInput(Side side, boolean down, long nowMs) {
        if (phase == Phase.COMPLETE) return snapshotLocked();

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
                if (leftDown && rightDown) {
                    if (bothDownAt == 0L) bothDownAt = nowMs;
                } else {
                    bothDownAt = 0L;
                    resetProgressLocked();
                }
                break;
            case IGNITING:
            case COMPLETE:
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
                if (leftDown && rightDown) {
                    if (bothDownAt == 0L) bothDownAt = nowMs;
                    updateProgressLocked(nowMs - bothDownAt, IGNITION_HOLD_MS);
                    if (progress >= 100) {
                        phase = Phase.IGNITING;
                        progress = 100;
                        level = 4;
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

    public synchronized Snapshot startFastIgnition() {
        leftCalibrated = true;
        rightCalibrated = true;
        leftDown = false;
        rightDown = false;
        progress = 100;
        level = 4;
        phase = Phase.IGNITING;
        return snapshotLocked();
    }

    public synchronized Snapshot complete() {
        phase = Phase.COMPLETE;
        leftDown = false;
        rightDown = false;
        progress = 100;
        level = 0;
        return snapshotLocked();
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
                rightCalibrated
        );
    }
}
