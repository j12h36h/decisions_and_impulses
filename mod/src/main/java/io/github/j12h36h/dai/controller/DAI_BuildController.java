package io.github.j12h36h.dai.controller;

import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

public final class DAI_BuildController {

    private static boolean placePending;

    private DAI_BuildController() {
        // Utility class.
    }

    public static void place() {

        placePending = true;

        DAI_Core.LOGGER.debug(
                "<DAI>: Block placement requested."
        );
    }

    public static void tick() {

        if (!placePending) {
            return;
        }

        placePending = false;

        tickPlace();
    }

    public static void reset() {

        placePending = false;
    }

    private static void tickPlace() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.gameMode == null
        ) {
            return;
        }

        if (
                !(minecraft.hitResult
                        instanceof BlockHitResult blockHitResult)
        ) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Block placement ignored because no block is targeted."
            );

            return;
        }

        ItemStack stack =
                minecraft.player.getMainHandItem();

        if (stack.isEmpty()) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Block placement ignored because the main hand is empty."
            );

            return;
        }

        InteractionResult result =
                minecraft.gameMode.useItemOn(
                        minecraft.player,
                        InteractionHand.MAIN_HAND,
                        blockHitResult
                );

        if (result.consumesAction()) {

            minecraft.player.swing(
                    InteractionHand.MAIN_HAND
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Placement interaction succeeded at {}.",
                    blockHitResult.getBlockPos()
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Placement interaction did not succeed at {}.",
                blockHitResult.getBlockPos()
        );
    }
}