package io.github.j12h36h.dai.customization;

import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Reloadable registry storage for DAI 1.9 game-customization definitions. */
public final class DAI_GameCustomizationRegistry {

    public record Entry(
            DAI_GameCustomizationKind kind,
            Identifier id,
            DAI_GameCustomizationDefinition definition
    ) {}

    private static final EnumMap<DAI_GameCustomizationKind, LinkedHashMap<Identifier, Entry>> VALUES =
            new EnumMap<>(DAI_GameCustomizationKind.class);

    static {
        for (DAI_GameCustomizationKind kind : DAI_GameCustomizationKind.values()) {
            VALUES.put(kind, new LinkedHashMap<>());
        }
    }

    private DAI_GameCustomizationRegistry() {}

    public static synchronized void clear(DAI_GameCustomizationKind kind) {
        if (kind != null) VALUES.get(kind).clear();
    }

    public static synchronized void register(
            DAI_GameCustomizationKind kind,
            Identifier id,
            DAI_GameCustomizationDefinition definition
    ) {
        if (kind == null || id == null || definition == null) return;
        VALUES.get(kind).put(id, new Entry(kind, id, definition));
    }

    public static synchronized Entry get(DAI_GameCustomizationKind kind, String rawId) {
        if (kind == null || rawId == null || rawId.isBlank()) return null;
        Identifier id = Identifier.tryParse(rawId.trim());
        return id == null ? null : VALUES.get(kind).get(id);
    }

    public static synchronized Entry find(String rawId) {
        if (rawId == null || rawId.isBlank()) return null;
        Identifier id = Identifier.tryParse(rawId.trim());
        if (id == null) return null;
        for (DAI_GameCustomizationKind kind : DAI_GameCustomizationKind.values()) {
            Entry entry = VALUES.get(kind).get(id);
            if (entry != null) return entry;
        }
        return null;
    }

    public static synchronized Map<Identifier, Entry> entries(DAI_GameCustomizationKind kind) {
        if (kind == null) return Map.of();
        return Map.copyOf(VALUES.get(kind));
    }

    public static synchronized Set<String> ids(DAI_GameCustomizationKind kind) {
        if (kind == null) return Set.of();
        return VALUES.get(kind).keySet().stream()
                .map(Identifier::toString)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static synchronized int size(DAI_GameCustomizationKind kind) {
        return kind == null ? 0 : VALUES.get(kind).size();
    }

    public static synchronized int totalSize() {
        int total = 0;
        for (var map : VALUES.values()) total += map.size();
        return total;
    }
}
