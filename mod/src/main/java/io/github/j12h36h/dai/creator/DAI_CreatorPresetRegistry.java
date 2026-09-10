package io.github.j12h36h.dai.creator;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Merged preset library; all preset content is JSON-authored. */
public final class DAI_CreatorPresetRegistry {
    private static Map<Identifier, DAI_CreatorPresetDefinition> data = Map.of();
    private static Map<Identifier, DAI_CreatorPresetDefinition> resources = Map.of();

    private DAI_CreatorPresetRegistry() {}

    public static synchronized void replaceData(Map<Identifier, DAI_CreatorPresetDefinition> definitions) {
        data = copy(definitions); log();
    }
    public static synchronized void replaceResources(Map<Identifier, DAI_CreatorPresetDefinition> definitions) {
        resources = copy(definitions); log();
    }

    public static List<Entry> forSchema(String schemaId) {
        LinkedHashMap<Identifier, DAI_CreatorPresetDefinition> merged = new LinkedHashMap<>(data);
        merged.putAll(resources);
        List<Entry> output = new ArrayList<>();
        String wanted = schemaId == null ? "" : schemaId.trim();
        merged.forEach((id, definition) -> {
            if (id == null || definition == null || !definition.enabled()) return;
            String target = definition.schema().trim();
            if (target.equals("*") || target.equals(wanted)) output.add(new Entry(id, definition));
        });
        output.sort(Comparator
                .comparingInt((Entry entry) -> entry.definition().priority()).reversed()
                .thenComparing(entry -> entry.definition().displayName(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(output);
    }

    private static Map<Identifier, DAI_CreatorPresetDefinition> copy(Map<Identifier, DAI_CreatorPresetDefinition> source) {
        if (source == null || source.isEmpty()) return Map.of();
        LinkedHashMap<Identifier, DAI_CreatorPresetDefinition> output = new LinkedHashMap<>();
        source.forEach((id, definition) -> {
            if (id != null && definition != null && definition.enabled()) output.put(id, definition);
        });
        return Map.copyOf(output);
    }

    private static void log() {
        DAI_Core.debug("<DAI>: Creator presets available: {} datapack, {} resource-pack.", data.size(), resources.size());
    }

    public record Entry(Identifier id, DAI_CreatorPresetDefinition definition) {}
}
