package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.creation.DAI_RecipeDefinition;
import io.github.j12h36h.dai.client.logics.creation.DAI_RecipeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class DAI_SmeltingFallbackLogic {

    public enum Result {
        STARTED,
        NOT_FOUND,
        WRONG_MENU,
        MISSING_INPUT,
        MISSING_FUEL,
        FAILED
    }

    private DAI_SmeltingFallbackLogic() {
        // Utility class.
    }

    public static Result tryStart(
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

        boolean foundSmeltingRecipe =
                false;

        boolean correctMenu =
                false;

        for (
                DAI_RecipeDefinition recipe
                : candidates
        ) {

            if (
                    recipe == null
                            || !recipe.isSmelting()
            ) {
                continue;
            }

            foundSmeltingRecipe =
                    true;

            if (
                    !DAI_ContainerRecipeLogic.supports(
                            menu,
                            recipe
                    )
            ) {
                continue;
            }

            correctMenu =
                    true;

            DAI_RecipeDefinition.Ingredient input =
                    recipe.slots()
                            .get(
                                    "input"
                            );

            DAI_RecipeDefinition.Ingredient fuel =
                    recipe.slots()
                            .get(
                                    "fuel"
                            );

            if (
                    input == null
                            || !input.isValid()
            ) {
                return Result.FAILED;
            }

            if (
                    !DAI_ContainerRecipeLogic.hasIngredient(
                            menu,
                            input,
                            Math.max(
                                    1,
                                    input.count()
                            )
                    )
            ) {
                return Result.MISSING_INPUT;
            }

            if (
                    fuel != null
                            && fuel.isValid()
                            && !DAI_ContainerRecipeLogic.hasIngredient(
                            menu,
                            fuel,
                            Math.max(
                                    1,
                                    fuel.count()
                            )
                    )
            ) {
                return Result.MISSING_FUEL;
            }

            if (
                    !DAI_ContainerRecipeLogic.moveIngredientToAlias(
                            minecraft,
                            menu,
                            "input",
                            input
                    )
            ) {
                return Result.FAILED;
            }

            if (
                    fuel != null
                            && fuel.isValid()
                            && !DAI_ContainerRecipeLogic.moveIngredientToAlias(
                            minecraft,
                            menu,
                            "fuel",
                            fuel
                    )
            ) {
                return Result.FAILED;
            }

            DAI_Core.LOGGER.info(
                    "<DAI>: Started DAI smelting fallback recipe '{}' for result='{}' in menu='{}'.",
                    recipe.id(),
                    requestedResult,
                    menu.getClass().getSimpleName()
            );

            return Result.STARTED;
        }

        if (!foundSmeltingRecipe) {
            return Result.NOT_FOUND;
        }

        return correctMenu
                ? Result.FAILED
                : Result.WRONG_MENU;
    }

    public static boolean resultReady(
            Minecraft minecraft,
            Identifier requestedResult
    ) {

        if (
                minecraft == null
                        || minecraft.player == null
                        || requestedResult == null
        ) {
            return false;
        }

        AbstractContainerMenu menu =
                minecraft.player.containerMenu;

        ItemStack output =
                DAI_ContainerRecipeLogic.stackAtAlias(
                        menu,
                        "output"
                );

        if (output.isEmpty()) {
            return false;
        }

        Identifier outputId =
                DAI_CraftingMenuLogic.itemId(
                        output
                );

        return requestedResult.equals(
                outputId
        );
    }

    public static boolean takeResult(
            Minecraft minecraft,
            Identifier requestedResult
    ) {

        if (
                !resultReady(
                        minecraft,
                        requestedResult
                )
        ) {
            return false;
        }

        return DAI_ContainerRecipeLogic.takeAlias(
                minecraft,
                minecraft.player.containerMenu,
                "output"
        );
    }
}
