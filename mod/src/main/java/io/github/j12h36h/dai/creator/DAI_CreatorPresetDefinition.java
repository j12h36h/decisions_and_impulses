package io.github.j12h36h.dai.creator;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

/** Data-defined preset/variation patch for one or more Creator schemas. */
public final class DAI_CreatorPresetDefinition {
    public static final String FOLDER = "creator_presets";

    public static final Codec<DAI_CreatorPresetDefinition> CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> {
                Object value = dynamic.convert(JsonOps.INSTANCE).getValue();
                return value instanceof JsonObject object
                        ? new DAI_CreatorPresetDefinition(object)
                        : new DAI_CreatorPresetDefinition(new JsonObject());
            },
            definition -> new Dynamic<>(JsonOps.INSTANCE, definition.json())
    );

    private final JsonObject json;

    public DAI_CreatorPresetDefinition(JsonObject json) {
        this.json = json == null ? new JsonObject() : json.deepCopy();
    }

    public JsonObject json() { return json.deepCopy(); }
    public boolean enabled() { return DAI_CreatorSchemaDefinition.bool(json, "enabled", true); }
    public int priority() { return DAI_CreatorSchemaDefinition.integer(json, "priority", 0); }
    public String displayName() { return DAI_CreatorSchemaDefinition.string(json, "display_name", "Preset"); }
    public String schema() { return DAI_CreatorSchemaDefinition.string(json, "schema", "*"); }
    public String help() { return DAI_CreatorSchemaDefinition.string(json, "help", ""); }
    public JsonObject patch() { return DAI_CreatorSchemaDefinition.object(json, "patch").deepCopy(); }
}
