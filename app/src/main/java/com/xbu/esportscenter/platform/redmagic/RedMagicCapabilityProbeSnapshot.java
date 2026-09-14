package com.xbu.esportscenter.platform.redmagic;

/** Read-only evidence snapshot for REDMAGIC capability discovery. */
public final class RedMagicCapabilityProbeSnapshot {
    public final boolean likelyRedMagic;
    public final boolean gameSpacePackagePresent;
    public final boolean gameAssistPackagePresent;
    public final boolean gameSettingsExposed;
    public final boolean fanEnableNodePresent;
    public final boolean fanSpeedNodePresent;

    public RedMagicCapabilityProbeSnapshot(
            boolean likelyRedMagic,
            boolean gameSpacePackagePresent,
            boolean gameAssistPackagePresent,
            boolean gameSettingsExposed,
            boolean fanEnableNodePresent,
            boolean fanSpeedNodePresent
    ) {
        this.likelyRedMagic = likelyRedMagic;
        this.gameSpacePackagePresent = gameSpacePackagePresent;
        this.gameAssistPackagePresent = gameAssistPackagePresent;
        this.gameSettingsExposed = gameSettingsExposed;
        this.fanEnableNodePresent = fanEnableNodePresent;
        this.fanSpeedNodePresent = fanSpeedNodePresent;
    }

    public boolean hasNativeGameStackEvidence() {
        return gameSpacePackagePresent || gameAssistPackagePresent || gameSettingsExposed;
    }

    public boolean hasFanEvidence() {
        return fanEnableNodePresent || fanSpeedNodePresent;
    }
}
