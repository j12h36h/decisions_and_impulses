package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.input.DAI_InputState;
import io.github.j12h36h.dai.client.logics.navigation.DAI_Path;
import io.github.j12h36h.dai.client.logics.navigation.DAI_ItemPickupPlanner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /*
     * Once the safe pickup stance has been reached, the collector may nudge
     * toward the ItemEntity while sneaking. If that nudge does not reduce the
     * horizontal pickup distance by a meaningful amount for roughly one
     * second, the drop is considered unsafe from this stance rather than
     * allowing an endless ledge dance.
     */
    private static final int EDGE_PICKUP_STALL_TICKS =
            20;

    /*
     * Even tiny back-and-forth improvements can repeatedly reset the local
     * no-progress timer. Cap the entire direct sneaking nudge phase so one
     * awkward ledge drop can never monopolize collection indefinitely.
     */
    private static final int EDGE_PICKUP_MAX_NUDGE_TICKS =
            30;

    /*
     * If the safe pickup stance itself leaves the ItemEntity beyond the
     * allowed direct-pickup radius, waiting for the whole collection timeout
     * cannot improve anything because movement is intentionally stopped in
     * that branch. Give the entity a brief half-second grace period in case it
     * is still falling/sliding, then reject only that entity and continue.
     */
    private static final int OUT_OF_RANGE_PICKUP_GRACE_TICKS =
            10;

    private static final double EDGE_PICKUP_PROGRESS_DISTANCE =
            0.10D;

    private static final int MAX_EDGE_REJECTED_ITEMS =
            64;

    private static DAI_Path path;

    private static int pathIndex;

    private static int ticksRemaining;

    private static int trackedEntityId =
            -1;

    private static BlockPos trackedItemBlock;

    private static BlockPos pickupStance;

    private static double edgePickupBestDistance =
            Double.POSITIVE_INFINITY;

    private static int edgePickupNoProgressTicks;

    private static int edgePickupNudgeTicks;

    private static int outOfRangePickupTicks;

    /*
     * Unsafe ledge drops are ignored by entity id until that entity disappears.
     * This keeps one bad drop from being selected again by the next collection
     * objective while still allowing every other nearby item to be collected.
     */
    private static final Set<Integer> edgeRejectedEntityIds =
            new HashSet<>();

    /* Optional item id/tag filter supplied through action.action(). */
    private static String itemFilter =
            "";

    private static double searchRadius =
            DEFAULT_SEARCH_RADIUS;

    private static int startingMatchingItemCount;

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

            resetEdgePickupProgress();

            pruneEdgeRejectedItems(
                    minecraft
            );

            itemFilter =
                    action != null
                            && action.hasAction()
                            ? action.action()
                            : "";

            startingMatchingItemCount =
                    countMatchingInventoryItems(
                            minecraft
                    );

            active =
                    true;

            DAI_InputState.setManagedOverride(
                    true
            );

            DAI_Core.debug(
                    "<DAI>: Started nearby-item collection with radius={} timeout={} tick(s).",
                    searchRadius,
                    ticksRemaining
            );
        }

        /*
         * Filtered collection is a bounded resource-acquisition primitive.
         * Once at least one requested item has actually entered inventory,
         * release the hard barrier immediately so the datapack can decide
         * whether more of that resource is still required.
         *
         * Unfiltered collection retains its original collect-all behavior.
         */
        if (
                itemFilter != null
                        && !itemFilter.isBlank()
                        && countMatchingInventoryItems(
                        minecraft
                ) > startingMatchingItemCount
        ) {

            finish(
                    DAI_ActionResult.SUCCESS,
                    "requested item collected"
            );

            return;
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

            resetEdgePickupProgress();
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
        if (path == null) {

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
                DAI_Core.debug(
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

                outOfRangePickupTicks =
                        0;

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

                if (
                        edgePickupStalled(
                                minecraft,
                                target
                        )
                ) {

                    rejectUnsafeEdgeItem(
                            target
                    );

                    DAI_InputState
                            .movement()
                            .setMovement(
                                    0.0F,
                                    0.0F
                            );

                    DAI_InputState
                            .movement()
                            .setSneak(false);

                    clearTrackedTarget();

                    continueCollection();

                    return;
                }

            } else {

                /*
                 * No direct nudge is occurring while the entity remains
                 * outside pickup range. Reset only the nudge-progress
                 * metrics here; preserve outOfRangePickupTicks so the
                 * half-second grace period can actually accumulate.
                 */
                resetEdgeNudgeProgress();

                DAI_InputState
                        .movement()
                        .setMovement(
                                0.0F,
                                0.0F
                        );

                outOfRangePickupTicks++;

                if (
                        outOfRangePickupTicks
                                >= OUT_OF_RANGE_PICKUP_GRACE_TICKS
                ) {

                    DAI_Core.LOGGER.info(
                            "<DAI>: Skipping dropped item {} at {} because safe pickup stance remained {:.2f} block(s) away after {} tick(s).",
                            target.getItem(),
                            target.blockPosition(),
                            distance,
                            OUT_OF_RANGE_PICKUP_GRACE_TICKS
                    );

                    rejectUnsafeEdgeItem(
                            target
                    );

                    DAI_InputState
                            .movement()
                            .setSneak(false);

                    clearTrackedTarget();

                    continueCollection();

                    return;
                }
            }
        } else {

            resetEdgePickupProgress();

            outOfRangePickupTicks =
                    0;

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

        edgeRejectedEntityIds.clear();

        if (wasActive) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.CANCELLED
            );

            DAI_Core.debug(
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

        pruneEdgeRejectedItems(
                minecraft
        );

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
                                        && !edgeRejectedEntityIds.contains(
                                        item.getId()
                                )
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

        resetEdgePickupProgress();

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

        resetEdgePickupProgress();
    }

    private static boolean matchesRequestedItem(
            ItemEntity itemEntity
    ) {

        return itemEntity != null
                && matchesRequestedStack(
                itemEntity.getItem()
        );
    }

    private static boolean matchesRequestedStack(
            ItemStack stack
    ) {

        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (
                itemFilter == null
                        || itemFilter.isBlank()
        ) {
            return true;
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

            return stack.is(tag);
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
                        stack.getItem()
                )
        );
    }

    private static int countMatchingInventoryItems(
            Minecraft minecraft
    ) {

        if (
                minecraft == null
                        || minecraft.player == null
        ) {
            return 0;
        }

        int count = 0;

        var inventory =
                minecraft.player.getInventory();

        for (
                int slot = 0;
                slot < inventory.getContainerSize();
                slot++
        ) {

            ItemStack stack =
                    inventory.getItem(
                            slot
                    );

            if (matchesRequestedStack(stack)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    private static boolean edgePickupStalled(
            Minecraft minecraft,
            ItemEntity target
    ) {

        if (
                minecraft == null
                        || minecraft.player == null
                        || target == null
        ) {
            return false;
        }

        edgePickupNudgeTicks++;

        double currentDistance =
                horizontalDistance(
                        minecraft.player.position(),
                        target.position()
                );

        if (Double.isInfinite(edgePickupBestDistance)) {

            edgePickupBestDistance =
                    currentDistance;

            edgePickupNoProgressTicks =
                    0;

        } else if (
                edgePickupBestDistance
                        - currentDistance
                        >= EDGE_PICKUP_PROGRESS_DISTANCE
        ) {

            /*
             * Meaningful progress resets the local stall timer, but NOT the
             * absolute nudge budget. Tiny repeated oscillations therefore
             * cannot extend this phase forever.
             */
            edgePickupBestDistance =
                    currentDistance;

            edgePickupNoProgressTicks =
                    0;

        } else {

            edgePickupNoProgressTicks++;
        }

        return edgePickupNoProgressTicks
                >= EDGE_PICKUP_STALL_TICKS
                || edgePickupNudgeTicks
                >= EDGE_PICKUP_MAX_NUDGE_TICKS;
    }

    private static void resetEdgeNudgeProgress() {

        edgePickupBestDistance =
                Double.POSITIVE_INFINITY;

        edgePickupNoProgressTicks =
                0;

        edgePickupNudgeTicks =
                0;
    }

    private static void resetEdgePickupProgress() {

        resetEdgeNudgeProgress();

        outOfRangePickupTicks =
                0;
    }

    private static void rejectUnsafeEdgeItem(
            ItemEntity target
    ) {

        if (target == null) {
            return;
        }

        if (
                edgeRejectedEntityIds.size()
                        >= MAX_EDGE_REJECTED_ITEMS
        ) {
            edgeRejectedEntityIds.clear();
        }

        edgeRejectedEntityIds.add(
                target.getId()
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Skipping dropped item {} at {} after safe edge pickup exceeded its progress/nudge budget (stall={} tick(s), maxNudge={} tick(s)).",
                target.getItem(),
                target.blockPosition(),
                EDGE_PICKUP_STALL_TICKS,
                EDGE_PICKUP_MAX_NUDGE_TICKS
        );
    }

    private static void pruneEdgeRejectedItems(
            Minecraft minecraft
    ) {

        if (
                minecraft == null
                        || minecraft.level == null
                        || edgeRejectedEntityIds.isEmpty()
        ) {
            return;
        }

        edgeRejectedEntityIds.removeIf(
                entityId -> {

                    var entity =
                            minecraft.level.getEntity(
                                    entityId
                            );

                    return !(entity instanceof ItemEntity item)
                            || !item.isAlive();
                }
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

        /*
         * Keep the completed path as a marker that the proven safe pickup
         * stance was reached. Rebuilding a one-node path here every tick
         * prevented the direct-pickup stall/out-of-range budgets from ever
         * accumulating and produced the repeated pickup-plan spam seen in the
         * current Speedrun log. Item movement still explicitly nulls the path
         * above and triggers a real replan.
         */
        pathIndex = nodes.size();

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

        resetEdgePickupProgress();

        itemFilter =
                "";

        startingMatchingItemCount =
                0;

        searchRadius =
                DEFAULT_SEARCH_RADIUS;

        active =
                false;
    }
}