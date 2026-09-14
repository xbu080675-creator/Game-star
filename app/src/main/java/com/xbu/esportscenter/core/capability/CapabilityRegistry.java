package com.xbu.esportscenter.core.capability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Platform-agnostic registry of semantic capabilities.
 * Core never stores vendor API objects, shell commands, Binder handles or Android Context here.
 */
public final class CapabilityRegistry {
    public enum State {
        AVAILABLE,
        UNAVAILABLE,
        DENIED,
        DEAD,
        UNSUPPORTED,
        ERROR
    }

    public static final class Entry {
        public final String id;
        public final State state;
        public final String detailCode;

        public Entry(String id, State state, String detailCode) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
            this.id = id;
            this.state = state == null ? State.ERROR : state;
            this.detailCode = detailCode == null ? "" : detailCode;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public synchronized void publish(Entry entry) {
        entries.put(entry.id, entry);
    }

    public synchronized Entry get(String id) {
        return entries.get(id);
    }

    public synchronized Map<String, Entry> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }
}
