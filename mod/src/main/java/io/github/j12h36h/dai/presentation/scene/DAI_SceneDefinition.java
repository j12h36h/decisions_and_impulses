package io.github.j12h36h.dai.presentation.scene;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

/** Open-ended JSON scene graph. The engine renders primitives; packs define every scene. */
public final class DAI_SceneDefinition {
    public static final Codec<DAI_SceneDefinition> CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> {
                Object value = dynamic.convert(JsonOps.INSTANCE).getValue();
                return value instanceof JsonObject object
                        ? new DAI_SceneDefinition(object)
                        : new DAI_SceneDefinition(new JsonObject());
            },
            definition -> new Dynamic<>(JsonOps.INSTANCE, definition.json())
    );

    private final JsonObject json;

    public DAI_SceneDefinition(JsonObject json) {
        this.json = json == null ? new JsonObject() : json.deepCopy();
    }

    public JsonObject json() { return json.deepCopy(); }
    public boolean enabled() { return bool(json, "enabled", true); }
    public int priority() { return integer(json, "priority", 0); }

    public static JsonObject object(JsonObject root, String key) {
        if (root == null || key == null) return new JsonObject();
        JsonElement value = root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
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
}
