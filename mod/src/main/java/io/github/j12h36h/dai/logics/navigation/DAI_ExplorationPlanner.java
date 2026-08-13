package io.github.j12h36h.dai.logics.navigation;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class DAI_ExplorationPlanner {

    private static final int MINIMUM_LEG_DISTANCE =
            8;

    private static final int MAXIMUM_LEG_DISTANCE =
            16;

    private static final int DESTINATION_ATTEMPTS =
            24;

    private DAI_ExplorationPlanner() {
        // Utility class.
    }

    public static Plan plan(
            Minecraft minecraft,
            int verticalSearchRange
    ) {

        if (
                minecraft == null
                        || minecraft.player == null
                        || minecraft.level == null
        ) {
            return null;
        }

        BlockPos playerPosition =
                minecraft.player.blockPosition();

        BlockPos start =
                findNearestWalkablePosition(
                        minecraft,
                        playerPosition,
                        verticalSearchRange
                );

        if (start == null) {

            DAI_Core.debug(
                    "<DAI>: Exploration planner could not resolve a walkable start near {}.",
                    playerPosition
            );

            return null;
        }

        List<BlockPos> freshCandidates =
                new ArrayList<>();

        List<BlockPos> recentCandidates =
                new ArrayList<>();

        collectCandidates(
                minecraft,
                playerPosition,
                verticalSearchRange,
                freshCandidates,
                recentCandidates
        );

        Collections.shuffle(
                freshCandidates
        );

        Collections.shuffle(
                recentCandidates
        );

        Plan fresh =
                findReachablePlan(
                        minecraft,
                        start,
                        freshCandidates
                );

        if (fresh != null) {

            DAI_Core.debug(
                    "<DAI>: Selected fresh short-leg exploration destination {}.",
                    fresh.destination()
            );

            return fresh;
        }

        Plan recent =
                findReachablePlan(
                        minecraft,
                        start,
                        recentCandidates
                );

        if (recent != null) {

            DAI_Core.debug(
                    "<DAI>: No fresh short-leg destination was reachable; reusing recent destination {}.",
                    recent.destination()
            );
        }

        return recent;
    }

    private static void collectCandidates(
            Minecraft minecraft,
            BlockPos playerPosition,
            int verticalSearchRange,
            List<BlockPos> freshCandidates,
            List<BlockPos> recentCandidates
    ) {

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
                                    MINIMUM_LEG_DISTANCE,
                                    MAXIMUM_LEG_DISTANCE + 1
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
                            column,
                            verticalSearchRange
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

            freshCandidates.add(
                    walkable
            );
        }
    }

    private static Plan findReachablePlan(
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

            if (path == null) {
                continue;
            }

            return new Plan(
                    candidate.immutable(),
                    path
            );
        }

        return null;
    }

    private static BlockPos findNearestWalkablePosition(
            Minecraft minecraft,
            BlockPos origin,
            int verticalSearchRange
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

        int range =
                Math.max(
                        4,
                        verticalSearchRange
                );

        for (
                int offset = 1;
                offset <= range;
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

    public record Plan(
            BlockPos destination,
            DAI_Path path
    ) {
    }
}