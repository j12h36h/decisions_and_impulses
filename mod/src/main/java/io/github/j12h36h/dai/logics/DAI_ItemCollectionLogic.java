package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.input.DAI_InputState;
import io.github.j12h36h.dai.logics.navigation.DAI_Path;
import io.github.j12h36h.dai.logics.navigation.DAI_ItemPickupPlanner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
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
     * Once the safe pickup stance is reached, allow only a short sneaking
     * horizontal nudge toward the entity.
     */
    private static final double DIRECT_PICKUP_DISTANCE =
            2.0D;

    private static DAI_Path path;

    private static int pathIndex;

    private static int ticksRemaining;

    private static int trackedEntityId =
            -1;

    private static BlockPos trackedItemBlock;

    private static BlockPos pickupStance;

    /* Optional item id/tag filter supplied through action.action(). */
    private static String itemFilter =
            "";

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

            pickupStance =
                    null;

            itemFilter =
                    action != null
                            && action.hasAction()
                            ? action.action()
                            : "";

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

            pickupStance =
                    null;
        }

        double distance =
                minecraft.player.position()
                        .distanceTo(
                                target.position()
                        );

        /*
         * Do not drive directly at the ItemEntity here.
         *
         * A drop may be sitting in the shaft that was just mined. The pickup
         * planner first chooses a safe stance and proves any descent has an
         * exit route. Only once that stance is reached may we make a small
         * sneaking pickup nudge toward the entity.
         */

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

            DAI_InputState
                    .movement()
                    .setJump(false);

            /*
             * Edge pickup is intentionally sneaking. Vanilla sneak prevents
             * walking off a ledge, so a drop below the rim cannot pull DAI
             * into the hole merely because its entity center is lower.
             */
            DAI_InputState
                    .movement()
                    .setSneak(true);

            if (
                    distance
                            <= DIRECT_PICKUP_DISTANCE
            ) {

                Vec3 safeNudgeTarget =
                        new Vec3(
                                target.getX(),
                                minecraft.player.getY(),
                                target.getZ()
                        );

                moveToward(
                        minecraft,
                        safeNudgeTarget
                );

            } else {

                DAI_InputState
                        .movement()
                        .setMovement(
                                0.0F,
                                0.0F
                        );
            }
        } else {

            DAI_InputState
                    .movement()
                    .setSneak(false);
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
                                        && matchesRequestedItem(item)
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

        return DAI_ItemPickupPlanner.plan(
                minecraft,
                item
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

        pickupStance =
                null;

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

        pickupStance =
                null;
    }

    private static boolean matchesRequestedItem(
            ItemEntity itemEntity
    ) {

        if (
                itemEntity == null
                        || itemFilter == null
                        || itemFilter.isBlank()
        ) {
            return itemEntity != null;
        }

        String normalized =
                itemFilter.trim()
                        .toLowerCase();

        if (normalized.startsWith("#")) {

            Identifier tagId =
                    Identifier.tryParse(
                            normalized.substring(1)
                    );

            if (tagId == null) {
                return false;
            }

            TagKey<Item> tag =
                    TagKey.create(
                            Registries.ITEM,
                            tagId
                    );

            return itemEntity.getItem()
                    .is(tag);
        }

        Identifier itemId =
                Identifier.tryParse(
                        normalized.contains(":")
                                ? normalized
                                : "minecraft:" + normalized
                );

        if (itemId == null) {
            return false;
        }

        return itemId.equals(
                BuiltInRegistries.ITEM.getKey(
                        itemEntity.getItem()
                                .getItem()
                )
        );
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

        DAI_ItemPickupPlanner.PickupPlan plan =
                DAI_ItemPickupPlanner.plan(
                        minecraft,
                        item
                );

        if (plan == null) {
            return false;
        }

        pickupStance =
                plan.stance();

        path =
                plan.path();

        pathIndex =
                path.nodes().size() > 1
                        ? 1
                        : path.nodes().size();

        DAI_Core.LOGGER.info(
                "<DAI>: Collecting dropped item {} via safe pickup stance {} using {} path node(s).",
                item.getItem(),
                pickupStance,
                path.nodes().size()
        );

        return true;
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
                itemFilter,
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

        DAI_InputState
                .movement()
                .setSneak(
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

        pickupStance =
                null;

        itemFilter =
                "";

        searchRadius =
                DEFAULT_SEARCH_RADIUS;

        active =
                false;
    }
}