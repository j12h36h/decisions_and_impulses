package io.github.j12h36h.dai.logics.controller;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.approach.*;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.core.DAI_RuntimeTelemetry;
import io.github.j12h36h.dai.logics.input.DAI_InputState;
import io.github.j12h36h.dai.menus.system.DAI_FailedTargetMemory;
import io.github.j12h36h.dai.menus.system.DAI_TargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class DAI_ApproachController {

    public static final double DEFAULT_STOPPING_DISTANCE =
            3.25D;

    private static final int DEFAULT_TIMEOUT_TICKS =
            200;

    private static final int STUCK_CHECK_INTERVAL =
            10;

    /*
     * Route-level stagnation already rebuilds a dead route after roughly
     * 1.5 seconds. This second detector deliberately survives those route
     * rebuilds and watches actual player displacement across the entire
     * normal approach attempt.
     *
     * If the player has not moved at least 0.20 blocks for two seconds while
     * a normal approach route is actively expected to move them, escalate to
     * approach recovery instead of waiting for the full action timeout.
     */
    private static final int APPROACH_STALL_TICKS =
            40;

    private static final double APPROACH_STALL_DISTANCE_SQUARED =
            0.04D;

    /*
     * Positional movement alone is not proof that an approach is succeeding:
     * a route can wander, oscillate, or repeatedly reposition while never
     * getting meaningfully closer to the target.
     *
     * Track the best eye-to-target distance across normal approach pathing.
     * If it fails to improve by at least 0.25 blocks for roughly three
     * seconds, escalate through the existing safe/destructive recovery chain.
     */
    private static final int TARGET_DISTANCE_STALL_TICKS =
            60;

    private static final double TARGET_DISTANCE_PROGRESS =
            0.25D;

    private static final double TARGET_DISTANCE_NEAR_MARGIN =
            0.75D;

    private static final int DEBUG_LOG_INTERVAL =
            20;

    private static Vec3 approachProgressAnchor;
    private static int approachNoProgressTicks;

    private static double bestTargetDistance =
            Double.POSITIVE_INFINITY;

    private static int targetDistanceNoProgressTicks;

    private DAI_ApproachController() {
        // Utility class.
    }

    /*
     * ------------------------------------------------------------
     * START
     * ------------------------------------------------------------
     */

    public static boolean start(
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

            return false;
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

            return false;
        }

        if (DAI_ApproachState.active()) {

            BlockPos activeTarget =
                    DAI_ApproachState.target();

            if (
                    blockPos.equals(
                            activeTarget
                    )
            ) {

                DAI_ActionStatus.set(
                        DAI_ActionResult.RUNNING
                );

                return true;
            }

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.debug(
                    "<DAI>: Rejected approach to {} because generation={} still owns target {}.",
                    blockPos,
                    DAI_ApproachState.generation(),
                    activeTarget
            );

            return false;
        }

        int generation =
                DAI_ApproachState.nextGeneration();

        DAI_ApproachState.setCompletedTarget(
                null
        );

        DAI_ApproachState.setTarget(
                blockPos
        );

        DAI_ApproachState.setStopDistance(
                requestedStopDistance > 0.0D
                        ? requestedStopDistance
                        : DEFAULT_STOPPING_DISTANCE
        );

        DAI_ApproachState.setTicksRemaining(
                timeoutTicks > 0
                        ? timeoutTicks
                        : DEFAULT_TIMEOUT_TICKS
        );

        DAI_ApproachState.clearPath();

        DAI_ApproachState.clearRecovery();

        DAI_ApproachState.clearFallback();

        DAI_ApproachState.resetAlignmentTicks();

        DAI_ApproachState.setStuckCheckTicks(
                STUCK_CHECK_INTERVAL
        );

        DAI_ApproachState.setLastProgressPosition(
                minecraft.player.position()
        );

        resetApproachProgressMonitor(
                minecraft
        );

        resetTargetDistanceProgressMonitor(
                minecraft,
                blockPos
        );

        DAI_ApproachState.setDebugLogTicks(
                DEBUG_LOG_INTERVAL
        );

        DAI_ApproachState.resetRouteProgress();

        DAI_ApproachState.setActive(
                true
        );

        DAI_InputState.setManagedOverride(
                true
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.debug(
                "<DAI>: Started approaching block {} with stopDistance={} timeout={} generation={}.",
                blockPos,
                DAI_ApproachState.stopDistance(),
                DAI_ApproachState.ticksRemaining(),
                generation
        );

        DAI_RuntimeTelemetry.approachStart(
                blockPos,
                generation
        );

        return true;
    }

    public static boolean startSelectedBlock(
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

            return false;
        }

        return start(
                selected,
                requestedStopDistance,
                timeoutTicks
        );
    }

    /*
     * ------------------------------------------------------------
     * TICK
     * ------------------------------------------------------------
     */

    public static void tick() {

        if (!DAI_ApproachState.active()) {
            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        BlockPos target =
                DAI_ApproachState.target();

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

        if (!DAI_ApproachState.consumeTick()) {

            finish(
                    DAI_ActionResult.TIMED_OUT,
                    "approach timed out"
            );

            return;
        }

        /*
         * Detect whole-approach positional stagnation before a dead route can
         * repeatedly rebuild itself until the full timeout expires.
         *
         * Only normal target routes are monitored here. Safe-recovery routes,
         * obstruction breaking, alignment, and other intentional stationary
         * states retain their existing dedicated lifecycle/recovery rules.
         */
        if (tickApproachProgressMonitor(minecraft)) {

            DAI_Core.LOGGER.info(
                    "<DAI>: Approach made no positional progress for {} tick(s); escalating recovery for target {}.",
                    APPROACH_STALL_TICKS,
                    target
            );

            DAI_ApproachState.clearPath();

            resetApproachProgressMonitor(
                    minecraft
            );

            resetTargetDistanceProgressMonitor(
                    minecraft,
                    target
            );

            handlePathFailure(
                    minecraft,
                    "approach stalled with no positional progress"
            );

            return;
        }

        /*
         * A second progress monitor catches the opposite failure mode:
         * movement continues, but distance to the actual target does not
         * meaningfully improve.
         */
        if (
                tickTargetDistanceProgressMonitor(
                        minecraft,
                        target
                )
        ) {

            DAI_Core.LOGGER.info(
                    "<DAI>: Approach failed to reduce target distance for {} tick(s); escalating recovery for target {}.",
                    TARGET_DISTANCE_STALL_TICKS,
                    target
            );

            DAI_ApproachState.clearPath();

            resetApproachProgressMonitor(
                    minecraft
            );

            resetTargetDistanceProgressMonitor(
                    minecraft,
                    target
            );

            handlePathFailure(
                    minecraft,
                    "approach stalled without reducing target distance"
            );

            return;
        }

        /*
         * Breaking temporarily owns approach movement.
         */
        if (DAI_BreakController.isActive()) {

            DAI_ApproachState.resetAlignmentTicks();

            DAI_ApproachRecovery.waitForBreak(
                    minecraft
            );

            return;
        }

        /*
         * --------------------------------------------------------
         * EXISTING PATH OWNS MOVEMENT
         * --------------------------------------------------------
         *
         * This includes both normal target routes and temporary safe
         * recovery routes.
         */
        if (
                DAI_ApproachState.approachPath() != null
                        && DAI_ApproachState.approachPosition() != null
        ) {

            DAI_ApproachState.resetAlignmentTicks();

            if (
                    DAI_ApproachPathing.follow(
                            minecraft
                    )
            ) {
                return;
            }

            /*
             * A completed safe-recovery path is NOT a completed target
             * approach.
             *
             * Release recovery ownership and immediately retry normal
             * target path construction from the player's new position.
             */
            if (DAI_ApproachState.recoveryActive()) {

                DAI_ApproachRecovery.completeSafeRecovery(
                        minecraft
                );

                tickPathing(
                        minecraft
                );

                return;
            }

            /*
             * Ordinary target/reposition path completed.
             */
            DAI_ApproachState.clearPath();

            tickCurrentPosition(
                    minecraft
            );

            return;
        }

        /*
         * Recovery state should never survive without its route.
         *
         * If this happens because a recovery route was abandoned as
         * stagnant, allow normal planning/recovery selection to continue.
         */
        if (DAI_ApproachState.recoveryActive()) {

            DAI_ApproachState.clearRecovery();
        }

        Vec3 targetCenter =
                Vec3.atCenterOf(
                        target
                );

        double distance =
                minecraft.player
                        .getEyePosition()
                        .distanceTo(
                                targetCenter
                        );

        if (
                distance
                        > DAI_ApproachState.stopDistance()
        ) {

            DAI_ApproachState.resetAlignmentTicks();

            tickPathing(
                    minecraft
            );

            return;
        }

        DAI_ApproachTargeting.ApproachTargetResult targetingResult =
                DAI_ApproachTargeting.tick(
                        minecraft
                );

        switch (targetingResult) {

            case SUCCESS -> {

                finish(
                        DAI_ActionResult.SUCCESS,
                        "target reached and visible"
                );

                return;
            }

            case WAITING_FOR_ALIGNMENT,
                 OBSTRUCTION_HANDLED -> {

                return;
            }

            case REPOSITION -> {

                DAI_ApproachState.clearPath();

                tickReposition(
                        minecraft
                );

                return;
            }

            case NOT_HANDLED -> {

                finish(
                        DAI_ActionResult.FAILURE,
                        "target could not be evaluated"
                );
            }
        }
    }

    /*
     * ------------------------------------------------------------
     * APPROACH-LEVEL PROGRESS
     * ------------------------------------------------------------
     */

    /**
     * Returns true only when a normal installed route has expected movement
     * but the player's actual position has remained effectively unchanged for
     * the full approach-level stall window.
     */
    private static boolean tickApproachProgressMonitor(
            Minecraft minecraft
    ) {

        if (
                minecraft.player == null
                        || DAI_BreakController.isActive()
                        || DAI_ApproachState.recoveryActive()
                        || DAI_ApproachState.approachPath() == null
                        || DAI_ApproachState.approachPosition() == null
        ) {

            resetApproachProgressMonitor(
                    minecraft
            );

            return false;
        }

        Vec3 currentPosition =
                minecraft.player.position();

        if (approachProgressAnchor == null) {

            approachProgressAnchor =
                    currentPosition;

            approachNoProgressTicks =
                    0;

            return false;
        }

        if (
                currentPosition.distanceToSqr(
                        approachProgressAnchor
                ) >= APPROACH_STALL_DISTANCE_SQUARED
        ) {

            approachProgressAnchor =
                    currentPosition;

            approachNoProgressTicks =
                    0;

            return false;
        }

        approachNoProgressTicks++;

        return approachNoProgressTicks
                >= APPROACH_STALL_TICKS;
    }

    private static void resetApproachProgressMonitor(
            Minecraft minecraft
    ) {

        approachProgressAnchor =
                minecraft == null
                        || minecraft.player == null
                        ? null
                        : minecraft.player.position();

        approachNoProgressTicks =
                0;
    }

    /**
     * Returns true when normal approach execution is active but the best
     * observed target distance has not improved meaningfully for the full
     * target-progress window.
     *
     * This deliberately survives ordinary route rebuilds. Safe recovery and
     * breaking own their own movement semantics and reset this monitor.
     */
    private static boolean tickTargetDistanceProgressMonitor(
            Minecraft minecraft,
            BlockPos target
    ) {

        if (
                minecraft == null
                        || minecraft.player == null
                        || target == null
                        || DAI_BreakController.isActive()
                        || DAI_ApproachState.recoveryActive()
        ) {

            resetTargetDistanceProgressMonitor(
                    minecraft,
                    target
            );

            return false;
        }

        double currentDistance =
                minecraft.player
                        .getEyePosition()
                        .distanceTo(
                                Vec3.atCenterOf(
                                        target
                                )
                        );

        /*
         * Once close enough for targeting/alignment work, distance is no
         * longer the useful success metric. Do not penalize legitimate camera
         * alignment or obstruction handling inside this margin.
         */
        if (
                currentDistance
                        <= DAI_ApproachState.stopDistance()
                        + TARGET_DISTANCE_NEAR_MARGIN
        ) {

            bestTargetDistance =
                    currentDistance;

            targetDistanceNoProgressTicks =
                    0;

            return false;
        }

        if (Double.isInfinite(bestTargetDistance)) {

            bestTargetDistance =
                    currentDistance;

            targetDistanceNoProgressTicks =
                    0;

            return false;
        }

        if (
                bestTargetDistance
                        - currentDistance
                        >= TARGET_DISTANCE_PROGRESS
        ) {

            bestTargetDistance =
                    currentDistance;

            targetDistanceNoProgressTicks =
                    0;

            return false;
        }

        targetDistanceNoProgressTicks++;

        return targetDistanceNoProgressTicks
                >= TARGET_DISTANCE_STALL_TICKS;
    }

    private static void resetTargetDistanceProgressMonitor(
            Minecraft minecraft,
            BlockPos target
    ) {

        if (
                minecraft == null
                        || minecraft.player == null
                        || target == null
        ) {

            bestTargetDistance =
                    Double.POSITIVE_INFINITY;

        } else {

            bestTargetDistance =
                    minecraft.player
                            .getEyePosition()
                            .distanceTo(
                                    Vec3.atCenterOf(
                                            target
                                    )
                            );
        }

        targetDistanceNoProgressTicks =
                0;
    }

    /*
     * ------------------------------------------------------------
     * PATHING
     * ------------------------------------------------------------
     */

    private static void tickPathing(
            Minecraft minecraft
    ) {

        if (
                DAI_ApproachState.approachPath() == null
                        || DAI_ApproachState.approachPosition() == null
        ) {

            DAI_ApproachPathing.PathBuildResult buildResult =
                    DAI_ApproachPathing.rebuild(
                            minecraft
                    );

            if (
                    buildResult
                            == DAI_ApproachPathing.PathBuildResult.FAILURE
            ) {

                handlePathFailure(
                        minecraft,
                        "no reachable approach position"
                );

                return;
            }

            if (
                    buildResult
                            == DAI_ApproachPathing.PathBuildResult.ALREADY_AT_DESTINATION
            ) {

                tickCurrentPosition(
                        minecraft
                );

                return;
            }
        }

        if (
                DAI_ApproachPathing.follow(
                        minecraft
                )
        ) {
            return;
        }

        /*
         * Safe recovery completed while tickPathing() owned the route.
         */
        if (DAI_ApproachState.recoveryActive()) {

            DAI_ApproachRecovery.completeSafeRecovery(
                    minecraft
            );

            /*
             * Do not recursively call tickPathing() here.
             *
             * The next client tick will perform a fresh normal approach
             * build from the recovered position.
             */
            return;
        }

        DAI_ApproachState.clearPath();

        tickCurrentPosition(
                minecraft
        );
    }

    private static void tickCurrentPosition(
            Minecraft minecraft
    ) {

        DAI_ApproachTargeting.ApproachTargetResult result =
                DAI_ApproachTargeting.tick(
                        minecraft
                );

        switch (result) {

            case SUCCESS ->
                    finish(
                            DAI_ActionResult.SUCCESS,
                            "target reached and visible"
                    );

            case WAITING_FOR_ALIGNMENT,
                 OBSTRUCTION_HANDLED -> {
                // Targeting owns this tick.
            }

            case REPOSITION -> {

                DAI_ApproachState.clearPath();

                tickReposition(
                        minecraft
                );
            }

            case NOT_HANDLED ->
                    finish(
                            DAI_ActionResult.FAILURE,
                            "target could not be evaluated after path completion"
                    );
        }
    }

    private static void tickReposition(
            Minecraft minecraft
    ) {

        DAI_ApproachPathing.PathBuildResult result =
                DAI_ApproachPathing.rebuild(
                        minecraft
                );

        if (
                result
                        == DAI_ApproachPathing.PathBuildResult.PATH_READY
        ) {

            DAI_Core.debug(
                    "<DAI>: Repositioning for block target {} using {} path node(s).",
                    DAI_ApproachState.target(),
                    DAI_ApproachState.approachPath() == null
                            ? 0
                            : DAI_ApproachState.approachPath().nodes().size()
            );

            return;
        }

        if (
                result
                        == DAI_ApproachPathing.PathBuildResult.FAILURE
        ) {

            handlePathFailure(
                    minecraft,
                    "no alternate reachable interaction position"
            );

            return;
        }

        finish(
                DAI_ActionResult.FAILURE,
                "current approach position cannot interact with target"
        );
    }

    /*
     * ------------------------------------------------------------
     * RECOVERY
     * ------------------------------------------------------------
     */

    private static void handlePathFailure(
            Minecraft minecraft,
            String reason
    ) {

        /*
         * First attempt a completely non-destructive recovery.
         *
         * Examples:
         *
         * - walk toward the edge of a canopy
         * - descend through a safe route
         * - reposition onto lower reachable terrain
         */
        if (
                DAI_ApproachRecovery.trySafeRecovery(
                        minecraft
                )
        ) {
            return;
        }

        /*
         * Only after safe recovery fails may DAI consider clearing a
         * specifically selected obstruction.
         */
        if (
                DAI_ApproachRecovery.tryDestructiveFallback(
                        minecraft
                )
        ) {
            return;
        }

        finish(
                DAI_ActionResult.FAILURE,
                reason
        );
    }

    /*
     * ------------------------------------------------------------
     * STOP / RESET
     * ------------------------------------------------------------
     */

    public static void stop() {

        if (!DAI_ApproachState.active()) {
            return;
        }

        finish(
                DAI_ActionResult.CANCELLED,
                "stopped manually"
        );
    }

    public static void reset() {

        if (!DAI_ApproachState.active()) {

            clearState();

            return;
        }

        int generation =
                DAI_ApproachState.generation();

        DAI_ApproachState.rememberResult(
                generation,
                DAI_ActionResult.CANCELLED
        );

        DAI_ApproachState.setCompletedTarget(
                null
        );

        clearState();

        DAI_ActionStatus.set(
                DAI_ActionResult.CANCELLED
        );

        DAI_Core.debug(
                "<DAI>: Active block approach generation={} cancelled during reset.",
                generation
        );
    }

    /**
     * Discards all block-approach target ownership without changing the
     * current semantic action result.
     *
     * This is used by target-selection/clear actions. A target_clear must
     * stop movement toward the old target, and a newly selected waypoint
     * must not be shadowed by completedTarget from the previous approach.
     */
    public static void discardTargetOwnership() {

        boolean wasActive =
                DAI_ApproachState.active();

        int generation =
                DAI_ApproachState.generation();

        if (wasActive) {

            DAI_ApproachState.rememberResult(
                    generation,
                    DAI_ActionResult.CANCELLED
            );
        }

        DAI_ApproachState.setCompletedTarget(
                null
        );

        clearState();

        if (wasActive) {

            DAI_Core.debug(
                    "<DAI>: Discarded stale block-approach ownership for generation={}.",
                    generation
            );
        }
    }

    /*
     * ------------------------------------------------------------
     * PUBLIC STATE
     * ------------------------------------------------------------
     */

    public static boolean isActive() {

        return DAI_ApproachState.active();
    }

    public static int generation() {

        return DAI_ApproachState.generation();
    }

    public static BlockPos target() {

        return DAI_ApproachState.target();
    }

    public static DAI_ActionResult resultForGeneration(
            int generation
    ) {

        return DAI_ApproachState.resultForGeneration(
                generation
        );
    }

    /*
     * ------------------------------------------------------------
     * TARGETING FACADE
     * ------------------------------------------------------------
     */

    public static BlockPos interactionTarget() {

        return DAI_ApproachTargeting.interactionTarget();
    }

    public static void faceSelectedBlock() {

        DAI_ApproachTargeting.faceSelectedBlock();
    }

    public static void faceBlock(
            BlockPos blockPos
    ) {

        DAI_ApproachTargeting.faceBlock(
                blockPos
        );
    }

    public static boolean isLookingAtSelectedBlock() {

        return DAI_ApproachTargeting.isLookingAtSelectedBlock();
    }

    public static boolean isLookingAtBlock(
            BlockPos blockPos
    ) {

        return DAI_ApproachTargeting.isLookingAtBlock(
                blockPos
        );
    }

    public static void requestWaitForTargetBlock(
            DAI_ActionDefinition action
    ) {

        DAI_ApproachTargeting.requestWaitForTargetBlock(
                action
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

        BlockPos finishedTarget =
                DAI_ApproachState.target();

        int finishedGeneration =
                DAI_ApproachState.generation();

        DAI_ApproachState.rememberResult(
                finishedGeneration,
                result
        );

        if (
                finishedTarget != null
                        && result == DAI_ActionResult.SUCCESS
        ) {

            DAI_FailedTargetMemory.forget(
                    finishedTarget
            );

            DAI_ApproachState.setCompletedTarget(
                    finishedTarget
            );

        } else {

            DAI_ApproachState.setCompletedTarget(
                    null
            );
        }

        if (
                finishedTarget != null
                        && (
                        result == DAI_ActionResult.FAILURE
                                || result == DAI_ActionResult.TIMED_OUT
                )
                        && shouldBlacklist(
                        reason
                )
        ) {

            DAI_FailedTargetMemory.remember(
                    finishedTarget
            );

            DAI_Core.debug(
                    "<DAI>: Temporarily blacklisted unusable block target {}.",
                    finishedTarget
            );
        }

        DAI_RuntimeTelemetry.approachFinish(
                finishedTarget,
                finishedGeneration,
                result,
                reason
        );

        clearState();

        DAI_ActionStatus.set(
                result
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Finished approaching block {} with result={}: {}, generation={}.",
                finishedTarget,
                result,
                reason,
                finishedGeneration
        );
    }

    private static boolean shouldBlacklist(
            String reason
    ) {

        if (reason == null) {
            return false;
        }

        return reason.equals(
                "no reachable approach position"
        )
                || reason.equals(
                "no alternate reachable interaction position"
        )
                || reason.equals(
                "current approach position cannot interact with target"
        )
                || reason.equals(
                "target could not be evaluated after path completion"
        )
                || reason.equals(
                "approach stalled with no positional progress"
        )
                || reason.equals(
                "approach stalled without reducing target distance"
        )
                || reason.equals(
                "approach timed out"
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

        approachProgressAnchor =
                null;

        approachNoProgressTicks =
                0;

        bestTargetDistance =
                Double.POSITIVE_INFINITY;

        targetDistanceNoProgressTicks =
                0;

        DAI_ApproachObstruction.reset();

        DAI_ApproachState.clearActiveState();
    }
}