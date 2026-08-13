package io.github.j12h36h.dai.logics.controller;

import io.github.j12h36h.dai.logics.DAI_TargetLogic;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.navigation.DAI_ExplorationMemory;
import io.github.j12h36h.dai.logics.navigation.DAI_ExplorationPlanner;
import io.github.j12h36h.dai.logics.navigation.DAI_Path;
import io.github.j12h36h.dai.logics.navigation.DAI_PathFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class DAI_ExploreController {

    private static final int DEFAULT_TIMEOUT_TICKS =
            1200;

    private static final int DEFAULT_SEARCH_RADIUS =
            24;

    private static final int MINIMUM_SEARCH_RADIUS =
            4;

    private static final int MAXIMUM_SEARCH_RADIUS =
            48;

    private static final int RESCAN_INTERVAL_TICKS =
            20;
    private static final int DESTINATION_RETRY_DELAY_TICKS =
            10;

    private static final int MAX_RESULT_HISTORY =
            32;

    private static final Map<Integer, DAI_ActionResult> RESULT_HISTORY =
            new LinkedHashMap<>();

    private static String requestedTarget =
            "";

    private static int searchRadius =
            DEFAULT_SEARCH_RADIUS;

    private static int ticksRemaining;

    private static int rescanTicks;

    private static int destinationRetryTicks;

    private static boolean active;

    private static int generation;

    private DAI_ExploreController() {
        // Utility class.
    }
    public static boolean start(
            String target,
            int requestedRadius,
            int timeoutTicks
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
                    "<DAI>: Cannot explore without an active player and level."
            );

            return false;
        }

        if (
                target == null
                        || target.isBlank()
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot explore for a blank target."
            );

            return false;
        }

        String normalizedTarget =
                target.trim();
        if (active) {

            if (
                    normalizedTarget.equals(
                            requestedTarget
                    )
            ) {

                DAI_ActionStatus.set(
                        DAI_ActionResult.RUNNING
                );

                DAI_Core.debug(
                        "<DAI>: Exploration generation={} already searching for '{}'; reusing it.",
                        generation,
                        requestedTarget
                );

                return true;
            }

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.debug(
                    "<DAI>: Rejected exploration for '{}' because generation={} still owns search target '{}'.",
                    normalizedTarget,
                    generation,
                    requestedTarget
            );

            return false;
        }

        generation++;

        RESULT_HISTORY.remove(
                generation
        );

        requestedTarget =
                normalizedTarget;

        searchRadius =
                Mth.clamp(
                        requestedRadius > 0
                                ? requestedRadius
                                : DEFAULT_SEARCH_RADIUS,
                        MINIMUM_SEARCH_RADIUS,
                        MAXIMUM_SEARCH_RADIUS
                );

        ticksRemaining =
                timeoutTicks > 0
                        ? timeoutTicks
                        : DEFAULT_TIMEOUT_TICKS;

        DAI_ExplorationMemory.visit(
                minecraft.player.blockPosition()
        );

        rescanTicks =
                0;

        destinationRetryTicks =
                0;

        active =
                true;

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.debug(
                "<DAI>: Started exploration generation={} for '{}' with radius={} and timeout={} tick(s).",
                generation,
                requestedTarget,
                searchRadius,
                ticksRemaining
        );

        return true;
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
        ) {

            finish(
                    DAI_ActionResult.FAILURE,
                    "player or level became unavailable"
            );

            return;
        }

        DAI_ExplorationMemory.visit(
                minecraft.player.blockPosition()
        );

        if (ticksRemaining-- <= 0) {

            finish(
                    DAI_ActionResult.TIMED_OUT,
                    "exploration timed out"
            );

            return;
        }

        if (shouldRescan()) {

            if (
                    DAI_TargetLogic.findAndSelectBlock(
                            requestedTarget,
                            searchRadius
                    )
            ) {
                if (DAI_PathController.isActive()) {
                    DAI_PathController.stop();
                }

                finish(
                        DAI_ActionResult.SUCCESS,
                        "requested block was found"
                );

                return;
            }
        }
        if (DAI_PathController.isActive()) {
            return;
        }
        if (destinationRetryTicks > 0) {

            destinationRetryTicks--;

            return;
        }

        DAI_ExplorationPlanner.Plan plan =
                DAI_ExplorationPlanner.plan(
                        minecraft,
                        searchRadius
                );

        if (plan == null) {

            destinationRetryTicks =
                    DESTINATION_RETRY_DELAY_TICKS;

            DAI_Core.debug(
                    "<DAI>: Exploration generation={} found no reachable short-leg destination; retrying.",
                    generation
            );

            return;
        }

        DAI_PathController.start(
                plan.path()
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.debug(
                "<DAI>: Exploration generation={} moving toward {} via {} node(s) while searching for '{}'.",
                generation,
                plan.destination(),
                plan.path().nodes().size(),
                requestedTarget
        );
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

        if (!active) {

            if (DAI_PathController.isActive()) {
                DAI_PathController.reset();
            }

            clearState();

            return;
        }

        int cancelledGeneration =
                generation;

        rememberResult(
                cancelledGeneration,
                DAI_ActionResult.CANCELLED
        );

        if (DAI_PathController.isActive()) {
            DAI_PathController.reset();
        }

        clearState();

        DAI_ActionStatus.set(
                DAI_ActionResult.CANCELLED
        );

        DAI_Core.debug(
                "<DAI>: Exploration generation={} cancelled during reset.",
                cancelledGeneration
        );
    }

    public static boolean isActive() {

        return active;
    }

    public static int generation() {

        return generation;
    }

    public static String requestedTarget() {

        return requestedTarget;
    }
    public static DAI_ActionResult resultForGeneration(
            int requestedGeneration
    ) {

        if (requestedGeneration <= 0) {
            return DAI_ActionResult.FAILURE;
        }

        if (
                active
                        && requestedGeneration == generation
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

    private static boolean shouldRescan() {

        if (rescanTicks <= 0) {

            rescanTicks =
                    RESCAN_INTERVAL_TICKS;

            return true;
        }

        rescanTicks--;

        return false;
    }

    private static void finish(
            DAI_ActionResult result,
            String reason
    ) {

        String finishedTarget =
                requestedTarget;

        int finishedGeneration =
                generation;

        rememberResult(
                finishedGeneration,
                result
        );
        if (DAI_PathController.isActive()) {
            DAI_PathController.stop();
        }

        clearState();

        DAI_ActionStatus.set(
                result
        );

        DAI_Core.debug(
                "<DAI>: Finished exploration generation={} for '{}' with result={}: {}.",
                finishedGeneration,
                finishedTarget,
                result,
                reason
        );
    }

    private static void rememberResult(
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

            Integer oldest =
                    RESULT_HISTORY
                            .keySet()
                            .iterator()
                            .next();

            RESULT_HISTORY.remove(
                    oldest
            );
        }
    }

    private static void clearState() {

        requestedTarget =
                "";

        searchRadius =
                DEFAULT_SEARCH_RADIUS;

        ticksRemaining =
                0;

        rescanTicks =
                0;

        destinationRetryTicks =
                0;

        active =
                false;
    }
}