package com.xbu.esportscenter.core.boot;

/**
 * Platform-neutral ordered progress for the first-boot Hardware Playground.
 *
 * Presentation may report semantic stage completion, but it cannot skip or reorder stages. The
 * final L+R ignition grip is enabled only after THERMAL has completed. FAST boot is an explicit
 * typed bypass and does not pretend the interactive playground was played.
 */
public final class HardwarePlaygroundProgress {
    public enum Stage {
        SHOULDERS,
        MOTION,
        GLASS,
        MECHANICS,
        THERMAL,
        FINAL_GRIP,
        COMPLETE
    }

    public enum SessionMode {
        INTERACTIVE,
        FAST
    }

    public static final class Snapshot {
        public final SessionMode mode;
        public final Stage stage;
        public final int completedStages;
        public final boolean finalGripEnabled;
        public final boolean complete;

        Snapshot(
                SessionMode mode,
                Stage stage,
                int completedStages,
                boolean finalGripEnabled,
                boolean complete
        ) {
            this.mode = mode;
            this.stage = stage;
            this.completedStages = completedStages;
            this.finalGripEnabled = finalGripEnabled;
            this.complete = complete;
        }
    }

    private final SessionMode mode;
    private Stage stage;
    private int completedStages;

    public HardwarePlaygroundProgress(SessionMode mode) {
        if (mode == null) throw new IllegalArgumentException("mode");
        this.mode = mode;
        if (mode == SessionMode.FAST) {
            stage = Stage.COMPLETE;
            completedStages = 6;
        } else {
            stage = Stage.SHOULDERS;
            completedStages = 0;
        }
    }

    public synchronized Snapshot complete(Stage completedStage) {
        if (mode == SessionMode.FAST || stage == Stage.COMPLETE) return snapshotLocked();
        if (completedStage == null || completedStage != stage) return snapshotLocked();

        switch (stage) {
            case SHOULDERS:
                stage = Stage.MOTION;
                break;
            case MOTION:
                stage = Stage.GLASS;
                break;
            case GLASS:
                stage = Stage.MECHANICS;
                break;
            case MECHANICS:
                stage = Stage.THERMAL;
                break;
            case THERMAL:
                stage = Stage.FINAL_GRIP;
                break;
            case FINAL_GRIP:
                stage = Stage.COMPLETE;
                break;
            default:
                break;
        }
        completedStages = Math.min(6, completedStages + 1);
        return snapshotLocked();
    }

    public synchronized Snapshot snapshot() {
        return snapshotLocked();
    }

    private Snapshot snapshotLocked() {
        return new Snapshot(
                mode,
                stage,
                completedStages,
                stage == Stage.FINAL_GRIP,
                stage == Stage.COMPLETE
        );
    }
}
