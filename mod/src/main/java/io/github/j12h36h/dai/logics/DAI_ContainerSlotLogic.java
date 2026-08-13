package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.menus.DAI_ScreenState;
import io.github.j12h36h.dai.menus.DAI_ScreenProfileManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class DAI_ContainerSlotLogic {

    private static final int DEFAULT_WAIT_TICKS =
            200;

    private static final int BARRIER_POLL_TICKS =
            1;

    private DAI_ContainerSlotLogic() {
        // Utility class.
    }

    /*
     * ------------------------------------------------------------
     * SLOT READING
     * ------------------------------------------------------------
     */

    public static ItemStack stackInSlot(
            int slotIndex
    ) {

        AbstractContainerMenu menu =
                DAI_ScreenState.menu();

        if (
                menu == null
                        || slotIndex < 0
                        || slotIndex >= menu.slots.size()
        ) {
            return ItemStack.EMPTY;
        }

        return menu.getSlot(
                slotIndex
        ).getItem();
    }

    public static boolean slotIsEmpty(
            int slotIndex
    ) {

        return stackInSlot(
                slotIndex
        ).isEmpty();
    }

    public static boolean slotHasItem(
            int slotIndex,
            String itemId
    ) {

        Identifier requested =
                parseIdentifier(
                        itemId
                );

        if (requested == null) {
            return false;
        }

        ItemStack stack =
                stackInSlot(
                        slotIndex
                );

        if (stack.isEmpty()) {
            return false;
        }

        Identifier actual =
                itemId(
                        stack
                );

        return requested.equals(
                actual
        );
    }

    /*
     * ------------------------------------------------------------
     * DIRECT SLOT CLICKS
     * ------------------------------------------------------------
     */

    public static void clickSlot(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        AbstractContainerMenu menu =
                requireOpenMenu(
                        minecraft
                );

        if (
                action == null
                        || menu == null
        ) {
            return;
        }

        Integer slot =
                resolveSlot(
                        action
                );

        if (slot == null) {
            return;
        }

        minecraft.gameMode.handleContainerInput(
                menu.containerId,
                slot,
                0,
                ContainerInput.PICKUP,
                minecraft.player
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.debug(
                "<DAI>: Clicked container slot {} in containerId={}.",
                slot,
                menu.containerId
        );
    }

    public static void shiftClickSlot(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        AbstractContainerMenu menu =
                requireOpenMenu(
                        minecraft
                );

        if (
                action == null
                        || menu == null
        ) {
            return;
        }

        Integer slot =
                resolveSlot(
                        action
                );

        if (slot == null) {
            return;
        }

        minecraft.gameMode.handleContainerInput(
                menu.containerId,
                slot,
                0,
                ContainerInput.QUICK_MOVE,
                minecraft.player
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.debug(
                "<DAI>: Shift-clicked container slot {} in containerId={}.",
                slot,
                menu.containerId
        );
    }

    /*
     * ------------------------------------------------------------
     * TAKE
     * ------------------------------------------------------------
     */

    public static void takeSlot(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        AbstractContainerMenu menu =
                requireOpenMenu(
                        minecraft
                );

        if (
                action == null
                        || menu == null
        ) {
            return;
        }

        Integer slot =
                resolveSlot(
                        action
                );

        if (slot == null) {
            return;
        }

        ItemStack stack =
                menu.getSlot(
                        slot
                ).getItem();

        if (stack.isEmpty()) {

            fail(
                    "Cannot take container slot "
                            + slot
                            + " because it is empty."
            );

            return;
        }

        minecraft.gameMode.handleContainerInput(
                menu.containerId,
                slot,
                0,
                ContainerInput.QUICK_MOVE,
                minecraft.player
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.debug(
                "<DAI>: Took stack {} from container slot {} in containerId={}.",
                stack,
                slot,
                menu.containerId
        );
    }

    /*
     * ------------------------------------------------------------
     * INSERT ITEM
     * ------------------------------------------------------------
     */

    /**
     * Supported forms:
     *
     * Numeric:
     *
     * {
     *   "type": "container_insert_item",
     *   "action": "minecraft:raw_iron",
     *   "slot": 0
     * }
     *
     * Profile-based:
     *
     * {
     *   "type": "container_insert_item",
     *   "action": "minecraft:raw_iron",
     *   "open": "decisions_and_impulses:minecraft/furnace",
     *   "direction": "input"
     * }
     */
    public static void insertItem(
            DAI_ActionDefinition action
    ) {

        if (action == null) {

            fail(
                    "container_insert_item requires an action."
            );

            return;
        }

        Identifier requestedItem =
                parseIdentifier(
                        action.action()
                );

        if (requestedItem == null) {

            fail(
                    "container_insert_item requires a valid item id."
            );

            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        AbstractContainerMenu menu =
                requireOpenMenu(
                        minecraft
                );

        if (menu == null) {
            return;
        }

        Integer destinationSlot =
                resolveSlot(
                        action
                );

        if (destinationSlot == null) {
            return;
        }

        int sourceSlot =
                findPlayerInventorySlot(
                        minecraft,
                        menu,
                        requestedItem
                );

        if (sourceSlot < 0) {

            fail(
                    "No inventory stack matching '"
                            + requestedItem
                            + "' was found."
            );

            return;
        }

        /*
         * Pick the inventory stack up.
         */
        minecraft.gameMode.handleContainerInput(
                menu.containerId,
                sourceSlot,
                0,
                ContainerInput.PICKUP,
                minecraft.player
        );

        /*
         * Place it into the destination slot.
         */
        minecraft.gameMode.handleContainerInput(
                menu.containerId,
                destinationSlot,
                0,
                ContainerInput.PICKUP,
                minecraft.player
        );

        /*
         * Return any remainder to the original inventory slot.
         */
        if (
                !menu.getCarried()
                        .isEmpty()
        ) {

            minecraft.gameMode.handleContainerInput(
                    menu.containerId,
                    sourceSlot,
                    0,
                    ContainerInput.PICKUP,
                    minecraft.player
            );
        }

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.debug(
                "<DAI>: Inserted '{}' from menu slot {} into container slot {} in containerId={}.",
                requestedItem,
                sourceSlot,
                destinationSlot,
                menu.containerId
        );
    }

    /*
     * ------------------------------------------------------------
     * WAIT FOR SLOT
     * ------------------------------------------------------------
     */

    public static void waitForSlot(
            DAI_ActionDefinition action
    ) {

        if (action == null) {

            releaseWaitBarrier();

            fail(
                    "wait_for_container_slot requires an action."
            );

            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        AbstractContainerMenu menu =
                requireOpenMenu(
                        minecraft
                );

        if (menu == null) {

            releaseWaitBarrier();

            return;
        }

        Integer slot =
                resolveSlot(
                        action
                );

        if (slot == null) {

            releaseWaitBarrier();

            return;
        }

        Identifier requested =
                parseIdentifier(
                        action.action()
                );

        if (requested == null) {

            releaseWaitBarrier();

            fail(
                    "wait_for_container_slot requires a valid expected item id."
            );

            return;
        }

        ItemStack stack =
                menu.getSlot(
                        slot
                ).getItem();

        if (!stack.isEmpty()) {

            Identifier actual =
                    itemId(
                            stack
                    );

            if (
                    requested.equals(
                            actual
                    )
            ) {

                releaseWaitBarrier();

                DAI_ActionStatus.set(
                        DAI_ActionResult.SUCCESS
                );

                DAI_Core.debug(
                        "<DAI>: Container slot {} now contains '{}' in containerId={}.",
                        slot,
                        requested,
                        menu.containerId
                );

                return;
            }
        }

        int remainingTicks =
                action.ticks() > 0
                        ? action.ticks()
                        : DEFAULT_WAIT_TICKS;

        remainingTicks--;

        if (remainingTicks <= 0) {

            releaseWaitBarrier();

            DAI_ActionStatus.set(
                    DAI_ActionResult.TIMED_OUT
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Timed out waiting for container slot {} to contain '{}'.",
                    slot,
                    requested
            );

            return;
        }

        DAI_ActionQueue.holdBarrier(
                createWaitAction(
                        action,
                        requested,
                        remainingTicks
                ),
                BARRIER_POLL_TICKS
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );
    }

    /*
     * ------------------------------------------------------------
     * SLOT RESOLUTION
     * ------------------------------------------------------------
     */

    /**
     * Resolves either:
     *
     * action.open      = profile id
     * action.direction = semantic slot name
     *
     * or:
     *
     * action.slot      = direct numeric slot
     */
    private static Integer resolveSlot(
            DAI_ActionDefinition action
    ) {

        if (action == null) {
            return null;
        }

        boolean hasProfile =
                action.open() != null
                        && !action.open().isBlank();

        boolean hasNamedSlot =
                action.direction() != null
                        && !action.direction().isBlank();

        /*
         * Semantic profile-based resolution takes precedence.
         */
        if (
                hasProfile
                        || hasNamedSlot
        ) {

            if (
                    !hasProfile
                            || !hasNamedSlot
            ) {

                fail(
                        "Profile-based container slot resolution requires both 'open' and 'direction'."
                );

                return null;
            }

            String profileId =
                    action.open()
                            .trim();

            String slotName =
                    action.direction()
                            .trim();

            if (
                    !DAI_ScreenProfileManager.contains(
                            profileId
                    )
            ) {

                fail(
                        "Unknown screen profile '"
                                + profileId
                                + "'."
                );

                return null;
            }

            Integer resolved =
                    DAI_ScreenProfileManager.resolveSlot(
                            profileId,
                            slotName
                    );

            if (resolved == null) {

                fail(
                        "Screen profile '"
                                + profileId
                                + "' could not resolve named slot '"
                                + slotName
                                + "' for the current screen."
                );

                return null;
            }

            DAI_Core.debug(
                    "<DAI>: Resolved screen profile '{}' slot '{}' -> {}.",
                    profileId,
                    slotName,
                    resolved
            );

            return resolved;
        }

        /*
         * Legacy/direct numeric slot.
         */
        int numericSlot =
                action.slot();

        AbstractContainerMenu menu =
                DAI_ScreenState.menu();

        if (
                menu == null
                        || numericSlot < 0
                        || numericSlot >= menu.slots.size()
        ) {

            fail(
                    "Container slot "
                            + numericSlot
                            + " does not exist."
            );

            return null;
        }

        return numericSlot;
    }

    /*
     * ------------------------------------------------------------
     * INVENTORY SEARCH
     * ------------------------------------------------------------
     */

    private static int findPlayerInventorySlot(
            Minecraft minecraft,
            AbstractContainerMenu menu,
            Identifier requestedItem
    ) {

        if (
                minecraft.player == null
                        || requestedItem == null
        ) {
            return -1;
        }

        Container playerInventory =
                minecraft.player
                        .getInventory();

        for (
                int index = 0;
                index < menu.slots.size();
                index++
        ) {

            Slot slot =
                    menu.getSlot(
                            index
                    );

            if (
                    slot.container
                            != playerInventory
            ) {
                continue;
            }

            ItemStack stack =
                    slot.getItem();

            if (stack.isEmpty()) {
                continue;
            }

            Identifier actual =
                    itemId(
                            stack
                    );

            if (
                    requestedItem.equals(
                            actual
                    )
            ) {

                return index;
            }
        }

        return -1;
    }

    /*
     * ------------------------------------------------------------
     * MENU VALIDATION
     * ------------------------------------------------------------
     */

    private static AbstractContainerMenu requireOpenMenu(
            Minecraft minecraft
    ) {

        if (
                minecraft.player == null
                        || minecraft.gameMode == null
        ) {

            fail(
                    "Container interaction requires an active player and game mode."
            );

            return null;
        }

        if (
                !DAI_ScreenState
                        .hasOpenContainerScreen()
        ) {

            fail(
                    "Container interaction requires an open screen."
            );

            return null;
        }

        AbstractContainerMenu menu =
                DAI_ScreenState.menu();

        if (menu == null) {

            fail(
                    "No active container menu is available."
            );

            return null;
        }

        return menu;
    }

    /*
     * ------------------------------------------------------------
     * WAIT BARRIER
     * ------------------------------------------------------------
     */

    private static DAI_ActionDefinition createWaitAction(
            DAI_ActionDefinition source,
            Identifier requested,
            int remainingTicks
    ) {

        return new DAI_ActionDefinition(
                "wait_for_container_slot",
                requested.toString(),
                List.of(),
                List.of(),
                "",
                source.open(),
                0.0F,
                0.0F,
                source.direction(),
                Math.max(
                        0,
                        remainingTicks
                ),
                source.slot(),
                false,
                0.0D
        );
    }

    private static void releaseWaitBarrier() {

        if (
                DAI_ActionQueue.barrierIs(
                        "wait_for_container_slot"
                )
        ) {

            DAI_ActionQueue.releaseBarrier();
        }
    }

    /*
     * ------------------------------------------------------------
     * IDENTIFIERS
     * ------------------------------------------------------------
     */

    private static Identifier itemId(
            ItemStack stack
    ) {

        if (
                stack == null
                        || stack.isEmpty()
        ) {
            return null;
        }

        return stack.getItem()
                .builtInRegistryHolder()
                .key()
                .identifier();
    }

    private static Identifier parseIdentifier(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {
            return null;
        }

        String normalized =
                value.trim();

        if (
                !normalized.contains(
                        ":"
                )
        ) {

            normalized =
                    "minecraft:"
                            + normalized;
        }

        return Identifier.tryParse(
                normalized
        );
    }

    /*
     * ------------------------------------------------------------
     * FAILURE
     * ------------------------------------------------------------
     */

    private static void fail(
            String reason
    ) {

        DAI_ActionStatus.set(
                DAI_ActionResult.FAILURE
        );

        DAI_Core.LOGGER.warn(
                "<DAI>: {}",
                reason
        );
    }
}