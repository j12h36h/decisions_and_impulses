package io.github.j12h36h.dai.controller;

import io.github.j12h36h.dai.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.action.DAI_ActionQueue;
import io.github.j12h36h.dai.action.DAI_ActionResult;
import io.github.j12h36h.dai.action.DAI_ActionStatus;
import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.input.DAI_InputState;
import io.github.j12h36h.dai.navigation.DAI_Path;
import io.github.j12h36h.dai.navigation.DAI_PathFinder;
import io.github.j12h36h.dai.system.DAI_FailedTargetMemory;
import io.github.j12h36h.dai.system.DAI_TargetState;
import io.github.j12h36h.dai.system.DAI_TargetVisibility;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class DAI_ApproachController {

    private static final double DEFAULT_STOP_DISTANCE =
            3.25D;

    private static final int DEFAULT_TIMEOUT_TICKS =
            200;

    private static final int DEFAULT_ALIGNMENT_CHECKS =
            20;

    private static final int STUCK_CHECK_INTERVAL =
            10;

    private static final double MINIMUM_PROGRESS_DISTANCE =
            0.10D;

    private static final double NODE_REACHED_DISTANCE =
            0.55D;

    private static final double NODE_PASSED_LATERAL_DISTANCE =
            0.90D;

    private static final int MAX_LOOKAHEAD_NODES =
            4;

    private static final double MINIMUM_ROUTE_PROGRESS =
            0.12D;

    private static final int MAX_ROUTE_STAGNANT_CHECKS =
            3;

    private static final int DEBUG_LOG_INTERVAL =
            20;

    /*
     * While travelling along an approach path, the camera should remain at a
     * sane exploration angle instead of inheriting a stale near-vertical
     * pitch from another look/alignment action.
     */
    private static final float TRAVEL_TARGET_PITCH =
            5.0F;

    private static final float MAX_TRAVEL_PITCH_STEP =
            6.0F;

    /*
     * Smoothly turn toward the active path steering point instead of
     * preserving a fixed yaw while travelling sideways/backwards.
     *
     * Movement projection uses this same pending yaw so camera rotation does
     * not rotate the local movement basis underneath DAI.
     */
    private static final float MAX_TRAVEL_YAW_STEP =
            12.0F;

    /*
     * Destructive fallback may only directly break an obstruction when it is
     * already within normal block interaction range. A distant obstruction
     * must not freeze movement while the controller stares at it.
     */
    private static final double MAX_FALLBACK_BREAK_DISTANCE =
            4.5D;

    private static BlockPos target;

    /*
     * Snapshot of the block belonging to the most recently successful
     * approach. Alignment/mining must use this instead of blindly rereading
     * the globally mutable DAI_TargetState after the approach has finished.
     */
    private static BlockPos completedTarget;

    private static double stopDistance =
            DEFAULT_STOP_DISTANCE;

    private static int ticksRemaining;

    private static boolean active;

    /*
     * Unique identity of the most recently started approach.
     *
     * Polling wait actions capture this generation so an old wait cannot
     * cancel a newer approach that starts before the old wait expires.
     */
    private static int generation;

    private static BlockPos approachPosition;

    private static DAI_Path approachPath;

    private static int pathIndex;

    private static BlockPos fallbackObstruction;

    private static int stuckCheckTicks;

    private static Vec3 lastProgressPosition;

    private static int lastProgressPathIndex =
            -1;

    private static double lastSteeringDistance =
            Double.POSITIVE_INFINITY;

    private static int routeStagnantChecks;

    private static int debugLogTicks;

    private DAI_ApproachController() {
        // Utility class.
    }

    public static void start(
            BlockPos blockPos,
            double requestedStopDistance,
            int timeoutTicks
    ) {

        if (blockPos == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot approach a null block target."
            );

            return;
        }

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
                    "<DAI>: Cannot approach a block without an active player and level."
            );

            return;
        }

        generation =
                nextGeneration(
                        generation
                );

        completedTarget =
                null;

        target =
                blockPos.immutable();

        stopDistance =
                requestedStopDistance > 0.0D
                        ? requestedStopDistance
                        : DEFAULT_STOP_DISTANCE;

        ticksRemaining =
                timeoutTicks > 0
                        ? timeoutTicks
                        : DEFAULT_TIMEOUT_TICKS;

        approachPosition =
                null;

        approachPath =
                null;

        pathIndex =
                0;

        fallbackObstruction =
                null;

        stuckCheckTicks =
                STUCK_CHECK_INTERVAL;

        lastProgressPosition =
                minecraft.player.position();

        debugLogTicks =
                DEBUG_LOG_INTERVAL;

        resetRouteProgress();

        active =
                true;

        DAI_InputState.setManagedOverride(
                true
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        rebuildApproachPath(
                minecraft
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Started approaching block {} with stopDistance={} and timeout={} tick(s), generation={}.",
                target,
                stopDistance,
                ticksRemaining,
                generation
        );
    }

    public static void startSelectedBlock(
            double requestedStopDistance,
            int timeoutTicks
    ) {

        BlockPos selected =
                DAI_TargetState.selectedBlock();

        if (selected == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot approach because no block target is selected."
            );

            return;
        }

        start(
                selected,
                requestedStopDistance,
                timeoutTicks
        );
    }

    public static void tick() {

        if (!active) {
            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || target == null
        ) {

            finish(
                    DAI_ActionResult.FAILURE,
                    "player, level, or target became unavailable"
            );

            return;
        }

        if (
                minecraft.level
                        .getBlockState(
                                target
                        )
                        .isAir()
        ) {

            finish(
                    DAI_ActionResult.FAILURE,
                    "target block no longer exists"
            );

            return;
        }

        if (ticksRemaining-- <= 0) {

            finish(
                    DAI_ActionResult.TIMED_OUT,
                    "approach timed out"
            );

            return;
        }

        /*
         * An active block break owns movement and look.
         *
         * Do not allow approach recovery to walk or reposition
         * while the break controller is still working.
         */
        if (DAI_BreakController.isActive()) {

            DAI_InputState
                    .movement()
                    .setMovement(
                            0.0F,
                            0.0F
                    );

            resetStuckDetection(
                    minecraft
            );

            return;
        }

        Vec3 eyePosition =
                minecraft.player.getEyePosition();

        Vec3 targetCenter =
                Vec3.atCenterOf(
                        target
                );

        double targetDistance =
                eyePosition.distanceTo(
                        targetCenter
                );

        /*
         * Outside interaction range: follow an actual path to a reachable
         * standing position near the target.
         */
        if (targetDistance > stopDistance) {

            if (
                    approachPath == null
                            || approachPosition == null
            ) {

                if (!rebuildApproachPath(minecraft)) {

                    if (tryDestructiveApproachFallback(minecraft)) {
                        return;
                    }

                    finish(
                            DAI_ActionResult.FAILURE,
                            "no reachable approach position"
                    );

                    return;
                }
            }

            if (followApproachPath(minecraft)) {
                return;
            }

            if (!rebuildApproachPath(minecraft)) {

                if (tryDestructiveApproachFallback(minecraft)) {
                    return;
                }

                finish(
                        DAI_ActionResult.FAILURE,
                        "target remains out of reach after path completion"
                );
            }

            return;
        }

        /*
         * We are either inside interaction distance or already
         * horizontally beside the target. Stop forward/stuck recovery
         * and evaluate the actual target from here.
         */
        resetStuckDetection(
                minecraft
        );

        DAI_InputState
                .movement()
                .setMovement(
                        0.0F,
                        0.0F
                );

        DAI_TargetVisibility.Result visibility =
                DAI_TargetVisibility.inspect(
                        target
                );

        /*
         * At least one usable point on the selected target is visible.
         */
        if (visibility.visible()) {

            rotateToward(
                    minecraft,
                    visibility.visiblePoint()
            );

            /*
             * Minecraft.hitResult can update one client tick after
             * the camera rotation is applied.
             *
             * Only finish once the target is both under the crosshair
             * and inside the requested interaction distance.
             */
            if (
                    targetDistance <= stopDistance
                            && isLookingAtTarget(
                            minecraft
                    )
            ) {

                finish(
                        DAI_ActionResult.SUCCESS,
                        "target reached and visible"
                );

                return;
            }

            return;
        }

        /*
         * Something is blocking every usable ray to the target.
         */
        if (visibility.blocked()) {

            BlockPos blocker =
                    visibility.blocker();

            if (
                    canClearObstruction(
                            minecraft,
                            blocker
                    )
            ) {

                clearObstruction(
                        minecraft,
                        blocker
                );

                return;
            }
        }

        /*
         * The blocker cannot safely be removed, or no blocker could
         * be identified. Recalculate a reachable interaction route.
         */
        if (!rebuildApproachPath(minecraft)) {

            if (tryDestructiveApproachFallback(minecraft)) {
                return;
            }

            finish(
                    DAI_ActionResult.FAILURE,
                    "no alternate reachable interaction position"
            );
        }
    }

    private static boolean canClearObstruction(
            Minecraft minecraft,
            BlockPos blocker
    ) {

        if (
                minecraft.level == null
                        || blocker == null
        ) {
            return false;
        }

        BlockState state =
                minecraft.level.getBlockState(
                        blocker
                );

        if (state.isAir()) {
            return false;
        }

        /*
         * Tree leaves are safe temporary obstructions.
         */
        if (
                state.is(
                        BlockTags.LEAVES
                )
        ) {
            return true;
        }

        /*
         * Clear lightweight replaceable vegetation.
         *
         * Fluids remain excluded.
         */
        if (
                state.canBeReplaced()
                        && state.getFluidState()
                        .isEmpty()
        ) {

            return state.getDestroySpeed(
                    minecraft.level,
                    blocker
            ) >= 0.0F;
        }

        return false;
    }

    private static void clearObstruction(
            Minecraft minecraft,
            BlockPos blocker
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || blocker == null
        ) {
            return;
        }

        /*
         * Wait for the player to be grounded before beginning the break.
         */
        if (!minecraft.player.onGround()) {

            DAI_InputState
                    .movement()
                    .setMovement(
                            0.0F,
                            0.0F
                    );

            return;
        }

        if (
                minecraft.level
                        .getBlockState(
                                blocker
                        )
                        .isAir()
        ) {
            return;
        }

        /*
         * Never restart a break already in progress.
         */
        if (DAI_BreakController.isActive()) {

            DAI_InputState
                    .movement()
                    .setMovement(
                            0.0F,
                            0.0F
                    );

            return;
        }

        Vec3 blockerCenter =
                Vec3.atCenterOf(
                        blocker
                );

        rotateToward(
                minecraft,
                blockerCenter
        );

        DAI_InputState
                .movement()
                .setMovement(
                        0.0F,
                        0.0F
                );

        /*
         * Let Minecraft's normal crosshair result confirm that the
         * blocker is actually targeted before starting destruction.
         */
        if (
                !(
                        minecraft.hitResult
                                instanceof BlockHitResult blockHitResult
                )
        ) {
            return;
        }

        if (
                !blocker.equals(
                        blockHitResult.getBlockPos()
                )
        ) {
            return;
        }

        DAI_Core.LOGGER.info(
                "<DAI>: Clearing obstruction {} before approaching target {}.",
                blocker,
                target
        );

        DAI_BreakController.breakOnce();
    }

    private static void handleStuckMovement(
            Minecraft minecraft,
            Vec3 steeringTarget
    ) {

        if (
                minecraft.player == null
                        || steeringTarget == null
        ) {
            return;
        }

        stuckCheckTicks--;

        if (stuckCheckTicks > 0) {
            return;
        }

        stuckCheckTicks =
                STUCK_CHECK_INTERVAL;

        double steeringDistance =
                horizontalDistance(
                        minecraft.player.position(),
                        steeringTarget
                );

        /*
         * Advancing to a later path node is always meaningful progress.
         */
        if (lastProgressPathIndex != pathIndex) {

            lastProgressPathIndex =
                    pathIndex;

            lastSteeringDistance =
                    steeringDistance;

            routeStagnantChecks =
                    0;

            return;
        }

        double improvement =
                lastSteeringDistance
                        - steeringDistance;

        lastSteeringDistance =
                steeringDistance;

        if (
                improvement
                        >= MINIMUM_ROUTE_PROGRESS
        ) {

            routeStagnantChecks =
                    0;

            return;
        }

        routeStagnantChecks++;

        if (
                routeStagnantChecks
                        < MAX_ROUTE_STAGNANT_CHECKS
        ) {
            return;
        }

        if (DAI_BreakController.isActive()) {
            return;
        }

        DAI_Core.LOGGER.info(
                "<DAI>: Approach route made no forward progress; rebuilding route from current position."
        );

        approachPosition =
                null;

        approachPath =
                null;

        pathIndex =
                0;

        resetRouteProgress();

        rebuildApproachPath(
                minecraft
        );
    }

    private static void resetStuckDetection(
            Minecraft minecraft
    ) {

        stuckCheckTicks =
                STUCK_CHECK_INTERVAL;

        DAI_InputState
                .movement()
                .setJump(
                        false
                );

        lastProgressPosition =
                minecraft.player == null
                        ? null
                        : minecraft.player.position();

        resetRouteProgress();
    }

    private static boolean tryDestructiveApproachFallback(
            Minecraft minecraft
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || target == null
        ) {
            return false;
        }

        if (
                fallbackObstruction != null
                        && minecraft.level
                        .getBlockState(
                                fallbackObstruction
                        )
                        .isAir()
        ) {

            fallbackObstruction =
                    null;

            return rebuildApproachPath(
                    minecraft
            );
        }

        if (fallbackObstruction == null) {

            fallbackObstruction =
                    DAI_PathFinder.findApproachObstruction(
                            minecraft.level,
                            minecraft.player.blockPosition(),
                            target,
                            stopDistance
                    );

            if (fallbackObstruction == null) {
                return false;
            }

            DAI_Core.LOGGER.info(
                    "<DAI>: Normal approach failed; destructive fallback selected obstruction {} for target {}.",
                    fallbackObstruction,
                    target
            );
        }

        Vec3 obstructionCenter =
                Vec3.atCenterOf(
                        fallbackObstruction
                );

        double obstructionDistance =
                minecraft.player
                        .getEyePosition()
                        .distanceTo(
                                obstructionCenter
                        );

        /*
         * findApproachObstruction() can identify a useful obstruction that
         * is reachable through a staging position, but this controller does
         * not currently navigate to that staging position.
         *
         * Do not freeze the player while staring at a block that cannot be
         * reached from the current position. Let the ordinary approach fail
         * so failed-target memory can choose another target instead.
         */
        if (
                obstructionDistance
                        > MAX_FALLBACK_BREAK_DISTANCE
        ) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Destructive-fallback obstruction {} is too far away (distance={}); abandoning fallback.",
                    fallbackObstruction,
                    String.format(
                            java.util.Locale.ROOT,
                            "%.2f",
                            obstructionDistance
                    )
            );

            fallbackObstruction =
                    null;

            return false;
        }

        rotateToward(
                minecraft,
                obstructionCenter
        );

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

        if (
                minecraft.hitResult
                        instanceof BlockHitResult blockHitResult
                        && fallbackObstruction.equals(
                        blockHitResult.getBlockPos()
                )
        ) {

            DAI_Core.LOGGER.info(
                    "<DAI>: Clearing destructive-fallback obstruction {}.",
                    fallbackObstruction
            );

            DAI_BreakController.breakOnce();
        }

        return true;
    }

    private static boolean rebuildApproachPath(
            Minecraft minecraft
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || target == null
        ) {
            return false;
        }

        BlockPos origin =
                minecraft.player.blockPosition();

        BlockPos destination =
                DAI_PathFinder.findNearestApproachPosition(
                        minecraft.level,
                        origin,
                        target,
                        stopDistance
                );

        if (destination == null) {
            approachPosition = null;
            approachPath = null;
            pathIndex = 0;
            return false;
        }

        DAI_Path path =
                DAI_PathFinder.find(
                        minecraft.level,
                        origin,
                        destination
                );

        if (path == null) {
            approachPosition = null;
            approachPath = null;
            pathIndex = 0;
            return false;
        }

        approachPosition =
                destination.immutable();

        approachPath =
                path;

        fallbackObstruction =
                null;

        pathIndex =
                path.nodes().size() > 1
                        ? 1
                        : path.nodes().size();

        resetRouteProgress();

        resetStuckDetection(
                minecraft
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Approach path selected from {} to {} for target {} ({} node(s)).",
                origin,
                approachPosition,
                target,
                path.nodes().size()
        );

        return true;
    }

    private static boolean followApproachPath(
            Minecraft minecraft
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || approachPath == null
        ) {
            return false;
        }

        List<BlockPos> nodes =
                approachPath.nodes();

        advanceCompletedPathNodes(
                minecraft,
                nodes
        );

        if (pathIndex >= nodes.size()) {

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

            return false;
        }

        BlockPos immediateNode =
                nodes.get(
                        pathIndex
                );

        int steeringIndex =
                findSteeringNodeIndex(
                        minecraft,
                        nodes
                );

        BlockPos steeringNode =
                nodes.get(
                        steeringIndex
                );

        Vec3 steeringPoint =
                Vec3.atBottomCenterOf(
                        steeringNode
                );

        /*
         * Lookahead may intentionally steer several nodes beyond pathIndex.
         * If the player has actually reached that steering node, all skipped
         * same-level nodes are complete as well. Advance pathIndex to the node
         * after the steering point so the controller does not keep an obsolete
         * immediate node behind the player.
         */
        if (
                steeringIndex > pathIndex
                        && hasReachedPathNode(
                        minecraft,
                        steeringNode
                )
        ) {

            pathIndex =
                    steeringIndex + 1;

            resetRouteProgress();

            if (pathIndex >= nodes.size()) {

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

                return false;
            }

            immediateNode =
                    nodes.get(
                            pathIndex
                    );

            steeringIndex =
                    findSteeringNodeIndex(
                            minecraft,
                            nodes
                    );

            steeringNode =
                    nodes.get(
                            steeringIndex
                    );

            steeringPoint =
                    Vec3.atBottomCenterOf(
                            steeringNode
                    );
        }

        /*
         * While following a route, smoothly turn the camera toward the
         * steering point while keeping a shallow travel pitch.
         *
         * Precise target/blocker aiming still happens elsewhere once the
         * player reaches interaction range.
         */
        float travelYaw =
                maintainTravelLook(
                        minecraft,
                        steeringPoint
                );

        /*
         * Convert the desired world-space route direction into Minecraft's
         * local forward/strafe axes using the SAME yaw that DAI_LookController
         * will apply later in this client tick.
         *
         * This preserves camera tracking without changing the requested
         * world-space travel direction as the camera turns.
         */
        MovementDebug movementDebug =
                moveToward(
                        minecraft,
                        steeringPoint,
                        travelYaw
                );

        tickDebugLogging(
                minecraft,
                immediateNode,
                steeringNode,
                steeringPoint,
                movementDebug
        );

        /*
         * Vertical movement is still dictated by the immediate A* node.
         * Lookahead steering never invents a jump.
         */
        boolean leavingWater =
                isLeavingWater(
                        minecraft,
                        immediateNode
                );

        boolean shouldJump =
                leavingWater
                        || immediateNode.getY()
                        > minecraft.player.blockPosition().getY();

        DAI_InputState
                .movement()
                .setJump(
                        shouldJump
                );

        handleStuckMovement(
                minecraft,
                steeringPoint
        );

        return true;
    }

    private static boolean hasReachedPathNode(
            Minecraft minecraft,
            BlockPos node
    ) {

        if (
                minecraft.player == null
                        || node == null
        ) {
            return false;
        }

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

        return horizontalDistance <= NODE_REACHED_DISTANCE
                && verticalDistance <= 0.75D;
    }

    private static void advanceCompletedPathNodes(
            Minecraft minecraft,
            List<BlockPos> nodes
    ) {

        if (
                minecraft.player == null
                        || nodes == null
        ) {
            return;
        }

        Vec3 playerPosition =
                minecraft.player.position();

        while (pathIndex < nodes.size()) {

            BlockPos currentNode =
                    nodes.get(
                            pathIndex
                    );

            Vec3 currentCenter =
                    Vec3.atBottomCenterOf(
                            currentNode
                    );

            double horizontalDistance =
                    horizontalDistance(
                            playerPosition,
                            currentCenter
                    );

            double verticalDistance =
                    Math.abs(
                            minecraft.player.getY()
                                    - currentNode.getY()
                    );

            boolean leavingWater =
                    isLeavingWater(
                            minecraft,
                            currentNode
                    );

            if (
                    !leavingWater
                            && horizontalDistance <= NODE_REACHED_DISTANCE
                            && verticalDistance <= 0.75D
            ) {

                pathIndex++;

                resetRouteProgress();

                continue;
            }

            if (pathIndex <= 0) {
                return;
            }

            BlockPos previousNode =
                    nodes.get(
                            pathIndex - 1
                    );

            Vec3 previousCenter =
                    Vec3.atBottomCenterOf(
                            previousNode
                    );

            if (
                    !leavingWater
                            && hasPassedPathSegment(
                            playerPosition,
                            previousCenter,
                            currentCenter
                    )
                            && verticalDistance <= 0.75D
            ) {

                pathIndex++;

                resetRouteProgress();

                continue;
            }

            return;
        }
    }

    private static boolean isLeavingWater(
            Minecraft minecraft,
            BlockPos immediateNode
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || immediateNode == null
                        || !minecraft.player.isInWater()
        ) {
            return false;
        }

        return minecraft.level
                .getFluidState(
                        immediateNode
                )
                .isEmpty();
    }

    private static int findSteeringNodeIndex(
            Minecraft minecraft,
            List<BlockPos> nodes
    ) {

        int bestIndex =
                pathIndex;

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {
            return bestIndex;
        }

        Vec3 playerFeet =
                minecraft.player.position();

        int maximumIndex =
                Math.min(
                        nodes.size() - 1,
                        pathIndex + MAX_LOOKAHEAD_NODES
                );

        for (
                int candidateIndex = pathIndex + 1;
                candidateIndex <= maximumIndex;
                candidateIndex++
        ) {

            BlockPos candidate =
                    nodes.get(
                            candidateIndex
                    );

            /*
             * Never look past a vertical transition. Jumps and drops remain
             * explicit node-by-node movements.
             */
            if (
                    candidate.getY()
                            != nodes.get(
                            pathIndex
                    ).getY()
            ) {
                break;
            }

            Vec3 candidateCenter =
                    Vec3.atBottomCenterOf(
                            candidate
                    );

            if (
                    !DAI_PathFinder.canTraverseSegment(
                            minecraft.level,
                            playerFeet,
                            candidateCenter
                    )
            ) {
                break;
            }

            bestIndex =
                    candidateIndex;
        }

        return bestIndex;
    }

    private static boolean hasPassedPathSegment(
            Vec3 playerPosition,
            Vec3 segmentStart,
            Vec3 segmentEnd
    ) {

        double segmentX =
                segmentEnd.x
                        - segmentStart.x;

        double segmentZ =
                segmentEnd.z
                        - segmentStart.z;

        double segmentLengthSquared =
                segmentX * segmentX
                        + segmentZ * segmentZ;

        if (segmentLengthSquared <= 0.0001D) {
            return false;
        }

        double playerX =
                playerPosition.x
                        - segmentStart.x;

        double playerZ =
                playerPosition.z
                        - segmentStart.z;

        double projection =
                (
                        playerX * segmentX
                                + playerZ * segmentZ
                )
                        / segmentLengthSquared;

        if (projection < 0.90D) {
            return false;
        }

        double projectedX =
                segmentStart.x
                        + segmentX * projection;

        double projectedZ =
                segmentStart.z
                        + segmentZ * projection;

        double lateralX =
                playerPosition.x
                        - projectedX;

        double lateralZ =
                playerPosition.z
                        - projectedZ;

        double lateralDistance =
                Math.sqrt(
                        lateralX * lateralX
                                + lateralZ * lateralZ
                );

        return lateralDistance
                <= NODE_PASSED_LATERAL_DISTANCE;
    }

    public static void stop() {

        if (!active) {
            return;
        }

        finish(
                DAI_ActionResult.CANCELLED,
                "stopped manually"
        );
    }

    public static void reset() {

        boolean wasActive =
                active;

        completedTarget =
                null;

        clearState();

        if (wasActive) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.CANCELLED
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Active block approach was cancelled during reset."
            );
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static int generation() {
        return generation;
    }

    /**
     * Returns the block owned by the current interaction chain.
     *
     * While an approach is active its private target wins. After a successful
     * approach, completedTarget preserves that exact block even if some other
     * recognition action changes DAI_TargetState.
     */
    public static BlockPos interactionTarget() {

        if (
                active
                        && target != null
        ) {
            return target;
        }

        if (completedTarget != null) {
            return completedTarget;
        }

        return DAI_TargetState.selectedBlock();
    }

    public static void faceSelectedBlock() {

        faceBlock(
                interactionTarget()
        );
    }

    public static void faceBlock(
            BlockPos blockPos
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || blockPos == null
        ) {
            return;
        }

        DAI_TargetVisibility.Result visibility =
                DAI_TargetVisibility.inspect(
                        blockPos
                );

        Vec3 lookPosition =
                visibility.visible()
                        ? visibility.visiblePoint()
                        : Vec3.atCenterOf(
                        blockPos
                );

        rotateToward(
                minecraft,
                lookPosition
        );
    }

    public static boolean isLookingAtSelectedBlock() {

        return isLookingAtBlock(
                interactionTarget()
        );
    }

    public static boolean isLookingAtBlock(
            BlockPos blockPos
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || blockPos == null
        ) {
            return false;
        }

        if (
                !(
                        minecraft.hitResult
                                instanceof BlockHitResult blockHitResult
                )
        ) {
            return false;
        }

        return blockPos.equals(
                blockHitResult.getBlockPos()
        );
    }

    public static void requestWaitForTargetBlock(
            DAI_ActionDefinition action
    ) {

        BlockPos selected =
                interactionTarget();

        if (selected == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot wait for block alignment because no block is selected."
            );

            return;
        }

        faceBlock(
                selected
        );

        if (
                isLookingAtBlock(
                        selected
                )
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.SUCCESS
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Camera aligned with selected block {}.",
                    selected
            );

            return;
        }

        int checksRemaining =
                action.ticks() > 0
                        ? action.ticks()
                        : DEFAULT_ALIGNMENT_CHECKS;

        if (checksRemaining <= 1) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.TIMED_OUT
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Timed out aligning with selected block {}.",
                    selected
            );

            return;
        }

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        createDelayAction(),
                        createAlignmentWaitAction(
                                checksRemaining - 1
                        )
                )
        );
    }

    private static boolean isLookingAtTarget(
            Minecraft minecraft
    ) {

        if (
                target == null
                        || minecraft.hitResult == null
        ) {
            return false;
        }

        if (
                !(
                        minecraft.hitResult
                                instanceof BlockHitResult blockHitResult
                )
        ) {
            return false;
        }

        return target.equals(
                blockHitResult.getBlockPos()
        );
    }

    private static void finish(
            DAI_ActionResult result,
            String reason
    ) {

        BlockPos finishedTarget =
                target;

        /*
         * Only remember failures that mean the selected block itself is
         * currently unreachable.
         *
         * Timeouts, cancellation, missing player/level state, or the target
         * disappearing should not poison future target selection.
         */
        if (
                finishedTarget != null
                        && result == DAI_ActionResult.FAILURE
                        && isReachabilityFailure(
                        reason
                )
        ) {

            DAI_FailedTargetMemory.remember(
                    finishedTarget
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Temporarily blacklisted unreachable block target {}.",
                    finishedTarget
            );
        }

        /*
         * A successful approach proves the target is usable again and binds
         * the remainder of the interaction chain to this exact block.
         */
        if (
                finishedTarget != null
                        && result == DAI_ActionResult.SUCCESS
        ) {

            DAI_FailedTargetMemory.forget(
                    finishedTarget
            );

            completedTarget =
                    finishedTarget.immutable();

        } else {

            completedTarget =
                    null;
        }

        clearState();

        DAI_ActionStatus.set(
                result
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Finished approaching block {} with result={}: {}.",
                finishedTarget,
                result,
                reason
        );
    }

    private static boolean isReachabilityFailure(
            String reason
    ) {

        if (reason == null) {
            return false;
        }

        return reason.equals(
                "no reachable approach position"
        )
                || reason.equals(
                "target remains out of reach after path completion"
        )
                || reason.equals(
                "no alternate reachable interaction position"
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

        target =
                null;

        stopDistance =
                DEFAULT_STOP_DISTANCE;

        ticksRemaining =
                0;

        approachPosition =
                null;

        approachPath =
                null;

        pathIndex =
                0;

        fallbackObstruction =
                null;

        stuckCheckTicks =
                0;

        lastProgressPosition =
                null;

        debugLogTicks =
                0;

        resetRouteProgress();

        active =
                false;
    }

    /**
     * Smoothly aims the travel camera toward the active steering point while
     * maintaining a shallow exploration pitch.
     *
     * @return the yaw requested for this tick; movement projection must use
     *         this same value so local forward/strafe remains world-correct
     */
    private static float maintainTravelLook(
            Minecraft minecraft,
            Vec3 steeringPoint
    ) {

        if (
                minecraft.player == null
                        || steeringPoint == null
        ) {
            return 0.0F;
        }

        double deltaX =
                steeringPoint.x
                        - minecraft.player.getX();

        double deltaZ =
                steeringPoint.z
                        - minecraft.player.getZ();

        float currentYaw =
                minecraft.player.getYRot();

        float desiredYaw =
                currentYaw;

        if (
                Math.abs(
                        deltaX
                ) > 0.001D
                        || Math.abs(
                        deltaZ
                ) > 0.001D
        ) {

            desiredYaw =
                    (float) (
                            Math.toDegrees(
                                    Math.atan2(
                                            deltaZ,
                                            deltaX
                                    )
                            )
                                    - 90.0D
                    );
        }

        float yawDifference =
                Mth.wrapDegrees(
                        desiredYaw
                                - currentYaw
                );

        float yawStep =
                Mth.clamp(
                        yawDifference,
                        -MAX_TRAVEL_YAW_STEP,
                        MAX_TRAVEL_YAW_STEP
                );

        float finalYaw =
                Mth.wrapDegrees(
                        currentYaw
                                + yawStep
                );

        float currentPitch =
                minecraft.player.getXRot();

        float pitchDifference =
                TRAVEL_TARGET_PITCH
                        - currentPitch;

        float pitchStep =
                Mth.clamp(
                        pitchDifference,
                        -MAX_TRAVEL_PITCH_STEP,
                        MAX_TRAVEL_PITCH_STEP
                );

        float finalPitch =
                Mth.clamp(
                        currentPitch
                                + pitchStep,
                        -90.0F,
                        90.0F
                );

        DAI_InputState
                .look()
                .setRotation(
                        finalYaw,
                        finalPitch
                );

        return finalYaw;
    }

    private static MovementDebug moveToward(
            Minecraft minecraft,
            Vec3 targetPosition,
            float movementYaw
    ) {

        if (
                minecraft.player == null
                        || targetPosition == null
        ) {
            return null;
        }

        double deltaX =
                targetPosition.x
                        - minecraft.player.getX();

        double deltaZ =
                targetPosition.z
                        - minecraft.player.getZ();

        double length =
                Math.sqrt(
                        deltaX * deltaX
                                + deltaZ * deltaZ
                );

        if (length <= 0.001D) {

            DAI_InputState
                    .movement()
                    .setMovement(
                            0.0F,
                            0.0F
                    );

            return new MovementDebug(
                    deltaX,
                    deltaZ,
                    length,
                    0.0D,
                    0.0D,
                    movementYaw,
                    0.0F,
                    0.0F
            );
        }

        double worldX =
                deltaX / length;

        double worldZ =
                deltaZ / length;

        double yawRadians =
                Math.toRadians(
                        movementYaw
                );

        /*
         * Minecraft local movement axes:
         *
         * yaw   0 = +Z
         * yaw  90 = -X
         *
         * Positive DAI strafe maps to Minecraft's LEFT input. Therefore:
         *
         * forward = (-sin(yaw), cos(yaw))
         * left    = ( cos(yaw), sin(yaw))
         *
         * The previous implementation incorrectly treated this second basis
         * vector as "right" and negated its projection. That mirrored
         * sideways/world-space movement. At yaw ~= 90 degrees, requesting
         * travel toward +Z became almost full RIGHT input and physically
         * moved the player toward -Z.
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

        /*
         * The world-space direction is normalized, so the local components
         * already have unit magnitude. Clamp defensively for floating-point
         * noise.
         */
        forward =
                Mth.clamp(
                        forward,
                        -1.0F,
                        1.0F
                );

        strafe =
                Mth.clamp(
                        strafe,
                        -1.0F,
                        1.0F
                );

        DAI_InputState
                .movement()
                .setMovement(
                        forward,
                        strafe
                );

        return new MovementDebug(
                deltaX,
                deltaZ,
                length,
                worldX,
                worldZ,
                movementYaw,
                forward,
                strafe
        );
    }

    private static void tickDebugLogging(
            Minecraft minecraft,
            BlockPos immediateNode,
            BlockPos steeringNode,
            Vec3 steeringPoint,
            MovementDebug movement
    ) {

        if (
                minecraft.player == null
                        || movement == null
        ) {
            return;
        }

        debugLogTicks--;

        if (debugLogTicks > 0) {
            return;
        }

        debugLogTicks =
                DEBUG_LOG_INTERVAL;

        Vec3 position =
                minecraft.player.position();

        DAI_Core.LOGGER.info(
                "<DAI:APPROACH> pos=({},{},{}) yaw={} pitch={} target={} approach={} "
                        + "pathIndex={}/{} immediate={} steering={} steeringPoint=({},{},{}) "
                        + "delta=({},{}) worldDir=({},{}) movementYaw={} calculated=(forward={},strafe={}) "
                        + "input=(forward={},strafe={},jump={},sneak={},sprint={}) generation={} status={}",
                formatDebug(
                        position.x
                ),
                formatDebug(
                        position.y
                ),
                formatDebug(
                        position.z
                ),
                formatDebug(
                        minecraft.player.getYRot()
                ),
                formatDebug(
                        minecraft.player.getXRot()
                ),
                target,
                approachPosition,
                pathIndex,
                approachPath == null
                        ? 0
                        : approachPath.nodes().size(),
                immediateNode,
                steeringNode,
                formatDebug(
                        steeringPoint.x
                ),
                formatDebug(
                        steeringPoint.y
                ),
                formatDebug(
                        steeringPoint.z
                ),
                formatDebug(
                        movement.deltaX()
                ),
                formatDebug(
                        movement.deltaZ()
                ),
                formatDebug(
                        movement.worldX()
                ),
                formatDebug(
                        movement.worldZ()
                ),
                formatDebug(
                        movement.yaw()
                ),
                formatDebug(
                        movement.forward()
                ),
                formatDebug(
                        movement.strafe()
                ),
                formatDebug(
                        DAI_InputState.movement().forward()
                ),
                formatDebug(
                        DAI_InputState.movement().strafe()
                ),
                DAI_InputState.movement().jump(),
                DAI_InputState.movement().sneak(),
                DAI_InputState.movement().sprint(),
                generation,
                DAI_ActionStatus.get()
        );
    }

    private static String formatDebug(
            double value
    ) {

        return String.format(
                java.util.Locale.ROOT,
                "%.3f",
                value
        );
    }

    private record MovementDebug(
            double deltaX,
            double deltaZ,
            double length,
            double worldX,
            double worldZ,
            float yaw,
            float forward,
            float strafe
    ) {
    }

    private static int nextGeneration(
            int current
    ) {

        return current == Integer.MAX_VALUE
                ? 1
                : current + 1;
    }

    private static void resetRouteProgress() {

        lastProgressPathIndex =
                -1;

        lastSteeringDistance =
                Double.POSITIVE_INFINITY;

        routeStagnantChecks =
                0;
    }

    private static void rotateToward(
            Minecraft minecraft,
            Vec3 targetPosition
    ) {

        if (
                minecraft.player == null
                        || targetPosition == null
        ) {
            return;
        }

        Vec3 eyePosition =
                minecraft.player
                        .getEyePosition();

        double deltaX =
                targetPosition.x
                        - eyePosition.x;

        double deltaY =
                targetPosition.y
                        - eyePosition.y;

        double deltaZ =
                targetPosition.z
                        - eyePosition.z;

        double horizontal =
                Math.sqrt(
                        deltaX * deltaX
                                + deltaZ * deltaZ
                );

        float yaw =
                (float) (
                        Math.toDegrees(
                                Math.atan2(
                                        deltaZ,
                                        deltaX
                                )
                        )
                                - 90.0D
                );

        float pitch =
                (float) (
                        -Math.toDegrees(
                                Math.atan2(
                                        deltaY,
                                        horizontal
                                )
                        )
                );

        float finalYaw =
                Mth.wrapDegrees(
                        yaw
                );

        float finalPitch =
                Mth.clamp(
                        pitch,
                        -90.0F,
                        90.0F
                );

        /*
         * Approach requests rotation through DAI_InputState only.
         * DAI_LookController applies the physical player rotation later in
         * the same central client tick.
         */
        DAI_InputState
                .look()
                .setRotation(
                        finalYaw,
                        finalPitch
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

    private static DAI_ActionDefinition createDelayAction() {

        return new DAI_ActionDefinition(
                "delay",
                "",
                List.of(),
                List.of(),
                "",
                "",
                0.0F,
                0.0F,
                "",
                1,
                0,
                false,
                0.0D
        );
    }

    private static DAI_ActionDefinition createAlignmentWaitAction(
            int checksRemaining
    ) {

        return new DAI_ActionDefinition(
                "wait_for_target_block",
                "",
                List.of(),
                List.of(),
                "",
                "",
                0.0F,
                0.0F,
                "",
                Math.max(
                        0,
                        checksRemaining
                ),
                0,
                false,
                0.0D
        );
    }
}