package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.approach.DAI_ApproachTargeting;
import io.github.j12h36h.dai.logics.controller.DAI_CreativeFlightController;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.core.DAI_RuntimeTelemetry;
import io.github.j12h36h.dai.logics.input.DAI_InputState;
import io.github.j12h36h.dai.menus.system.DAI_TargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Deterministic physical placement into the currently selected world position.
 *
 * Creative mode uses the same real player interaction as survival, but owns its
 * own reach/reposition policy: floors prefer a support directly beneath the
 * destination and the player flies to a collision-safe hover stance when the
 * current position is unsuitable. No setblock/teleport command is used.
 */
public final class DAI_ExactPlacementLogic {

    private static final int DEFAULT_APPROACH_TIMEOUT =
            160;

    private static final int VERIFY_DELAY_TICKS =
            2;

    private static final double DEFAULT_STOP_DISTANCE =
            3.75D;

    /* Stay below the practical Creative interaction edge for packet safety. */
    private static final double MAX_USE_DISTANCE =
            4.75D;

    private static final double CREATIVE_FLIGHT_TOLERANCE =
            0.35D;

    private static final double CREATIVE_TOP_STANDOFF =
            2.75D;

    private static final double CREATIVE_SIDE_STANDOFF =
            3.10D;

    private static final float CREATIVE_LOOK_YAW_TOLERANCE =
            1.75F;

    private static final float CREATIVE_LOOK_PITCH_TOLERANCE =
            1.75F;

    private static final int CREATIVE_LOOK_MAX_TICKS =
            48;

    private static PlacementRequest pending;
    private static int creativeLookTicksRemaining;

    private DAI_ExactPlacementLogic() {
        // Utility class.
    }

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
            failPlacement(
                    destination,
                    itemId,
                    null,
                    null,
                    "item_not_placeable",
                    "requested item is not a block item"
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
            pending = null;
            DAI_RuntimeTelemetry.placementSuccess();
            success();
            return;
        }

        if (
                !destinationState.canBeReplaced()
                        || !destinationState
                        .getFluidState()
                        .isEmpty()
        ) {
            failPlacement(
                    destination,
                    itemId,
                    null,
                    null,
                    "destination_not_replaceable",
                    BuiltInRegistries.BLOCK
                            .getKey(destinationState.getBlock())
                            .toString()
            );
            return;
        }

        boolean creative =
                minecraft.player
                        .getAbilities()
                        .mayfly;

        boolean occupiesDestination =
                playerIntersects(
                        minecraft,
                        minecraft.player.position(),
                        destination
                );

        /*
         * In survival this remains a hard failure. In Creative this is exactly
         * the situation the flight planner is supposed to correct, so do not
         * reject the cell before giving it a chance to move the player away.
         */
        if (
                occupiesDestination
                        && !creative
        ) {
            failPlacement(
                    destination,
                    itemId,
                    null,
                    null,
                    "destination_occupied",
                    "player intersects destination"
            );
            return;
        }

        DAI_ExactPlacementSupport.Support support =
                creative
                        ? DAI_ExactPlacementSupport.findBestCreative(
                        minecraft,
                        destination
                )
                        : DAI_ExactPlacementSupport.findBest(
                        minecraft,
                        destination
                );

        if (support == null) {
            failPlacement(
                    destination,
                    itemId,
                    null,
                    null,
                    "no_support",
                    "no adjacent solid support"
            );
            return;
        }

        if (
                !DAI_HotbarLogic.selectItem(
                        itemId
                )
        ) {
            failPlacement(
                    destination,
                    itemId,
                    support.position(),
                    support.face(),
                    "item_missing",
                    "requested block is not available in inventory"
            );
            return;
        }

        int timeout =
                action.ticks() > 0
                        ? action.ticks()
                        : DEFAULT_APPROACH_TIMEOUT;

        pending =
                new PlacementRequest(
                        destination.immutable(),
                        support.position(),
                        support.face(),
                        itemId,
                        timeout
                );

        creativeLookTicksRemaining =
                Math.min(
                        CREATIVE_LOOK_MAX_TICKS,
                        Math.max(1, timeout)
                );

        DAI_TargetState.selectBlock(
                support.position()
        );

        Vec3 hitLocation =
                hitLocation(
                        support.position(),
                        support.face()
                );

        if (creative) {
            queueCreativePlacement(
                    minecraft,
                    pending,
                    hitLocation,
                    occupiesDestination
            );
            return;
        }

        /* Survival/adventure retain the proven ground approach pipeline. */
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
    }

    private static void queueCreativePlacement(
            Minecraft minecraft,
            PlacementRequest request,
            Vec3 hitLocation,
            boolean forceReposition
    ) {

        creativeLookTicksRemaining =
                Math.min(
                        CREATIVE_LOOK_MAX_TICKS,
                        Math.max(1, request.timeoutTicks())
                );

        double reach =
                minecraft.player
                        .getEyePosition()
                        .distanceTo(
                                hitLocation
                        );

        if (
                !forceReposition
                        && reach <= MAX_USE_DISTANCE
        ) {
            DAI_ActionQueue.enqueueFirst(
                    createAction(
                            "exact_place_align",
                            "",
                            0,
                            0.0D
                    )
            );
            success();
            return;
        }

        Vec3 flightTarget =
                creativePlacementStance(
                        minecraft,
                        request.destination(),
                        hitLocation,
                        request.face()
                );

        int generation =
                DAI_CreativeFlightController.start(
                        flightTarget,
                        request.timeoutTicks(),
                        CREATIVE_FLIGHT_TOLERANCE
                );

        if (!DAI_CreativeFlightController.isActive()) {
            failAndClear(
                    request,
                    "flight_start_failed",
                    "could not start Creative placement flight"
            );
            return;
        }

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        createWaitForCreativeFlight(
                                generation,
                                request.timeoutTicks()
                        ),
                        createAction(
                                "creative_hover",
                                "",
                                1,
                                0.0D
                        ),
                        createAction(
                                "exact_place_align",
                                "",
                                0,
                                0.0D
                        )
                )
        );

        success();
    }

    /**
     * Smoothly turns the visible Creative camera toward the exact support-face
     * hit point before the physical right-click is allowed to fire. The
     * normal look controller applies one bounded rotation step each client
     * tick, so pyramid construction remains visibly player-driven.
     */
    public static void alignPlacement(
            DAI_ActionDefinition action
    ) {

        PlacementRequest request =
                pending;

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                request == null
                        || minecraft.player == null
                        || minecraft.level == null
        ) {
            DAI_InputState.setManagedOverride(false);
            pending = null;
            fail(
                    "Exact placement camera alignment lost its request or world context."
            );
            return;
        }

        /*
         * Creative flight releases managed input when its movement phase ends.
         * Exact placement must take temporary ownership back while the visible
         * camera eases toward the support-face hit point. Keep this override
         * active across queued alignment ticks so DAI_LookController can apply
         * each bounded rotation step to the real player camera.
         */
        DAI_InputState.setManagedOverride(true);

        Vec3 hitLocation =
                hitLocation(
                        request.support(),
                        request.face()
                );

        if (cameraAligned(
                minecraft,
                hitLocation
        )) {
            DAI_InputState.setManagedOverride(false);
            DAI_ActionQueue.enqueueFirst(
                    createAction(
                            "exact_place_finish",
                            "",
                            0,
                            0.0D
                    )
            );
            success();
            return;
        }

        if (creativeLookTicksRemaining <= 0) {
            failAndClear(
                    request,
                    "camera_alignment_timeout",
                    "could not face placement point smoothly"
            );
            return;
        }

        creativeLookTicksRemaining--;

        DAI_ApproachTargeting.rotateToward(
                minecraft,
                hitLocation
        );

        DAI_ActionQueue.enqueueFirst(
                createAction(
                        "exact_place_align",
                        "",
                        0,
                        0.0D
                )
        );

        success();
    }

    /** Internal continuation registered as an action for queue sequencing. */
    public static void finishPlacement(
            DAI_ActionDefinition action
    ) {

        PlacementRequest request =
                pending;

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                request == null
                        || minecraft.player == null
                        || minecraft.level == null
                        || minecraft.gameMode == null
        ) {
            pending = null;
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
                failAndClear(
                        request,
                        "item_not_placeable",
                        "item stopped resolving to a block item"
                );
                return;
            }

            if (
                    current.is(
                            blockItem.getBlock()
                    )
            ) {
                pending = null;
                DAI_RuntimeTelemetry.placementSuccess();
                success();
                return;
            }

            if (
                    !current.canBeReplaced()
                            || !current
                            .getFluidState()
                            .isEmpty()
            ) {
                failAndClear(
                        request,
                        "destination_changed",
                        BuiltInRegistries.BLOCK
                                .getKey(current.getBlock())
                                .toString()
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
                failAndClear(
                        request,
                        "support_missing",
                        "selected support disappeared"
                );
                return;
            }

            if (
                    playerIntersects(
                            minecraft,
                            minecraft.player.position(),
                            request.destination()
                    )
            ) {
                /*
                 * Flight drift or a newly selected cell can leave the player
                 * inside the destination. Reposition instead of consuming the
                 * datapack attempt as a failure.
                 */
                if (minecraft.player.getAbilities().mayfly) {
                    Vec3 hitLocation =
                            hitLocation(
                                    request.support(),
                                    request.face()
                            );
                    queueCreativePlacement(
                            minecraft,
                            request,
                            hitLocation,
                            true
                    );
                    return;
                }

                failAndClear(
                        request,
                        "destination_occupied",
                        "player intersects destination at finish"
                );
                return;
            }

            Vec3 hitLocation =
                    hitLocation(
                            request.support(),
                            request.face()
                    );

            double reach =
                    minecraft.player
                            .getEyePosition()
                            .distanceTo(
                                    hitLocation
                            );

            if (reach > MAX_USE_DISTANCE) {
                if (minecraft.player.getAbilities().mayfly) {
                    queueCreativePlacement(
                            minecraft,
                            request,
                            hitLocation,
                            true
                    );
                    return;
                }

                failAndClear(
                        request,
                        "out_of_range",
                        "reach=" + format(reach)
                );
                return;
            }

            if (
                    !DAI_HotbarLogic.selectItem(
                            request.itemId()
                    )
            ) {
                failAndClear(
                        request,
                        "item_missing",
                        "requested block disappeared from inventory"
                );
                return;
            }

            /*
             * Creative placement has already completed its smooth alignment
             * phase. Keep one final bounded correction for survival/adventure
             * exact placement, which still uses the proven approach pipeline.
             */
            if (!minecraft.player.getAbilities().mayfly) {
                DAI_ApproachTargeting.rotateToward(
                        minecraft,
                        hitLocation
                );
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
                failAndClear(
                        request,
                        "interaction_rejected",
                        String.valueOf(result)
                );
                return;
            }

            minecraft.player.swing(
                    InteractionHand.MAIN_HAND
            );

            /*
             * The interaction return value is not the commit point. Give the
             * client/server two ticks to publish the block state, then verify
             * the actual destination before reporting placement success.
             */
            DAI_ActionQueue.enqueueFirst(
                    createAction(
                            "exact_place_verify",
                            "",
                            0,
                            0.0D
                    )
            );
            DAI_ActionQueue.delay(
                    VERIFY_DELAY_TICKS
            );

            success();

            /* Per-block success stays DEBUG so long builds do not flood logs. */
            DAI_Core.debug(
                    "<DAI>: Exact-placement interaction sent '{}' -> {} support={} face={}.",
                    request.itemId(),
                    request.destination(),
                    request.support(),
                    request.face()
            );

        } finally {
            DAI_TargetState.selectBlock(
                    request.destination()
            );
        }
    }

    /** World-state commit check after a physical placement interaction. */
    public static void verifyPlacement(
            DAI_ActionDefinition action
    ) {

        PlacementRequest request =
                pending;

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                request == null
                        || minecraft.player == null
                        || minecraft.level == null
        ) {
            pending = null;
            fail(
                    "Exact placement verification lost its request or world context."
            );
            return;
        }

        DAI_TargetState.selectBlock(
                request.destination()
        );

        Item item =
                BuiltInRegistries.ITEM.getValue(
                        request.itemId()
                );

        if (!(item instanceof BlockItem blockItem)) {
            failAndClear(
                    request,
                    "verify_item_invalid",
                    "item no longer resolves to a block"
            );
            return;
        }

        BlockState actual =
                minecraft.level.getBlockState(
                        request.destination()
                );

        if (
                actual.is(
                        blockItem.getBlock()
                )
        ) {
            pending = null;
            DAI_RuntimeTelemetry.placementSuccess();
            success();
            return;
        }

        failAndClear(
                request,
                "world_verify_missing",
                "actual="
                        + BuiltInRegistries.BLOCK
                        .getKey(actual.getBlock())
        );
    }

    public static void reset() {
        DAI_InputState.setManagedOverride(false);
        pending = null;
        creativeLookTicksRemaining = 0;
    }

    private static boolean cameraAligned(
            Minecraft minecraft,
            Vec3 targetPosition
    ) {

        if (
                minecraft.player == null
                        || targetPosition == null
        ) {
            return false;
        }

        Vec3 eyePosition =
                minecraft.player.getEyePosition();

        double deltaX =
                targetPosition.x - eyePosition.x;

        double deltaY =
                targetPosition.y - eyePosition.y;

        double deltaZ =
                targetPosition.z - eyePosition.z;

        double horizontal =
                Math.sqrt(
                        deltaX * deltaX
                                + deltaZ * deltaZ
                );

        float targetYaw =
                Mth.wrapDegrees(
                        (float) (
                                Math.toDegrees(
                                        Math.atan2(
                                                deltaZ,
                                                deltaX
                                        )
                                )
                                        - 90.0D
                        )
                );

        float targetPitch =
                Mth.clamp(
                        (float) -Math.toDegrees(
                                Math.atan2(
                                        deltaY,
                                        horizontal
                                )
                        ),
                        -90.0F,
                        90.0F
                );

        float yawError =
                Math.abs(
                        Mth.wrapDegrees(
                                targetYaw
                                        - minecraft.player.getYRot()
                        )
                );

        float pitchError =
                Math.abs(
                        targetPitch
                                - minecraft.player.getXRot()
                );

        return yawError <= CREATIVE_LOOK_YAW_TOLERANCE
                && pitchError <= CREATIVE_LOOK_PITCH_TOLERANCE;
    }

    private static Vec3 hitLocation(
            BlockPos support,
            Direction face
    ) {
        return Vec3.atCenterOf(
                support
        )
                .add(
                        face.getStepX() * 0.5D,
                        face.getStepY() * 0.5D,
                        face.getStepZ() * 0.5D
                );
    }

    private static Vec3 creativePlacementStance(
            Minecraft minecraft,
            BlockPos destination,
            Vec3 hitLocation,
            Direction face
    ) {

        Vec3 normal =
                new Vec3(
                        face.getStepX(),
                        face.getStepY(),
                        face.getStepZ()
                );

        Vec3 desiredEye;

        if (face == Direction.UP) {
            /* Stable top-down hover for foundations/floors/stacked layers. */
            desiredEye =
                    hitLocation.add(
                            0.0D,
                            CREATIVE_TOP_STANDOFF,
                            0.0D
                    );
        } else if (face == Direction.DOWN) {
            desiredEye =
                    hitLocation.add(
                            0.0D,
                            -CREATIVE_SIDE_STANDOFF,
                            0.0D
                    );
        } else {
            /*
             * Horizontal support fallback for walls/details. Stay well off
             * the destination horizontally and slightly above its center.
             */
            desiredEye =
                    hitLocation
                            .add(
                                    normal.scale(
                                            CREATIVE_SIDE_STANDOFF
                                    )
                            )
                            .add(
                                    0.0D,
                                    1.10D,
                                    0.0D
                            );
        }

        double eyeHeight =
                minecraft.player
                        .getEyePosition()
                        .y
                        - minecraft.player
                        .position()
                        .y;

        Vec3 stance =
                desiredEye.add(
                        0.0D,
                        -eyeHeight,
                        0.0D
                );

        /*
         * Defensive collision correction. This mainly protects against future
         * geometry policies; the normal top/side formulas should already keep
         * the player outside the cell being created.
         */
        if (
                playerIntersects(
                        minecraft,
                        stance,
                        destination
                )
        ) {
            stance =
                    stance.add(
                            0.0D,
                            1.75D,
                            0.0D
                    );
        }

        return stance;
    }

    private static boolean playerIntersects(
            Minecraft minecraft,
            Vec3 playerPosition,
            BlockPos block
    ) {

        if (
                minecraft == null
                        || minecraft.player == null
                        || playerPosition == null
                        || block == null
        ) {
            return false;
        }

        Vec3 current =
                minecraft.player.position();

        AABB box =
                minecraft.player
                        .getBoundingBox()
                        .move(
                                playerPosition.x - current.x,
                                playerPosition.y - current.y,
                                playerPosition.z - current.z
                        );

        return box.intersects(
                new AABB(
                        block
                )
        );
    }

    private static DAI_ActionDefinition createWaitForCreativeFlight(
            int generation,
            int ticks
    ) {
        return new DAI_ActionDefinition(
                "wait_for_creative_flight",
                "",
                List.of(),
                List.of(),
                "",
                "",
                0.0F,
                0.0F,
                "",
                ticks,
                generation,
                false,
                0.0D
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

    private static void failAndClear(
            PlacementRequest request,
            String reason,
            String detail
    ) {
        if (request != null) {
            failPlacement(
                    request.destination(),
                    request.itemId(),
                    request.support(),
                    request.face(),
                    reason,
                    detail
            );
            DAI_TargetState.selectBlock(
                    request.destination()
            );
        } else {
            fail(
                    "Exact placement failed: {} {}",
                    reason,
                    detail
            );
        }

        DAI_InputState.setManagedOverride(false);
        pending = null;
        creativeLookTicksRemaining = 0;
    }

    private static void failPlacement(
            BlockPos destination,
            Identifier itemId,
            BlockPos support,
            Direction face,
            String reason,
            String detail
    ) {
        Minecraft minecraft =
                Minecraft.getInstance();

        double reach = -1.0D;
        if (
                minecraft.player != null
                        && support != null
                        && face != null
        ) {
            reach = minecraft.player
                    .getEyePosition()
                    .distanceTo(
                            hitLocation(
                                    support,
                                    face
                            )
                    );
        }

        DAI_RuntimeTelemetry.placementFailure(
                destination,
                itemId,
                support,
                face,
                reach,
                reason,
                detail
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.FAILURE
        );

        DAI_Core.debug(
                "<DAI>: Exact placement failure reason={} destination={} item={} support={} face={} detail={}.",
                reason,
                destination,
                itemId,
                support,
                face,
                detail
        );
    }

    private static void fail(
            String message,
            Object... arguments
    ) {
        DAI_ActionStatus.set(
                DAI_ActionResult.FAILURE
        );

        DAI_Core.debug(
                "<DAI>: " + message,
                arguments
        );
    }

    private static String format(
            double value
    ) {
        return String.format(
                java.util.Locale.ROOT,
                "%.2f",
                value
        );
    }

    private record PlacementRequest(
            BlockPos destination,
            BlockPos support,
            Direction face,
            Identifier itemId,
            int timeoutTicks
    ) {
    }
}
