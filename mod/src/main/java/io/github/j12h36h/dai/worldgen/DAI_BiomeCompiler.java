package io.github.j12h36h.dai.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/** Compiles DAI-friendly biome JSON into Minecraft 26.2 biome registry JSON. */
public final class DAI_BiomeCompiler {

    private static final List<String> FEATURE_STEPS = List.of(
            "raw_generation",
            "lakes",
            "local_modifications",
            "underground_structures",
            "surface_structures",
            "strongholds",
            "underground_ores",
            "underground_decoration",
            "fluid_springs",
            "vegetal_decoration",
            "top_layer_modification"
    );

    private DAI_BiomeCompiler() {}

    public static JsonObject compile(JsonObject source) {
        if (source == null) source = new JsonObject();

        // Advanced creators can begin from a native biome object and then use
        // DAI's friendly fields to selectively override it.
        JsonObject out = rawBase(source);

        putBoolean(source, out, "has_precipitation", true);
        putNumber(source, out, "temperature", 0.8D);
        putNumber(source, out, "downfall", 0.4D);
        copyIfPresent(source, out, "temperature_modifier");

        JsonObject effects = objectCopy(out, "effects");
        if (effects == null) effects = new JsonObject();
        JsonObject authoredEffects = object(source, "effects");
        mergeObject(effects, authoredEffects);

        String waterColor = string(source, "water_color", "");
        if (!waterColor.isBlank()) effects.addProperty("water_color", waterColor);
        else if (!effects.has("water_color")) effects.addProperty("water_color", "#3f76e4");
        String grassColor = string(source, "grass_color", "");
        if (!grassColor.isBlank()) effects.addProperty("grass_color", grassColor);
        String foliageColor = string(source, "foliage_color", "");
        if (!foliageColor.isBlank()) effects.addProperty("foliage_color", foliageColor);
        String dryFoliageColor = string(source, "dry_foliage_color", "");
        if (!dryFoliageColor.isBlank()) effects.addProperty("dry_foliage_color", dryFoliageColor);
        copyIfPresent(source, effects, "grass_color_modifier");
        out.add("effects", effects);

        JsonObject attributes = objectCopy(out, "attributes");
        if (attributes == null) attributes = new JsonObject();
        DAI_EnvironmentAttributesCompiler.mergeObject(
                attributes,
                DAI_EnvironmentAttributesCompiler.attributesFor(source)
        );
        if (attributes.size() > 0) out.add("attributes", attributes);
        else out.remove("attributes");

        if (source.has("carvers")) {
            out.add("carvers", source.get("carvers").deepCopy());
        } else if (!out.has("carvers")) {
            out.add("carvers", new JsonArray());
        }

        if (source.has("features") || source.has("feature_steps") || source.has("generation_features")) {
            out.add("features", compileFeatures(source));
        } else if (!out.has("features")) {
            out.add("features", emptyFeatureSteps());
        }

        JsonObject authoredSpawners = objectCopy(source, "spawners");
        if (authoredSpawners != null) out.add("spawners", authoredSpawners);
        else if (!out.has("spawners")) out.add("spawners", new JsonObject());

        JsonObject authoredSpawnCosts = objectCopy(source, "spawn_costs");
        if (authoredSpawnCosts != null) out.add("spawn_costs", authoredSpawnCosts);
        else if (!out.has("spawn_costs")) out.add("spawn_costs", new JsonObject());

        return out;
    }

    private static JsonObject rawBase(JsonObject source) {
        JsonObject raw = object(source, "raw");
        if (raw == null) raw = object(source, "vanilla");
        return raw == null ? new JsonObject() : raw.deepCopy();
    }

    private static JsonArray compileFeatures(JsonObject source) {
        JsonElement direct = source.get("features");
        if (direct != null && direct.isJsonArray()) return direct.getAsJsonArray().deepCopy();

        JsonObject steps = object(source, "feature_steps");
        if (steps == null) steps = object(source, "generation_features");
        JsonArray features = new JsonArray();
        for (String step : FEATURE_STEPS) {
            JsonElement value = steps == null ? null : steps.get(step);
            features.add(value != null && value.isJsonArray() ? value.deepCopy() : new JsonArray());
        }
        return features;
    }

    private static JsonArray emptyFeatureSteps() {
        JsonArray features = new JsonArray();
        for (int i = 0; i < FEATURE_STEPS.size(); i++) features.add(new JsonArray());
        return features;
    }

    private static void mergeObject(JsonObject target, JsonObject source) {
        if (target == null || source == null) return;
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            target.add(entry.getKey(), entry.getValue().deepCopy());
        }
    }

    private static void putBoolean(JsonObject source, JsonObject target, String key, boolean fallback) {
        if (source.has(key)) target.addProperty(key, bool(source, key, fallback));
        else if (!target.has(key)) target.addProperty(key, fallback);
    }

    private static void putNumber(JsonObject source, JsonObject target, String key, double fallback) {
        if (source.has(key)) target.addProperty(key, number(source, key, fallback));
        else if (!target.has(key)) target.addProperty(key, fallback);
    }

    private static void copyIfPresent(JsonObject source, JsonObject target, String key) {
        if (source.has(key)) target.add(key, source.get(key).deepCopy());
    }

    private static JsonObject object(JsonObject root, String key) {
        if (root == null || !root.has(key)) return null;
        JsonElement element = root.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonObject objectCopy(JsonObject root, String key) {
        JsonObject object = object(root, key);
        return object == null ? null : object.deepCopy();
    }

    private static String string(JsonObject root, String key, String fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsString().trim(); }
        catch (Exception ignored) { return fallback; }
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsBoolean(); }
        catch (Exception ignored) { return fallback; }
    }

    private static double number(JsonObject root, String key, double fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsDouble(); }
        catch (Exception ignored) { return fallback; }
    }
}
