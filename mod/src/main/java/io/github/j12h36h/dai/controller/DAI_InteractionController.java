package io.github.j12h36h.dai.controller;

import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.ui.DAI_MenuCore;
import io.github.j12h36h.dai.ui.DAI_ScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class DAI_InteractionController {

    private static boolean interactPending;

    private DAI_InteractionController() {
        // Utility class.
    }

    public static void requestInteract() {

        interactPending = true;

        DAI_Core.LOGGER.debug(
                "<DAI>: World interaction requested."
        );
    }

    public static void tick() {

        if (!interactPending) {
            return;
        }

        interactPending = false;

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || minecraft.gameMode == null
        ) {
            return;
        }

        HitResult hitResult =
                minecraft.hitResult;

        if (hitResult instanceof BlockHitResult blockHitResult) {

            pushCurrentMenuIfNeeded(
                    minecraft,
                    minecraft.level
                            .getBlockState(
                                    blockHitResult.getBlockPos()
                            )
                            .getMenuProvider(
                                    minecraft.level,
                                    blockHitResult.getBlockPos()
                            )
            );

            InteractionResult result =
                    minecraft.gameMode.useItemOn(
                            minecraft.player,
                            InteractionHand.MAIN_HAND,
                            blockHitResult
                    );

            handleInteractionResult(
                    minecraft,
                    result,
                    "block at "
                            + blockHitResult.getBlockPos()
            );

            return;
        }

        if (hitResult instanceof EntityHitResult entityHitResult) {

            Entity entity =
                    entityHitResult.getEntity();

            pushCurrentMenuIfNeeded(
                    minecraft,
                    entity instanceof MenuProvider menuProvider
                            ? menuProvider
                            : null
            );

            InteractionResult result =
                    minecraft.gameMode.interact(
                            minecraft.player,
                            entity,
                            entityHitResult,
                            InteractionHand.MAIN_HAND
                    );

            handleInteractionResult(
                    minecraft,
                    result,
                    "entity '"
                            + entity.getName().getString()
                            + "'"
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: World interaction ignored because nothing is targeted."
        );
    }

    public static void reset() {

        interactPending = false;
    }

    private static void pushCurrentMenuIfNeeded(
            Minecraft minecraft,
            MenuProvider menuProvider
    ) {

        if (
                menuProvider != null
                        && minecraft.gui.screen()
                        instanceof DAI_MenuCore daiMenu
        ) {

            DAI_ScreenManager.push(
                    daiMenu
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Stored DAI menu before opening an interaction screen."
            );
        }
    }

    private static void handleInteractionResult(
            Minecraft minecraft,
            InteractionResult result,
            String target
    ) {

        if (result.consumesAction()) {

            minecraft.player.swing(
                    InteractionHand.MAIN_HAND
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Interacted with {}.",
                    target
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Interaction with {} did not succeed.",
                target
        );
    }
}