package com.xbu.esportscenter.core.boot;

/**
 * Platform-neutral AND gate for hardware completion, playground completion and hidden
 * provisioning readiness. Once opened, the gate stays open for the lifetime of the boot session.
 *
 * FAST boot is an explicit typed session mode. It bypasses the interactive playground by marking
 * playgroundReady at construction time instead of mutating presentation state or dropping input.
 */
public final class IgnitionGate {
    public enum SessionMode {
        INTERACTIVE,
        FAST
    }

    public static final class Snapshot {
        public final SessionMode mode;
        public final boolean hardwareReady;
        public final boolean playgroundReady;
        public final boolean provisioningReady;
        public final boolean open;

        Snapshot(
                SessionMode mode,
                boolean hardwareReady,
                boolean playgroundReady,
                boolean provisioningReady,
                boolean open
        ) {
            this.mode = mode;
            this.hardwareReady = hardwareReady;
            this.playgroundReady = playgroundReady;
            this.provisioningReady = provisioningReady;
            this.open = open;
        }
    }

    private final SessionMode mode;
    private boolean hardwareReady;
    private boolean playgroundReady;
    private boolean provisioningReady;
    private boolean open;

    public IgnitionGate(SessionMode mode) {
        if (mode == null) throw new IllegalArgumentException("mode");
        this.mode = mode;
        playgroundReady = mode == SessionMode.FAST;
    }

    public synchronized Snapshot setHardwareReady(boolean ready) {
        hardwareReady = ready;
        evaluateLocked();
        return snapshotLocked();
    }

    public synchronized Snapshot setPlaygroundReady(boolean ready) {
        if (mode == SessionMode.INTERACTIVE) {
            playgroundReady = ready;
        } else {
            playgroundReady = true;
        }
        evaluateLocked();
        return snapshotLocked();
    }

    public synchronized Snapshot setProvisioningReady(boolean ready) {
        provisioningReady = ready;
        evaluateLocked();
        return snapshotLocked();
    }

    public synchronized Snapshot snapshot() {
        return snapshotLocked();
    }

    private void evaluateLocked() {
        if (!open && hardwareReady && playgroundReady && provisioningReady) open = true;
    }

    private Snapshot snapshotLocked() {
        return new Snapshot(mode, hardwareReady, playgroundReady, provisioningReady, open);
    }
}
