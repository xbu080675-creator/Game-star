package com.xbu.esportscenter;

import android.app.Application;
import android.os.SystemClock;
import android.util.Log;

import com.xbu.esportscenter.core.boot.BootOrchestrator;
import com.xbu.esportscenter.core.boot.BootPhase;
import com.xbu.esportscenter.core.capability.CapabilityAvailability;
import com.xbu.esportscenter.core.capability.CapabilityRegistry;
import com.xbu.esportscenter.core.capability.GameCapabilityIds;
import com.xbu.esportscenter.core.entry.GameEntryCoordinator;
import com.xbu.esportscenter.core.entry.GameEntrySource;
import com.xbu.esportscenter.core.session.GameSessionManager;
import com.xbu.esportscenter.platform.redmagic.RedMagicCapabilityProbe;
import com.xbu.esportscenter.platform.redmagic.RedMagicCapabilityProbeSnapshot;
import com.xbu.esportscenter.platform.redmagic.RedMagicEntryMonitor;
import com.xbu.esportscenter.platform.redmagic.RedMagicSnapshot;

/** Process-level backend bootstrap. */
public final class GameStarBoxApplication extends Application {
    private static final String TAG_CORE = "[GSB-CORE]";
    private static final String TAG_ENTRY = "[GSB-ENTRY]";
    private static final String TAG_BOOT = "[GSB-BOOT]";
    private static final String TAG_RM = "[GSB-RM]";
    private static final String CAPABILITY_REDMAGIC_ENTRY_EDGE = "platform.redmagic.entry.edge";
    private static final String CAPABILITY_REDMAGIC_NATIVE_STACK = "platform.redmagic.native_game_stack.evidence";
    private static final String CAPABILITY_REDMAGIC_FAN_NODE = "platform.redmagic.fan_node.evidence";

    private final BootOrchestrator boot = new BootOrchestrator();
    private final CapabilityRegistry capabilities = new CapabilityRegistry();
    private final GameSessionManager gameSessions = new GameSessionManager();
    private final GameEntryCoordinator gameEntry = new GameEntryCoordinator();
    private RedMagicEntryMonitor redMagicEntryMonitor;
    private RedMagicCapabilityProbe redMagicCapabilityProbe;
    private volatile RedMagicSnapshot lastRedMagicSnapshot;
    private volatile RedMagicCapabilityProbeSnapshot lastCapabilityProbe;
    private boolean redMagicSwitchInitialized;
    private Integer previousRedMagicSwitch;

    @Override
    public void onCreate() {
        super.onCreate();

        boot.addListener(snapshot -> Log.i(
                TAG_BOOT,
                "phase=" + snapshot.phase
                        + " progress=" + snapshot.overallProgress
                        + " blockingReady=" + snapshot.blockingReady
        ));
        boot.complete(BootPhase.CORE_INIT);

        gameSessions.addListener((previous, current, gameId) ->
                Log.i(TAG_CORE, "session " + previous + " -> " + current + " game=" + gameId));
        boot.complete(BootPhase.SESSION_RUNTIME_READY);

        gameEntry.addListener(request -> Log.i(
                TAG_ENTRY,
                "GSB-ENTRY-REQUEST seq=" + request.sequence + " source=" + request.source
        ));

        boot.complete(BootPhase.CAPABILITY_REGISTRY_READY);

        redMagicCapabilityProbe = new RedMagicCapabilityProbe(this);
        redMagicEntryMonitor = new RedMagicEntryMonitor(this, this::onRedMagicSnapshot);
        try {
            redMagicEntryMonitor.start();
        } catch (RuntimeException e) {
            capabilities.publish(new CapabilityRegistry.Entry(
                    RedMagicEntryMonitor.CAPABILITY_ID,
                    CapabilityAvailability.UNAVAILABLE,
                    "GSB-RM-ENTRY-MONITOR-START-FAILED"
            ));
            capabilities.publish(new CapabilityRegistry.Entry(
                    CAPABILITY_REDMAGIC_ENTRY_EDGE,
                    CapabilityAvailability.UNAVAILABLE,
                    "GSB-RM-ENTRY-EDGE-MONITOR-UNAVAILABLE"
            ));
            publishSemanticSurvey(false, false);
            boot.complete(BootPhase.REDMAGIC_PROBE);
            Log.w(TAG_CORE, "GSB-RM-ENTRY-MONITOR-START-FAILED", e);
        }
    }

    private synchronized void onRedMagicSnapshot(RedMagicSnapshot snapshot) {
        lastRedMagicSnapshot = snapshot;
        lastCapabilityProbe = redMagicCapabilityProbe.probe(snapshot);
        publishCapabilityEvidence(lastCapabilityProbe);
        boot.complete(BootPhase.REDMAGIC_PROBE);

        CapabilityAvailability availability;
        String detail;

        if (!snapshot.likelyRedMagic) {
            availability = CapabilityAvailability.UNSUPPORTED;
            detail = "GSB-RM-ENTRY-NON-REDMAGIC";
        } else if (snapshot.gameSpaceSwitch == null
                && snapshot.gameScene == null
                && snapshot.gameMode == null) {
            availability = CapabilityAvailability.UNAVAILABLE;
            detail = "GSB-RM-ENTRY-SETTINGS-NOT-EXPOSED";
        } else {
            availability = CapabilityAvailability.AVAILABLE;
            detail = "GSB-RM-ENTRY-READ-AVAILABLE";
        }

        capabilities.publish(new CapabilityRegistry.Entry(
                RedMagicEntryMonitor.CAPABILITY_ID,
                availability,
                detail
        ));

        if (!snapshot.likelyRedMagic || snapshot.gameSpaceSwitch == null) {
            capabilities.publish(new CapabilityRegistry.Entry(
                    CAPABILITY_REDMAGIC_ENTRY_EDGE,
                    snapshot.likelyRedMagic ? CapabilityAvailability.UNAVAILABLE : CapabilityAvailability.UNSUPPORTED,
                    snapshot.likelyRedMagic
                            ? "GSB-RM-ENTRY-EDGE-SWITCH-NOT-EXPOSED"
                            : "GSB-RM-ENTRY-NON-REDMAGIC"
            ));
            redMagicSwitchInitialized = false;
            previousRedMagicSwitch = null;
            return;
        }

        capabilities.publish(new CapabilityRegistry.Entry(
                CAPABILITY_REDMAGIC_ENTRY_EDGE,
                CapabilityAvailability.AVAILABLE,
                "GSB-RM-ENTRY-EDGE-AVAILABLE"
        ));

        Integer currentSwitch = snapshot.gameSpaceSwitch;
        if (!redMagicSwitchInitialized) {
            redMagicSwitchInitialized = true;
            previousRedMagicSwitch = currentSwitch;
            Log.i(TAG_ENTRY, "GSB-RM-ENTRY-EDGE-BASELINE switch=" + currentSwitch);
            return;
        }

        Integer previousSwitch = previousRedMagicSwitch;
        previousRedMagicSwitch = currentSwitch;

        // REDMAGIC convention observed on current ROM: 0 = competitive/game-space switch ON.
        // Only emit on a real non-zero -> 0 edge. App startup while already ON must not create
        // a synthetic entry request.
        if (previousSwitch != null && previousSwitch != 0 && currentSwitch == 0) {
            gameEntry.request(
                    GameEntrySource.REDMAGIC_COMPETITIVE_SWITCH,
                    SystemClock.elapsedRealtime()
            );
        }
    }

    private void publishCapabilityEvidence(RedMagicCapabilityProbeSnapshot probe) {
        if (probe == null) return;

        capabilities.publish(new CapabilityRegistry.Entry(
                CAPABILITY_REDMAGIC_NATIVE_STACK,
                probe.likelyRedMagic && probe.hasNativeGameStackEvidence()
                        ? CapabilityAvailability.AVAILABLE
                        : (probe.likelyRedMagic ? CapabilityAvailability.UNAVAILABLE : CapabilityAvailability.UNSUPPORTED),
                probe.hasNativeGameStackEvidence()
                        ? "GSB-RM-SURVEY-NATIVE-STACK-EVIDENCE"
                        : (probe.likelyRedMagic ? "GSB-RM-SURVEY-NATIVE-STACK-NOT-OBSERVED" : "GSB-RM-SURVEY-NON-REDMAGIC")
        ));

        capabilities.publish(new CapabilityRegistry.Entry(
                CAPABILITY_REDMAGIC_FAN_NODE,
                probe.likelyRedMagic && probe.hasFanEvidence()
                        ? CapabilityAvailability.AVAILABLE
                        : (probe.likelyRedMagic ? CapabilityAvailability.UNAVAILABLE : CapabilityAvailability.UNSUPPORTED),
                probe.hasFanEvidence()
                        ? "GSB-RM-SURVEY-FAN-NODE-EVIDENCE"
                        : (probe.likelyRedMagic ? "GSB-RM-SURVEY-FAN-NODE-NOT-OBSERVED" : "GSB-RM-SURVEY-NON-REDMAGIC")
        ));

        publishSemanticSurvey(probe.likelyRedMagic, probe.hasNativeGameStackEvidence());

        Log.i(TAG_RM,
                "survey nativeStack=" + probe.hasNativeGameStackEvidence()
                        + " gameSpace=" + probe.gameSpacePackagePresent
                        + " gameAssist=" + probe.gameAssistPackagePresent
                        + " settings=" + probe.gameSettingsExposed
                        + " fanEnable=" + probe.fanEnableNodePresent
                        + " fanSpeed=" + probe.fanSpeedNodePresent);
    }

    private void publishSemanticSurvey(boolean likelyRedMagic, boolean nativeStackEvidence) {
        String detail;
        CapabilityAvailability state;
        if (!likelyRedMagic) {
            state = CapabilityAvailability.UNSUPPORTED;
            detail = "GSB-RM-SURVEY-NON-REDMAGIC";
        } else {
            state = CapabilityAvailability.UNKNOWN;
            detail = nativeStackEvidence
                    ? "GSB-RM-SURVEY-CONTROL-PATH-NOT-YET-VERIFIED"
                    : "GSB-RM-SURVEY-NATIVE-EVIDENCE-NOT-OBSERVED";
        }

        String[] semanticIds = {
                GameCapabilityIds.NETWORK_BOOST,
                GameCapabilityIds.PERFORMANCE_BOOST,
                GameCapabilityIds.MEMORY_CLEANUP,
                GameCapabilityIds.INTERRUPTION_SHIELD,
                GameCapabilityIds.FPS_MONITOR,
                GameCapabilityIds.CROSSHAIR,
                GameCapabilityIds.FAN_CONTROL,
                GameCapabilityIds.CHARGE_SEPARATION,
                GameCapabilityIds.SHOULDER_MAPPING,
                GameCapabilityIds.SCREEN_RECORD
        };
        for (String id : semanticIds) {
            capabilities.publish(new CapabilityRegistry.Entry(id, state, detail));
        }
    }

    public BootOrchestrator getBootOrchestrator() {
        return boot;
    }

    public CapabilityRegistry getCapabilities() {
        return capabilities;
    }

    public GameSessionManager getGameSessions() {
        return gameSessions;
    }

    public GameEntryCoordinator getGameEntry() {
        return gameEntry;
    }

    public RedMagicSnapshot getLastRedMagicSnapshot() {
        return lastRedMagicSnapshot;
    }

    public RedMagicCapabilityProbeSnapshot getLastCapabilityProbe() {
        return lastCapabilityProbe;
    }
}
