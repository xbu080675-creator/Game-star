package com.xbu.esportscenter.core.boot;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Immutable platform-neutral startup snapshot. */
public final class BootSnapshot {
    public final BootPhase phase;
    public final int overallProgress;
    public final boolean blockingReady;
    public final Set<BootPhase> completed;

    BootSnapshot(BootPhase phase, int overallProgress, boolean blockingReady, Set<BootPhase> completed) {
        this.phase = phase;
        this.overallProgress = Math.max(0, Math.min(100, overallProgress));
        this.blockingReady = blockingReady;
        this.completed = Collections.unmodifiableSet(EnumSet.copyOf(completed));
    }
}
