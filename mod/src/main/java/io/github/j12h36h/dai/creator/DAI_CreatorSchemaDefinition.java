package io.github.j12h36h.dai.creator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

import java.util.Locale;

/**
 * Open-ended Creator schema. DAI supplies the editor/runtime primitives while
 * datapacks/resource packs describe authorable modules, fields, rails, presets,
 * help text, previews and templates through JSON.
 */
public final class DAI_CreatorSchemaDefinition {
    public static final String FOLDER = "creator_schemas";

    public static final Codec<DAI_CreatorSchemaDefinition> CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> {
                Object value = dynamic.convert(JsonOps.INSTANCE).getValue();
                return value instanceof JsonObject object
                        ? new DAI_CreatorSchemaDefinition(object)
                        : new DAI_CreatorSchemaDefinition(new JsonObject());
            },
            definition -> new Dynamic<>(JsonOps.INSTANCE, definition.json())
    );

    private final JsonObject json;

    public DAI_CreatorSchemaDefinition(JsonObject json) {
        this.json = json == null ? new JsonObject() : json.deepCopy();
    }

    public JsonObject json() { return json.deepCopy(); }
    public boolean enabled() { return bool(json, "enabled", true); }
    public int priority() { return integer(json, "priority", 0); }
    public String displayName() { return string(json, "display_name", "Creator Module"); }
    public String shortName() { return string(json, "short_name", displayName()); }
    public String description() { return string(json, "description", ""); }
    public String folder() { return string(json, "folder", ""); }
    public String rail() { return normalized(string(json, "rail", "standalone")); }
    public String category() { return string(json, "category", "GENERAL"); }
    public String previewType() { return normalized(string(object(json, "preview"), "type", "json")); }
    public String previewSource() { return normalized(string(object(json, "preview"), "source", "registry")); }
    public String previewScene() { return string(object(json, "preview"), "scene", ""); }
    public JsonArray fields() { return array(json, "fields"); }
    public JsonArray variations() { return array(json, "variations"); }
    public JsonArray rightActions() { return array(json, "right_actions"); }
    public JsonArray tools() { return array(json, "tools"); }
    public JsonObject layout() { return object(json, "layout"); }
    public JsonObject template() {
        JsonObject template = object(json, "template");
        return template.deepCopy();
    }

    public static JsonObject object(JsonObject root, String key) {
        if (root == null || key == null || !root.has(key)) return new JsonObject();
        JsonElement value = root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    public static JsonArray array(JsonObject root, String key) {
        if (root == null || key == null || !root.has(key)) return new JsonArray();
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
        try { return root != null && root.has(key) ? root.get(key).getAsDouble() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    public static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
    }
}
