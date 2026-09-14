package com.xbu.esportscenter.core.entry;

/** Immutable semantic request emitted by a platform adapter when the user asks to enter Game Star Box. */
public final class GameEntryRequest {
    public final long sequence;
    public final long createdAtElapsedRealtimeMs;
    public final GameEntrySource source;

    public GameEntryRequest(long sequence, long createdAtElapsedRealtimeMs, GameEntrySource source) {
        this.sequence = sequence;
        this.createdAtElapsedRealtimeMs = createdAtElapsedRealtimeMs;
        this.source = source == null ? GameEntrySource.UNKNOWN : source;
    }
}
