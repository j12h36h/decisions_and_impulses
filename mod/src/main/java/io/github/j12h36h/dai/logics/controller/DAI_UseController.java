package io.github.j12h36h.dai.logics.controller;

import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

public final class DAI_UseController {

    private static final int HELD_USE_COOLDOWN =
            4;

    private static boolean usePending;
    private static boolean useHeld;

    private static int useCooldown;

    private DAI_UseController() {
        // Utility class.
    }

    /**
     * Requests one use of the item currently held in the main hand.
     */
    public static void requestUse() {

        usePending =
                true;

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Item use requested."
        );
    }

    /**
     * Begins holding the main-hand use action.
     *
     * The operation remains RUNNING until stopUse(), failure, or reset.
     */
    public static void startUse() {

        useHeld =
                true;

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Held item use started."
        );
    }

    /**
     * Releases an active held-use operation.
     */
    public static void stopUse() {

        boolean wasActive =
                useHeld;

        useHeld =
                false;

        usePending =
                false;

        useCooldown =
                0;

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player != null
                        && minecraft.gameMode != null
                        && minecraft.player.isUsingItem()
        ) {

            minecraft.gameMode.releaseUsingItem(
                    minecraft.player
            );
        }

        if (wasActive) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.SUCCESS
            );
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Held item use released."
        );
    }

    public static void tick() {

        if (useCooldown > 0) {
            useCooldown--;
        }

        if (
                useHeld
                        && useCooldown > 0
        ) {
            return;
        }

        if (
                !usePending
                        && !useHeld
        ) {
            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.gameMode == null
        ) {

            fail(
                    "player or game mode is unavailable"
            );

            return;
        }

        /*
         * A held-use operation has already been accepted and is still
         * being processed by Minecraft.
         */
        if (
                useHeld
                        && minecraft.player.isUsingItem()
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.RUNNING
            );

            return;
        }

        boolean heldOperation =
                useHeld;

        usePending =
                false;

        InteractionResult result =
                minecraft.gameMode.useItem(
                        minecraft.player,
                        InteractionHand.MAIN_HAND
                );

        if (
                result == null
                        || !result.consumesAction()
        ) {

            if (heldOperation) {
                useHeld = false;
            }

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Main-hand item use was not accepted by Minecraft."
            );

            return;
        }

        minecraft.player.swing(
                InteractionHand.MAIN_HAND
        );

        useCooldown =
                HELD_USE_COOLDOWN;

        DAI_ActionStatus.set(
                heldOperation
                        ? DAI_ActionResult.RUNNING
                        : DAI_ActionResult.SUCCESS
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Used held item; heldOperation={}, result={}.",
                heldOperation,
                result
        );
    }

    public static void reset() {

        boolean wasActive =
                usePending
                        || useHeld
                        || useCooldown > 0;

        usePending =
                false;

        useHeld =
                false;

        useCooldown =
                0;

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player != null
                        && minecraft.player.isUsingItem()
        ) {

            if (minecraft.gameMode != null) {

                minecraft.gameMode.releaseUsingItem(
                        minecraft.player
                );

            } else {

                minecraft.player.stopUsingItem();
            }
        }

        if (wasActive) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.CANCELLED
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Active item-use operation cancelled during reset."
            );
        }
    }

    public static boolean isActive() {

        return usePending
                || useHeld
                || useCooldown > 0;
    }

    public static boolean isHeld() {
        return useHeld;
    }

    private static void fail(
            String reason
    ) {

        usePending =
                false;

        useHeld =
                false;

        useCooldown =
                0;

        DAI_ActionStatus.set(
                DAI_ActionResult.FAILURE
        );

        DAI_Core.LOGGER.warn(
                "<DAI>: Item use failed because {}.",
                reason
        );
    }
}