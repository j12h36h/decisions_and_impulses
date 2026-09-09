package io.github.j12h36h.dai.animations.eras;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Merged registry for datapack and resource-pack ERAS cinematic projects. */
public final class DAI_ErasCinematicRegistry {
    private static volatile Map<Identifier, DAI_ErasCinematicDefinition> SERVER = Map.of();
    private static volatile Map<Identifier, DAI_ErasCinematicDefinition> CLIENT = Map.of();

    private DAI_ErasCinematicRegistry() {}

    public static void replaceServer(Map<Identifier, DAI_ErasCinematicDefinition> definitions) {
        SERVER = definitions == null ? Map.of() : Map.copyOf(definitions);
    }

    public static void replaceClient(Map<Identifier, DAI_ErasCinematicDefinition> definitions) {
        CLIENT = definitions == null ? Map.of() : Map.copyOf(definitions);
    }

    public static DAI_ErasCinematicDefinition get(String raw) {
        Identifier id = parse(raw);
        return id == null ? null : get(id);
    }

    public static DAI_ErasCinematicDefinition get(Identifier id) {
        if (id == null) return null;
        DAI_ErasCinematicDefinition client = CLIENT.get(id);
        return client != null ? client : SERVER.get(id);
    }

    public static boolean contains(String raw) { return get(raw) != null; }
    public static boolean contains(Identifier id) { return get(id) != null; }
    public static boolean containsServer(String raw) {
        Identifier id = parse(raw);
        return id != null && SERVER.containsKey(id);
    }

    public static int serverSize() { return SERVER.size(); }
    public static int clientSize() { return CLIENT.size(); }

    public static Map<Identifier, DAI_ErasCinematicDefinition> snapshot() {
        LinkedHashMap<Identifier, DAI_ErasCinematicDefinition> merged = new LinkedHashMap<>(SERVER);
        merged.putAll(CLIENT);
        return Map.copyOf(merged);
    }

    public static Set<Identifier> ids() { return snapshot().keySet(); }

    private static Identifier parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Identifier.tryParse(raw.trim().toLowerCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
