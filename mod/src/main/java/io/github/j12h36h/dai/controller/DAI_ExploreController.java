package io.github.j12h36h.dai.controller;

import io.github.j12h36h.dai.action.DAI_ActionResult;
import io.github.j12h36h.dai.action.DAI_ActionStatus;
import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.logic.DAI_TargetLogic;
import io.github.j12h36h.dai.navigation.DAI_ExplorationMemory;
import io.github.j12h36h.dai.navigation.DAI_Path;
import io.github.j12h36h.dai.navigation.DAI_PathFinder;
import io.github.j12h36h.dai.system.DAI_TargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    private static final double MINIMUM_DESTINATION_FACTOR =
            0.70D;

    private static final int DESTINATION_ATTEMPTS =
            32;

    private static String requestedTarget =
            "";

    private static int searchRadius =
            DEFAULT_SEARCH_RADIUS;

    private static int ticksRemaining;

    private static int rescanTicks;

    private static boolean active;

    private DAI_ExploreController() {
        // Utility class.
    }

    public static void start(
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

            finish(
                    DAI_ActionResult.FAILURE,
                    "player or level was unavailable"
            );

            return;
        }

        if (
                target == null
                        || target.isBlank()
        ) {

            finish(
                    DAI_ActionResult.FAILURE,
                    "requested target was blank"
            );

            return;
        }

        requestedTarget =
                target.trim();

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

        active =
                true;

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Started exploration for '{}' with radius={} and timeout={} tick(s).",
                requestedTarget,
                searchRadius,
                ticksRemaining
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

                DAI_PathController.stop();

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

        /*
         * If path following just ended unsuccessfully, try another
         * destination rather than failing the entire exploration.
         */
        BlockPos destination =
                chooseDestination(
                        minecraft
                );

        if (destination == null) {

            finish(
                    DAI_ActionResult.FAILURE,
                    "no reachable exploration destination could be found"
            );

            return;
        }

        BlockPos start =
                findNearestWalkablePosition(
                        minecraft,
                        minecraft.player.blockPosition()
                );

        if (start == null) {

            finish(
                    DAI_ActionResult.FAILURE,
                    "player was not standing near a walkable path node"
            );

            return;
        }

        DAI_Path path =
                DAI_PathFinder.find(
                        minecraft.level,
                        start,
                        destination
                );

        if (path == null) {

            /*
             * Do not fail immediately. The next tick will attempt a
             * different exploration destination.
             */
            DAI_Core.LOGGER.debug(
                    "<DAI>: No path found to exploration destination {}; retrying.",
                    destination
            );

            return;
        }

        DAI_PathController.start(
                path
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Exploring toward {} while searching for '{}'.",
                destination,
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

        boolean wasActive =
                active;

        /*
         * Exploration owns the path it starts. Reset that child controller
         * before clearing exploration state so a cancelled/stopped gameplay
         * session cannot leave an exploration path continuing to drive
         * movement after its owner is gone.
         */
        if (DAI_PathController.isActive()) {
            DAI_PathController.reset();
        }

        clearState();

        if (wasActive) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.CANCELLED
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Active exploration cancelled during reset."
            );
        }
    }

    public static boolean isActive() {
        return active;
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

    private static BlockPos chooseDestination(
            Minecraft minecraft
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {
            return null;
        }

        BlockPos playerPosition =
                minecraft.player.blockPosition();

        int minimumDistance =
                Math.max(
                        MINIMUM_SEARCH_RADIUS,
                        (int) Math.round(
                                searchRadius
                                        * MINIMUM_DESTINATION_FACTOR
                        )
                );

        List<BlockPos> unexploredCandidates =
                new ArrayList<>();

        List<BlockPos> recentCandidates =
                new ArrayList<>();

        for (
                int attempt = 0;
                attempt < DESTINATION_ATTEMPTS;
                attempt++
        ) {

            double angle =
                    ThreadLocalRandom.current()
                            .nextDouble(
                                    Math.PI * 2.0D
                            );

            int distance =
                    ThreadLocalRandom.current()
                            .nextInt(
                                    minimumDistance,
                                    searchRadius + 1
                            );

            int offsetX =
                    (int) Math.round(
                            Math.cos(
                                    angle
                            )
                                    * distance
                    );

            int offsetZ =
                    (int) Math.round(
                            Math.sin(
                                    angle
                            )
                                    * distance
                    );

            BlockPos column =
                    playerPosition.offset(
                            offsetX,
                            0,
                            offsetZ
                    );

            BlockPos walkable =
                    findNearestWalkablePosition(
                            minecraft,
                            column
                    );

            if (
                    walkable == null
                            || walkable.equals(
                            playerPosition
                    )
            ) {
                continue;
            }

            if (
                    DAI_ExplorationMemory.wasRecentlyVisited(
                            walkable
                    )
            ) {

                recentCandidates.add(
                        walkable
                );

                continue;
            }

            unexploredCandidates.add(
                    walkable
            );
        }

        if (
                unexploredCandidates.isEmpty()
                        && recentCandidates.isEmpty()
        ) {
            return null;
        }

        Collections.shuffle(
                unexploredCandidates
        );

        Collections.shuffle(
                recentCandidates
        );

        BlockPos start =
                findNearestWalkablePosition(
                        minecraft,
                        playerPosition
                );

        if (start == null) {
            return null;
        }

        /*
         * Prefer destinations in chunks that are not present in the recent
         * exploration history.
         */
        BlockPos destination =
                findReachableDestination(
                        minecraft,
                        start,
                        unexploredCandidates
                );

        if (destination != null) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Selected fresh exploration destination {}.",
                    destination
            );

            return destination;
        }

        /*
         * Reusing a recent chunk remains a fallback so exploration can
         * backtrack out of terrain that has no reachable fresh destination.
         */
        destination =
                findReachableDestination(
                        minecraft,
                        start,
                        recentCandidates
                );

        if (destination != null) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: No fresh exploration destination was reachable; reusing recent destination {}.",
                    destination
            );
        }

        return destination;
    }

    private static BlockPos findReachableDestination(
            Minecraft minecraft,
            BlockPos start,
            List<BlockPos> candidates
    ) {

        if (
                minecraft.level == null
                        || start == null
                        || candidates == null
        ) {
            return null;
        }

        for (BlockPos candidate : candidates) {

            DAI_Path path =
                    DAI_PathFinder.find(
                            minecraft.level,
                            start,
                            candidate
                    );

            if (path != null) {
                return candidate;
            }
        }

        return null;
    }

    private static BlockPos findNearestWalkablePosition(
            Minecraft minecraft,
            BlockPos origin
    ) {

        if (
                minecraft.level == null
                        || origin == null
        ) {
            return null;
        }

        if (
                DAI_PathFinder.isWalkablePosition(
                        minecraft.level,
                        origin
                )
        ) {
            return origin.immutable();
        }

        /*
         * Search vertically around the requested X/Z column. This
         * lets exploration pick natural terrain even when the original
         * Y coordinate differs from the surface height.
         */
        for (
                int offset = 1;
                offset <= searchRadius;
                offset++
        ) {

            BlockPos above =
                    origin.above(
                            offset
                    );

            if (
                    DAI_PathFinder.isWalkablePosition(
                            minecraft.level,
                            above
                    )
            ) {
                return above.immutable();
            }

            BlockPos below =
                    origin.below(
                            offset
                    );

            if (
                    DAI_PathFinder.isWalkablePosition(
                            minecraft.level,
                            below
                    )
            ) {
                return below.immutable();
            }
        }

        return null;
    }

    private static void finish(
            DAI_ActionResult result,
            String reason
    ) {

        String finishedTarget =
                requestedTarget;

        clearState();

        DAI_ActionStatus.set(
                result
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Finished exploration for '{}' with result={}: {}.",
                finishedTarget,
                result,
                reason
        );
    }

    private static void clearState() {

        if (DAI_PathController.isActive()) {
            DAI_PathController.stop();
        }

        requestedTarget =
                "";

        searchRadius =
                DEFAULT_SEARCH_RADIUS;

        ticksRemaining =
                0;

        rescanTicks =
                0;

        active =
                false;
    }
}