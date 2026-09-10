package io.github.j12h36h.dai.presentation.scene;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/** Merges datapack scene behavior with optional resource-pack visual overrides. */
public final class DAI_SceneRegistry {
    private static Map<Identifier, DAI_SceneDefinition> data = Map.of();
    private static Map<Identifier, DAI_SceneDefinition> resources = Map.of();

    private DAI_SceneRegistry() {}

    public static synchronized void replaceData(Map<Identifier, DAI_SceneDefinition> definitions) {
        data = copy(definitions);
        log();
    }

    public static synchronized void replaceResources(Map<Identifier, DAI_SceneDefinition> definitions) {
        resources = copy(definitions);
        log();
    }

    public static DAI_SceneDefinition get(Identifier id) {
        if (id == null) return null;
        DAI_SceneDefinition resource = resources.get(id);
        if (resource != null) return resource;
        return data.get(id);
    }

    public static DAI_SceneDefinition get(String id) {
        return get(Identifier.tryParse(id == null ? "" : id.trim()));
    }

    public static boolean contains(String id) { return get(id) != null; }
    public static int size() {
        LinkedHashMap<Identifier, DAI_SceneDefinition> merged = new LinkedHashMap<>(data);
        merged.putAll(resources);
        return merged.size();
    }

    private static Map<Identifier, DAI_SceneDefinition> copy(Map<Identifier, DAI_SceneDefinition> source) {
        if (source == null || source.isEmpty()) return Map.of();
        LinkedHashMap<Identifier, DAI_SceneDefinition> out = new LinkedHashMap<>();
        source.forEach((id, definition) -> {
            if (id != null && definition != null && definition.enabled()) out.put(id, definition);
        });
        return Map.copyOf(out);
    }

    private static void log() {
        DAI_Core.debug("<DAI>: Scene environments available: {} datapack, {} resource-pack.", data.size(), resources.size());
    }
}
