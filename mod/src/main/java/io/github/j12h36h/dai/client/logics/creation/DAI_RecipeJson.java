package io.github.j12h36h.dai.client.logics.creation;

import com.google.gson.JsonObject;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.Identifier;

public final class DAI_RecipeJson {

    private DAI_RecipeJson() {
        // Utility class.
    }

    public static JsonObject object(
            JsonObject json,
            String key
    ) {

        if (
                json == null
                        || !json.has(key)
                        || !json.get(key).isJsonObject()
        ) {
            return null;
        }

        return json.getAsJsonObject(
                key
        );
    }

    public static String string(
            JsonObject json,
            String key
    ) {

        if (
                json == null
                        || !json.has(key)
                        || json.get(key).isJsonNull()
        ) {
            return null;
        }

        return json.get(key)
                .getAsString();
    }

    public static int integer(
            JsonObject json,
            String key,
            int fallback
    ) {

        if (
                json == null
                        || !json.has(key)
        ) {
            return fallback;
        }

        return json.get(key)
                .getAsInt();
    }

    public static Identifier identifier(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {
            return null;
        }

        String normalized =
                value.contains(":")
                        ? value
                        : "minecraft:"
                        + value;

        return Identifier.tryParse(
                normalized
        );
    }

    public static void warn(
            Identifier id,
            String reason
    ) {

        DAI_Core.LOGGER.warn(
                "<DAI>: Invalid processing recipe '{}': {}.",
                id,
                reason
        );
    }
}
