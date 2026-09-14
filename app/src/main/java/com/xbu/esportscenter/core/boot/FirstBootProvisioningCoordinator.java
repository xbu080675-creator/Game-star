package com.xbu.esportscenter.core.boot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Platform-neutral coordinator for work that must be ready before first-boot ignition is released.
 *
 * The visible hardware-awakening sequence and hidden provisioning run in parallel. This class only
 * tracks semantic readiness; it never performs Android, WebView, package-manager or network work.
 */
public final class FirstBootProvisioningCoordinator {
    public enum Phase {
        CORE_RUNTIME,
        GAME_CATALOG,
        MAIN_SURFACE
    }

    public interface Listener {
        void onProvisioningSnapshot(Snapshot snapshot);
    }

    public static final class Snapshot {
        public final EnumSet<Phase> completed;
        public final int overallProgress;
        public final boolean blockingReady;

        Snapshot(EnumSet<Phase> completed) {
            this.completed = EnumSet.copyOf(completed);
            this.overallProgress = completed.size() * 100 / Phase.values().length;
            this.blockingReady = completed.containsAll(EnumSet.allOf(Phase.class));
        }

        public boolean isComplete(Phase phase) {
            return completed.contains(phase);
        }
    }

    private final EnumSet<Phase> completed = EnumSet.noneOf(Phase.class);
    private final List<Listener> listeners = new ArrayList<>();

    public synchronized void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public synchronized void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void complete(Phase phase) {
        if (phase == null) throw new IllegalArgumentException("phase");
        Snapshot snapshot;
        List<Listener> copy;
        synchronized (this) {
            if (!completed.add(phase)) return;
            snapshot = snapshotLocked();
            copy = new ArrayList<>(listeners);
        }
        for (Listener listener : copy) listener.onProvisioningSnapshot(snapshot);
    }

    public synchronized boolean isComplete(Phase phase) {
        return completed.contains(phase);
    }

    public synchronized Snapshot snapshot() {
        return snapshotLocked();
    }

    private Snapshot snapshotLocked() {
        return new Snapshot(completed);
    }
}
