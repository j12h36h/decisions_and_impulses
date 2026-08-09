package io.github.j12h36h.dai.logics.controller;

import io.github.j12h36h.dai.logics.approach.DAI_ApproachProfile;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.menus.DAI_MenuCore;
import io.github.j12h36h.dai.menus.DAI_ScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Locale;

public final class DAI_InteractionController {

    /*
     * Non-living entities do not have an approach profile.
     *
     * Minecraft already performs its own interaction reach checks, but
     * keeping a conservative local confidence limit prevents DAI from
     * assuming that every rendered/intersected non-living entity should
     * be interacted with.
     */
    private static final double DEFAULT_ENTITY_INTERACTION_DISTANCE =
            3.0D;

    private static boolean interactPending;

    private DAI_InteractionController() {
        // Utility class.
    }

    public static void requestInteract() {

        interactPending =
                true;

        DAI_Core.LOGGER.debug(
                "<DAI>: World interaction requested."
        );
    }

    public static void tick() {

        if (!interactPending) {
            return;
        }

        interactPending =
                false;

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

        /*
         * Block interaction remains unchanged.
         *
         * Block reach and block approach are managed separately through
         * the block-oriented approach system.
         */
        if (
                hitResult
                        instanceof BlockHitResult blockHitResult
        ) {

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

        if (
                hitResult
                        instanceof EntityHitResult entityHitResult
        ) {

            Entity entity =
                    entityHitResult.getEntity();

            if (
                    entity == null
                            || entity.isRemoved()
            ) {

                DAI_Core.LOGGER.debug(
                        "<DAI>: Entity interaction ignored because the target is no longer valid."
                );

                return;
            }

            double distance =
                    minecraft.player.distanceTo(
                            entity
                    );

            double interactionDistance =
                    interactionDistanceFor(
                            entity
                    );

            /*
             * Recognition, pursuit, and interaction are separate
             * confidence levels.
             *
             * Even if Minecraft currently reports the entity under the
             * crosshair, DAI does not interact unless the target is also
             * inside the category-specific interaction distance.
             */
            if (
                    distance
                            > interactionDistance
            ) {

                DAI_Core.LOGGER.debug(
                        "<DAI>: Ignored interaction with entity '{}' because distance={} exceeds interactionDistance={}.",
                        entity.getName().getString(),
                        formatDistance(
                                distance
                        ),
                        formatDistance(
                                interactionDistance
                        )
                );

                return;
            }

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

        interactPending =
                false;
    }

    private static double interactionDistanceFor(
            Entity entity
    ) {

        if (
                entity
                        instanceof LivingEntity livingEntity
        ) {

            return DAI_ApproachProfile
                    .forEntity(
                            livingEntity
                    )
                    .interactionDistance();
        }

        return DEFAULT_ENTITY_INTERACTION_DISTANCE;
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

    private static String formatDistance(
            double distance
    ) {

        return String.format(
                Locale.ROOT,
                "%.2f",
                distance
        );
    }
}