package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.menus.system.DAI_TargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Deterministic block placement into the currently selected world position.
 *
 * The existing place_targeted_block action is intentionally untouched: it
 * still places against the player's actual crosshair. This action exists for
 * datapack blueprints where the destination coordinate itself is authoritative.
 */
public final class DAI_ExactPlacementLogic {

    private static final int DEFAULT_APPROACH_TIMEOUT =
            160;

    private static final double DEFAULT_STOP_DISTANCE =
            3.75D;

    private static final double MAX_USE_DISTANCE =
            5.25D;

    private static PlacementRequest pending;

    private DAI_ExactPlacementLogic() {
        // Utility class.
    }

    /**
     * Places action.action block item into DAI_TargetState.selectedBlock().
     *
     * The destination must be replaceable and must have at least one adjacent
     * non-replaceable support block. The blueprint should therefore order
     * construction so each new node has support from terrain or an earlier
     * completed node.
     */
    public static void placeAtSelectedPosition(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || minecraft.gameMode == null
                        || action == null
                        || !action.hasAction()
        ) {

            fail(
                    "place_block_at_selected_position requires player/world/game mode and a block item id in 'action'."
            );

            return;
        }

        BlockPos destination =
                DAI_TargetState.selectedBlock();

        if (destination == null) {

            fail(
                    "place_block_at_selected_position requires a selected destination block."
            );

            return;
        }

        Identifier itemId =
                parseItemId(
                        action.action()
                );

        if (itemId == null) {

            fail(
                    "Invalid exact-placement item id '{}'.",
                    action.action()
            );

            return;
        }

        Item item =
                BuiltInRegistries.ITEM.getValue(
                        itemId
                );

        if (
                item == null
                        || item == Items.AIR
                        || !(item instanceof BlockItem blockItem)
        ) {

            fail(
                    "Exact-placement item '{}' is not a placeable block item.",
                    itemId
            );

            return;
        }

        BlockState destinationState =
                minecraft.level.getBlockState(
                        destination
                );

        if (
                destinationState.is(
                        blockItem.getBlock()
                )
        ) {

            success();

            DAI_Core.LOGGER.debug(
                    "<DAI>: Exact placement skipped because {} already contains '{}'.",
                    destination,
                    itemId
            );

            return;
        }

        if (
                !destinationState.canBeReplaced()
                        || !destinationState
                        .getFluidState()
                        .isEmpty()
        ) {

            fail(
                    "Exact-placement destination {} is not safely replaceable.",
                    destination
            );

            return;
        }

        if (
                minecraft.player
                        .getBoundingBox()
                        .intersects(
                                new net.minecraft.world.phys.AABB(
                                        destination
                                )
                        )
        ) {

            fail(
                    "Exact-placement destination {} is occupied by the player.",
                    destination
            );

            return;
        }

        DAI_ExactPlacementSupport.Support support =
                DAI_ExactPlacementSupport.findBest(
                        minecraft,
                        destination
                );

        if (support == null) {

            fail(
                    "No adjacent support exists for exact-placement destination {}.",
                    destination
            );

            return;
        }

        if (
                !DAI_HotbarLogic.selectItem(
                        itemId
                )
        ) {

            fail(
                    "Cannot exact-place '{}' because it is not available in inventory.",
                    itemId
            );

            return;
        }

        pending =
                new PlacementRequest(
                        destination.immutable(),
                        support.position(),
                        support.face(),
                        itemId
                );

        DAI_TargetState.selectBlock(
                support.position()
        );

        int timeout =
                action.ticks() > 0
                        ? action.ticks()
                        : DEFAULT_APPROACH_TIMEOUT;

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        createAction(
                                "approach_target_block",
                                "",
                                timeout,
                                DEFAULT_STOP_DISTANCE
                        ),
                        createAction(
                                "wait_for_approach",
                                "",
                                timeout,
                                0.0D
                        ),
                        createAction(
                                "exact_place_finish",
                                "",
                                0,
                                0.0D
                        )
                )
        );

        success();

        DAI_Core.LOGGER.debug(
                "<DAI>: Queued exact placement of '{}' into {} using support {} face={}.",
                itemId,
                destination,
                support.position(),
                support.face()
        );
    }

    /** Internal continuation registered as an action for queue sequencing. */
    public static void finishPlacement(
            DAI_ActionDefinition action
    ) {

        PlacementRequest request =
                pending;

        pending = null;

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                request == null
                        || minecraft.player == null
                        || minecraft.level == null
                        || minecraft.gameMode == null
        ) {

            fail(
                    "Exact placement continuation lost its request or world context."
            );

            return;
        }

        try {

            BlockState current =
                    minecraft.level.getBlockState(
                            request.destination()
                    );

            Item item =
                    BuiltInRegistries.ITEM.getValue(
                            request.itemId()
                    );

            if (!(item instanceof BlockItem blockItem)) {

                fail(
                        "Exact-placement item '{}' is no longer a block item.",
                        request.itemId()
                );

                return;
            }

            if (
                    current.is(
                            blockItem.getBlock()
                    )
            ) {

                success();
                return;
            }

            if (
                    !current.canBeReplaced()
                            || !current
                            .getFluidState()
                            .isEmpty()
            ) {

                fail(
                        "Exact-placement destination {} changed and is no longer replaceable.",
                        request.destination()
                );

                return;
            }

            BlockState supportState =
                    minecraft.level.getBlockState(
                            request.support()
                    );

            if (
                    supportState.isAir()
                            || supportState.canBeReplaced()
            ) {

                fail(
                        "Exact-placement support {} disappeared before placement.",
                        request.support()
                );

                return;
            }

            Vec3 hitLocation =
                    Vec3.atCenterOf(
                            request.support()
                    )
                            .add(
                                    request.face().getStepX() * 0.5D,
                                    request.face().getStepY() * 0.5D,
                                    request.face().getStepZ() * 0.5D
                            );

            if (
                    minecraft.player
                            .getEyePosition()
                            .distanceTo(
                                    hitLocation
                            )
                            > MAX_USE_DISTANCE
            ) {

                fail(
                        "Exact-placement support {} is outside interaction range.",
                        request.support()
                );

                return;
            }

            if (
                    !DAI_HotbarLogic.selectItem(
                            request.itemId()
                    )
            ) {

                fail(
                        "Exact-placement item '{}' disappeared from inventory.",
                        request.itemId()
                );

                return;
            }

            BlockHitResult hitResult =
                    new BlockHitResult(
                            hitLocation,
                            request.face(),
                            request.support(),
                            false
                    );

            InteractionResult result =
                    minecraft.gameMode.useItemOn(
                            minecraft.player,
                            InteractionHand.MAIN_HAND,
                            hitResult
                    );

            if (!result.consumesAction()) {

                fail(
                        "Exact placement interaction did not succeed for destination {} using support {}.",
                        request.destination(),
                        request.support()
                );

                return;
            }

            minecraft.player.swing(
                    InteractionHand.MAIN_HAND
            );

            success();

            DAI_Core.LOGGER.info(
                    "<DAI>: Exact-placed '{}' into blueprint position {} using support {} face={}.",
                    request.itemId(),
                    request.destination(),
                    request.support(),
                    request.face()
            );

        } finally {

            /* Restore the authoritative blueprint node for immediate verify. */
            DAI_TargetState.selectBlock(
                    request.destination()
            );
        }
    }

    public static void reset() {
        pending = null;
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
                    "minecraft:" + normalized;
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

    private static void success() {

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );
    }

    private static void fail(
            String message,
            Object... arguments
    ) {

        DAI_ActionStatus.set(
                DAI_ActionResult.FAILURE
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: " + message,
                arguments
        );
    }

    private record PlacementRequest(
            BlockPos destination,
            BlockPos support,
            Direction face,
            Identifier itemId
    ) {
    }
}
