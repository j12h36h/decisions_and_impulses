package io.github.j12h36h.dai.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Compiles DAI timeline definitions into Minecraft 26.2 timeline JSON. */
public final class DAI_TimelineCompiler {

    private DAI_TimelineCompiler() {}

    public static JsonObject compile(JsonObject source) {
        if (source == null) source = new JsonObject();
        JsonObject out = rawBase(source);

        if (source.has("clock")) {
            out.addProperty("clock", string(source, "clock", "minecraft:overworld"));
        } else if (!out.has("clock")) {
            out.addProperty("clock", "minecraft:overworld");
        }

        if (source.has("period_ticks")) {
            out.addProperty("period_ticks", integer(source, "period_ticks", 24000));
        } else if (!out.has("period_ticks")) {
            out.addProperty("period_ticks", 24000);
        }

        if (source.has("time_markers")) {
            out.add("time_markers", source.get("time_markers").deepCopy());
        }

        JsonObject tracks = object(source, "tracks");
        if (tracks == null) tracks = object(source, "visual_tracks");
        if (tracks != null) {
            out.add("tracks", DAI_EnvironmentAttributesCompiler.translateTracks(tracks));
        } else if (!out.has("tracks")) {
            out.add("tracks", new JsonObject());
        }
        return out;
    }

    private static JsonObject rawBase(JsonObject source) {
        JsonObject raw = object(source, "raw");
        if (raw == null) raw = object(source, "vanilla");
        return raw == null ? new JsonObject() : raw.deepCopy();
    }

    private static JsonObject object(JsonObject root, String key) {
        if (root == null || !root.has(key)) return null;
        JsonElement element = root.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }


    private static String string(JsonObject root, String key, String fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsString().trim(); }
        catch (Exception ignored) { return fallback; }
    }

    private static int integer(JsonObject root, String key, int fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsInt(); }
        catch (Exception ignored) { return fallback; }
    }
}
