package com.xbu.esportscenter.core.boot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Platform-neutral startup coordinator.
 *
 * Core only tracks semantic readiness. Platform code decides when a phase is complete.
 * Non-blocking work such as OTA checks, network data and future device discovery must not
 * participate in this coordinator.
 */
public final class BootOrchestrator {
    public interface Listener {
        void onBootSnapshot(BootSnapshot snapshot);
    }

    private static final BootPhase[] ORDER = {
            BootPhase.CORE_INIT,
            BootPhase.CAPABILITY_REGISTRY_READY,
            BootPhase.REDMAGIC_PROBE,
            BootPhase.SESSION_RUNTIME_READY,
            BootPhase.WEBVIEW_READY,
            BootPhase.BOOT_COMPLETE
    };

    private static final int[] PROGRESS = {15, 30, 50, 65, 88, 100};

    private final EnumSet<BootPhase> completed = EnumSet.noneOf(BootPhase.class);
    private final List<Listener> listeners = new ArrayList<>();

    public synchronized void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public synchronized void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void complete(BootPhase phase) {
        if (phase == null) throw new IllegalArgumentException("phase");
        BootSnapshot snapshot;
        List<Listener> copy;
        synchronized (this) {
            if (!completed.add(phase)) return;
            if (phase != BootPhase.BOOT_COMPLETE && isBlockingReadyLocked()) {
                completed.add(BootPhase.BOOT_COMPLETE);
            }
            snapshot = snapshotLocked();
            copy = new ArrayList<>(listeners);
        }
        for (Listener listener : copy) listener.onBootSnapshot(snapshot);
    }

    public synchronized BootSnapshot snapshot() {
        return snapshotLocked();
    }

    private boolean isBlockingReadyLocked() {
        return completed.contains(BootPhase.CORE_INIT)
                && completed.contains(BootPhase.CAPABILITY_REGISTRY_READY)
                && completed.contains(BootPhase.REDMAGIC_PROBE)
                && completed.contains(BootPhase.SESSION_RUNTIME_READY)
                && completed.contains(BootPhase.WEBVIEW_READY);
    }

    private BootSnapshot snapshotLocked() {
        BootPhase current = BootPhase.CORE_INIT;
        int progress = 0;
        for (int i = 0; i < ORDER.length; i++) {
            BootPhase phase = ORDER[i];
            if (completed.contains(phase)) {
                current = phase;
                progress = Math.max(progress, PROGRESS[i]);
            } else {
                break;
            }
        }
        boolean ready = isBlockingReadyLocked();
        if (ready) {
            current = BootPhase.BOOT_COMPLETE;
            progress = 100;
        }
        return new BootSnapshot(current, progress, ready, completed);
    }
}
