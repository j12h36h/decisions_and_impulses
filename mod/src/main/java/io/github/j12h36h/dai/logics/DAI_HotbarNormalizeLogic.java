package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Deterministic survival-hotbar normalization.
 *
 * Reserved slots (one-based for players):
 * 1 sword, 2 pickaxe, 3 axe, 4 shovel, 6 food.
 * Slot 5 is an intentionally empty staging slot for temporary building blocks.
 * Slots 7-9 are intentionally left untouched for context-specific items.
 *
 * The operation is bounded to at most six SWAP operations and never runs
 * while an external container is open. This keeps inventory normalization
 * from becoming another persistent controller or source of queue growth.
 */
public final class DAI_HotbarNormalizeLogic {

    private static final int HOTBAR_SIZE = 9;
    private static final int BUILD_STAGING_SLOT = 4;

    private static final SlotRule[] RULES = {
            new SlotRule(0, ids(
                    "minecraft:netherite_sword",
                    "minecraft:diamond_sword",
                    "minecraft:iron_sword",
                    "minecraft:stone_sword",
                    "minecraft:golden_sword",
                    "minecraft:wooden_sword"
            )),
            new SlotRule(1, ids(
                    "minecraft:netherite_pickaxe",
                    "minecraft:diamond_pickaxe",
                    "minecraft:iron_pickaxe",
                    "minecraft:stone_pickaxe",
                    "minecraft:golden_pickaxe",
                    "minecraft:wooden_pickaxe"
            )),
            new SlotRule(2, ids(
                    "minecraft:netherite_axe",
                    "minecraft:diamond_axe",
                    "minecraft:iron_axe",
                    "minecraft:stone_axe",
                    "minecraft:golden_axe",
                    "minecraft:wooden_axe"
            )),
            new SlotRule(3, ids(
                    "minecraft:netherite_shovel",
                    "minecraft:diamond_shovel",
                    "minecraft:iron_shovel",
                    "minecraft:stone_shovel",
                    "minecraft:golden_shovel",
                    "minecraft:wooden_shovel"
            )),
            new SlotRule(5, ids(
                    "minecraft:enchanted_golden_apple",
                    "minecraft:golden_apple",
                    "minecraft:golden_carrot",
                    "minecraft:cooked_beef",
                    "minecraft:cooked_porkchop",
                    "minecraft:cooked_mutton",
                    "minecraft:cooked_chicken",
                    "minecraft:cooked_rabbit",
                    "minecraft:cooked_salmon",
                    "minecraft:cooked_cod",
                    "minecraft:bread",
                    "minecraft:baked_potato",
                    "minecraft:apple",
                    "minecraft:carrot",
                    "minecraft:melon_slice",
                    "minecraft:sweet_berries",
                    "minecraft:glow_berries",
                    "minecraft:dried_kelp"
            ))
    };

    private DAI_HotbarNormalizeLogic() {
        // Utility class.
    }

    public static void normalize(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.gameMode == null
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.debug(
                    "<DAI>: Cannot normalize survival hotbar without a player and game mode."
            );

            return;
        }

        if (
                !(minecraft.player.containerMenu
                        instanceof InventoryMenu)
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.debug(
                    "<DAI>: Deferred survival-hotbar normalization because another container is open."
            );

            return;
        }

        Inventory inventory =
                minecraft.player
                        .getInventory();

        AbstractContainerMenu menu =
                minecraft.player
                        .containerMenu;

        int selectedSlot =
                inventory.getSelectedSlot();

        int moved = 0;
        int unresolved = 0;

        for (SlotRule rule : RULES) {

            int sourceSlot =
                    findBestSource(
                            inventory,
                            rule.candidates()
                    );

            if (sourceSlot < 0) {

                unresolved++;
                continue;
            }

            if (sourceSlot == rule.hotbarSlot()) {
                continue;
            }

            int sourceMenuSlot =
                    inventorySlotToMenuSlot(
                            sourceSlot
                    );

            minecraft.gameMode.handleContainerInput(
                    menu.containerId,
                    sourceMenuSlot,
                    rule.hotbarSlot(),
                    ContainerInput.SWAP,
                    minecraft.player
            );

            moved++;
        }

        if (clearBuildStagingSlot(
                minecraft,
                inventory,
                menu
        )) {
            moved++;
        } else if (!inventory.getItem(BUILD_STAGING_SLOT).isEmpty()) {
            unresolved++;
        }

        inventory.setSelectedSlot(
                Math.max(
                        0,
                        Math.min(
                                selectedSlot,
                                HOTBAR_SIZE - 1
                        )
                )
        );

        DAI_HotbarLogic.syncSelectedSlotFromPlayer();

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Normalized survival hotbar (moved={}, unresolved_reserved_slots={}).",
                moved,
                unresolved
        );
    }

    /**
     * Restores the dedicated build staging slot to empty after normalization.
     * The displaced temporary block is swapped into the first empty main-inventory
     * slot. If the inventory is completely full, the staging stack is preserved.
     */
    private static boolean clearBuildStagingSlot(
            Minecraft minecraft,
            Inventory inventory,
            AbstractContainerMenu menu
    ) {

        if (inventory.getItem(BUILD_STAGING_SLOT).isEmpty()) {
            return false;
        }

        int emptyInventorySlot = -1;

        for (
                int slot = HOTBAR_SIZE;
                slot < inventory.getContainerSize();
                slot++
        ) {

            if (!inventory.getItem(slot).isEmpty()) {
                continue;
            }

            emptyInventorySlot = slot;
            break;
        }

        if (emptyInventorySlot < 0) {
            DAI_Core.debug(
                    "<DAI>: Build staging slot remains occupied because the main inventory is full."
            );
            return false;
        }

        minecraft.gameMode.handleContainerInput(
                menu.containerId,
                inventorySlotToMenuSlot(emptyInventorySlot),
                BUILD_STAGING_SLOT,
                ContainerInput.SWAP,
                minecraft.player
        );

        DAI_Core.debug(
                "<DAI>: Restored build staging hotbar slot 5 to empty."
        );

        return inventory.getItem(BUILD_STAGING_SLOT).isEmpty();
    }

    private static int findBestSource(
            Inventory inventory,
            Identifier[] candidates
    ) {

        for (Identifier candidate : candidates) {

            for (
                    int slot = 0;
                    slot < inventory.getContainerSize();
                    slot++
            ) {

                ItemStack stack =
                        inventory.getItem(
                                slot
                        );

                if (
                        stack.isEmpty()
                                || !candidate.equals(
                                BuiltInRegistries.ITEM.getKey(
                                        stack.getItem()
                                )
                        )
                ) {
                    continue;
                }

                return slot;
            }
        }

        return -1;
    }

    private static int inventorySlotToMenuSlot(
            int inventorySlot
    ) {

        if (
                inventorySlot >= 0
                        && inventorySlot < HOTBAR_SIZE
        ) {
            return 36 + inventorySlot;
        }

        return inventorySlot;
    }

    private static Identifier[] ids(
            String... values
    ) {

        Identifier[] result =
                new Identifier[values.length];

        for (
                int index = 0;
                index < values.length;
                index++
        ) {

            Identifier identifier =
                    Identifier.tryParse(
                            values[index]
                    );

            if (identifier == null) {
                throw new IllegalArgumentException(
                        "Invalid built-in item identifier: "
                                + values[index]
                );
            }

            result[index] =
                    identifier;
        }

        return result;
    }

    private record SlotRule(
            int hotbarSlot,
            Identifier[] candidates
    ) {
    }
}
