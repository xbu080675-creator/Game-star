package com.xbu.esportscenter.core.boot;

/**
 * Platform-neutral AND gate for visible hardware completion and hidden provisioning readiness.
 * Once opened, the gate stays open for the lifetime of the boot session.
 */
public final class IgnitionGate {
    public static final class Snapshot {
        public final boolean hardwareReady;
        public final boolean provisioningReady;
        public final boolean open;

        Snapshot(boolean hardwareReady, boolean provisioningReady, boolean open) {
            this.hardwareReady = hardwareReady;
            this.provisioningReady = provisioningReady;
            this.open = open;
        }
    }

    private boolean hardwareReady;
    private boolean provisioningReady;
    private boolean open;

    public synchronized Snapshot setHardwareReady(boolean ready) {
        hardwareReady = ready;
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
        if (!open && hardwareReady && provisioningReady) open = true;
    }

    private Snapshot snapshotLocked() {
        return new Snapshot(hardwareReady, provisioningReady, open);
    }
}
