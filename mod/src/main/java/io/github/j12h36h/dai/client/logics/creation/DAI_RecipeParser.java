package io.github.j12h36h.dai.client.logics.creation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DAI_RecipeParser {

    private DAI_RecipeParser() {
        // Utility class.
    }

    public static void load(
            Identifier resourceId,
            Resource resource
    ) {

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        resource.open(),
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            JsonElement root =
                    JsonParser.parseReader(
                            reader
                    );

            if (!root.isJsonObject()) {
                DAI_RecipeJson.warn(resourceId, "root must be a JSON object");
                return;
            }

            loadJson(resourceId, root.getAsJsonObject());

        } catch (Exception exception) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Failed loading processing recipe '{}'.",
                    resourceId,
                    exception
            );
        }
    }


    /** Parses an already-loaded datapack JSON object for client-local data loading. */
    public static void loadJson(Identifier resourceId, JsonObject json) {
        if (resourceId == null || json == null) return;

        Identifier recipeId = toRecipeId(resourceId);
        DAI_RecipeDefinition definition = parse(recipeId, json);
        if (definition != null) DAI_RecipeRegistry.register(definition);
    }

    private static DAI_RecipeDefinition parse(
            Identifier recipeId,
            JsonObject json
    ) {

        String type =
                DAI_RecipeJson.string(
                        json,
                        "type"
                );

        String container =
                DAI_RecipeJson.string(
                        json,
                        "container"
                );

        if (
                type == null
                        || type.isBlank()
        ) {
            DAI_RecipeJson.warn(recipeId, "missing 'type'");
            return null;
        }

        DAI_RecipeDefinition.Result result =
                parseResult(
                        DAI_RecipeJson.object(
                                json,
                                "result"
                        )
                );

        if (
                result == null
                        || !result.isValid()
        ) {
            DAI_RecipeJson.warn(recipeId, "invalid or missing result");
            return null;
        }

        if (
                "crafting_shaped".equalsIgnoreCase(
                        type
                )
        ) {

            return parseShaped(
                    recipeId,
                    type,
                    container,
                    json,
                    result
            );
        }

        if (
                "smelting".equalsIgnoreCase(
                        type
                )
                        || "container_process".equalsIgnoreCase(
                        type
                )
        ) {

            return parseContainerProcess(
                    recipeId,
                    type,
                    container,
                    json,
                    result
            );
        }

        DAI_RecipeJson.warn(
                recipeId,
                "unsupported process type '"
                        + type
                        + "'"
        );

        return null;
    }

    private static DAI_RecipeDefinition parseShaped(
            Identifier recipeId,
            String type,
            String container,
            JsonObject json,
            DAI_RecipeDefinition.Result result
    ) {

        JsonElement patternElement =
                json.get(
                        "pattern"
                );

        if (
                patternElement == null
                        || !patternElement.isJsonArray()
        ) {
            DAI_RecipeJson.warn(recipeId, "crafting_shaped requires 'pattern'");
            return null;
        }

        List<String> pattern =
                new ArrayList<>();

        patternElement.getAsJsonArray()
                .forEach(
                        element -> pattern.add(
                                element.getAsString()
                        )
                );

        if (
                pattern.isEmpty()
                        || pattern.size() > 3
        ) {
            DAI_RecipeJson.warn(recipeId, "pattern height must be 1..3");
            return null;
        }

        int width =
                pattern.getFirst()
                        .length();

        if (
                width <= 0
                        || width > 3
        ) {
            DAI_RecipeJson.warn(recipeId, "pattern width must be 1..3");
            return null;
        }

        for (String row : pattern) {

            if (
                    row == null
                            || row.length() != width
            ) {
                DAI_RecipeJson.warn(recipeId, "all pattern rows must have equal width");
                return null;
            }
        }

        JsonObject keyObject =
                DAI_RecipeJson.object(
                        json,
                        "key"
                );

        if (keyObject == null) {
            DAI_RecipeJson.warn(recipeId, "crafting_shaped requires 'key'");
            return null;
        }

        Map<Character, DAI_RecipeDefinition.Ingredient> key =
                new LinkedHashMap<>();

        for (
                Map.Entry<String, JsonElement> entry
                : keyObject.entrySet()
        ) {

            if (
                    entry.getKey() == null
                            || entry.getKey().length() != 1
                            || !entry.getValue().isJsonObject()
            ) {
                continue;
            }

            DAI_RecipeDefinition.Ingredient ingredient =
                    parseIngredient(
                            entry.getValue()
                                    .getAsJsonObject()
                    );

            if (
                    ingredient != null
                            && ingredient.isValid()
            ) {
                key.put(
                        entry.getKey().charAt(0),
                        ingredient
                );
            }
        }

        for (String row : pattern) {

            for (
                    int index = 0;
                    index < row.length();
                    index++
            ) {

                char symbol =
                        row.charAt(index);

                if (
                        symbol != ' '
                                && !key.containsKey(
                                symbol
                        )
                ) {
                    DAI_RecipeJson.warn(
                            recipeId,
                            "pattern references undefined key '"
                                    + symbol
                                    + "'"
                    );
                    return null;
                }
            }
        }

        return new DAI_RecipeDefinition(
                recipeId,
                type,
                container == null
                        ? "minecraft:crafting_table"
                        : container,
                width,
                pattern.size(),
                List.copyOf(pattern),
                Map.copyOf(key),
                Map.of(),
                result,
                0
        );
    }

    private static DAI_RecipeDefinition parseContainerProcess(
            Identifier recipeId,
            String type,
            String container,
            JsonObject json,
            DAI_RecipeDefinition.Result result
    ) {

        JsonObject slotsObject =
                DAI_RecipeJson.object(
                        json,
                        "slots"
                );

        if (slotsObject == null) {
            DAI_RecipeJson.warn(recipeId, type + " requires 'slots'");
            return null;
        }

        Map<String, DAI_RecipeDefinition.Ingredient> slots =
                new LinkedHashMap<>();

        for (
                Map.Entry<String, JsonElement> entry
                : slotsObject.entrySet()
        ) {

            if (!entry.getValue().isJsonObject()) {
                continue;
            }

            DAI_RecipeDefinition.Ingredient ingredient =
                    parseIngredient(
                            entry.getValue()
                                    .getAsJsonObject()
                    );

            if (
                    ingredient != null
                            && ingredient.isValid()
            ) {
                slots.put(
                        entry.getKey(),
                        ingredient
                );
            }
        }

        if (slots.isEmpty()) {
            DAI_RecipeJson.warn(recipeId, type + " has no valid slot ingredients");
            return null;
        }

        return new DAI_RecipeDefinition(
                recipeId,
                type,
                container,
                0,
                0,
                List.of(),
                Map.of(),
                Map.copyOf(slots),
                result,
                Math.max(
                        0,
                        DAI_RecipeJson.integer(
                                json,
                                "process_ticks",
                                0
                        )
                )
        );
    }

    private static DAI_RecipeDefinition.Ingredient parseIngredient(
            JsonObject json
    ) {

        if (json == null) {
            return null;
        }

        return new DAI_RecipeDefinition.Ingredient(
                DAI_RecipeJson.identifier(
                        DAI_RecipeJson.string(
                                json,
                                "item"
                        )
                ),
                DAI_RecipeJson.identifier(
                        DAI_RecipeJson.string(
                                json,
                                "tag"
                        )
                ),
                Math.max(
                        1,
                        DAI_RecipeJson.integer(
                                json,
                                "count",
                                1
                        )
                )
        );
    }

    private static DAI_RecipeDefinition.Result parseResult(
            JsonObject json
    ) {

        if (json == null) {
            return null;
        }

        return new DAI_RecipeDefinition.Result(
                DAI_RecipeJson.identifier(
                        DAI_RecipeJson.string(
                                json,
                                "item"
                        )
                ),
                Math.max(
                        1,
                        DAI_RecipeJson.integer(
                                json,
                                "count",
                                1
                        )
                )
        );
    }

    private static Identifier toRecipeId(
            Identifier resourceId
    ) {

        String path =
                resourceId.getPath();

        String prefix =
                DAI_RecipeLoader.DIRECTORY
                        + "/";

        if (path.startsWith(prefix)) {
            path =
                    path.substring(
                            prefix.length()
                    );
        }

        if (path.endsWith(".json")) {
            path =
                    path.substring(
                            0,
                            path.length() - 5
                    );
        }

        return Identifier.fromNamespaceAndPath(
                resourceId.getNamespace(),
                path
        );
    }

}