package io.github.j12h36h.dai.input;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

import java.util.Locale;

/**
 * Open-ended item/input presentation profile loaded from JSON.
 *
 * The engine provides gesture detection and first-person transform primitives;
 * packs decide which items use them, which DAI actions fire, and every pose,
 * threshold and timing value.
 */
public final class DAI_InputProfileDefinition {
    public static final String FOLDER = "input_profiles";

    public static final Codec<DAI_InputProfileDefinition> CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> {
                Object value = dynamic.convert(JsonOps.INSTANCE).getValue();
                return value instanceof JsonObject object
                        ? new DAI_InputProfileDefinition(object)
                        : new DAI_InputProfileDefinition(new JsonObject());
            },
            definition -> new Dynamic<>(JsonOps.INSTANCE, definition.json())
    );

    private final JsonObject json;

    public DAI_InputProfileDefinition(JsonObject json) {
        this.json = json == null ? new JsonObject() : json.deepCopy();
    }

    public JsonObject json() { return json.deepCopy(); }
    public boolean enabled() { return bool(json, "enabled", true); }
    public int priority() { return integer(json, "priority", 0); }
    public JsonObject match() { return object(json, "match"); }
    public JsonObject primary() { return object(json, "primary"); }
    public JsonObject secondary() { return object(json, "secondary"); }
    public JsonObject directional() { return object(json, "directional"); }
    public JsonObject firstPerson() { return object(json, "first_person"); }

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

    public static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
