package com.xbu.esportscenter;

import android.app.Application;
import android.os.SystemClock;
import android.util.Log;

import com.xbu.esportscenter.core.capability.CapabilityAvailability;
import com.xbu.esportscenter.core.capability.CapabilityRegistry;
import com.xbu.esportscenter.core.entry.GameEntryCoordinator;
import com.xbu.esportscenter.core.entry.GameEntrySource;
import com.xbu.esportscenter.core.session.GameSessionManager;
import com.xbu.esportscenter.platform.redmagic.RedMagicEntryMonitor;
import com.xbu.esportscenter.platform.redmagic.RedMagicSnapshot;

/** Process-level backend bootstrap. */
public final class GameStarBoxApplication extends Application {
    private static final String TAG_CORE = "[GSB-CORE]";
    private static final String TAG_ENTRY = "[GSB-ENTRY]";
    private static final String CAPABILITY_REDMAGIC_ENTRY_EDGE = "platform.redmagic.entry.edge";

    private final CapabilityRegistry capabilities = new CapabilityRegistry();
    private final GameSessionManager gameSessions = new GameSessionManager();
    private final GameEntryCoordinator gameEntry = new GameEntryCoordinator();
    private RedMagicEntryMonitor redMagicEntryMonitor;
    private volatile RedMagicSnapshot lastRedMagicSnapshot;
    private boolean redMagicSwitchInitialized;
    private Integer previousRedMagicSwitch;

    @Override
    public void onCreate() {
        super.onCreate();

        gameSessions.addListener((previous, current, gameId) ->
                Log.i(TAG_CORE, "session " + previous + " -> " + current + " game=" + gameId));

        gameEntry.addListener(request -> Log.i(
                TAG_ENTRY,
                "GSB-ENTRY-REQUEST seq=" + request.sequence + " source=" + request.source
        ));

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
            Log.w(TAG_CORE, "GSB-RM-ENTRY-MONITOR-START-FAILED", e);
        }
    }

    private synchronized void onRedMagicSnapshot(RedMagicSnapshot snapshot) {
        lastRedMagicSnapshot = snapshot;
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
}
