package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.creation.DAI_CraftingGrid;
import io.github.j12h36h.dai.client.logics.creation.DAI_RecipeDefinition;
import io.github.j12h36h.dai.client.logics.creation.DAI_RecipeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;

public final class DAI_CraftingFallbackLogic {

    public enum Result {
        PLACED,
        NOT_FOUND,
        WRONG_MENU,
        MISSING_INGREDIENTS,
        FAILED
    }

    private DAI_CraftingFallbackLogic() {
        // Utility class.
    }

    public static Result tryPlace(
            Minecraft minecraft,
            Identifier requestedResult
    ) {

        if (
                minecraft == null
                        || minecraft.player == null
                        || minecraft.gameMode == null
                        || requestedResult == null
        ) {
            return Result.FAILED;
        }

        List<DAI_RecipeDefinition> candidates =
                DAI_RecipeRegistry.findByResult(
                        requestedResult
                );

        if (candidates.isEmpty()) {
            return Result.NOT_FOUND;
        }

        AbstractContainerMenu menu =
                minecraft.player.containerMenu;

        boolean correctMenu =
                false;

        for (
                DAI_RecipeDefinition recipe
                : candidates
        ) {

            if (
                    recipe == null
                            || !recipe.isCrafting()
            ) {
                continue;
            }

            if (
                    !DAI_CraftingGrid.menuSupports(
                            menu,
                            recipe
                    )
            ) {
                continue;
            }

            correctMenu =
                    true;

            if (
                    !DAI_CraftingGrid.hasIngredients(
                            menu,
                            recipe
                    )
            ) {
                continue;
            }

            DAI_Core.LOGGER.info(
                    "<DAI>: Vanilla recipe lookup failed for '{}'; using DAI fallback recipe '{}'.",
                    requestedResult,
                    recipe.id()
            );

            return DAI_CraftingGrid.placePattern(
                    minecraft,
                    menu,
                    recipe
            )
                    ? Result.PLACED
                    : Result.FAILED;
        }

        return correctMenu
                ? Result.MISSING_INGREDIENTS
                : Result.WRONG_MENU;
    }
}
