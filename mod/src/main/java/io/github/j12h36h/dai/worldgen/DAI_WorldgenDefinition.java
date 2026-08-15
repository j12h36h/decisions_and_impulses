package io.github.j12h36h.dai.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * High-level DAI world-generation profile.
 *
 * The world_preset field points at a normal Minecraft datapack world preset,
 * while bootstrap structures/actions provide an experience-friendly layer for
 * staged construction and generated starting areas.
 */
public record DAI_WorldgenDefinition(
        String id,
        boolean enabled,
        String worldPreset,
        Long seed,
        Spawn spawn,
        List<StructurePlacement> initialStructures,
        List<String> generationCommands,
        List<String> bootstrapActions
) {
    public DAI_WorldgenDefinition {
        id = normalize(id);
        worldPreset = normalize(worldPreset);
        spawn = spawn == null ? Spawn.DEFAULT : spawn;
        initialStructures = initialStructures == null ? List.of() : List.copyOf(initialStructures);
        generationCommands = generationCommands == null ? List.of() : generationCommands.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
        bootstrapActions = bootstrapActions == null ? List.of() : bootstrapActions.stream().map(DAI_WorldgenDefinition::normalize).filter(s -> !s.isBlank()).toList();
    }

    public static DAI_WorldgenDefinition parse(String id, JsonObject root) {
        JsonObject spawn = object(root, "spawn");
        List<StructurePlacement> structures = new ArrayList<>();
        JsonArray structureArray = array(root, "initial_structures");
        if (structureArray != null) {
            for (JsonElement element : structureArray) {
                if (!element.isJsonObject()) continue;
                JsonObject value = element.getAsJsonObject();
                String structure = string(value, "structure", "");
                if (structure.isBlank()) continue;
                structures.add(new StructurePlacement(
                        normalize(structure),
                        integer(value, "x", 0),
                        integer(value, "y", 64),
                        integer(value, "z", 0),
                        string(value, "rotation", "none"),
                        string(value, "mirror", "none")
                ));
            }
        }

        List<String> commands = new ArrayList<>();
        JsonArray commandsArray = array(root, "generation_commands");
        if (commandsArray != null) {
            for (JsonElement element : commandsArray) {
                try { commands.add(element.getAsString()); } catch (Exception ignored) {}
            }
        }

        List<String> actions = new ArrayList<>();
        JsonArray actionsArray = array(root, "bootstrap_actions");
        if (actionsArray != null) {
            for (JsonElement element : actionsArray) {
                try { actions.add(element.getAsString()); } catch (Exception ignored) {}
            }
        }

        Long seed = null;
        if (root != null && root.has("seed") && !root.get("seed").isJsonNull()) {
            try { seed = root.get("seed").getAsLong(); } catch (Exception ignored) {}
        }

        return new DAI_WorldgenDefinition(
                id,
                bool(root, "enabled", true),
                string(root, "world_preset", "minecraft:normal"),
                seed,
                new Spawn(
                        integer(spawn, "x", 0),
                        integer(spawn, "y", 64),
                        integer(spawn, "z", 0),
                        decimal(spawn, "yaw", 0.0F),
                        decimal(spawn, "pitch", 0.0F)
                ),
                structures,
                commands,
                actions
        );
    }

    public record Spawn(int x, int y, int z, float yaw, float pitch) {
        public static final Spawn DEFAULT = new Spawn(0, 64, 0, 0.0F, 0.0F);
    }

    public record StructurePlacement(
            String structure,
            int x,
            int y,
            int z,
            String rotation,
            String mirror
    ) {}

    private static JsonObject object(JsonObject root, String key) {
        if (root == null) return null;
        JsonElement element = root.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject root, String key) {
        if (root == null) return null;
        JsonElement element = root.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String string(JsonObject root, String key, String fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsString(); } catch (Exception ignored) { return fallback; }
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsBoolean(); } catch (Exception ignored) { return fallback; }
    }

    private static int integer(JsonObject root, String key, int fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsInt(); } catch (Exception ignored) { return fallback; }
    }

    private static float decimal(JsonObject root, String key, float fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsFloat(); } catch (Exception ignored) { return fallback; }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
