package com.xbu.esportscenter.core.capability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform-agnostic registry of semantic capabilities.
 * Core never stores vendor API objects, shell commands, Binder handles or Android Context here.
 */
public final class CapabilityRegistry {
    public interface Listener {
        void onCapabilitySnapshot(Map<String, Entry> snapshot);
    }

    public static final class Entry {
        public final String id;
        public final CapabilityAvailability availability;
        public final String detailCode;

        public Entry(String id, CapabilityAvailability availability, String detailCode) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
            this.id = id;
            this.availability = availability == null ? CapabilityAvailability.UNKNOWN : availability;
            this.detailCode = detailCode == null ? "" : detailCode;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    public void publish(Entry entry) {
        if (entry == null) throw new IllegalArgumentException("entry");
        Map<String, Entry> snapshot;
        List<Listener> copy;
        synchronized (this) {
            entries.put(entry.id, entry);
            snapshot = snapshotLocked();
            copy = new ArrayList<>(listeners);
        }
        for (Listener listener : copy) listener.onCapabilitySnapshot(snapshot);
    }

    public synchronized Entry get(String id) {
        return entries.get(id);
    }

    public synchronized Map<String, Entry> snapshot() {
        return snapshotLocked();
    }

    public synchronized void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public synchronized void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private Map<String, Entry> snapshotLocked() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }
}
