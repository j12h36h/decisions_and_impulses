package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.input.DAI_BreakController;
import io.github.j12h36h.dai.input.DAI_BuildController;
import io.github.j12h36h.dai.input.DAI_TargetController;
import io.github.j12h36h.dai.ui.DAI_MenuCore;
import io.github.j12h36h.dai.ui.DAI_ScreenManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class DAI_ActionController {

    private static boolean attackPending;
    private static boolean usePending;

    private static boolean attackHeld;
    private static boolean useHeld;

    private static boolean dropPending;
    private static boolean swapPending;
    private static boolean interactPending;

    private static final int HELD_ATTACK_COOLDOWN = 10;
    private static int attackCooldown;

    private static final int HELD_USE_COOLDOWN = 4;
    private static int useCooldown;

    private DAI_ActionController() {
        // Utility class.
    }

    public static void requestAttack() {

        attackPending = true;

        DAI_Core.LOGGER.debug(
                "<DAI>: Basic attack requested."
        );
    }

    public static void requestUse() {

        usePending = true;

        DAI_Core.LOGGER.debug(
                "<DAI>: Item use requested."
        );
    }

    public static void startAttack() {

        attackHeld = true;

        DAI_Core.LOGGER.debug(
                "<DAI>: Held attack started."
        );
    }

    public static void stopAttack() {

        attackHeld = false;

        DAI_Core.LOGGER.debug(
                "<DAI>: Held attack stopped."
        );
    }

    public static void startUse() {

        useHeld = true;

        DAI_Core.LOGGER.debug(
                "<DAI>: Held item use started."
        );
    }

    public static void stopUse() {

        useHeld = false;

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

        DAI_Core.LOGGER.debug(
                "<DAI>: Held item use released."
        );
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

    public static void requestInteract() {

        interactPending = true;

        DAI_Core.LOGGER.debug(
                "<DAI>: World interaction requested."
        );
    }

    public static void tick() {

        if (attackCooldown > 0) {
            attackCooldown--;
        }

        if (useCooldown > 0) {
            useCooldown--;
        }

        tickAttack();
        tickUse();
        tickDrop();
        tickSwap();
        tickInteract();

        DAI_BreakController.tick();
        DAI_BuildController.tick();
    }

    public static void reset() {

        attackPending = false;
        usePending = false;

        attackHeld = false;
        useHeld = false;

        dropPending = false;
        swapPending = false;
        interactPending = false;

        attackCooldown = 0;
        useCooldown = 0;

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player != null
                        && minecraft.player.isUsingItem()
        ) {
            minecraft.player.stopUsingItem();
        }

        DAI_BreakController.reset();
        DAI_BuildController.reset();
    }

    private static void tickAttack() {

        if (attackHeld && attackCooldown > 0) {
            return;
        }

        if (!attackPending && !attackHeld) {
            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.gameMode == null
        ) {
            return;
        }

        if (attackHeld) {

            float strength =
                    minecraft.player.getAttackStrengthScale(
                            0.0F
                    );

            if (strength < 0.95F) {
                return;
            }
        }

        attackPending = false;

        HitResult hitResult =
                minecraft.hitResult;

        if (hitResult instanceof EntityHitResult entityHitResult) {

            minecraft.gameMode.attack(
                    minecraft.player,
                    entityHitResult.getEntity()
            );
        }

        minecraft.player.swing(
                InteractionHand.MAIN_HAND
        );

        attackCooldown = HELD_ATTACK_COOLDOWN;

        DAI_Core.LOGGER.debug(
                "<DAI>: Performed held/basic attack; cooldown={} tick(s).",
                attackHeld
                        ? HELD_ATTACK_COOLDOWN
                        : 0
        );
    }

    private static void tickUse() {

        if (useHeld && useCooldown > 0) {
            return;
        }

        if (!usePending && !useHeld) {
            return;
        }

        usePending = false;

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.gameMode == null
        ) {
            return;
        }

        if (
                useHeld
                        && minecraft.player.isUsingItem()
        ) {
            return;
        }

        minecraft.gameMode.useItem(
                minecraft.player,
                InteractionHand.MAIN_HAND
        );

        useCooldown = HELD_USE_COOLDOWN;

        usePending = false;

        if (
                minecraft.player == null
                        || minecraft.gameMode == null
        ) {
            return;
        }

        minecraft.gameMode.useItem(
                minecraft.player,
                InteractionHand.MAIN_HAND
        );

        minecraft.player.swing(
                InteractionHand.MAIN_HAND
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Used held item."
        );
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

    private static void tickInteract() {

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

            if (!result.consumesAction()) {

                result =
                        minecraft.gameMode.interact(
                                minecraft.player,
                                entity,
                                entityHitResult,
                                InteractionHand.MAIN_HAND
                        );
            }

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