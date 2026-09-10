package io.github.j12h36h.dai.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

import java.util.Locale;

/**
 * Open-ended, pack-authored story/archive profile.
 *
 * The engine deliberately assigns no meaning to an event, session, title,
 * page, or scene. Packs define all policy in this JSON object.
 */
public final class DAI_StoryProfileDefinition {
    public static final String FOLDER = "story_profiles";

    public static final Codec<DAI_StoryProfileDefinition> CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> {
                Object value = dynamic.convert(JsonOps.INSTANCE).getValue();
                return value instanceof JsonObject object
                        ? new DAI_StoryProfileDefinition(object)
                        : new DAI_StoryProfileDefinition(new JsonObject());
            },
            definition -> new Dynamic<>(JsonOps.INSTANCE, definition.json())
    );

    private final JsonObject json;

    public DAI_StoryProfileDefinition(JsonObject json) {
        this.json = json == null ? new JsonObject() : json.deepCopy();
    }

    public JsonObject json() { return json.deepCopy(); }
    public boolean enabled() { return bool(json, "enabled", true); }
    public int priority() { return integer(json, "priority", 0); }
    public int pollInterval() { return clamp(integer(json, "poll_interval_ticks", 5), 1, 1200); }
    public JsonObject session() { return object(json, "session"); }
    public JsonArray events() { return array(json, "events"); }
    public JsonObject compiler() { return object(json, "compiler"); }
    public JsonObject viewer() { return object(json, "viewer"); }

    public static JsonObject object(JsonObject root, String key) {
        if (root == null || key == null) return new JsonObject();
        JsonElement value = root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    public static JsonArray array(JsonObject root, String key) {
        if (root == null || key == null) return new JsonArray();
        JsonElement value = root.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    public static String string(JsonObject root, String key, String fallback) {
        try { return root != null && root.has(key) ? root.get(key).getAsString() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    public static boolean bool(JsonObject root, String key, boolean fallback) {
        try { return root != null && root.has(key) ? root.get(key).getAsBoolean() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    public static int integer(JsonObject root, String key, int fallback) {
        try { return root != null && root.has(key) ? root.get(key).getAsInt() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    public static double number(JsonObject root, String key, double fallback) {
        try {
            double value = root != null && root.has(key) ? root.get(key).getAsDouble() : fallback;
            return Double.isFinite(value) ? value : fallback;
        } catch (RuntimeException ignored) { return fallback; }
    }

    public static int color(JsonObject root, String key, int fallback) {
        if (root == null || !root.has(key)) return fallback;
        JsonElement value = root.get(key);
        try {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return value.getAsInt();
            String raw = value.getAsString().trim();
            if (raw.startsWith("#")) raw = raw.substring(1);
            if (raw.startsWith("0x") || raw.startsWith("0X")) raw = raw.substring(2);
            if (raw.length() == 6) return 0xFF000000 | Integer.parseUnsignedInt(raw, 16);
            if (raw.length() == 8) return (int)Long.parseLong(raw, 16);
        } catch (RuntimeException ignored) {}
        return fallback;
    }

    public static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
