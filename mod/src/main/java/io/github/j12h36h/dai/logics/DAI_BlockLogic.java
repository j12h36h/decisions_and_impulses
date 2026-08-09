package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.controller.DAI_ApproachController;
import io.github.j12h36h.dai.logics.controller.DAI_BuildController;
import io.github.j12h36h.dai.menus.system.DAI_TargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public final class DAI_BlockLogic {

    private static final int DEFAULT_PLACEMENT_RADIUS =
            8;

    private static final int MAXIMUM_PLACEMENT_RADIUS =
            32;

    private static final int DEFAULT_APPROACH_TIMEOUT =
            200;

    private static final int DEFAULT_ALIGNMENT_RETRIES =
            20;

    private static final double DEFAULT_STOP_DISTANCE =
            3.25D;

    private static final int HOTBAR_SWITCH_DELAY =
            2;

    private DAI_BlockLogic() {
        // Utility class.
    }

    /**
     * Performs the basic place action against the block face currently
     * under the player's crosshair.
     */
    public static void place(
            DAI_ActionDefinition action
    ) {

        DAI_BuildController.place();
    }

    /**
     * Selects a supplied block item and places it against the block face
     * currently under the player's crosshair.
     */
    public static void placeTargetedBlock(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || minecraft.gameMode == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot place a block without an active player, level, and game mode."
            );

            return;
        }

        Identifier blockItemId =
                validateBlockItem(
                        action,
                        "place_targeted_block"
                );

        if (blockItemId == null) {
            return;
        }

        if (
                !(
                        minecraft.hitResult
                                instanceof BlockHitResult
                )
        ) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Cannot place '{}' because no block face is targeted.",
                    blockItemId
            );

            return;
        }

        if (
                !DAI_HotbarLogic.selectItem(
                        blockItemId
                )
        ) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Cannot place '{}' because it is not available in the player inventory.",
                    blockItemId
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Preparing to place block item '{}'.",
                blockItemId
        );

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        createAction(
                                "delay",
                                "",
                                HOTBAR_SWITCH_DELAY,
                                0.0D
                        ),
                        createAction(
                                "interact",
                                "",
                                0,
                                0.0D
                        )
                )
        );
    }

    /**
     * Finds the nearest solid support block with an adjacent replaceable
     * position, approaches it, aligns with it, and places the requested
     * block item.
     */
    public static void placeNearestBlock(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot place a nearby block without an active player and level."
            );

            return;
        }

        Identifier blockItemId =
                validateBlockItem(
                        action,
                        "place_nearest_block"
                );

        if (blockItemId == null) {
            return;
        }

        int searchRadius =
                action.value() > 0.0D
                        ? Mth.clamp(
                        (int) Math.round(
                                action.value()
                        ),
                        1,
                        MAXIMUM_PLACEMENT_RADIUS
                )
                        : DEFAULT_PLACEMENT_RADIUS;

        int timeoutTicks =
                action.ticks() > 0
                        ? action.ticks()
                        : DEFAULT_APPROACH_TIMEOUT;

        BlockPos support =
                findNearestPlacementSupport(
                        minecraft,
                        searchRadius
                );

        if (support == null) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: No valid placement support was found within {} block(s).",
                    searchRadius
            );

            return;
        }

        DAI_TargetState.selectBlock(
                support
        );

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        createAction(
                                "approach_target_block",
                                "",
                                timeoutTicks,
                                DEFAULT_STOP_DISTANCE
                        ),
                        createAction(
                                "wait_for_approach",
                                "",
                                timeoutTicks,
                                0.0D
                        ),
                        createAction(
                                "wait_for_target_block",
                                "",
                                DEFAULT_ALIGNMENT_RETRIES,
                                0.0D
                        ),
                        createAction(
                                "place_targeted_block",
                                blockItemId.toString(),
                                0,
                                0.0D
                        )
                )
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Queued placement of '{}' using support block {} within radius {}.",
                blockItemId,
                support,
                searchRadius
        );
    }

    /**
     * Harvests the currently selected block when it is a mature supported
     * crop.
     */
    public static void harvestCrop(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot harvest a crop without an active player and level."
            );

            return;
        }

        BlockPos selected =
                DAI_TargetState.selectedBlock();

        if (selected == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: harvest_crop requires a selected block target."
            );

            return;
        }

        BlockState state =
                minecraft.level.getBlockState(
                        selected
                );

        if (!isHarvestableCrop(state)) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Selected block '{}' at {} is not a mature supported crop.",
                    state.getBlock()
                            .builtInRegistryHolder()
                            .key()
                            .identifier(),
                    selected
            );

            return;
        }

        DAI_ApproachController.faceSelectedBlock();

        int alignmentRetries =
                action.ticks() > 0
                        ? action.ticks()
                        : DEFAULT_ALIGNMENT_RETRIES;

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        createAction(
                                "wait_for_target_block",
                                "",
                                alignmentRetries,
                                0.0D
                        ),
                        createAction(
                                "break_once",
                                "",
                                0,
                                0.0D
                        )
                )
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Queued harvest for mature crop '{}' at {}.",
                state.getBlock()
                        .builtInRegistryHolder()
                        .key()
                        .identifier(),
                selected
        );
    }

    private static Identifier validateBlockItem(
            DAI_ActionDefinition action,
            String actionType
    ) {

        if (!action.hasAction()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: {} requires a block item id in 'action'.",
                    actionType
            );

            return null;
        }

        Identifier blockItemId =
                parseItemId(
                        action.action()
                );

        if (blockItemId == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Invalid block item id '{}'.",
                    action.action()
            );

            return null;
        }

        Item item =
                BuiltInRegistries.ITEM.getValue(
                        blockItemId
                );

        if (
                item == null
                        || item == Items.AIR
                        || !(item instanceof BlockItem)
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Item '{}' is not a placeable block item.",
                    blockItemId
            );

            return null;
        }

        return blockItemId;
    }

    private static BlockPos findNearestPlacementSupport(
            Minecraft minecraft,
            int radius
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {
            return null;
        }

        BlockPos playerPosition =
                minecraft.player.blockPosition();

        BlockPos nearest =
                null;

        double nearestDistanceSquared =
                Double.MAX_VALUE;

        BlockPos minimum =
                playerPosition.offset(
                        -radius,
                        -radius,
                        -radius
                );

        BlockPos maximum =
                playerPosition.offset(
                        radius,
                        radius,
                        radius
                );

        for (
                BlockPos candidate
                : BlockPos.betweenClosed(
                minimum,
                maximum
        )
        ) {

            BlockState state =
                    minecraft.level.getBlockState(
                            candidate
                    );

            if (state.isAir()) {
                continue;
            }

            if (
                    !hasPlaceableAdjacentSpace(
                            minecraft,
                            candidate
                    )
            ) {
                continue;
            }

            double distanceSquared =
                    candidate.distSqr(
                            playerPosition
                    );

            if (
                    distanceSquared
                            >= nearestDistanceSquared
            ) {
                continue;
            }

            nearestDistanceSquared =
                    distanceSquared;

            nearest =
                    candidate.immutable();
        }

        return nearest;
    }

    private static boolean hasPlaceableAdjacentSpace(
            Minecraft minecraft,
            BlockPos support
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {
            return false;
        }

        for (
                Direction direction
                : Direction.values()
        ) {

            BlockPos adjacent =
                    support.relative(
                            direction
                    );

            BlockState adjacentState =
                    minecraft.level.getBlockState(
                            adjacent
                    );

            if (!adjacentState.canBeReplaced()) {
                continue;
            }

            /*
             * Never select a placement position occupied by the player.
             */
            if (
                    minecraft.player
                            .getBoundingBox()
                            .intersects(
                                    new net.minecraft.world.phys.AABB(
                                            adjacent
                                    )
                            )
            ) {
                continue;
            }

            /*
             * Do not place into fluid.
             */
            if (
                    !adjacentState
                            .getFluidState()
                            .isEmpty()
            ) {
                continue;
            }

            return true;
        }

        return false;
    }

    private static boolean isHarvestableCrop(
            BlockState state
    ) {

        if (
                state.getBlock()
                        instanceof CropBlock cropBlock
        ) {

            return cropBlock.isMaxAge(
                    state
            );
        }

        if (
                state.getBlock()
                        instanceof NetherWartBlock
        ) {

            return state.getValue(
                    NetherWartBlock.AGE
            ) >= 3;
        }

        if (
                state.getBlock()
                        instanceof CocoaBlock
        ) {

            return state.getValue(
                    CocoaBlock.AGE
            ) >= 2;
        }

        return state.is(
                Blocks.PUMPKIN
        ) || state.is(
                Blocks.MELON
        );
    }

    private static Identifier parseItemId(
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

        if (!normalized.contains(":")) {

            normalized =
                    "minecraft:"
                            + normalized;
        }

        return Identifier.tryParse(
                normalized
        );
    }

    private static DAI_ActionDefinition createAction(
            String type,
            String action,
            int ticks,
            double value
    ) {

        return new DAI_ActionDefinition(
                type,
                action,
                List.of(),
                List.of(),
                "",
                "",
                0.0F,
                0.0F,
                "",
                ticks,
                0,
                false,
                value
        );
    }
}