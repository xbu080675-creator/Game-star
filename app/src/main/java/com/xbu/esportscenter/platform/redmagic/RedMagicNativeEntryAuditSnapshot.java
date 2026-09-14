package com.xbu.esportscenter.platform.redmagic;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Immutable read-only evidence from exported REDMAGIC/Nubia package components. */
public final class RedMagicNativeEntryAuditSnapshot {
    public static final class EntryPoint {
        public final String packageName;
        public final String kind;
        public final String className;
        public final String permission;

        EntryPoint(String packageName, String kind, String className, String permission) {
            this.packageName = packageName == null ? "" : packageName;
            this.kind = kind == null ? "" : kind;
            this.className = className == null ? "" : className;
            this.permission = permission == null ? "" : permission;
        }

        private String searchable() {
            return (packageName + " " + kind + " " + className + " " + permission)
                    .toLowerCase(Locale.ROOT);
        }
    }

    public final boolean gameSpaceVisible;
    public final boolean gameAssistVisible;
    public final int exportedComponentCount;
    public final List<EntryPoint> candidates;

    RedMagicNativeEntryAuditSnapshot(
            boolean gameSpaceVisible,
            boolean gameAssistVisible,
            int exportedComponentCount,
            List<EntryPoint> candidates
    ) {
        this.gameSpaceVisible = gameSpaceVisible;
        this.gameAssistVisible = gameAssistVisible;
        this.exportedComponentCount = Math.max(0, exportedComponentCount);
        this.candidates = Collections.unmodifiableList(List.copyOf(candidates));
    }

    public boolean hasCandidateEvidence() {
        return !candidates.isEmpty();
    }

    public int performanceCandidateCount() {
        return countAny("perf", "boost", "power", "thermal", "game", "mode");
    }

    public int interruptionCandidateCount() {
        return countAny("notify", "notification", "disturb", "dnd", "shield");
    }

    public int memoryCandidateCount() {
        return countAny("clean", "kill", "memory", "mindsync", "process");
    }

    public int networkCandidateCount() {
        return countAny("network", "wifi", "qos", "latency", "acceler", ".net");
    }

    public int gameToolCandidateCount() {
        return countAny("fan", "charge", "shoulder", "trigger", "record", "capture", "fps", "overlay", "hud");
    }

    private int countAny(String... needles) {
        int count = 0;
        for (EntryPoint entry : candidates) {
            String haystack = entry.searchable();
            for (String needle : needles) {
                if (haystack.contains(needle)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }
}
