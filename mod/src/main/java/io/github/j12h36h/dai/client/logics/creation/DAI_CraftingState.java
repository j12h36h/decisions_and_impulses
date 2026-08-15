package io.github.j12h36h.dai.client.logics.creation;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.List;

public final class DAI_CraftingState {

    private static boolean pending;

    private static boolean fallback;

    private static int containerId =
            -1;

    private static Identifier result;

    private static List<RecipeDisplayEntry> recipes =
            List.of();

    private static int recipeIndex;

    private static int recipePolls;

    private DAI_CraftingState() {
        // Utility class.
    }

    public static void begin(
            int newContainerId,
            Identifier newResult,
            List<RecipeDisplayEntry> newRecipes
    ) {

        pending =
                true;

        fallback =
                false;

        containerId =
                newContainerId;

        result =
                newResult;

        recipes =
                newRecipes == null
                        ? List.of()
                        : List.copyOf(
                        newRecipes
                );

        recipeIndex =
                0;

        recipePolls =
                0;
    }

    public static void beginFallback(
            int newContainerId,
            Identifier newResult
    ) {

        pending =
                true;

        fallback =
                true;

        containerId =
                newContainerId;

        result =
                newResult;

        recipes =
                List.of();

        recipeIndex =
                0;

        recipePolls =
                0;
    }

    public static void clear() {

        pending =
                false;

        fallback =
                false;

        containerId =
                -1;

        result =
                null;

        recipes =
                List.of();

        recipeIndex =
                0;

        recipePolls =
                0;
    }

    public static void incrementRecipePolls() {

        recipePolls++;
    }

    public static void advanceRecipe() {

        recipeIndex++;

        recipePolls =
                0;
    }

    public static boolean isPending() {

        return pending;
    }

    public static boolean isFallback() {

        return fallback;
    }

    public static int containerId() {

        return containerId;
    }

    public static Identifier result() {

        return result;
    }

    public static List<RecipeDisplayEntry> recipes() {

        return recipes;
    }

    public static int recipeIndex() {

        return recipeIndex;
    }

    public static int recipePolls() {

        return recipePolls;
    }
}
