package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.creation.DAI_CraftingState;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.ArrayList;
import java.util.List;

public final class DAI_CraftingRecipeLogic {

    private DAI_CraftingRecipeLogic() {
        // Utility class.
    }

    public static boolean placeCurrentRecipe(
            Minecraft minecraft,
            boolean craftMax
    ) {

        if (
                minecraft.player == null
                        || minecraft.gameMode == null
                        || !DAI_CraftingState.isPending()
                        || DAI_CraftingState.recipeIndex() < 0
                        || DAI_CraftingState.recipeIndex()
                        >= DAI_CraftingState.recipes().size()
        ) {
            return false;
        }

        AbstractContainerMenu menu =
                minecraft.player.containerMenu;

        if (
                menu == null
                        || menu.containerId
                        != DAI_CraftingState.containerId()
                        || !DAI_CraftingMenuLogic.isCraftingMenu(
                        menu
                )
        ) {
            return false;
        }

        RecipeDisplayEntry recipe =
                DAI_CraftingState.recipes()
                        .get(
                                DAI_CraftingState.recipeIndex()
                        );

        DAI_Core.debug(
                "<DAI>: Placing recipe candidate {}/{} for result='{}', displayId={}, menu='{}', containerId={}, craftMax={}.",
                DAI_CraftingState.recipeIndex() + 1,
                DAI_CraftingState.recipes().size(),
                DAI_CraftingState.result(),
                recipe.id().index(),
                menu.getClass().getSimpleName(),
                DAI_CraftingState.containerId(),
                craftMax
        );

        try {

            minecraft.gameMode.handlePlaceRecipe(
                    DAI_CraftingState.containerId(),
                    recipe.id(),
                    craftMax
            );

            return true;

        } catch (RuntimeException exception) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Recipe candidate {}/{} for '{}' could not be placed.",
                    DAI_CraftingState.recipeIndex() + 1,
                    DAI_CraftingState.recipes().size(),
                    DAI_CraftingState.result(),
                    exception
            );

            return false;
        }
    }

    public static boolean hasNextRecipe() {

        return DAI_CraftingState.recipeIndex() + 1
                < DAI_CraftingState.recipes().size();
    }

    public static List<RecipeDisplayEntry> findRecipesByResult(
            Minecraft minecraft,
            Identifier requestedResult
    ) {

        List<RecipeDisplayEntry> craftableMatches =
                new ArrayList<>();

        List<RecipeDisplayEntry> fallbackMatches =
                new ArrayList<>();

        if (
                minecraft.player == null
                        || requestedResult == null
        ) {
            return craftableMatches;
        }

        ClientRecipeBook recipeBook =
                minecraft.player
                        .getRecipeBook();

        for (
                RecipeCollection collection
                : recipeBook.getCollections()
        ) {

            for (
                    RecipeDisplayEntry entry
                    : collection.getRecipes()
            ) {

                if (entry == null) {
                    continue;
                }

                ItemStack result =
                        resolveRecipeResult(
                                entry
                        );

                if (result.isEmpty()) {
                    continue;
                }

                Identifier resultId =
                        DAI_CraftingMenuLogic.itemId(
                                result
                        );

                if (
                        !requestedResult.equals(
                                resultId
                        )
                ) {
                    continue;
                }

                if (
                        containsRecipe(
                                craftableMatches,
                                entry
                        )
                                || containsRecipe(
                                fallbackMatches,
                                entry
                        )
                ) {
                    continue;
                }

                boolean craftable =
                        collection.isCraftable(
                                entry.id()
                        );

                DAI_Core.debug(
                        "<DAI>: Recipe candidate for result='{}': displayId={}, resolvedResult='{}', craftable={}.",
                        requestedResult,
                        entry.id().index(),
                        resultId,
                        craftable
                );

                if (craftable) {

                    craftableMatches.add(
                            entry
                    );

                } else {

                    fallbackMatches.add(
                            entry
                    );
                }
            }
        }

        if (!craftableMatches.isEmpty()) {

            DAI_Core.debug(
                    "<DAI>: Found {} craftable recipe display(s) for '{}' ({} additional unavailable display(s)).",
                    craftableMatches.size(),
                    requestedResult,
                    fallbackMatches.size()
            );

            return craftableMatches;
        }

        if (!fallbackMatches.isEmpty()) {

            DAI_Core.debug(
                    "<DAI>: Found {} recipe display(s) for '{}', but none are currently marked craftable.",
                    fallbackMatches.size(),
                    requestedResult
            );
        }

        return fallbackMatches;
    }

    private static boolean containsRecipe(
            List<RecipeDisplayEntry> recipes,
            RecipeDisplayEntry candidate
    ) {

        if (
                recipes == null
                        || candidate == null
        ) {
            return false;
        }

        for (
                RecipeDisplayEntry existing
                : recipes
        ) {

            if (
                    existing != null
                            && existing.id()
                            .equals(
                                    candidate.id()
                            )
            ) {
                return true;
            }
        }

        return false;
    }

    private static ItemStack resolveRecipeResult(
            RecipeDisplayEntry entry
    ) {

        if (entry == null) {
            return ItemStack.EMPTY;
        }

        SlotDisplay resultDisplay =
                entry.display()
                        .result();

        if (
                resultDisplay
                        instanceof SlotDisplay.ItemSlotDisplay itemDisplay
        ) {

            return new ItemStack(
                    itemDisplay.item()
                            .value()
            );
        }

        if (
                resultDisplay
                        instanceof SlotDisplay.ItemStackSlotDisplay stackDisplay
        ) {

            return stackDisplay.stack()
                    .create();
        }

        return ItemStack.EMPTY;
    }
}