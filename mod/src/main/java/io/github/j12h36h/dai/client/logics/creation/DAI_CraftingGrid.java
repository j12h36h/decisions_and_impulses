package io.github.j12h36h.dai.client.logics.creation;

import io.github.j12h36h.dai.client.logics.DAI_CraftingMenuLogic;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class DAI_CraftingGrid {

    private DAI_CraftingGrid() {
        // Utility class.
    }

    public static boolean menuSupports(
            AbstractContainerMenu menu,
            DAI_RecipeDefinition recipe
    ) {

        if (
                menu == null
                        || recipe == null
        ) {
            return false;
        }

        String menuName =
                menu.getClass()
                        .getSimpleName();

        if (
                recipe.width() > 2
                        || recipe.height() > 2
        ) {
            return "CraftingMenu".equals(menuName);
        }

        return "InventoryMenu".equals(menuName)
                || "CraftingMenu".equals(menuName);
    }

    public static boolean hasIngredients(
            AbstractContainerMenu menu,
            DAI_RecipeDefinition recipe
    ) {

        if (
                menu == null
                        || recipe == null
        ) {
            return false;
        }

        for (
                DAI_RecipeDefinition.Ingredient ingredient
                : recipe.key().values()
        ) {

            int required =
                    countPatternUses(
                            recipe,
                            ingredient
                    )
                            * ingredient.count();

            if (
                    countAvailable(
                            menu,
                            ingredient
                    )
                            < required
            ) {
                return false;
            }
        }

        return true;
    }

    public static boolean placePattern(
            Minecraft minecraft,
            AbstractContainerMenu menu,
            DAI_RecipeDefinition recipe
    ) {

        if (
                minecraft.player == null
                        || minecraft.gameMode == null
                        || !menu.getCarried().isEmpty()
        ) {
            return false;
        }

        if (
                !clearGrid(
                        minecraft,
                        menu
                )
        ) {
            return false;
        }

        int gridWidth =
                gridWidth(menu);

        int inventoryStart =
                firstInventorySlot(menu);

        for (
                int row = 0;
                row < recipe.height();
                row++
        ) {

            String patternRow =
                    recipe.pattern().get(row);

            for (
                    int column = 0;
                    column < recipe.width();
                    column++
            ) {

                char symbol =
                        patternRow.charAt(column);

                if (symbol == ' ') {
                    continue;
                }

                DAI_RecipeDefinition.Ingredient ingredient =
                        recipe.key().get(symbol);

                int targetSlot =
                        1
                                + row * gridWidth
                                + column;

                if (
                        !fillSlot(
                                minecraft,
                                menu,
                                inventoryStart,
                                targetSlot,
                                ingredient
                        )
                ) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean fillSlot(
            Minecraft minecraft,
            AbstractContainerMenu menu,
            int inventoryStart,
            int targetSlot,
            DAI_RecipeDefinition.Ingredient ingredient
    ) {

        for (
                int amount = 0;
                amount < ingredient.count();
                amount++
        ) {

            int sourceSlot =
                    findInventorySlot(
                            menu,
                            inventoryStart,
                            ingredient
                    );

            if (sourceSlot < 0) {

                DAI_Core.LOGGER.warn(
                        "<DAI>: Fallback crafting ran out of ingredient '{}' while filling slot {}.",
                        ingredient,
                        targetSlot
                );

                return false;
            }

            if (
                    !moveOne(
                            minecraft,
                            menu,
                            sourceSlot,
                            targetSlot
                    )
            ) {
                return false;
            }
        }

        return true;
    }

    private static boolean clearGrid(
            Minecraft minecraft,
            AbstractContainerMenu menu
    ) {

        int gridSlots =
                "CraftingMenu".equals(
                        menu.getClass().getSimpleName()
                )
                        ? 9
                        : 4;

        for (
                int slotId = 1;
                slotId <= gridSlots;
                slotId++
        ) {

            if (
                    menu.getSlot(slotId)
                            .getItem()
                            .isEmpty()
            ) {
                continue;
            }

            minecraft.gameMode.handleContainerInput(
                    menu.containerId,
                    slotId,
                    0,
                    ContainerInput.QUICK_MOVE,
                    minecraft.player
            );
        }

        return true;
    }

    private static int findInventorySlot(
            AbstractContainerMenu menu,
            int inventoryStart,
            DAI_RecipeDefinition.Ingredient ingredient
    ) {

        for (
                int slotId = inventoryStart;
                slotId < menu.slots.size();
                slotId++
        ) {

            if (
                    matches(
                            menu.getSlot(slotId).getItem(),
                            ingredient
                    )
            ) {
                return slotId;
            }
        }

        return -1;
    }

    private static boolean moveOne(
            Minecraft minecraft,
            AbstractContainerMenu menu,
            int sourceSlot,
            int targetSlot
    ) {

        minecraft.gameMode.handleContainerInput(
                menu.containerId,
                sourceSlot,
                0,
                ContainerInput.PICKUP,
                minecraft.player
        );

        minecraft.gameMode.handleContainerInput(
                menu.containerId,
                targetSlot,
                1,
                ContainerInput.PICKUP,
                minecraft.player
        );

        minecraft.gameMode.handleContainerInput(
                menu.containerId,
                sourceSlot,
                0,
                ContainerInput.PICKUP,
                minecraft.player
        );

        return menu.getCarried().isEmpty();
    }

    private static boolean matches(
            ItemStack stack,
            DAI_RecipeDefinition.Ingredient ingredient
    ) {

        if (
                stack == null
                        || stack.isEmpty()
                        || ingredient == null
        ) {
            return false;
        }

        if (ingredient.item() != null) {

            Identifier stackId =
                    DAI_CraftingMenuLogic.itemId(stack);

            if (
                    ingredient.item()
                            .equals(stackId)
            ) {
                return true;
            }
        }

        if (ingredient.tag() != null) {

            TagKey<Item> tag =
                    TagKey.create(
                            Registries.ITEM,
                            ingredient.tag()
                    );

            return stack.is(tag);
        }

        return false;
    }

    private static int countPatternUses(
            DAI_RecipeDefinition recipe,
            DAI_RecipeDefinition.Ingredient ingredient
    ) {

        int count =
                0;

        for (String row : recipe.pattern()) {
            for (
                    int index = 0;
                    index < row.length();
                    index++
            ) {

                DAI_RecipeDefinition.Ingredient mapped =
                        recipe.key().get(
                                row.charAt(index)
                        );

                if (ingredient.equals(mapped)) {
                    count++;
                }
            }
        }

        return count;
    }

    private static int countAvailable(
            AbstractContainerMenu menu,
            DAI_RecipeDefinition.Ingredient ingredient
    ) {

        int total =
                0;

        for (
                int slotId = firstInventorySlot(menu);
                slotId < menu.slots.size();
                slotId++
        ) {

            ItemStack stack =
                    menu.getSlot(slotId).getItem();

            if (matches(stack, ingredient)) {
                total += stack.getCount();
            }
        }

        return total;
    }

    private static int gridWidth(
            AbstractContainerMenu menu
    ) {

        return "CraftingMenu".equals(
                menu.getClass().getSimpleName()
        )
                ? 3
                : 2;
    }

    private static int firstInventorySlot(
            AbstractContainerMenu menu
    ) {

        return "CraftingMenu".equals(
                menu.getClass().getSimpleName()
        )
                ? 10
                : 9;
    }
}
