package io.github.j12h36h.dai.logic;

import io.github.j12h36h.dai.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.action.DAI_ActionQueue;
import io.github.j12h36h.dai.action.DAI_ActionResult;
import io.github.j12h36h.dai.action.DAI_ActionStatus;
import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.input.DAI_InputState;
import io.github.j12h36h.dai.navigation.DAI_Path;
import io.github.j12h36h.dai.navigation.DAI_PathFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public final class DAI_ItemCollectionLogic {

    private static final double DEFAULT_SEARCH_RADIUS =
            6.0D;

    private static final int DEFAULT_TIMEOUT_TICKS =
            120;

    private static final double PICKUP_DISTANCE =
            1.25D;

    private static final double NODE_REACHED_DISTANCE =
            0.35D;

    private static DAI_Path path;

    private static int pathIndex;

    private static int ticksRemaining;

    private static int trackedEntityId =
            -1;

    private static boolean active;

    private DAI_ItemCollectionLogic() {
        // Utility class.
    }

    public static void collectNearbyItems(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot collect item drops without an active player and level."
            );

            return;
        }

        if (!active) {

            ticksRemaining =
                    action != null
                            && action.ticks() > 0
                            ? action.ticks()
                            : DEFAULT_TIMEOUT_TICKS;

            active =
                    true;

            path =
                    null;

            pathIndex =
                    0;

            trackedEntityId =
                    -1;

            DAI_InputState.setManagedOverride(
                    true
            );
        }

        if (ticksRemaining-- <= 0) {

            finish(
                    DAI_ActionResult.TIMED_OUT,
                    "item collection timed out"
            );

            return;
        }

        double searchRadius =
                action != null
                        && action.value() > 0.0D
                        ? action.value()
                        : DEFAULT_SEARCH_RADIUS;

        ItemEntity target =
                findTrackedItem(
                        minecraft
                );

        if (
                target == null
                        || !target.isAlive()
        ) {

            target =
                    findNearestReachableItem(
                            minecraft,
                            searchRadius
                    );

            if (target == null) {

                finish(
                        DAI_ActionResult.SUCCESS,
                        "no nearby item drops remain"
                );

                return;
            }

            trackedEntityId =
                    target.getId();

            if (
                    !buildPath(
                            minecraft,
                            target
                    )
            ) {

                trackedEntityId =
                        -1;

                scheduleRetry(
                        action
                );

                return;
            }
        }

        double distance =
                minecraft.player.position()
                        .distanceTo(
                                target.position()
                        );

        if (distance <= PICKUP_DISTANCE) {

            DAI_InputState
                    .movement()
                    .setMovement(
                            0.0F,
                            0.0F
                    );

            DAI_InputState
                    .movement()
                    .setJump(
                            false
                    );

            scheduleRetry(
                    action
            );

            return;
        }

        if (
                path == null
                        || pathIndex >= path.nodes().size()
        ) {

            if (
                    !buildPath(
                            minecraft,
                            target
                    )
            ) {

                trackedEntityId =
                        -1;

                scheduleRetry(
                        action
                );

                return;
            }
        }

        followPath(
                minecraft
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        scheduleRetry(
                action
        );
    }

    public static boolean isActive() {

        return active;
    }

    /**
     * Cancels all persistent item-collection state.
     *
     * Item collection is controller-like asynchronous work even though it
     * lives in the logic package: it owns movement, a path, retry state, and
     * a tracked entity across multiple queue ticks. Automation/session reset
     * therefore needs an explicit hard reset for it.
     */
    public static void reset() {

        boolean wasActive =
                active;

        DAI_InputState
                .movement()
                .setMovement(
                        0.0F,
                        0.0F
                );

        DAI_InputState
                .movement()
                .setJump(
                        false
                );

        DAI_InputState.setManagedOverride(
                false
        );

        path =
                null;

        pathIndex =
                0;

        ticksRemaining =
                0;

        trackedEntityId =
                -1;

        active =
                false;

        if (wasActive) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.CANCELLED
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Active item collection cancelled during reset."
            );
        }
    }

    private static ItemEntity findTrackedItem(
            Minecraft minecraft
    ) {

        if (
                minecraft.level == null
                        || trackedEntityId < 0
        ) {
            return null;
        }

        if (
                minecraft.level.getEntity(
                        trackedEntityId
                )
                        instanceof ItemEntity itemEntity
        ) {
            return itemEntity;
        }

        return null;
    }

    private static ItemEntity findNearestReachableItem(
            Minecraft minecraft,
            double searchRadius
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {
            return null;
        }

        AABB searchBox =
                minecraft.player
                        .getBoundingBox()
                        .inflate(
                                searchRadius
                        );

        return minecraft.level
                .getEntitiesOfClass(
                        ItemEntity.class,
                        searchBox,
                        item -> item.isAlive()
                                && !item.hasPickUpDelay()
                )
                .stream()
                .filter(
                        item -> canReach(
                                minecraft,
                                item
                        )
                )
                .min(
                        Comparator.comparingDouble(
                                item -> item.distanceToSqr(
                                        minecraft.player
                                )
                        )
                )
                .orElse(
                        null
                );
    }

    private static boolean canReach(
            Minecraft minecraft,
            ItemEntity item
    ) {

        if (
                minecraft.level == null
                        || minecraft.player == null
                        || item == null
        ) {
            return false;
        }

        BlockPos start =
                minecraft.player.blockPosition();

        BlockPos destination =
                findTraversableDestination(
                        minecraft,
                        item.blockPosition()
                );

        if (destination == null) {
            return false;
        }

        return DAI_PathFinder.find(
                minecraft.level,
                start,
                destination
        ) != null;
    }

    private static boolean buildPath(
            Minecraft minecraft,
            ItemEntity item
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || item == null
        ) {
            return false;
        }

        BlockPos destination =
                findTraversableDestination(
                        minecraft,
                        item.blockPosition()
                );

        if (destination == null) {
            return false;
        }

        DAI_Path newPath =
                DAI_PathFinder.find(
                        minecraft.level,
                        minecraft.player.blockPosition(),
                        destination
                );

        if (newPath == null) {
            return false;
        }

        path =
                newPath;

        pathIndex =
                path.nodes().size() > 1
                        ? 1
                        : path.nodes().size();

        DAI_Core.LOGGER.info(
                "<DAI>: Collecting dropped item {} via {} path node(s).",
                item.getItem(),
                path.nodes().size()
        );

        return true;
    }

    private static BlockPos findTraversableDestination(
            Minecraft minecraft,
            BlockPos itemPosition
    ) {

        if (
                minecraft.level == null
                        || itemPosition == null
        ) {
            return null;
        }

        if (
                DAI_PathFinder.isTraversablePosition(
                        minecraft.level,
                        itemPosition
                )
        ) {
            return itemPosition.immutable();
        }

        BlockPos[] candidates = {
                itemPosition.below(),
                itemPosition.north(),
                itemPosition.south(),
                itemPosition.east(),
                itemPosition.west(),
                itemPosition.north().below(),
                itemPosition.south().below(),
                itemPosition.east().below(),
                itemPosition.west().below()
        };

        for (BlockPos candidate : candidates) {

            if (
                    DAI_PathFinder.isTraversablePosition(
                            minecraft.level,
                            candidate
                    )
            ) {
                return candidate.immutable();
            }
        }

        return null;
    }

    private static void followPath(
            Minecraft minecraft
    ) {

        if (
                minecraft.player == null
                        || path == null
        ) {
            return;
        }

        List<BlockPos> nodes =
                path.nodes();

        while (pathIndex < nodes.size()) {

            BlockPos node =
                    nodes.get(
                            pathIndex
                    );

            Vec3 nodeCenter =
                    Vec3.atBottomCenterOf(
                            node
                    );

            double horizontalDistance =
                    horizontalDistance(
                            minecraft.player.position(),
                            nodeCenter
                    );

            double verticalDistance =
                    Math.abs(
                            minecraft.player.getY()
                                    - node.getY()
                    );

            if (
                    horizontalDistance <= NODE_REACHED_DISTANCE
                            && verticalDistance <= 0.75D
            ) {

                pathIndex++;

                continue;
            }

            moveToward(
                    minecraft,
                    nodeCenter
            );

            boolean shouldJump =
                    node.getY()
                            > minecraft.player.blockPosition().getY();

            DAI_InputState
                    .movement()
                    .setJump(
                            shouldJump
                    );

            return;
        }

        DAI_InputState
                .movement()
                .setMovement(
                        0.0F,
                        0.0F
                );

        DAI_InputState
                .movement()
                .setJump(
                        false
                );
    }

    /**
     * Moves toward a world-space point without taking ownership of the
     * player's camera.
     *
     * Positive DAI strafe maps to Minecraft's left input, matching the
     * corrected navigation convention used by the approach/path controllers.
     */
    private static void moveToward(
            Minecraft minecraft,
            Vec3 targetPosition
    ) {

        if (
                minecraft.player == null
                        || targetPosition == null
        ) {
            return;
        }

        Vec3 playerPosition =
                minecraft.player.position();

        double deltaX =
                targetPosition.x
                        - playerPosition.x;

        double deltaZ =
                targetPosition.z
                        - playerPosition.z;

        double length =
                Math.sqrt(
                        deltaX * deltaX
                                + deltaZ * deltaZ
                );

        if (length <= 1.0E-6D) {

            DAI_InputState
                    .movement()
                    .setMovement(
                            0.0F,
                            0.0F
                    );

            return;
        }

        double worldX =
                deltaX / length;

        double worldZ =
                deltaZ / length;

        double yawRadians =
                Math.toRadians(
                        minecraft.player.getYRot()
                );

        /*
         * Minecraft local movement basis:
         *
         * forward = (-sin(yaw), cos(yaw))
         * left    = ( cos(yaw), sin(yaw))
         */
        double forwardX =
                -Math.sin(
                        yawRadians
                );

        double forwardZ =
                Math.cos(
                        yawRadians
                );

        double leftX =
                Math.cos(
                        yawRadians
                );

        double leftZ =
                Math.sin(
                        yawRadians
                );

        float forward =
                (float) (
                        worldX * forwardX
                                + worldZ * forwardZ
                );

        float strafe =
                (float) (
                        worldX * leftX
                                + worldZ * leftZ
                );

        DAI_InputState
                .movement()
                .setMovement(
                        forward,
                        strafe
                );
    }

    private static double horizontalDistance(
            Vec3 first,
            Vec3 second
    ) {

        double deltaX =
                second.x - first.x;

        double deltaZ =
                second.z - first.z;

        return Math.sqrt(
                deltaX * deltaX
                        + deltaZ * deltaZ
        );
    }

    private static void scheduleRetry(
            DAI_ActionDefinition action
    ) {

        DAI_ActionQueue.enqueueFirst(
                new DAI_ActionDefinition(
                        "collect_nearby_items",
                        "",
                        List.of(),
                        List.of(),
                        "",
                        "",
                        0.0F,
                        0.0F,
                        "",
                        Math.max(
                                1,
                                ticksRemaining
                        ),
                        0,
                        false,
                        action != null
                                ? action.value()
                                : DEFAULT_SEARCH_RADIUS
                )
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );
    }

    private static void finish(
            DAI_ActionResult result,
            String reason
    ) {

        DAI_InputState
                .movement()
                .setMovement(
                        0.0F,
                        0.0F
                );

        DAI_InputState
                .movement()
                .setJump(
                        false
                );

        DAI_InputState.setManagedOverride(
                false
        );

        path =
                null;

        pathIndex =
                0;

        ticksRemaining =
                0;

        trackedEntityId =
                -1;

        active =
                false;

        DAI_ActionStatus.set(
                result
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Finished collecting nearby item drops with result={}: {}.",
                result,
                reason
        );
    }
}