package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.creation.DAI_RecipeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DAI_ContainerRecipeLogic {

    public record Layout(
            Map<String, Integer> slots,
            int inventoryStart
    ) {

        public Integer slot(
                String alias
        ) {

            if (
                    alias == null
                            || slots == null
            ) {
                return null;
            }

            return slots.get(
                    alias
            );
        }
    }

    private static final Map<
            String,
            Layout
            > LAYOUTS =
            new LinkedHashMap<>();

    static {

        registerLayout(
                "FurnaceMenu",
                Map.of(
                        "input",
                        0,
                        "fuel",
                        1,
                        "output",
                        2
                ),
                3
        );

        registerLayout(
                "BlastFurnaceMenu",
                Map.of(
                        "input",
                        0,
                        "fuel",
                        1,
                        "output",
                        2
                ),
                3
        );

        registerLayout(
                "SmokerMenu",
                Map.of(
                        "input",
                        0,
                        "fuel",
                        1,
                        "output",
                        2
                ),
                3
        );
    }

    private DAI_ContainerRecipeLogic() {
        // Utility class.
    }

    public static void registerLayout(
            String menuClassName,
            Map<String, Integer> slots,
            int inventoryStart
    ) {

        if (
                menuClassName == null
                        || menuClassName.isBlank()
                        || slots == null
                        || slots.isEmpty()
        ) {
            return;
        }

        LAYOUTS.put(
                menuClassName,
                new Layout(
                        Map.copyOf(
                                slots
                        ),
                        Math.max(
                                0,
                                inventoryStart
                        )
                )
        );
    }

    public static Layout layout(
            AbstractContainerMenu menu
    ) {

        if (menu == null) {
            return null;
        }

        return LAYOUTS.get(
                menu.getClass()
                        .getSimpleName()
        );
    }

    public static boolean supports(
            AbstractContainerMenu menu,
            DAI_RecipeDefinition recipe
    ) {

        if (
                menu == null
                        || recipe == null
        ) {
            return false;
        }

        return layout(
                menu
        ) != null;
    }

    public static boolean hasIngredient(
            AbstractContainerMenu menu,
            DAI_RecipeDefinition.Ingredient ingredient,
            int requiredCount
    ) {

        Layout layout =
                layout(
                        menu
                );

        if (
                layout == null
                        || ingredient == null
                        || requiredCount <= 0
        ) {
            return false;
        }

        int found =
                0;

        for (
                int slotId = layout.inventoryStart();
                slotId < menu.slots.size();
                slotId++
        ) {

            ItemStack stack =
                    menu.getSlot(
                                    slotId
                            )
                            .getItem();

            if (
                    matches(
                            stack,
                            ingredient
                    )
            ) {

                found +=
                        stack.getCount();

                if (found >= requiredCount) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean moveIngredientToAlias(
            Minecraft minecraft,
            AbstractContainerMenu menu,
            String alias,
            DAI_RecipeDefinition.Ingredient ingredient
    ) {

        if (
                minecraft == null
                        || minecraft.player == null
                        || minecraft.gameMode == null
                        || menu == null
                        || alias == null
                        || ingredient == null
        ) {
            return false;
        }

        Layout layout =
                layout(
                        menu
                );

        if (layout == null) {
            return false;
        }

        Integer targetSlot =
                layout.slot(
                        alias
                );

        if (targetSlot == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Container menu '{}' has no registered slot alias '{}'.",
                    menu.getClass().getSimpleName(),
                    alias
            );

            return false;
        }

        for (
                int amount = 0;
                amount < Math.max(
                        1,
                        ingredient.count()
                );
                amount++
        ) {

            int sourceSlot =
                    findInventorySlot(
                            menu,
                            layout.inventoryStart(),
                            ingredient
                    );

            if (sourceSlot < 0) {
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

    public static ItemStack stackAtAlias(
            AbstractContainerMenu menu,
            String alias
    ) {

        Layout layout =
                layout(
                        menu
                );

        if (
                layout == null
                        || alias == null
        ) {
            return ItemStack.EMPTY;
        }

        Integer slotId =
                layout.slot(
                        alias
                );

        if (
                slotId == null
                        || slotId < 0
                        || slotId >= menu.slots.size()
        ) {
            return ItemStack.EMPTY;
        }

        return menu.getSlot(
                        slotId
                )
                .getItem();
    }

    public static boolean takeAlias(
            Minecraft minecraft,
            AbstractContainerMenu menu,
            String alias
    ) {

        if (
                minecraft == null
                        || minecraft.player == null
                        || minecraft.gameMode == null
        ) {
            return false;
        }

        Layout layout =
                layout(
                        menu
                );

        if (layout == null) {
            return false;
        }

        Integer slotId =
                layout.slot(
                        alias
                );

        if (slotId == null) {
            return false;
        }

        minecraft.gameMode.handleContainerInput(
                menu.containerId,
                slotId,
                0,
                ContainerInput.QUICK_MOVE,
                minecraft.player
        );

        return true;
    }

    public static boolean matches(
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
                    DAI_CraftingMenuLogic.itemId(
                            stack
                    );

            if (
                    ingredient.item()
                            .equals(
                                    stackId
                            )
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

            return stack.is(
                    tag
            );
        }

        return false;
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
                            menu.getSlot(
                                    slotId
                            ).getItem(),
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

        return menu.getCarried()
                .isEmpty();
    }
}