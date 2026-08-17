package io.github.j12h36h.dai.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Friendly JSON world/dimension generation settings exposed by DAI.
 *
 * These settings are compiled during mod bootstrap into ordinary Minecraft
 * dynamic-registry JSON by DAI_GeneratedWorldDataPack. Packs therefore get a
 * compact DAI schema while Minecraft still owns the actual dimension and
 * world-preset codecs.
 */
public record DAI_WorldTypeDefinition(
        String type,
        String dimensionType,
        String biome,
        String noiseSettings,
        List<Layer> layers,
        int waterDepth,
        String floorBlock,
        int floorHeight,
        String surfaceBlock,
        int surfaceHeight,
        String waterBlock,
        boolean features,
        boolean lakes,
        boolean structures,
        boolean includeNether,
        boolean includeEnd
) {

    public static final DAI_WorldTypeDefinition NORMAL =
            new DAI_WorldTypeDefinition(
                    "normal",
                    "minecraft:overworld",
                    "minecraft:plains",
                    "minecraft:overworld",
                    List.of(),
                    60,
                    "minecraft:bedrock",
                    1,
                    "minecraft:sand",
                    1,
                    "minecraft:water",
                    true,
                    true,
                    true,
                    true,
                    true
            );

    public DAI_WorldTypeDefinition {
        type = normalizeType(type);
        dimensionType = id(dimensionType, "minecraft:overworld");
        biome = id(biome, "minecraft:plains");
        noiseSettings = id(noiseSettings, defaultNoiseSettings(type));
        layers = layers == null ? List.of() : List.copyOf(layers);
        waterDepth = clamp(waterDepth, 0, 384);
        floorBlock = id(floorBlock, "minecraft:bedrock");
        floorHeight = clamp(floorHeight, 0, 384);
        surfaceBlock = id(surfaceBlock, "minecraft:sand");
        surfaceHeight = clamp(surfaceHeight, 0, 384);
        waterBlock = id(waterBlock, "minecraft:water");
    }

    public static DAI_WorldTypeDefinition parse(JsonObject root) {
        if (root == null) return NORMAL;

        String type = string(root, "type", "normal");
        List<Layer> layers = new ArrayList<>();
        JsonArray array = array(root, "layers");
        if (array != null) {
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject value = element.getAsJsonObject();
                String block = string(value, "block", "minecraft:air");
                int height = integer(value, "height", 1);
                if (height <= 0 || block.isBlank()) continue;
                layers.add(new Layer(block, height));
            }
        }

        return new DAI_WorldTypeDefinition(
                type,
                string(root, "dimension_type", "minecraft:overworld"),
                string(root, "biome", defaultBiome(type)),
                string(root, "noise_settings", defaultNoiseSettings(type)),
                layers,
                integer(root, "water_depth", 60),
                string(root, "floor_block", "minecraft:bedrock"),
                integer(root, "floor_height", 1),
                string(root, "surface_block", "minecraft:sand"),
                integer(root, "surface_height", 1),
                string(root, "water_block", "minecraft:water"),
                bool(root, "features", defaultFeatures(type)),
                bool(root, "lakes", defaultLakes(type)),
                bool(root, "structures", defaultStructures(type)),
                bool(root, "include_nether", true),
                bool(root, "include_end", true)
        );
    }

    /** Accepts a short string world_type as well as an object. */
    public static DAI_WorldTypeDefinition parse(JsonElement element) {
        if (element == null || element.isJsonNull()) return NORMAL;
        if (element.isJsonObject()) return parse(element.getAsJsonObject());
        try {
            JsonObject object = new JsonObject();
            object.addProperty("type", element.getAsString());
            return parse(object);
        } catch (Exception ignored) {
            return NORMAL;
        }
    }

    public boolean requiresGeneratedPreset() {
        return switch (type) {
            case "void", "flat", "superflat", "water", "water_world",
                    "fixed_biome", "fixed_biome_noise", "noise" -> true;
            default -> false;
        };
    }

    public String builtInPreset() {
        return switch (type) {
            case "amplified" -> "minecraft:amplified";
            case "large_biomes", "large-biomes" -> "minecraft:large_biomes";
            case "single_biome", "single_biome_surface" -> "minecraft:single_biome_surface";
            case "debug" -> "minecraft:debug";
            case "flat", "superflat", "void", "water", "water_world" -> "minecraft:flat";
            default -> "minecraft:normal";
        };
    }

    public JsonObject dimensionJson() {
        JsonObject dimension = new JsonObject();
        dimension.addProperty("type", dimensionType);
        dimension.add("generator", generatorJson());
        return dimension;
    }

    public JsonObject worldPresetJson() {
        JsonObject dimensions = new JsonObject();
        dimensions.add("minecraft:overworld", dimensionJson());
        if (includeNether) dimensions.add("minecraft:the_nether", vanillaNether());
        if (includeEnd) dimensions.add("minecraft:the_end", vanillaEnd());

        JsonObject preset = new JsonObject();
        preset.add("dimensions", dimensions);
        return preset;
    }

    public JsonObject generatorJson() {
        return switch (type) {
            case "void" -> flatGenerator(List.of(new Layer("minecraft:air", 1)));
            case "water", "water_world" -> flatGenerator(waterLayers());
            case "flat", "superflat" -> flatGenerator(
                    layers.isEmpty() ? defaultFlatLayers() : layers
            );
            case "fixed_biome", "fixed_biome_noise" -> noiseGenerator(true);
            case "noise" -> noiseGenerator(!biome.isBlank());
            default -> noiseGenerator(false);
        };
    }

    private JsonObject flatGenerator(List<Layer> requestedLayers) {
        JsonObject settings = new JsonObject();
        settings.addProperty("biome", biome);
        settings.addProperty("features", features);
        settings.addProperty("lakes", lakes);

        JsonArray layerArray = new JsonArray();
        for (Layer layer : requestedLayers) {
            JsonObject value = new JsonObject();
            value.addProperty("height", layer.height());
            value.addProperty("block", layer.block());
            layerArray.add(value);
        }
        settings.add("layers", layerArray);
        if (!structures) settings.add("structure_overrides", new JsonArray());

        JsonObject generator = new JsonObject();
        generator.addProperty("type", "minecraft:flat");
        generator.add("settings", settings);
        return generator;
    }

    private JsonObject noiseGenerator(boolean fixedBiome) {
        JsonObject biomeSource = new JsonObject();
        if (fixedBiome) {
            biomeSource.addProperty("type", "minecraft:fixed");
            biomeSource.addProperty("biome", biome);
        } else {
            biomeSource.addProperty("type", "minecraft:multi_noise");
            biomeSource.addProperty("preset", "minecraft:overworld");
        }

        JsonObject generator = new JsonObject();
        generator.addProperty("type", "minecraft:noise");
        generator.add("biome_source", biomeSource);
        generator.addProperty("settings", noiseSettings);
        return generator;
    }

    private List<Layer> waterLayers() {
        ArrayList<Layer> result = new ArrayList<>();
        if (floorHeight > 0) result.add(new Layer(floorBlock, floorHeight));
        if (surfaceHeight > 0) result.add(new Layer(surfaceBlock, surfaceHeight));
        if (waterDepth > 0) result.add(new Layer(waterBlock, waterDepth));
        return result;
    }

    private static List<Layer> defaultFlatLayers() {
        return List.of(
                new Layer("minecraft:bedrock", 1),
                new Layer("minecraft:dirt", 2),
                new Layer("minecraft:grass_block", 1)
        );
    }

    private static JsonObject vanillaNether() {
        JsonObject biomeSource = new JsonObject();
        biomeSource.addProperty("type", "minecraft:multi_noise");
        biomeSource.addProperty("preset", "minecraft:nether");

        JsonObject generator = new JsonObject();
        generator.addProperty("type", "minecraft:noise");
        generator.add("biome_source", biomeSource);
        generator.addProperty("settings", "minecraft:nether");

        JsonObject dimension = new JsonObject();
        dimension.addProperty("type", "minecraft:the_nether");
        dimension.add("generator", generator);
        return dimension;
    }

    private static JsonObject vanillaEnd() {
        JsonObject biomeSource = new JsonObject();
        biomeSource.addProperty("type", "minecraft:the_end");

        JsonObject generator = new JsonObject();
        generator.addProperty("type", "minecraft:noise");
        generator.add("biome_source", biomeSource);
        generator.addProperty("settings", "minecraft:end");

        JsonObject dimension = new JsonObject();
        dimension.addProperty("type", "minecraft:the_end");
        dimension.add("generator", generator);
        return dimension;
    }

    public record Layer(String block, int height) {
        public Layer {
            block = id(block, "minecraft:air");
            height = clamp(height, 1, 384);
        }
    }

    private static String normalizeType(String value) {
        if (value == null || value.isBlank()) return "normal";
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String id(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String defaultBiome(String type) {
        String normalized = normalizeType(type);
        return normalized.equals("void") ? "minecraft:the_void" : "minecraft:plains";
    }

    private static String defaultNoiseSettings(String type) {
        String normalized = normalizeType(type);
        return normalized.equals("amplified") ? "minecraft:overworld_large_biomes" : "minecraft:overworld";
    }

    private static boolean defaultFeatures(String type) {
        String normalized = normalizeType(type);
        return !normalized.equals("void") && !normalized.equals("water") && !normalized.equals("water_world");
    }

    private static boolean defaultLakes(String type) {
        return defaultFeatures(type);
    }

    private static boolean defaultStructures(String type) {
        return !normalizeType(type).equals("void");
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

    private static int integer(JsonObject root, String key, int fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsInt(); } catch (Exception ignored) { return fallback; }
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsBoolean(); } catch (Exception ignored) { return fallback; }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
