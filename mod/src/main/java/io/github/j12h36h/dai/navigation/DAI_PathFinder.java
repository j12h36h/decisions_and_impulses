package io.github.j12h36h.dai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class DAI_PathFinder {

    private static final int DEFAULT_MAX_SEARCH_DISTANCE =
            64;

    private static final int DEFAULT_MAX_EXPANDED_NODES =
            4096;

    private static final int MAX_DROP_DISTANCE =
            3;

    private static final double STRAIGHT_COST =
            1.0D;

    private static final double JUMP_COST =
            1.25D;

    private static final double DROP_COST =
            1.10D;

    /*
     * Water is traversable, but intentionally more expensive than
     * walking so A* prefers reasonable land routes.
     */
    private static final double WATER_COST =
            2.0D;

    /*
     * Collision-empty vegetation is technically passable, but dense patches
     * are poor navigation corridors in practice. A* should prefer clean air
     * when a reasonable alternate route exists.
     */
    private static final double VEGETATION_COST =
            1.75D;

    private static final double HEAD_VEGETATION_COST =
            0.75D;

    private static final double IRREGULAR_SUPPORT_COST =
            0.35D;

    private static final int[][] HORIZONTAL_DIRECTIONS = {
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1}
    };

    private DAI_PathFinder() {
        // Utility class.
    }

    public static DAI_Path find(
            Level level,
            BlockPos start,
            BlockPos destination
    ) {

        return find(
                level,
                start,
                destination,
                DEFAULT_MAX_SEARCH_DISTANCE,
                DEFAULT_MAX_EXPANDED_NODES
        );
    }

    public static DAI_Path find(
            Level level,
            BlockPos start,
            BlockPos destination,
            int maxSearchDistance,
            int maxExpandedNodes
    ) {

        if (
                level == null
                        || start == null
                        || destination == null
                        || maxSearchDistance <= 0
                        || maxExpandedNodes <= 0
        ) {
            return null;
        }

        BlockPos immutableStart =
                start.immutable();

        BlockPos immutableDestination =
                destination.immutable();

        /*
         * The player's actual block position is allowed to be the A* root
         * even when it temporarily fails normal traversability checks.
         *
         * Minecraft can report the player inside/against irregular terrain,
         * vegetation, water edges, partial collision shapes, or other
         * transitional geometry. Rejecting the start outright makes every
         * otherwise valid approach candidate appear unreachable.
         *
         * Destinations remain strict: DAI must only finish on a genuinely
         * traversable navigation position.
         */
        boolean startTraversable =
                isTraversablePosition(
                        level,
                        immutableStart
                );

        if (
                !isTraversablePosition(
                        level,
                        immutableDestination
                )
        ) {
            return null;
        }

        if (
                immutableStart.equals(
                        immutableDestination
                )
        ) {

            return new DAI_Path(
                    List.of(
                            immutableStart
                    )
            );
        }

        PriorityQueue<PathNode> open =
                new PriorityQueue<>(
                        Comparator.comparingDouble(
                                PathNode::estimatedTotalCost
                        )
                );

        Map<BlockPos, Double> bestCosts =
                new HashMap<>();

        Map<BlockPos, BlockPos> previous =
                new HashMap<>();

        Set<BlockPos> closed =
                new HashSet<>();

        double initialHeuristic =
                heuristic(
                        immutableStart,
                        immutableDestination
                );

        open.add(
                new PathNode(
                        immutableStart,
                        0.0D,
                        initialHeuristic
                )
        );

        bestCosts.put(
                immutableStart,
                0.0D
        );

        int expandedNodes =
                0;

        while (!open.isEmpty()) {

            PathNode current =
                    open.poll();

            BlockPos currentPosition =
                    current.position();

            if (
                    closed.contains(
                            currentPosition
                    )
            ) {
                continue;
            }

            if (
                    currentPosition.equals(
                            immutableDestination
                    )
            ) {

                return reconstructPath(
                        previous,
                        immutableStart,
                        immutableDestination
                );
            }

            closed.add(
                    currentPosition
            );

            expandedNodes++;

            if (
                    expandedNodes
                            >= maxExpandedNodes
            ) {
                return null;
            }

            List<PathStep> steps =
                    !startTraversable
                            && currentPosition.equals(
                            immutableStart
                    )
                            ? findStartRecoveryNeighbors(
                            level,
                            immutableStart
                    )
                            : findNeighbors(
                            level,
                            currentPosition
                    );

            for (
                    PathStep step
                    : steps
            ) {

                BlockPos neighbor =
                        step.position();

                if (
                        closed.contains(
                                neighbor
                        )
                ) {
                    continue;
                }

                if (
                        horizontalDistanceSquared(
                                immutableStart,
                                neighbor
                        )
                                > (double) maxSearchDistance
                                * maxSearchDistance
                ) {
                    continue;
                }

                double tentativeCost =
                        current.costFromStart()
                                + step.cost();

                double existingCost =
                        bestCosts.getOrDefault(
                                neighbor,
                                Double.POSITIVE_INFINITY
                        );

                if (
                        tentativeCost
                                >= existingCost
                ) {
                    continue;
                }

                previous.put(
                        neighbor,
                        currentPosition
                );

                bestCosts.put(
                        neighbor,
                        tentativeCost
                );

                double estimatedTotalCost =
                        tentativeCost
                                + heuristic(
                                neighbor,
                                immutableDestination
                        );

                open.add(
                        new PathNode(
                                neighbor,
                                tentativeCost,
                                estimatedTotalCost
                        )
                );
            }
        }

        return null;
    }

    /**
     * Finds conservative first-step nodes when the player's actual current
     * block position is not a normal traversable A* node.
     *
     * The original start position is still retained in the reconstructed
     * path. Only the first expansion is special-cased, after which ordinary
     * pathfinding rules resume.
     */
    private static List<PathStep> findStartRecoveryNeighbors(
            Level level,
            BlockPos start
    ) {

        List<PathStep> neighbors =
                new ArrayList<>();

        if (
                level == null
                        || start == null
        ) {
            return neighbors;
        }

        /*
         * First try the four cardinal directions at the player's current
         * height. These are the least surprising recovery moves.
         */
        for (
                int[] direction
                : HORIZONTAL_DIRECTIONS
        ) {

            BlockPos sameLevel =
                    start.offset(
                            direction[0],
                            0,
                            direction[1]
                    );

            addStartRecoveryNeighbor(
                    level,
                    neighbors,
                    sameLevel,
                    STRAIGHT_COST
            );
        }

        /*
         * Then allow one-block vertical transitions around the player.
         *
         * This handles common edge cases such as standing partially in a
         * slab/stair boundary, water bank, vegetation layer, or immediately
         * beside a one-block rise/drop.
         */
        for (
                int[] direction
                : HORIZONTAL_DIRECTIONS
        ) {

            BlockPos above =
                    start.offset(
                            direction[0],
                            1,
                            direction[1]
                    );

            addStartRecoveryNeighbor(
                    level,
                    neighbors,
                    above,
                    JUMP_COST
            );

            BlockPos below =
                    start.offset(
                            direction[0],
                            -1,
                            direction[1]
                    );

            addStartRecoveryNeighbor(
                    level,
                    neighbors,
                    below,
                    DROP_COST
            );
        }

        /*
         * Finally try the same X/Z column one block above or below. These
         * are useful when blockPosition() lands on the wrong vertical cell
         * during a transition.
         */
        addStartRecoveryNeighbor(
                level,
                neighbors,
                start.above(),
                JUMP_COST
        );

        addStartRecoveryNeighbor(
                level,
                neighbors,
                start.below(),
                DROP_COST
        );

        return neighbors;
    }

    private static void addStartRecoveryNeighbor(
            Level level,
            List<PathStep> neighbors,
            BlockPos candidate,
            double baseCost
    ) {

        if (
                level == null
                        || neighbors == null
                        || candidate == null
                        || !isTraversablePosition(
                        level,
                        candidate
                )
        ) {
            return;
        }

        for (PathStep existing : neighbors) {

            if (
                    existing.position()
                            .equals(
                                    candidate
                            )
            ) {
                return;
            }
        }

        double cost =
                baseCost;

        if (
                isWaterPosition(
                        level,
                        candidate
                )
        ) {

            cost +=
                    WATER_COST;
        } else {

            cost +=
                    terrainCost(
                            level,
                            candidate
                    );
        }

        neighbors.add(
                new PathStep(
                        candidate.immutable(),
                        cost
                )
        );
    }

    private static List<PathStep> findNeighbors(
            Level level,
            BlockPos position
    ) {

        List<PathStep> neighbors =
                new ArrayList<>();

        for (
                int[] direction
                : HORIZONTAL_DIRECTIONS
        ) {

            int offsetX =
                    direction[0];

            int offsetZ =
                    direction[1];

            BlockPos sameLevel =
                    position.offset(
                            offsetX,
                            0,
                            offsetZ
                    );

            if (
                    isWaterPosition(
                            level,
                            sameLevel
                    )
            ) {

                neighbors.add(
                        new PathStep(
                                sameLevel.immutable(),
                                WATER_COST
                        )
                );

                continue;
            }

            if (
                    isWalkablePosition(
                            level,
                            sameLevel
                    )
            ) {

                neighbors.add(
                        new PathStep(
                                sameLevel.immutable(),
                                STRAIGHT_COST
                                        + terrainCost(
                                        level,
                                        sameLevel
                                )
                        )
                );

                continue;
            }

            BlockPos jumpPosition =
                    position.offset(
                            offsetX,
                            1,
                            offsetZ
                    );

            if (
                    canJumpTo(
                            level,
                            position,
                            jumpPosition
                    )
            ) {

                neighbors.add(
                        new PathStep(
                                jumpPosition.immutable(),
                                JUMP_COST
                                        + terrainCost(
                                        level,
                                        jumpPosition
                                )
                        )
                );

                continue;
            }

            BlockPos dropPosition =
                    findDropPosition(
                            level,
                            position,
                            offsetX,
                            offsetZ
                    );

            if (dropPosition != null) {

                neighbors.add(
                        new PathStep(
                                dropPosition,
                                DROP_COST
                                        + terrainCost(
                                        level,
                                        dropPosition
                                )
                        )
                );
            }
        }

        return neighbors;
    }

    /**
     * Additional A* cost for terrain that is technically traversable but
     * undesirable for reliable player-scale movement.
     */
    private static double terrainCost(
            Level level,
            BlockPos position
    ) {

        if (
                level == null
                        || position == null
        ) {
            return 0.0D;
        }

        double cost =
                0.0D;

        BlockState feet =
                level.getBlockState(
                        position
                );

        BlockState head =
                level.getBlockState(
                        position.above()
                );

        BlockState support =
                level.getBlockState(
                        position.below()
                );

        if (isPassableNonAirBlock(level, position, feet)) {

            cost +=
                    VEGETATION_COST;
        }

        if (
                isPassableNonAirBlock(
                        level,
                        position.above(),
                        head
                )
        ) {

            cost +=
                    HEAD_VEGETATION_COST;
        }

        /*
         * Penalize partial/irregular support shapes slightly. This keeps A*
         * biased toward ordinary full-block ground without making unusual
         * terrain completely forbidden.
         */
        if (
                !support.isAir()
                        && !support.isCollisionShapeFullBlock(
                        level,
                        position.below()
                )
        ) {

            cost +=
                    IRREGULAR_SUPPORT_COST;
        }

        return cost;
    }

    /**
     * True for blocks such as grass/flowers that occupy the world but have
     * no collision. These are traversable, yet less desirable than clean air.
     */
    private static boolean isPassableNonAirBlock(
            Level level,
            BlockPos position,
            BlockState state
    ) {

        if (
                state == null
                        || state.isAir()
        ) {
            return false;
        }

        if (
                !level.getFluidState(
                        position
                ).isEmpty()
        ) {
            return false;
        }

        return state.getCollisionShape(
                level,
                position
        ).isEmpty();
    }

    private static boolean canJumpTo(
            Level level,
            BlockPos current,
            BlockPos destination
    ) {

        if (
                destination.getY()
                        != current.getY() + 1
        ) {
            return false;
        }

        if (
                !isPassable(
                        level,
                        current.above(
                                2
                        )
                )
        ) {
            return false;
        }

        return isWalkablePosition(
                level,
                destination
        );
    }

    private static BlockPos findDropPosition(
            Level level,
            BlockPos current,
            int offsetX,
            int offsetZ
    ) {

        BlockPos horizontal =
                current.offset(
                        offsetX,
                        0,
                        offsetZ
                );

        if (
                !isPassable(
                        level,
                        horizontal
                )
                        || !isPassable(
                        level,
                        horizontal.above()
                )
        ) {
            return null;
        }

        for (
                int drop = 1;
                drop <= MAX_DROP_DISTANCE;
                drop++
        ) {

            BlockPos candidate =
                    horizontal.below(
                            drop
                    );

            /*
             * Falling into water is allowed.
             */
            if (
                    isWaterPosition(
                            level,
                            candidate
                    )
            ) {
                return candidate.immutable();
            }

            if (
                    isWalkablePosition(
                            level,
                            candidate
                    )
            ) {
                return candidate.immutable();
            }

            if (
                    !isPassable(
                            level,
                            candidate
                    )
            ) {
                return null;
            }
        }

        return null;
    }

    /**
     * Finds a nearby traversable standing position from which the center of
     * the target block is within the requested interaction distance.
     *
     * The candidate must be horizontally offset from the target. This keeps
     * navigation from choosing a position directly underneath an elevated
     * block, which would otherwise cause repeated vertical jump recovery.
     */
    public static BlockPos findNearestApproachPosition(
            Level level,
            BlockPos origin,
            BlockPos target,
            double interactionDistance
    ) {

        if (
                level == null
                        || origin == null
                        || target == null
                        || interactionDistance <= 0.0D
        ) {
            return null;
        }

        int searchRadius =
                Math.max(
                        1,
                        (int) Math.ceil(
                                interactionDistance
                        )
                );

        BlockPos best =
                null;

        double bestScore =
                Double.POSITIVE_INFINITY;

        Vec3 targetCenter =
                Vec3.atCenterOf(
                        target
                );

        for (
                int offsetY = -searchRadius;
                offsetY <= searchRadius;
                offsetY++
        ) {

            for (
                    int offsetX = -searchRadius;
                    offsetX <= searchRadius;
                    offsetX++
            ) {

                for (
                        int offsetZ = -searchRadius;
                        offsetZ <= searchRadius;
                        offsetZ++
                ) {

                    /*
                     * Never select the same X/Z column as the target.
                     * Standing directly beneath a high log is not a useful
                     * mining approach position.
                     */
                    if (
                            offsetX == 0
                                    && offsetZ == 0
                    ) {
                        continue;
                    }

                    BlockPos candidate =
                            target.offset(
                                    offsetX,
                                    offsetY,
                                    offsetZ
                            );

                    if (
                            !isTraversablePosition(
                                    level,
                                    candidate
                            )
                    ) {
                        continue;
                    }

                    Vec3 estimatedEyePosition =
                            Vec3.atBottomCenterOf(
                                    candidate
                            ).add(
                                    0.0D,
                                    1.62D,
                                    0.0D
                            );

                    double targetDistance =
                            estimatedEyePosition.distanceTo(
                                    targetCenter
                            );

                    if (
                            targetDistance
                                    > interactionDistance
                    ) {
                        continue;
                    }

                    DAI_Path path =
                            find(
                                    level,
                                    origin,
                                    candidate
                            );

                    if (path == null) {
                        continue;
                    }

                    double verticalDistance =
                            Math.abs(
                                    origin.getY()
                                            - candidate.getY()
                            );

                    /*
                     * Prefer the shortest actual reachable path.
                     */
                    double score =
                            Math.max(
                                    0,
                                    path.nodes().size() - 1
                            )
                                    + verticalDistance * 0.25D
                                    + targetDistance * 0.05D;

                    if (
                            score
                                    >= bestScore
                    ) {
                        continue;
                    }

                    bestScore =
                            score;

                    best =
                            candidate.immutable();
                }
            }
        }

        return best;
    }

    /**
     * Destructive fallback used only after ordinary approach pathfinding
     * fails. It selects one breakable obstruction at a time, allowing the
     * caller to clear it and then retry the normal pathfinder.
     */
    public static BlockPos findApproachObstruction(
            Level level,
            BlockPos origin,
            BlockPos target,
            double interactionDistance
    ) {

        if (
                level == null
                        || origin == null
                        || target == null
        ) {
            return null;
        }

        int radius =
                Math.max(
                        2,
                        (int) Math.ceil(
                                interactionDistance
                        )
                );

        BlockPos best =
                null;

        double bestScore =
                Double.MAX_VALUE;

        for (int deltaY = -1; deltaY <= 2; deltaY++) {

            for (int deltaX = -radius; deltaX <= radius; deltaX++) {

                for (int deltaZ = -radius; deltaZ <= radius; deltaZ++) {

                    BlockPos candidate =
                            target.offset(
                                    deltaX,
                                    deltaY,
                                    deltaZ
                            );

                    Vec3 eyePosition =
                            Vec3.atBottomCenterOf(
                                    candidate
                            ).add(
                                    0.0D,
                                    1.62D,
                                    0.0D
                            );

                    if (
                            eyePosition.distanceTo(
                                    Vec3.atCenterOf(
                                            target
                                    )
                            )
                                    > interactionDistance
                    ) {
                        continue;
                    }

                    if (
                            isTraversablePosition(
                                    level,
                                    candidate
                            )
                    ) {
                        continue;
                    }

                    BlockPos obstruction =
                            firstBreakableClearanceBlock(
                                    level,
                                    candidate
                            );

                    if (
                            obstruction == null
                                    || obstruction.equals(
                                    target
                            )
                    ) {
                        continue;
                    }

                    BlockPos staging =
                            findReachableStagingPosition(
                                    level,
                                    origin,
                                    candidate
                            );

                    if (staging == null) {
                        continue;
                    }

                    double score =
                            Math.sqrt(
                                    horizontalDistanceSquared(
                                            origin,
                                            staging
                                    )
                            )
                                    + Math.abs(
                                    origin.getY()
                                            - staging.getY()
                            ) * 1.25D;

                    if (score >= bestScore) {
                        continue;
                    }

                    bestScore =
                            score;

                    best =
                            obstruction.immutable();
                }
            }
        }

        return best;
    }

    private static BlockPos firstBreakableClearanceBlock(
            Level level,
            BlockPos candidate
    ) {

        if (
                !isPassable(
                        level,
                        candidate
                )
                        && isBreakableForApproach(
                        level,
                        candidate
                )
        ) {
            return candidate;
        }

        BlockPos head =
                candidate.above();

        if (
                !isPassable(
                        level,
                        head
                )
                        && isBreakableForApproach(
                        level,
                        head
                )
        ) {
            return head;
        }

        return null;
    }

    private static boolean isBreakableForApproach(
            Level level,
            BlockPos position
    ) {

        if (
                !level.getFluidState(
                        position
                ).isEmpty()
        ) {
            return false;
        }

        BlockState state =
                level.getBlockState(
                        position
                );

        if (
                state.isAir()
                        || state.getCollisionShape(
                        level,
                        position
                ).isEmpty()
        ) {
            return false;
        }

        return state.getDestroySpeed(
                level,
                position
        ) >= 0.0F;
    }

    private static BlockPos findReachableStagingPosition(
            Level level,
            BlockPos origin,
            BlockPos candidate
    ) {

        BlockPos[] stagingCandidates = {
                candidate.north(),
                candidate.south(),
                candidate.east(),
                candidate.west(),
                candidate.below().north(),
                candidate.below().south(),
                candidate.below().east(),
                candidate.below().west()
        };

        for (BlockPos staging : stagingCandidates) {

            if (
                    !isTraversablePosition(
                            level,
                            staging
                    )
            ) {
                continue;
            }

            if (
                    find(
                            level,
                            origin,
                            staging
                    ) != null
            ) {
                return staging.immutable();
            }
        }

        return null;
    }

    /**
     * Tests whether the player can travel through the horizontal corridor
     * between two points without clipping solid terrain.
     *
     * This is intentionally conservative. It samples a player-width corridor
     * instead of treating a path node as a single zero-width point.
     *
     * Vertical traversal is still owned by the discrete A* nodes; this helper
     * is primarily used to look ahead across ordinary walkable terrain.
     */
    public static boolean canTraverseSegment(
            Level level,
            Vec3 from,
            Vec3 to
    ) {

        if (
                level == null
                        || from == null
                        || to == null
        ) {
            return false;
        }

        /*
         * Do not shortcut deliberate vertical steps. Jump/drop handling
         * remains node-based so the controller cannot cut across unsupported
         * space merely because the straight line looks clear.
         */
        if (
                Math.abs(
                        to.y - from.y
                )
                        > 0.60D
        ) {
            return false;
        }

        double deltaX =
                to.x - from.x;

        double deltaZ =
                to.z - from.z;

        double horizontalDistance =
                Math.sqrt(
                        deltaX * deltaX
                                + deltaZ * deltaZ
                );

        if (horizontalDistance <= 0.001D) {
            return true;
        }

        int samples =
                Math.max(
                        1,
                        (int) Math.ceil(
                                horizontalDistance / 0.25D
                        )
                );

        /*
         * Slightly smaller than the player's actual half-width. This avoids
         * false negatives from sampling directly on block boundaries while
         * still detecting narrow corners that a point-only path misses.
         */
        double radius =
                0.28D;

        double[][] offsets = {
                {0.0D, 0.0D},
                {radius, 0.0D},
                {-radius, 0.0D},
                {0.0D, radius},
                {0.0D, -radius},
                {radius, radius},
                {radius, -radius},
                {-radius, radius},
                {-radius, -radius}
        };

        for (int sample = 0; sample <= samples; sample++) {

            double progress =
                    (double) sample
                            / (double) samples;

            double x =
                    from.x
                            + deltaX * progress;

            double y =
                    from.y
                            + (to.y - from.y) * progress;

            double z =
                    from.z
                            + deltaZ * progress;

            for (double[] offset : offsets) {

                BlockPos feet =
                        BlockPos.containing(
                                x + offset[0],
                                y,
                                z + offset[1]
                        );

                BlockPos head =
                        feet.above();

                if (
                        !isPassable(
                                level,
                                feet
                        )
                                || !isPassable(
                                level,
                                head
                        )
                ) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Returns true for either a normal standing position or water that
     * the navigation controller can swim through.
     */
    public static boolean isTraversablePosition(
            Level level,
            BlockPos position
    ) {

        return isWalkablePosition(
                level,
                position
        )
                || isWaterPosition(
                level,
                position
        );
    }

    public static boolean isWalkablePosition(
            Level level,
            BlockPos position
    ) {

        if (
                level == null
                        || position == null
        ) {
            return false;
        }

        /*
         * Water is handled separately. It should not accidentally be
         * interpreted as an ordinary land node.
         */
        if (
                isWaterPosition(
                        level,
                        position
                )
        ) {
            return false;
        }

        BlockPos head =
                position.above();

        BlockPos support =
                position.below();

        if (
                !isPassable(
                        level,
                        position
                )
                        || !isPassable(
                        level,
                        head
                )
        ) {
            return false;
        }

        return isSafeSupport(
                level,
                support
        );
    }

    /**
     * Returns whether this position represents swimmable water.
     */
    public static boolean isWaterPosition(
            Level level,
            BlockPos position
    ) {

        if (
                level == null
                        || position == null
        ) {
            return false;
        }

        return level.getFluidState(
                position
        ).is(
                FluidTags.WATER
        );
    }

    private static boolean isPassable(
            Level level,
            BlockPos position
    ) {

        BlockState state =
                level.getBlockState(
                        position
                );

        /*
         * Lava is never considered passable for autonomous navigation.
         */
        if (
                level.getFluidState(
                        position
                ).is(
                        FluidTags.LAVA
                )
        ) {
            return false;
        }

        return state.getCollisionShape(
                level,
                position
        ).isEmpty();
    }

    private static boolean isSafeSupport(
            Level level,
            BlockPos position
    ) {

        BlockState state =
                level.getBlockState(
                        position
                );

        if (
                state.isAir()
                        || state.is(
                        Blocks.LAVA
                )
                        || state.is(
                        Blocks.FIRE
                )
                        || state.is(
                        Blocks.SOUL_FIRE
                )
                        || level.getFluidState(
                        position
                ).is(
                        FluidTags.LAVA
                )
        ) {
            return false;
        }

        return !state.getCollisionShape(
                level,
                position
        ).isEmpty();
    }

    private static DAI_Path reconstructPath(
            Map<BlockPos, BlockPos> previous,
            BlockPos start,
            BlockPos destination
    ) {

        List<BlockPos> nodes =
                new ArrayList<>();

        BlockPos current =
                destination;

        nodes.add(
                current
        );

        while (
                !current.equals(
                        start
                )
        ) {

            current =
                    previous.get(
                            current
                    );

            if (current == null) {
                return null;
            }

            nodes.add(
                    current
            );
        }

        Collections.reverse(
                nodes
        );

        return new DAI_Path(
                nodes
        );
    }

    private static double heuristic(
            BlockPos from,
            BlockPos to
    ) {

        int deltaX =
                Math.abs(
                        from.getX()
                                - to.getX()
                );

        int deltaY =
                Math.abs(
                        from.getY()
                                - to.getY()
                );

        int deltaZ =
                Math.abs(
                        from.getZ()
                                - to.getZ()
                );

        return deltaX
                + deltaZ
                + deltaY * 1.25D;
    }

    private static double horizontalDistanceSquared(
            BlockPos first,
            BlockPos second
    ) {

        double deltaX =
                first.getX()
                        - second.getX();

        double deltaZ =
                first.getZ()
                        - second.getZ();

        return deltaX * deltaX
                + deltaZ * deltaZ;
    }

    private record PathNode(
            BlockPos position,
            double costFromStart,
            double estimatedTotalCost
    ) {
    }

    private record PathStep(
            BlockPos position,
            double cost
    ) {
    }
}