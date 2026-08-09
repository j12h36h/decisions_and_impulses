package io.github.j12h36h.dai.logics.controller;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;

public final class DAI_ItemController {

    private static boolean dropPending;
    private static boolean swapPending;

    private DAI_ItemController() {
        // Utility class.
    }

    public static void requestDrop() {

        dropPending = true;

        DAI_Core.LOGGER.debug(
                "<DAI>: Item drop requested."
        );
    }

    public static void requestSwap() {

        swapPending = true;

        DAI_Core.LOGGER.debug(
                "<DAI>: Item swap requested."
        );
    }

    public static void tick() {

        tickDrop();
        tickSwap();
    }

    public static void reset() {

        dropPending = false;
        swapPending = false;
    }

    private static void tickDrop() {

        if (!dropPending) {
            return;
        }

        dropPending = false;

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }

        minecraft.player.drop(
                false
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Dropped held item."
        );
    }

    private static void tickSwap() {

        if (!swapPending) {
            return;
        }

        swapPending = false;

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.getConnection() == null
        ) {
            return;
        }

        minecraft.getConnection().send(
                new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        BlockPos.ZERO,
                        Direction.DOWN
                )
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Swapped main-hand and offhand items."
        );
    }
}