package io.github.j12h36h.dai.logics.approach;

import io.github.j12h36h.dai.logics.controller.DAI_BreakController;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.input.DAI_InputState;
import io.github.j12h36h.dai.logics.navigation.DAI_Path;
import io.github.j12h36h.dai.logics.navigation.DAI_PathFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class DAI_ApproachRecovery {

    private static final int STUCK_CHECK_INTERVAL =
            10;

    private static final double MINIMUM_ROUTE_PROGRESS =
            0.12D;

    private static final int MAX_ROUTE_STAGNANT_CHECKS =
            3;

    private static final double MAX_FALLBACK_BREAK_DISTANCE =
            4.5D;

    /*
     * Safe recovery is a staging mechanism, not an alternate navigation mode.
     * Four distinct staging routes are enough to escape a canopy/ledge while
     * preventing an unreachable underground target from consuming the whole
     * action timeout by oscillating between nearby positions.
     */
    private static final int MAX_SAFE_RECOVERY_ATTEMPTS =
            4;

    private DAI_ApproachRecovery() {
        // Utility class.
    }

    /*
     * ------------------------------------------------------------
     * ROUTE PROGRESS
     * ------------------------------------------------------------
     */

    /**
     * Tracks forward progress along the currently installed route.
     *
     * This works for both:
     *
     * - normal target approach routes
     * - temporary safe recovery routes
     */
    public static void handleMovement(
            Minecraft minecraft,
            Vec3 steeringTarget
    ) {

        if (
                minecraft.player == null
                        || steeringTarget == null
        ) {
            return;
        }

        if (
                DAI_ApproachState.decrementStuckCheckTicks()
                        > 0
        ) {
            return;
        }

        DAI_ApproachState.setStuckCheckTicks(
                STUCK_CHECK_INTERVAL
        );

        double steeringDistance =
                DAI_ApproachMovement.horizontalDistance(
                        minecraft.player.position(),
                        steeringTarget
                );

        /*
         * Reaching another route node is always useful progress.
         */
        if (
                DAI_ApproachState.lastProgressPathIndex()
                        != DAI_ApproachState.pathIndex()
        ) {

            DAI_ApproachState.setLastProgressPathIndex(
                    DAI_ApproachState.pathIndex()
            );

            DAI_ApproachState.setLastSteeringDistance(
                    steeringDistance
            );

            DAI_ApproachState.setRouteStagnantChecks(
                    0
            );

            return;
        }

        double improvement =
                DAI_ApproachState.lastSteeringDistance()
                        - steeringDistance;

        DAI_ApproachState.setLastSteeringDistance(
                steeringDistance
        );

        if (
                improvement
                        >= MINIMUM_ROUTE_PROGRESS
        ) {

            DAI_ApproachState.setRouteStagnantChecks(
                    0
            );

            return;
        }

        int stagnantChecks =
                DAI_ApproachState.incrementRouteStagnantChecks();

        if (
                stagnantChecks
                        < MAX_ROUTE_STAGNANT_CHECKS
        ) {
            return;
        }

        if (DAI_BreakController.isActive()) {
            return;
        }

        /*
         * A recovery route that becomes stuck should not automatically
         * rebuild as though it were a normal approach route.
         *
         * Abandon that staging route and allow recovery selection to run
         * again from the player's actual position.
         */
        if (DAI_ApproachState.recoveryActive()) {

            DAI_Core.LOGGER.info(
                    "<DAI>: Safe recovery route made no forward progress; abandoning recovery position {}.",
                    DAI_ApproachState.recoveryPosition()
            );

            DAI_ApproachPathing.clear();

            DAI_ApproachState.clearRecovery();

            DAI_ApproachState.resetRouteProgress();

            return;
        }

        DAI_Core.LOGGER.info(
                "<DAI>: Approach route made no forward progress; rebuilding route from current position."
        );

        DAI_ApproachPathing.clear();

        DAI_ApproachState.resetRouteProgress();

        DAI_ApproachPathing.rebuild(
                minecraft
        );
    }

    /*
     * ------------------------------------------------------------
     * SAFE RECOVERY
     * ------------------------------------------------------------
     */

    /**
     * Attempts to install a non-destructive staging route.
     *
     * Safe recovery is used when DAI cannot currently find a final
     * interaction position near the target.
     *
     * It does NOT break blocks.
     *
     * Instead:
     *
     * current position
     *      ↓
     * reachable recovery position
     *      ↓
     * follow normal path machinery
     *      ↓
     * retry target approach
     */
    public static boolean trySafeRecovery(
            Minecraft minecraft
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || DAI_ApproachState.target() == null
        ) {
            return false;
        }

        if (
                DAI_ApproachState.safeRecoveryAttempts()
                        >= MAX_SAFE_RECOVERY_ATTEMPTS
        ) {

            DAI_Core.debug(
                    "<DAI>: Safe recovery budget exhausted for target {} after {} staging route(s).",
                    DAI_ApproachState.target(),
                    DAI_ApproachState.safeRecoveryAttempts()
            );

            return false;
        }

        /*
         * A recovery route already owns movement.
         *
         * The controller/path follower will continue processing it.
         */
        if (
                DAI_ApproachState.recoveryActive()
                        && DAI_ApproachState.approachPath() != null
                        && DAI_ApproachState.approachPosition() != null
        ) {
            return true;
        }

        /*
         * Stale recovery state without a route should not survive.
         */
        if (DAI_ApproachState.recoveryActive()) {

            DAI_ApproachState.clearRecovery();
        }

        BlockPos origin =
                minecraft.player.blockPosition();

        BlockPos target =
                DAI_ApproachState.target();

        BlockPos recoveryPosition =
                DAI_PathFinder.findRecoveryPosition(
                        minecraft.level,
                        origin,
                        target
                );

        if (recoveryPosition == null) {
            return false;
        }

        DAI_Path recoveryPath =
                DAI_PathFinder.find(
                        minecraft.level,
                        origin,
                        recoveryPosition
                );

        if (
                recoveryPath == null
                        || recoveryPath.nodes().size() <= 1
        ) {

            DAI_ApproachState.clearRecovery();

            return false;
        }

        /*
         * A safe recovery and destructive fallback must never own the
         * approach simultaneously.
         */
        DAI_ApproachState.clearFallback();

        DAI_ApproachState.incrementSafeRecoveryAttempts();

        DAI_ApproachState.setRecoveryPosition(
                recoveryPosition
        );

        DAI_ApproachState.setRecoveryActive(
                true
        );

        /*
         * Reuse the ordinary path-following machinery.
         *
         * recoveryActive tells the controller that completion of this route
         * means "retry normal target pathing", not "evaluate the target from
         * here as though this were the final approach position".
         */
        DAI_ApproachState.setApproachPosition(
                recoveryPosition
        );

        DAI_ApproachState.setApproachPath(
                recoveryPath
        );

        DAI_ApproachState.setPathIndex(
                recoveryPath.nodes().size() > 1
                        ? 1
                        : recoveryPath.nodes().size()
        );

        DAI_ApproachState.resetRouteProgress();

        DAI_ApproachState.setStuckCheckTicks(
                STUCK_CHECK_INTERVAL
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Normal target approach unavailable; safe recovery route {}/{} selected from {} to {} for target {} ({} node(s)).",
                DAI_ApproachState.safeRecoveryAttempts(),
                MAX_SAFE_RECOVERY_ATTEMPTS,
                origin,
                recoveryPosition,
                target,
                recoveryPath.nodes().size()
        );

        return true;
    }

    /**
     * Called when the currently installed recovery route has completed.
     *
     * Recovery ownership is removed so normal target approach planning can
     * run again from the player's new position.
     */
    public static void completeSafeRecovery(
            Minecraft minecraft
    ) {

        if (!DAI_ApproachState.recoveryActive()) {
            return;
        }

        BlockPos recoveryPosition =
                DAI_ApproachState.recoveryPosition();

        BlockPos currentPosition =
                minecraft.player == null
                        ? null
                        : minecraft.player.blockPosition();

        DAI_Core.LOGGER.info(
                "<DAI>: Safe recovery route completed at {} (planned={}); retrying normal approach to target {}.",
                currentPosition,
                recoveryPosition,
                DAI_ApproachState.target()
        );

        /*
         * Clear the temporary route first.
         */
        DAI_ApproachPathing.clear();

        DAI_ApproachState.clearRecovery();

        DAI_ApproachState.resetRouteProgress();

        DAI_ApproachState.setStuckCheckTicks(
                STUCK_CHECK_INTERVAL
        );
    }

    /**
     * Explicitly abandons safe recovery.
     */
    public static void clearSafeRecovery() {

        if (!DAI_ApproachState.recoveryActive()) {
            return;
        }

        DAI_ApproachPathing.clear();

        DAI_ApproachState.clearRecovery();

        DAI_ApproachState.resetRouteProgress();
    }

    /*
     * ------------------------------------------------------------
     * STUCK RESET
     * ------------------------------------------------------------
     */

    /**
     * Resets route-stagnation detection whenever movement is intentionally
     * stopped, such as during target alignment or obstruction breaking.
     */
    public static void resetStuckDetection(
            Minecraft minecraft
    ) {

        DAI_ApproachState.setStuckCheckTicks(
                STUCK_CHECK_INTERVAL
        );

        DAI_InputState
                .movement()
                .setJump(
                        false
                );

        DAI_ApproachState.setLastProgressPosition(
                minecraft.player == null
                        ? null
                        : minecraft.player.position()
        );

        DAI_ApproachState.resetRouteProgress();
    }

    /*
     * ------------------------------------------------------------
     * DESTRUCTIVE FALLBACK
     * ------------------------------------------------------------
     */

    /**
     * Handles destructive recovery only after normal pathing and safe
     * non-destructive recovery have failed.
     *
     * Once an obstruction is selected, recovery keeps ownership of that
     * exact block until it has been destroyed or the fallback is abandoned.
     */
    public static boolean tryDestructiveFallback(
            Minecraft minecraft
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || DAI_ApproachState.target() == null
        ) {
            return false;
        }

        /*
         * Destructive fallback may not begin while a safe recovery route is
         * active.
         */
        if (DAI_ApproachState.recoveryActive()) {
            return true;
        }

        BlockPos fallbackObstruction =
                DAI_ApproachState.fallbackObstruction();

        /*
         * A previously selected fallback obstruction has been destroyed.
         *
         * Release fallback ownership and attempt normal path construction
         * again from the player's current position.
         */
        if (
                DAI_ApproachState.fallbackActive()
                        && fallbackObstruction != null
                        && minecraft.level
                        .getBlockState(
                                fallbackObstruction
                        )
                        .isAir()
        ) {

            DAI_Core.LOGGER.info(
                    "<DAI>: Destructive-fallback obstruction {} was cleared; rebuilding approach path.",
                    fallbackObstruction
            );

            DAI_ApproachState.clearFallback();

            return DAI_ApproachPathing.rebuild(
                    minecraft
            ) != DAI_ApproachPathing.PathBuildResult.FAILURE;
        }

        /*
         * Discover a fallback obstruction only once per recovery attempt.
         */
        if (
                !DAI_ApproachState.fallbackActive()
                        || fallbackObstruction == null
        ) {

            BlockPos selectedObstruction =
                    DAI_PathFinder.findApproachObstruction(
                            minecraft.level,
                            minecraft.player.blockPosition(),
                            DAI_ApproachState.target(),
                            DAI_ApproachState.stopDistance()
                    );

            if (selectedObstruction == null) {

                DAI_ApproachState.clearFallback();

                return false;
            }

            DAI_ApproachState.setFallbackObstruction(
                    selectedObstruction
            );

            DAI_ApproachState.setFallbackActive(
                    true
            );

            fallbackObstruction =
                    DAI_ApproachState.fallbackObstruction();

            DAI_Core.LOGGER.info(
                    "<DAI>: Normal and safe recovery failed; destructive fallback selected obstruction {} for target {}.",
                    fallbackObstruction,
                    DAI_ApproachState.target()
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
         * Never freeze navigation while staring at a block that cannot
         * actually be reached for normal breaking.
         */
        if (
                obstructionDistance
                        > MAX_FALLBACK_BREAK_DISTANCE
        ) {

            DAI_Core.debug(
                    "<DAI>: Destructive-fallback obstruction {} is too far away (distance={}); abandoning fallback.",
                    fallbackObstruction,
                    String.format(
                            java.util.Locale.ROOT,
                            "%.2f",
                            obstructionDistance
                    )
            );

            DAI_ApproachState.clearFallback();

            return false;
        }

        /*
         * Destructive fallback owns both look and movement until the
         * selected obstruction is cleared.
         */
        DAI_ApproachTargeting.rotateToward(
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

        resetStuckDetection(
                minecraft
        );

        if (DAI_BreakController.isActive()) {
            return true;
        }

        /*
         * Rotation and Minecraft's hit result may be one tick apart.
         */
        if (
                !(
                        minecraft.hitResult
                                instanceof BlockHitResult blockHitResult
                )
        ) {
            return true;
        }

        if (
                !fallbackObstruction.equals(
                        blockHitResult.getBlockPos()
                )
        ) {
            return true;
        }

        DAI_Core.LOGGER.info(
                "<DAI>: Clearing destructive-fallback obstruction {}.",
                fallbackObstruction
        );

        DAI_BreakController.breakOnce();

        return true;
    }

    /*
     * ------------------------------------------------------------
     * BREAK WAIT
     * ------------------------------------------------------------
     */

    /**
     * Used while another controller owns movement, most commonly an active
     * block break.
     */
    public static void waitForBreak(
            Minecraft minecraft
    ) {

        DAI_InputState
                .movement()
                .setMovement(
                        0.0F,
                        0.0F
                );

        resetStuckDetection(
                minecraft
        );
    }
}