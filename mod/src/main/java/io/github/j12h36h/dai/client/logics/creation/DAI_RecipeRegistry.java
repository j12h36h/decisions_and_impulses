package io.github.j12h36h.dai.client.logics.creation;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DAI_RecipeRegistry {

    private static final Map<
            Identifier,
            DAI_RecipeDefinition
            > RECIPES =
            new LinkedHashMap<>();

    private DAI_RecipeRegistry() {
        // Utility class.
    }

    public static void clear() {

        RECIPES.clear();
    }

    public static void register(
            DAI_RecipeDefinition recipe
    ) {

        if (
                recipe == null
                        || recipe.id() == null
        ) {
            return;
        }

        RECIPES.put(
                recipe.id(),
                recipe
        );
    }

    public static DAI_RecipeDefinition get(
            Identifier id
    ) {

        return id == null
                ? null
                : RECIPES.get(
                id
        );
    }

    public static List<DAI_RecipeDefinition> findByResult(
            Identifier result
    ) {

        if (result == null) {
            return List.of();
        }

        List<DAI_RecipeDefinition> matches =
                new ArrayList<>();

        for (
                DAI_RecipeDefinition recipe
                : RECIPES.values()
        ) {

            if (
                    recipe == null
                            || recipe.result() == null
                            || recipe.result().item() == null
            ) {
                continue;
            }

            if (
                    result.equals(
                            recipe.result().item()
                    )
            ) {

                matches.add(
                        recipe
                );
            }
        }

        return List.copyOf(
                matches
        );
    }

    public static List<DAI_RecipeDefinition> all() {

        return Collections.unmodifiableList(
                new ArrayList<>(
                        RECIPES.values()
                )
        );
    }

    public static int size() {

        return RECIPES.size();
    }
}
