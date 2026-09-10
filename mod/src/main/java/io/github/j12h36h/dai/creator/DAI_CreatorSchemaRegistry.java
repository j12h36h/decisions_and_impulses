package io.github.j12h36h.dai.creator;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Merges datapack schemas with optional resource-pack overrides. */
public final class DAI_CreatorSchemaRegistry {
    private static Map<Identifier, DAI_CreatorSchemaDefinition> data = Map.of();
    private static Map<Identifier, DAI_CreatorSchemaDefinition> resources = Map.of();

    private DAI_CreatorSchemaRegistry() {}

    public static synchronized void replaceData(Map<Identifier, DAI_CreatorSchemaDefinition> definitions) {
        data = copy(definitions);
        log();
    }

    public static synchronized void replaceResources(Map<Identifier, DAI_CreatorSchemaDefinition> definitions) {
        resources = copy(definitions);
        log();
    }

    public static DAI_CreatorSchemaDefinition get(Identifier id) {
        if (id == null) return null;
        DAI_CreatorSchemaDefinition resource = resources.get(id);
        return resource != null ? resource : data.get(id);
    }

    public static DAI_CreatorSchemaDefinition get(String id) {
        return get(Identifier.tryParse(id == null ? "" : id.trim()));
    }

    public static List<Entry> entries() {
        LinkedHashMap<Identifier, DAI_CreatorSchemaDefinition> merged = new LinkedHashMap<>(data);
        merged.putAll(resources);
        List<Entry> output = new ArrayList<>();
        merged.forEach((id, definition) -> {
            if (id != null && definition != null && definition.enabled()) output.add(new Entry(id, definition));
        });
        output.sort(Comparator
                .comparingInt((Entry entry) -> entry.definition().priority()).reversed()
                .thenComparing(entry -> entry.definition().displayName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.id().toString()));
        return List.copyOf(output);
    }

    public static List<Entry> rail(String rail) {
        String normalized = DAI_CreatorSchemaDefinition.normalized(rail);
        return entries().stream()
                .filter(entry -> normalized.equals(DAI_CreatorSchemaDefinition.normalized(entry.definition().rail())))
                .toList();
    }

    public static int size() { return entries().size(); }

    private static Map<Identifier, DAI_CreatorSchemaDefinition> copy(Map<Identifier, DAI_CreatorSchemaDefinition> source) {
        if (source == null || source.isEmpty()) return Map.of();
        LinkedHashMap<Identifier, DAI_CreatorSchemaDefinition> output = new LinkedHashMap<>();
        source.forEach((id, definition) -> {
            if (id != null && definition != null && definition.enabled()) output.put(id, definition);
        });
        return Map.copyOf(output);
    }

    private static void log() {
        DAI_Core.debug("<DAI>: Creator schemas available: {} datapack, {} resource-pack.", data.size(), resources.size());
    }

    public record Entry(Identifier id, DAI_CreatorSchemaDefinition definition) {}
}
