package com.xbu.esportscenter.core.entry;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Platform-neutral owner of game-entry requests.
 *
 * Adapters may emit semantic requests, while Android/vendor APIs remain outside Core.
 */
public final class GameEntryCoordinator {
    public interface Listener {
        void onEntryRequested(GameEntryRequest request);
    }

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private volatile GameEntryRequest lastRequest;

    public void addListener(Listener listener) {
        if (listener != null) listeners.addIfAbsent(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public GameEntryRequest getLastRequest() {
        return lastRequest;
    }

    public GameEntryRequest request(GameEntrySource source, long elapsedRealtimeMs) {
        if (elapsedRealtimeMs < 0) {
            throw new IllegalArgumentException("elapsedRealtimeMs");
        }
        GameEntryRequest request = new GameEntryRequest(
                sequence.incrementAndGet(),
                elapsedRealtimeMs,
                source
        );
        lastRequest = request;
        for (Listener listener : listeners) {
            listener.onEntryRequested(request);
        }
        return request;
    }
}
