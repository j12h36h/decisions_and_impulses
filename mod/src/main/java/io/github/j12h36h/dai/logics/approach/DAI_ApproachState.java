package io.github.j12h36h.dai.logics.approach;

import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.navigation.DAI_Path;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DAI_ApproachState {

    private static final double DEFAULT_STOP_DISTANCE =
            3.25D;

    private static final int MAX_RESULT_HISTORY =
            32;

    private static final Map<Integer, DAI_ActionResult> RESULT_HISTORY =
            new LinkedHashMap<>();

    private static BlockPos target;

    private static BlockPos completedTarget;

    private static double stopDistance =
            DEFAULT_STOP_DISTANCE;

    private static int ticksRemaining;

    private static boolean active;

    private static int generation;

    private static int alignmentTicks;

    /*
     * ------------------------------------------------------------
     * NORMAL APPROACH PATH
     * ------------------------------------------------------------
     */

    private static BlockPos approachPosition;

    private static DAI_Path approachPath;

    private static int pathIndex;

    /*
     * ------------------------------------------------------------
     * RECOVERY ROUTE
     * ------------------------------------------------------------
     *
     * Recovery routes are intentionally separate from destructive
     * fallback state.
     *
     * A recovery route means:
     *
     * "I cannot currently build a normal interaction path, but I can
     * safely move somewhere that may allow normal pathing to resume."
     *
     * Examples:
     *
     * - descend from a canopy
     * - move toward a safe ledge
     * - leave an awkward elevated position
     *
     * No block breaking is implied by this state.
     */

    private static BlockPos recoveryPosition;

    private static boolean recoveryActive;

    /*
     * ------------------------------------------------------------
     * DESTRUCTIVE FALLBACK
     * ------------------------------------------------------------
     */

    private static BlockPos fallbackObstruction;

    private static boolean fallbackActive;

    /*
     * ------------------------------------------------------------
     * ROUTE PROGRESS
     * ------------------------------------------------------------
     */

    private static int stuckCheckTicks;

    private static Vec3 lastProgressPosition;

    private static int lastProgressPathIndex =
            -1;

    private static double lastSteeringDistance =
            Double.POSITIVE_INFINITY;

    private static int routeStagnantChecks;

    private static int debugLogTicks;

    private DAI_ApproachState() {
        // Utility class.
    }

    /*
     * ------------------------------------------------------------
     * TARGET
     * ------------------------------------------------------------
     */

    public static BlockPos target() {
        return target;
    }

    public static void setTarget(
            BlockPos blockPos
    ) {

        target =
                blockPos == null
                        ? null
                        : blockPos.immutable();
    }

    public static BlockPos completedTarget() {
        return completedTarget;
    }

    public static void setCompletedTarget(
            BlockPos blockPos
    ) {

        completedTarget =
                blockPos == null
                        ? null
                        : blockPos.immutable();
    }

    /*
     * ------------------------------------------------------------
     * STOP DISTANCE
     * ------------------------------------------------------------
     */

    public static double stopDistance() {
        return stopDistance;
    }

    public static void setStopDistance(
            double value
    ) {

        stopDistance =
                value;
    }

    public static void resetStopDistance() {

        stopDistance =
                DEFAULT_STOP_DISTANCE;
    }

    /*
     * ------------------------------------------------------------
     * TIMEOUT
     * ------------------------------------------------------------
     */

    public static int ticksRemaining() {
        return ticksRemaining;
    }

    public static void setTicksRemaining(
            int value
    ) {

        ticksRemaining =
                Math.max(
                        0,
                        value
                );
    }

    public static boolean consumeTick() {

        if (ticksRemaining <= 0) {
            return false;
        }

        ticksRemaining--;

        return true;
    }

    /*
     * ------------------------------------------------------------
     * ACTIVE
     * ------------------------------------------------------------
     */

    public static boolean active() {
        return active;
    }

    public static void setActive(
            boolean value
    ) {

        active =
                value;
    }

    /*
     * ------------------------------------------------------------
     * GENERATION
     * ------------------------------------------------------------
     */

    public static int generation() {
        return generation;
    }

    public static int nextGeneration() {

        generation =
                generation == Integer.MAX_VALUE
                        ? 1
                        : generation + 1;

        RESULT_HISTORY.remove(
                generation
        );

        return generation;
    }

    public static void rememberResult(
            int completedGeneration,
            DAI_ActionResult result
    ) {

        if (
                completedGeneration <= 0
                        || result == null
        ) {
            return;
        }

        RESULT_HISTORY.put(
                completedGeneration,
                result
        );

        while (
                RESULT_HISTORY.size()
                        > MAX_RESULT_HISTORY
        ) {

            Integer oldestGeneration =
                    RESULT_HISTORY
                            .keySet()
                            .iterator()
                            .next();

            RESULT_HISTORY.remove(
                    oldestGeneration
            );
        }
    }

    public static DAI_ActionResult resultForGeneration(
            int requestedGeneration
    ) {

        if (requestedGeneration <= 0) {
            return DAI_ActionResult.FAILURE;
        }

        if (
                active
                        && requestedGeneration
                        == generation
        ) {

            return DAI_ActionResult.RUNNING;
        }

        DAI_ActionResult result =
                RESULT_HISTORY.get(
                        requestedGeneration
                );

        return result != null
                ? result
                : DAI_ActionResult.FAILURE;
    }

    /*
     * ------------------------------------------------------------
     * ALIGNMENT
     * ------------------------------------------------------------
     */

    public static int alignmentTicks() {
        return alignmentTicks;
    }

    public static void resetAlignmentTicks() {

        alignmentTicks =
                0;
    }

    public static int incrementAlignmentTicks() {

        alignmentTicks++;

        return alignmentTicks;
    }

    /*
     * ------------------------------------------------------------
     * PATH
     * ------------------------------------------------------------
     */

    public static BlockPos approachPosition() {
        return approachPosition;
    }

    public static void setApproachPosition(
            BlockPos blockPos
    ) {

        approachPosition =
                blockPos == null
                        ? null
                        : blockPos.immutable();
    }

    public static DAI_Path approachPath() {
        return approachPath;
    }

    public static void setApproachPath(
            DAI_Path path
    ) {

        approachPath =
                path;
    }

    public static int pathIndex() {
        return pathIndex;
    }

    public static void setPathIndex(
            int value
    ) {

        pathIndex =
                Math.max(
                        0,
                        value
                );
    }

    public static void clearPath() {

        approachPosition =
                null;

        approachPath =
                null;

        pathIndex =
                0;

        resetRouteProgress();
    }

    /*
     * ------------------------------------------------------------
     * SAFE RECOVERY
     * ------------------------------------------------------------
     */

    public static BlockPos recoveryPosition() {
        return recoveryPosition;
    }

    public static void setRecoveryPosition(
            BlockPos blockPos
    ) {

        recoveryPosition =
                blockPos == null
                        ? null
                        : blockPos.immutable();
    }

    public static boolean recoveryActive() {
        return recoveryActive;
    }

    public static void setRecoveryActive(
            boolean value
    ) {

        recoveryActive =
                value;
    }

    public static void clearRecovery() {

        recoveryPosition =
                null;

        recoveryActive =
                false;
    }

    /*
     * ------------------------------------------------------------
     * DESTRUCTIVE FALLBACK
     * ------------------------------------------------------------
     */

    public static BlockPos fallbackObstruction() {
        return fallbackObstruction;
    }

    public static void setFallbackObstruction(
            BlockPos blockPos
    ) {

        fallbackObstruction =
                blockPos == null
                        ? null
                        : blockPos.immutable();
    }

    public static boolean fallbackActive() {
        return fallbackActive;
    }

    public static void setFallbackActive(
            boolean value
    ) {

        fallbackActive =
                value;
    }

    public static void clearFallback() {

        fallbackObstruction =
                null;

        fallbackActive =
                false;
    }

    /*
     * ------------------------------------------------------------
     * STUCK / ROUTE PROGRESS
     * ------------------------------------------------------------
     */

    public static int stuckCheckTicks() {
        return stuckCheckTicks;
    }

    public static void setStuckCheckTicks(
            int value
    ) {

        stuckCheckTicks =
                Math.max(
                        0,
                        value
                );
    }

    public static int decrementStuckCheckTicks() {

        if (stuckCheckTicks > 0) {
            stuckCheckTicks--;
        }

        return stuckCheckTicks;
    }

    public static Vec3 lastProgressPosition() {
        return lastProgressPosition;
    }

    public static void setLastProgressPosition(
            Vec3 position
    ) {

        lastProgressPosition =
                position;
    }

    public static int lastProgressPathIndex() {
        return lastProgressPathIndex;
    }

    public static void setLastProgressPathIndex(
            int value
    ) {

        lastProgressPathIndex =
                value;
    }

    public static double lastSteeringDistance() {
        return lastSteeringDistance;
    }

    public static void setLastSteeringDistance(
            double value
    ) {

        lastSteeringDistance =
                value;
    }

    public static int routeStagnantChecks() {
        return routeStagnantChecks;
    }

    public static void setRouteStagnantChecks(
            int value
    ) {

        routeStagnantChecks =
                Math.max(
                        0,
                        value
                );
    }

    public static int incrementRouteStagnantChecks() {

        routeStagnantChecks++;

        return routeStagnantChecks;
    }

    public static int debugLogTicks() {
        return debugLogTicks;
    }

    public static void setDebugLogTicks(
            int value
    ) {

        debugLogTicks =
                Math.max(
                        0,
                        value
                );
    }

    public static int decrementDebugLogTicks() {

        if (debugLogTicks > 0) {
            debugLogTicks--;
        }

        return debugLogTicks;
    }

    public static void resetRouteProgress() {

        lastProgressPathIndex =
                -1;

        lastSteeringDistance =
                Double.POSITIVE_INFINITY;

        routeStagnantChecks =
                0;
    }

    /*
     * ------------------------------------------------------------
     * CLEAR / RESET
     * ------------------------------------------------------------
     */

    /**
     * Clears only the currently active approach.
     *
     * Generation history and completedTarget intentionally survive so
     * wait_for_approach and following actions can consume them.
     */
    public static void clearActiveState() {

        target =
                null;

        stopDistance =
                DEFAULT_STOP_DISTANCE;

        ticksRemaining =
                0;

        alignmentTicks =
                0;

        clearPath();

        clearRecovery();

        clearFallback();

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
     * Full world/session reset.
     */
    public static void resetAll() {

        target =
                null;

        completedTarget =
                null;

        stopDistance =
                DEFAULT_STOP_DISTANCE;

        ticksRemaining =
                0;

        active =
                false;

        generation =
                0;

        alignmentTicks =
                0;

        RESULT_HISTORY.clear();

        approachPosition =
                null;

        approachPath =
                null;

        pathIndex =
                0;

        recoveryPosition =
                null;

        recoveryActive =
                false;

        fallbackObstruction =
                null;

        fallbackActive =
                false;

        stuckCheckTicks =
                0;

        lastProgressPosition =
                null;

        lastProgressPathIndex =
                -1;

        lastSteeringDistance =
                Double.POSITIVE_INFINITY;

        routeStagnantChecks =
                0;

        debugLogTicks =
                0;
    }
}