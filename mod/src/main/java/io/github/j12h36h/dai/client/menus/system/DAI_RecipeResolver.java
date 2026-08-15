package io.github.j12h36h.dai.client.menus.system;


import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplay;

public final class DAI_RecipeResolver {

    private DAI_RecipeResolver() {
        // Utility class.
    }

    public static RecipeDisplayEntry findByResult(
            Minecraft minecraft,
            Identifier requestedResult
    ) {

        if (
                minecraft == null
                        || minecraft.player == null
                        || requestedResult == null
        ) {
            return null;
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

                ItemStack result =
                        resolveSimpleResult(
                                entry
                        );

                if (result.isEmpty()) {
                    continue;
                }

                Identifier resultId =
                        result.getItem()
                                .builtInRegistryHolder()
                                .key()
                                .identifier();

                if (requestedResult.equals(resultId)) {
                    return entry;
                }
            }
        }

        return null;
    }

    public static boolean hasResult(
            Minecraft minecraft,
            Identifier requestedResult
    ) {

        return findByResult(
                minecraft,
                requestedResult
        ) != null;
    }

    private static ItemStack resolveSimpleResult(
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
