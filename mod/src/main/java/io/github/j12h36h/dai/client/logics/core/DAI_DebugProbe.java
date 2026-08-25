package io.github.j12h36h.dai.client.logics.core;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.StringJoiner;

/**
 * Low-overhead bounded event recorder used by the debug heartbeat. It records
 * only the most recent high-value state transitions so each test pass leaves a
 * compact causal trail without flooding latest.log.
 */
public final class DAI_DebugProbe {
    private static final int MAX_EVENTS = 96;
    private static final Deque<String> EVENTS = new ArrayDeque<>();

    private DAI_DebugProbe() {}

    public static synchronized void record(String category, String message) {
        while (EVENTS.size() >= MAX_EVENTS) EVENTS.removeFirst();
        EVENTS.addLast(Instant.now() + " " + safe(category) + " " + safe(message));
    }

    public static synchronized String recent(int limit) {
        if (EVENTS.isEmpty()) return "[]";
        int skip = Math.max(0, EVENTS.size() - Math.max(1, limit));
        int index = 0;
        StringJoiner joiner = new StringJoiner(" | ", "[", "]");
        for (String event : EVENTS) {
            if (index++ < skip) continue;
            joiner.add(event);
        }
        return joiner.toString();
    }

    public static synchronized void clear() { EVENTS.clear(); }

    private static String safe(String value) {
        if (value == null) return "-";
        return value.replace('\n', ' ').replace('\r', ' ');
    }
}
