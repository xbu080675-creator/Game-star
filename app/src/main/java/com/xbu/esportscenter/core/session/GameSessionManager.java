package com.xbu.esportscenter.core.session;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Platform-neutral owner of the semantic game session lifecycle.
 * Platform adapters may request transitions; Android/vendor APIs stay outside Core.
 */
public final class GameSessionManager {
    public interface Listener {
        void onStateChanged(GameSessionState previous, GameSessionState current, String gameId);
    }

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private GameSessionState state = GameSessionState.IDLE;
    private String gameId = "";

    public synchronized GameSessionState getState() {
        return state;
    }

    public synchronized String getGameId() {
        return gameId;
    }

    public void addListener(Listener listener) {
        if (listener != null) listeners.addIfAbsent(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void prepare(String nextGameId) {
        if (nextGameId == null || nextGameId.isBlank()) {
            throw new IllegalArgumentException("gameId");
        }
        transition(GameSessionState.PREPARING, nextGameId);
    }

    public void markLaunching() {
        requireState(GameSessionState.PREPARING);
        transition(GameSessionState.LAUNCHING, null);
    }

    public void markRunning() {
        requireState(GameSessionState.LAUNCHING, GameSessionState.SUSPENDED);
        transition(GameSessionState.RUNNING, null);
    }

    public void suspend() {
        requireState(GameSessionState.RUNNING);
        transition(GameSessionState.SUSPENDED, null);
    }

    public void end() {
        requireState(GameSessionState.PREPARING, GameSessionState.LAUNCHING,
                GameSessionState.RUNNING, GameSessionState.SUSPENDED);
        transition(GameSessionState.ENDED, null);
    }

    public void reset() {
        requireState(GameSessionState.ENDED, GameSessionState.IDLE);
        transition(GameSessionState.IDLE, "");
    }

    private void requireState(GameSessionState... allowed) {
        GameSessionState current;
        synchronized (this) {
            current = state;
        }
        for (GameSessionState candidate : allowed) {
            if (current == candidate) return;
        }
        throw new IllegalStateException("GSB-CORE-SESSION-INVALID-TRANSITION:" + current);
    }

    private void transition(GameSessionState next, String newGameId) {
        final GameSessionState previous;
        final String currentGameId;
        synchronized (this) {
            previous = state;
            if (newGameId != null) gameId = newGameId;
            state = next;
            currentGameId = gameId;
        }
        if (previous == next && newGameId == null) return;
        for (Listener listener : listeners) {
            listener.onStateChanged(previous, next, currentGameId);
        }
    }
}
