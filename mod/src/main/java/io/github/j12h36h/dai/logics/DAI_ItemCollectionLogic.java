package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.input.DAI_InputState;
import io.github.j12h36h.dai.logics.navigation.DAI_Path;
import io.github.j12h36h.dai.logics.navigation.DAI_PathFinder;
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

    private static final int BARRIER_POLL_TICKS =
            1;

    private static final double NODE_REACHED_DISTANCE =
            0.45D;

    /*
     * Once this close, path nodes are no longer important.
     * Move directly into the ItemEntity until Minecraft picks it up.
     */
    private static final double DIRECT_PICKUP_DISTANCE =
            2.0D;

    private static DAI_Path path;

    private static int pathIndex;

    private static int ticksRemaining;

    private static int trackedEntityId =
            -1;

    private static BlockPos trackedItemBlock;

    private static double searchRadius =
            DEFAULT_SEARCH_RADIUS;

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

            finish(
                    DAI_ActionResult.FAILURE,
                    "player or level unavailable"
            );

            return;
        }

        /*
         * First invocation starts collection ownership.
         *
         * After this point the queue is blocked until collection reaches
         * SUCCESS, FAILURE, TIMED_OUT, or is explicitly reset.
         */
        if (!active) {

            ticksRemaining =
                    action != null
                            && action.ticks() > 0
                            ? action.ticks()
                            : DEFAULT_TIMEOUT_TICKS;

            searchRadius =
                    action != null
                            && action.value() > 0.0D
                            ? action.value()
                            : DEFAULT_SEARCH_RADIUS;

            path =
                    null;

            pathIndex =
                    0;

            trackedEntityId =
                    -1;

            trackedItemBlock =
                    null;

            active =
                    true;

            DAI_InputState.setManagedOverride(
                    true
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Started nearby-item collection with radius={} timeout={} tick(s).",
                    searchRadius,
                    ticksRemaining
            );
        }

        /*
         * Collection owns its own timeout.
         */
        if (ticksRemaining-- <= 0) {

            finish(
                    DAI_ActionResult.TIMED_OUT,
                    "item collection timed out"
            );

            return;
        }

        ItemEntity target =
                findTrackedItem(
                        minecraft
                );

        /*
         * Previous target disappeared.
         *
         * Usually this means it was successfully picked up.
         */
        if (
                target == null
                        || !target.isAlive()
        ) {

            clearTrackedTarget();

            target =
                    findNearestReachableItem(
                            minecraft
                    );

            if (target == null) {

                finish(
                        DAI_ActionResult.SUCCESS,
                        "no nearby reachable item drops remain"
                );

                return;
            }

            beginTracking(
                    minecraft,
                    target
            );
        }

        /*
         * ItemEntities can move after spawning.
         *
         * If it moved into a different block, discard the old path and
         * recalculate from the player's current position.
         */
        BlockPos currentItemBlock =
                target.blockPosition();

        if (
                trackedItemBlock == null
                        || !trackedItemBlock.equals(
                        currentItemBlock
                )
        ) {

            trackedItemBlock =
                    currentItemBlock.immutable();

            path =
                    null;

            pathIndex =
                    0;
        }

        double distance =
                minecraft.player.position()
                        .distanceTo(
                                target.position()
                        );

        /*
         * Near the item, do not stop and hope collision reaches it.
         *
         * Move directly toward the actual ItemEntity position until it
         * disappears from the world.
         */
        if (
                distance
                        <= DIRECT_PICKUP_DISTANCE
        ) {

            moveToward(
                    minecraft,
                    target.position()
            );

            DAI_InputState
                    .movement()
                    .setJump(
                            shouldJumpTowardItem(
                                    minecraft,
                                    target
                            )
                    );

            continueCollection();

            return;
        }

        /*
         * Build/rebuild a world path when necessary.
         */
        if (
                path == null
                        || pathIndex
                        >= path.nodes().size()
        ) {

            if (
                    !buildPath(
                            minecraft,
                            target
                    )
            ) {

                /*
                 * This particular drop is currently unreachable.
                 *
                 * Forget it so another reachable nearby drop can be chosen
                 * on the next collection tick.
                 */
                DAI_Core.LOGGER.debug(
                        "<DAI>: Could not build collection path to dropped item {}.",
                        target.getItem()
                );

                clearTrackedTarget();

                continueCollection();

                return;
            }
        }

        boolean following =
                followPath(
                        minecraft
                );

        /*
         * Path finished but the ItemEntity has not yet been collected.
         *
         * Finish the last few blocks using the entity's exact position.
         */
        if (!following) {

            moveToward(
                    minecraft,
                    target.position()
            );

            DAI_InputState
                    .movement()
                    .setJump(
                            shouldJumpTowardItem(
                                    minecraft,
                                    target
                            )
                    );
        }

        continueCollection();
    }

    public static boolean isActive() {

        return active;
    }

    /**
     * Cancels persistent collection state.
     */
    public static void reset() {

        boolean wasActive =
                active;

        if (
                DAI_ActionQueue.barrierIs(
                        "collect_nearby_items"
                )
        ) {

            DAI_ActionQueue.releaseBarrier();
        }

        clearState();

        if (wasActive) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.CANCELLED
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Active item collection cancelled during reset."
            );
        }
    }

    /*
     * ------------------------------------------------------------
     * TARGET SELECTION
     * ------------------------------------------------------------
     */

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
                        instanceof ItemEntity item
        ) {

            return item.isAlive()
                    ? item
                    : null;
        }

        return null;
    }

    private static ItemEntity findNearestReachableItem(
            Minecraft minecraft
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
                        item ->
                                item.isAlive()
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
                                item ->
                                        item.distanceToSqr(
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

        BlockPos destination =
                findTraversableDestination(
                        minecraft,
                        item.blockPosition()
                );

        if (destination == null) {
            return false;
        }

        /*
         * The player may already occupy the destination neighborhood.
         * In that case direct pickup movement is sufficient.
         */
        if (
                destination.equals(
                        minecraft.player.blockPosition()
                )
        ) {
            return true;
        }

        return DAI_PathFinder.find(
                minecraft.level,
                minecraft.player.blockPosition(),
                destination
        ) != null;
    }

    private static void beginTracking(
            Minecraft minecraft,
            ItemEntity item
    ) {

        trackedEntityId =
                item.getId();

        trackedItemBlock =
                item.blockPosition()
                        .immutable();

        path =
                null;

        pathIndex =
                0;

        DAI_Core.LOGGER.info(
                "<DAI>: Tracking dropped item {} at {}.",
                item.getItem(),
                item.blockPosition()
        );

        buildPath(
                minecraft,
                item
        );
    }

    private static void clearTrackedTarget() {

        trackedEntityId =
                -1;

        trackedItemBlock =
                null;

        path =
                null;

        pathIndex =
                0;
    }

    /*
     * ------------------------------------------------------------
     * PATHING
     * ------------------------------------------------------------
     */

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

        BlockPos start =
                minecraft.player.blockPosition();

        if (
                destination.equals(
                        start
                )
        ) {

            path =
                    null;

            pathIndex =
                    0;

            return true;
        }

        DAI_Path newPath =
                DAI_PathFinder.find(
                        minecraft.level,
                        start,
                        destination
                );

        if (
                newPath == null
                        || newPath.nodes().isEmpty()
        ) {
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

    private static boolean followPath(
            Minecraft minecraft
    ) {

        if (
                minecraft.player == null
                        || path == null
        ) {
            return false;
        }

        List<BlockPos> nodes =
                path.nodes();

        while (
                pathIndex
                        < nodes.size()
        ) {

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
                    horizontalDistance
                            <= NODE_REACHED_DISTANCE
                            && verticalDistance
                            <= 0.75D
            ) {

                pathIndex++;

                continue;
            }

            moveToward(
                    minecraft,
                    nodeCenter
            );

            DAI_InputState
                    .movement()
                    .setJump(
                            node.getY()
                                    > minecraft.player
                                    .blockPosition()
                                    .getY()
                    );

            return true;
        }

        path =
                null;

        pathIndex =
                0;

        return false;
    }

    /*
     * ------------------------------------------------------------
     * MOVEMENT
     * ------------------------------------------------------------
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

    private static boolean shouldJumpTowardItem(
            Minecraft minecraft,
            ItemEntity item
    ) {

        if (
                minecraft.player == null
                        || item == null
        ) {
            return false;
        }

        return item.getY()
                > minecraft.player.getY()
                + 0.6D;
    }

    private static double horizontalDistance(
            Vec3 first,
            Vec3 second
    ) {

        double deltaX =
                second.x
                        - first.x;

        double deltaZ =
                second.z
                        - first.z;

        return Math.sqrt(
                deltaX * deltaX
                        + deltaZ * deltaZ
        );
    }

    /*
     * ------------------------------------------------------------
     * BARRIER
     * ------------------------------------------------------------
     */

    private static void continueCollection() {

        DAI_ActionQueue.holdBarrier(
                createCollectionPollAction(),
                BARRIER_POLL_TICKS
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );
    }

    private static DAI_ActionDefinition createCollectionPollAction() {

        return new DAI_ActionDefinition(
                "collect_nearby_items",
                "",
                List.of(),
                List.of(),
                "",
                "",
                0.0F,
                0.0F,
                "",
                0,
                0,
                false,
                searchRadius
        );
    }

    /*
     * ------------------------------------------------------------
     * COMPLETION
     * ------------------------------------------------------------
     */

    private static void finish(
            DAI_ActionResult result,
            String reason
    ) {

        if (
                DAI_ActionQueue.barrierIs(
                        "collect_nearby_items"
                )
        ) {

            DAI_ActionQueue.releaseBarrier();
        }

        clearState();

        DAI_ActionStatus.set(
                result
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Finished collecting nearby item drops with result={}: {}.",
                result,
                reason
        );
    }

    private static void clearState() {

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

        trackedItemBlock =
                null;

        searchRadius =
                DEFAULT_SEARCH_RADIUS;

        active =
                false;
    }
}