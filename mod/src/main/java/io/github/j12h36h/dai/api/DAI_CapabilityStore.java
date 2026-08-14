package io.github.j12h36h.dai.api;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-scoped capability store with source ownership.
 *
 * Existing add/remove calls use the manual source. Content systems can attach
 * the same capability independently without accidentally deleting each other.
 */
public final class DAI_CapabilityStore {

    private static final String MANUAL_SOURCE = "dai:manual";
    private static final Map<String, Set<String>> SOURCES = new ConcurrentHashMap<>();

    private DAI_CapabilityStore() {}

    public static boolean add(String capability) {
        return addFromSource(capability, MANUAL_SOURCE);
    }

    public static boolean remove(String capability) {
        return removeFromSource(capability, MANUAL_SOURCE);
    }

    public static boolean addFromSource(String capability, String source) {
        String normalized = DAI_StateStore.normalizeKey(capability);
        String normalizedSource = DAI_StateStore.normalizeKey(source);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Capability id cannot be null or blank.");
        }
        if (normalizedSource.isEmpty()) {
            throw new IllegalArgumentException("Capability source cannot be null or blank.");
        }
        return SOURCES.computeIfAbsent(normalized, ignored -> ConcurrentHashMap.newKeySet())
                .add(normalizedSource);
    }

    public static boolean removeFromSource(String capability, String source) {
        String normalized = DAI_StateStore.normalizeKey(capability);
        String normalizedSource = DAI_StateStore.normalizeKey(source);
        if (normalized.isEmpty() || normalizedSource.isEmpty()) return false;
        Set<String> sources = SOURCES.get(normalized);
        if (sources == null) return false;
        boolean removed = sources.remove(normalizedSource);
        if (sources.isEmpty()) SOURCES.remove(normalized);
        return removed;
    }

    public static boolean has(String capability) {
        String normalized = DAI_StateStore.normalizeKey(capability);
        Set<String> sources = normalized.isEmpty() ? null : SOURCES.get(normalized);
        return sources != null && !sources.isEmpty();
    }

    public static Set<String> sources(String capability) {
        String normalized = DAI_StateStore.normalizeKey(capability);
        Set<String> sources = normalized.isEmpty() ? null : SOURCES.get(normalized);
        return sources == null ? Set.of() : Set.copyOf(sources);
    }

    public static Set<String> snapshot() {
        return Set.copyOf(new LinkedHashSet<>(SOURCES.keySet()));
    }

    public static void clear() {
        SOURCES.clear();
    }
}
