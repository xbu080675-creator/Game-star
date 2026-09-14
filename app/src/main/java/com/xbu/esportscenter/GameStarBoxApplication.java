package com.xbu.esportscenter;

import android.app.Application;
import android.util.Log;

import com.xbu.esportscenter.core.capability.CapabilityAvailability;
import com.xbu.esportscenter.core.capability.CapabilityRegistry;
import com.xbu.esportscenter.core.session.GameSessionManager;
import com.xbu.esportscenter.platform.redmagic.RedMagicEntryMonitor;
import com.xbu.esportscenter.platform.redmagic.RedMagicSnapshot;

/** Process-level backend bootstrap. */
public final class GameStarBoxApplication extends Application {
    private static final String TAG_CORE = "[GSB-CORE]";

    private final CapabilityRegistry capabilities = new CapabilityRegistry();
    private final GameSessionManager gameSessions = new GameSessionManager();
    private RedMagicEntryMonitor redMagicEntryMonitor;
    private volatile RedMagicSnapshot lastRedMagicSnapshot;

    @Override
    public void onCreate() {
        super.onCreate();

        gameSessions.addListener((previous, current, gameId) ->
                Log.i(TAG_CORE, "session " + previous + " -> " + current + " game=" + gameId));

        redMagicEntryMonitor = new RedMagicEntryMonitor(this, this::onRedMagicSnapshot);
        try {
            redMagicEntryMonitor.start();
        } catch (RuntimeException e) {
            capabilities.publish(new CapabilityRegistry.Entry(
                    RedMagicEntryMonitor.CAPABILITY_ID,
                    CapabilityAvailability.UNAVAILABLE,
                    "GSB-RM-ENTRY-MONITOR-START-FAILED"
            ));
            Log.w(TAG_CORE, "GSB-RM-ENTRY-MONITOR-START-FAILED", e);
        }
    }

    private void onRedMagicSnapshot(RedMagicSnapshot snapshot) {
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
    }

    public CapabilityRegistry getCapabilities() {
        return capabilities;
    }

    public GameSessionManager getGameSessions() {
        return gameSessions;
    }

    public RedMagicSnapshot getLastRedMagicSnapshot() {
        return lastRedMagicSnapshot;
    }
}
