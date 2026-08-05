package io.github.j12h36h.dai.ui;

import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
public final class DAI_HotbarController {

    private static final int HOTBAR_SIZE = 9;

    private static int selectedSlot;

    private DAI_HotbarController() {
        // Utility class.
    }

    public static void open() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {
            selectedSlot = 0;
            return;
        }

        selectedSlot =
                minecraft.player
                        .getInventory()
                        .getSelectedSlot();
    }

    public static void previous() {

        selectedSlot =
                Math.floorMod(
                        selectedSlot - 1,
                        HOTBAR_SIZE
                );
    }

    public static void next() {

        selectedSlot =
                Math.floorMod(
                        selectedSlot + 1,
                        HOTBAR_SIZE
                );
    }

    public static void selectPrevious() {

        syncSelectedSlotFromPlayer();
        previous();
        select();
    }

    public static void selectNext() {

        syncSelectedSlotFromPlayer();
        next();
        select();
    }

    public static void syncSelectedSlotFromPlayer() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {

            selectedSlot = 0;

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot synchronize hotbar slot because the player is null."
            );

            return;
        }

        selectedSlot =
                minecraft.player
                        .getInventory()
                        .getSelectedSlot();
    }

    public static void select() {

        selectSlotIndex(
                selectedSlot
        );
    }

    public static void select(
            int slot
    ) {

        if (
                slot < 1
                        || slot > HOTBAR_SIZE
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Invalid hotbar slot {}. Expected 1-{}.",
                    slot,
                    HOTBAR_SIZE
            );

            return;
        }

        selectedSlot =
                slot - 1;

        selectSlotIndex(
                selectedSlot
        );
    }

    public static boolean selectItem(
            Identifier itemId
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.gameMode == null
                        || itemId == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot select a hotbar item without a player, game mode, and item id."
            );

            return false;
        }

        Item requestedItem =
                BuiltInRegistries.ITEM.getValue(
                        itemId
                );

        if (
                requestedItem == null
                        || requestedItem == Items.AIR
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Unknown hotbar item '{}'.",
                    itemId
            );

            return false;
        }

        Inventory inventory =
                minecraft.player
                        .getInventory();

        /*
         * Prefer an existing stack already in the hotbar.
         */
        for (
                int slot = 0;
                slot < HOTBAR_SIZE;
                slot++
        ) {

            ItemStack stack =
                    inventory.getItem(
                            slot
                    );

            if (!stack.is(requestedItem)) {
                continue;
            }

            selectedSlot =
                    slot;

            inventory.setSelectedSlot(
                    slot
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Selected hotbar item '{}' in slot {}.",
                    itemId,
                    slot + 1
            );

            return true;
        }

        /*
         * Search the remaining player inventory.
         */
        int sourceSlot =
                -1;

        for (
                int slot = HOTBAR_SIZE;
                slot < inventory.getContainerSize();
                slot++
        ) {

            ItemStack stack =
                    inventory.getItem(
                            slot
                    );

            if (!stack.is(requestedItem)) {
                continue;
            }

            sourceSlot =
                    slot;

            break;
        }

        if (sourceSlot < 0) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Inventory does not contain item '{}'.",
                    itemId
            );

            return false;
        }

        /*
         * InventoryMenu slot mapping is only safe while the normal
         * player inventory container is active.
         */
        if (
                !(
                        minecraft.player.containerMenu
                                instanceof InventoryMenu
                )
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot move inventory item '{}' into the hotbar while another container is open.",
                    itemId
            );

            return false;
        }

        int destinationSlot =
                findEmptyHotbarSlot(
                        inventory
                );

        /*
         * If the hotbar is full, replace the currently selected slot.
         * ContainerInput.SWAP preserves the displaced stack by moving
         * it back into the source inventory slot.
         */
        if (destinationSlot < 0) {

            destinationSlot =
                    inventory.getSelectedSlot();
        }

        AbstractContainerMenu menu =
                minecraft.player
                        .containerMenu;

        int sourceMenuSlot =
                inventorySlotToMenuSlot(
                        sourceSlot
                );

        minecraft.gameMode.handleContainerInput(
                menu.containerId,
                sourceMenuSlot,
                destinationSlot,
                ContainerInput.SWAP,
                minecraft.player
        );

        selectedSlot =
                destinationSlot;

        inventory.setSelectedSlot(
                destinationSlot
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Moved inventory item '{}' from inventory slot {} to hotbar slot {} and selected it.",
                itemId,
                sourceSlot,
                destinationSlot + 1
        );

        return true;
    }

    private static void selectSlotIndex(
            int slotIndex
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot select hotbar slot because the player is null."
            );

            return;
        }

        if (
                slotIndex < 0
                        || slotIndex >= HOTBAR_SIZE
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Invalid zero-based hotbar slot index {}.",
                    slotIndex
            );

            return;
        }

        selectedSlot =
                slotIndex;

        minecraft.player
                .getInventory()
                .setSelectedSlot(
                        slotIndex
                );

        DAI_Core.LOGGER.debug(
                "<DAI>: Selected hotbar slot {}.",
                slotIndex + 1
        );
    }

    private static int findEmptyHotbarSlot(
            Inventory inventory
    ) {

        for (
                int slot = 0;
                slot < HOTBAR_SIZE;
                slot++
        ) {

            if (
                    inventory.getItem(
                            slot
                    ).isEmpty()
            ) {
                return slot;
            }
        }

        return -1;
    }

    private static int inventorySlotToMenuSlot(
            int inventorySlot
    ) {

        /*
         * InventoryMenu slot layout:
         *
         * 0      crafting result
         * 1-4    crafting grid
         * 5-8    armor
         * 9-35   main inventory
         * 36-44  hotbar
         * 45     offhand
         */
        if (
                inventorySlot >= 0
                        && inventorySlot < HOTBAR_SIZE
        ) {

            return 36
                    + inventorySlot;
        }

        return inventorySlot;
    }

    public static int selectedSlot() {
        return selectedSlot;
    }

    public static void reset() {
        selectedSlot = 0;
    }
}