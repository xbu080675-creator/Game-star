package com.xbu.esportscenter.platform.android;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.xbu.esportscenter.core.session.GameSessionManager;
import com.xbu.esportscenter.core.session.GameSessionState;

/**
 * Android adapter that binds validated launcher intents to the platform-neutral GameSession state.
 *
 * This adapter only accepts intents produced by InstalledGameCatalog's launcher allow-list. It does
 * not expose arbitrary Intent/Component/URI construction to WebView and does not use usage access,
 * accessibility, shell or privileged APIs.
 */
public final class AndroidGameSessionLauncher {
    private static final String TAG = "[GSB-GAME]";

    private final Activity host;
    private final InstalledGameCatalog catalog;
    private final GameSessionManager sessions;
    private boolean dispatchPending;

    public AndroidGameSessionLauncher(
            Activity host,
            InstalledGameCatalog catalog,
            GameSessionManager sessions
    ) {
        this.host = host;
        this.catalog = catalog;
        this.sessions = sessions;
    }

    /**
     * Returns true when the package is currently in the validated launcher allow-list and a launch
     * has been accepted for UI-thread dispatch. Actual Activity launch failures are rolled back.
     */
    public synchronized boolean requestLaunch(String packageName) {
        if (dispatchPending || packageName == null || packageName.isBlank()) return false;
        Intent launch = catalog.createValidatedLaunchIntent(packageName);
        if (launch == null) return false;

        dispatchPending = true;
        host.runOnUiThread(() -> {
            try {
                launchOnUiThread(packageName, launch);
            } finally {
                synchronized (AndroidGameSessionLauncher.this) {
                    dispatchPending = false;
                }
            }
        });
        return true;
    }

    /** Called by the host after it returns to the foreground from a running game. */
    public void onHostResumed() {
        GameSessionState state = sessions.getState();
        if (state == GameSessionState.RUNNING) {
            try {
                sessions.suspend();
                Log.i(TAG, "session suspended game=" + sessions.getGameId());
            } catch (IllegalStateException ignored) {
            }
        }
    }

    /** Called when Game Star Box leaves the foreground after a launch request. */
    public void onHostStopped() {
        GameSessionState state = sessions.getState();
        if (state == GameSessionState.LAUNCHING) {
            try {
                sessions.markRunning();
                Log.i(TAG, "session running game=" + sessions.getGameId());
            } catch (IllegalStateException ignored) {
            }
        }
    }

    private void launchOnUiThread(String packageName, Intent launch) {
        GameSessionState before = sessions.getState();
        String currentGame = sessions.getGameId();
        boolean resumeSame = before == GameSessionState.SUSPENDED && packageName.equals(currentGame);
        boolean alreadySameRunning = before == GameSessionState.RUNNING && packageName.equals(currentGame);

        try {
            if (!resumeSame && !alreadySameRunning) {
                normalizeForNewGame(packageName);
            }

            host.startActivity(launch);

            // A suspended same-game session already proved that the target ran before. Once Android
            // accepts the relaunch intent, restore RUNNING immediately; initial launches wait for
            // onHostStopped() so a rejected/no-op launch cannot be marked running prematurely.
            if (resumeSame) {
                sessions.markRunning();
            }
            Log.i(TAG, "launch accepted package=" + packageName + " state=" + sessions.getState());
        } catch (RuntimeException e) {
            rollbackFailedNewLaunch(packageName, before, currentGame);
            Log.w(TAG, "GSB-GAME-LAUNCH-FAILED package=" + packageName, e);
        }
    }

    private void normalizeForNewGame(String packageName) {
        GameSessionState state = sessions.getState();
        if (state == GameSessionState.ENDED) {
            sessions.reset();
            state = sessions.getState();
        }
        if (state == GameSessionState.PREPARING || state == GameSessionState.LAUNCHING) {
            throw new IllegalStateException("GSB-GAME-LAUNCH-BUSY:" + state);
        }
        if (state == GameSessionState.RUNNING || state == GameSessionState.SUSPENDED) {
            sessions.end();
            sessions.reset();
        }
        sessions.prepare(packageName);
        sessions.markLaunching();
    }

    private void rollbackFailedNewLaunch(
            String requestedPackage,
            GameSessionState before,
            String previousGame
    ) {
        try {
            if (before == GameSessionState.SUSPENDED && requestedPackage.equals(previousGame)) {
                return;
            }
            GameSessionState now = sessions.getState();
            if (now == GameSessionState.PREPARING || now == GameSessionState.LAUNCHING
                    || now == GameSessionState.RUNNING || now == GameSessionState.SUSPENDED) {
                sessions.end();
            }
            if (sessions.getState() == GameSessionState.ENDED) sessions.reset();
        } catch (RuntimeException ignored) {
        }
    }
}
