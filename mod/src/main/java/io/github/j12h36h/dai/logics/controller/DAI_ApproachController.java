package io.github.j12h36h.dai.logics.controller;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.approach.DAI_ApproachPathing;
import io.github.j12h36h.dai.logics.approach.DAI_ApproachRecovery;
import io.github.j12h36h.dai.logics.approach.DAI_ApproachState;
import io.github.j12h36h.dai.logics.approach.DAI_ApproachTargeting;
import io.github.j12h36h.dai.logics.core.DAI_Core;
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

    private static final int DEBUG_LOG_INTERVAL =
            20;

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

            DAI_Core.LOGGER.debug(
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

        DAI_Core.LOGGER.debug(
                "<DAI>: Started approaching block {} with stopDistance={} timeout={} generation={}.",
                blockPos,
                DAI_ApproachState.stopDistance(),
                DAI_ApproachState.ticksRemaining(),
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

            DAI_Core.LOGGER.debug(
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

        DAI_Core.LOGGER.debug(
                "<DAI>: Active block approach generation={} cancelled during reset.",
                generation
        );
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
                        && result == DAI_ActionResult.FAILURE
                        && shouldBlacklist(
                        reason
                )
        ) {

            DAI_FailedTargetMemory.remember(
                    finishedTarget
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Temporarily blacklisted unusable block target {}.",
                    finishedTarget
            );
        }

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

        DAI_ApproachState.clearActiveState();
    }
}