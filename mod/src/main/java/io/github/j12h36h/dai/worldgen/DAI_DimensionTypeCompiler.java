package io.github.j12h36h.dai.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Compiles friendly DAI dimension-type definitions for Minecraft 26.2. */
public final class DAI_DimensionTypeCompiler {

    private DAI_DimensionTypeCompiler() {}

    public static JsonObject compile(JsonObject source) {
        if (source == null) source = new JsonObject();
        JsonObject out = rawBase(source);

        putNumber(source, out, "ambient_light", 0.0D);
        putNumber(source, out, "coordinate_scale", 1.0D);
        putBoolean(source, out, "has_ceiling", false);
        putBoolean(source, out, "has_ender_dragon_fight", false);
        putBoolean(source, out, "has_skylight", true);
        putInteger(source, out, "height", 384);
        putString(source, out, "infiniburn", "#minecraft:infiniburn_overworld");
        if (source.has("logical_height")) {
            out.addProperty("logical_height", integer(source, "logical_height", integer(source, "height", 384)));
        } else if (!out.has("logical_height")) {
            out.addProperty("logical_height", integer(source, "height", 384));
        }
        putInteger(source, out, "min_y", -64);
        putInteger(source, out, "monster_spawn_block_light_limit", 0);

        JsonElement spawnLight = source.get("monster_spawn_light_level");
        if (spawnLight != null) {
            out.add("monster_spawn_light_level", spawnLight.deepCopy());
        } else if (!out.has("monster_spawn_light_level")) {
            JsonObject uniform = new JsonObject();
            uniform.addProperty("type", "minecraft:uniform");
            uniform.addProperty("min_inclusive", 0);
            uniform.addProperty("max_inclusive", 7);
            out.add("monster_spawn_light_level", uniform);
        }

        copyIfPresent(source, out, "has_fixed_time");
        copyIfPresent(source, out, "skybox");
        copyIfPresent(source, out, "cardinal_light");
        copyIfPresent(source, out, "default_clock");
        copyIfPresent(source, out, "timelines");

        JsonObject attributes = objectCopy(out, "attributes");
        if (attributes == null) attributes = new JsonObject();
        DAI_EnvironmentAttributesCompiler.mergeObject(
                attributes,
                DAI_EnvironmentAttributesCompiler.attributesFor(source)
        );
        if (attributes.size() > 0) out.add("attributes", attributes);
        else out.remove("attributes");

        return out;
    }

    private static JsonObject rawBase(JsonObject source) {
        JsonObject raw = object(source, "raw");
        if (raw == null) raw = object(source, "vanilla");
        return raw == null ? new JsonObject() : raw.deepCopy();
    }

    private static void copyIfPresent(JsonObject source, JsonObject target, String key) {
        if (source.has(key)) target.add(key, source.get(key).deepCopy());
    }

    private static void putString(JsonObject source, JsonObject target, String key, String fallback) {
        if (source.has(key)) target.addProperty(key, string(source, key, fallback));
        else if (!target.has(key)) target.addProperty(key, fallback);
    }

    private static void putBoolean(JsonObject source, JsonObject target, String key, boolean fallback) {
        if (source.has(key)) target.addProperty(key, bool(source, key, fallback));
        else if (!target.has(key)) target.addProperty(key, fallback);
    }

    private static void putInteger(JsonObject source, JsonObject target, String key, int fallback) {
        if (source.has(key)) target.addProperty(key, integer(source, key, fallback));
        else if (!target.has(key)) target.addProperty(key, fallback);
    }

    private static void putNumber(JsonObject source, JsonObject target, String key, double fallback) {
        if (source.has(key)) target.addProperty(key, number(source, key, fallback));
        else if (!target.has(key)) target.addProperty(key, fallback);
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

    private static int integer(JsonObject root, String key, int fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsInt(); }
        catch (Exception ignored) { return fallback; }
    }

    private static double number(JsonObject root, String key, double fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsDouble(); }
        catch (Exception ignored) { return fallback; }
    }
}
