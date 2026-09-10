package io.github.j12h36h.dai.story;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;

import java.util.Map;

/** Shared apply helper for datapack/resource discovery of story profiles. */
public final class DAI_StoryProfileLoader {
    public static final String FOLDER = DAI_StoryProfileDefinition.FOLDER;
    private DAI_StoryProfileLoader() {}

    public static void applyDefinitions(Map<Identifier, DAI_StoryProfileDefinition> definitions) {
        DAI_StoryProfileRegistry.clear();
        if (definitions != null) definitions.forEach(DAI_StoryProfileRegistry::register);
    }

    public static DAI_StoryProfileDefinition decode(JsonElement json) {
        if (json == null) return null;
        return DAI_StoryProfileDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(null);
    }
}
