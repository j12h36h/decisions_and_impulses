package io.github.j12h36h.dai.logics.navigation;

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

    private static final double WATER_COST =
            2.0D;

    private static final double WATER_VERTICAL_COST =
            2.25D;

    private static final double WATER_EXIT_COST =
            1.15D;

    private static final double VEGETATION_COST =
            1.75D;

    private static final double HEAD_VEGETATION_COST =
            0.75D;

    private static final double IRREGULAR_SUPPORT_COST =
            0.35D;

    private static final int RECOVERY_SEARCH_RADIUS =
            8;

    private static final int RECOVERY_VERTICAL_RANGE =
            6;

    private static final int RECOVERY_MAX_EXPANDED_NODES =
            2048;

    /*
     * Vanilla block interaction is based on reaching the block volume,
     * not specifically its center.
     *
     * This small tolerance also prevents floating-point edge cases from
     * rejecting a position sitting effectively at the requested range.
     */
    private static final double APPROACH_RANGE_EPSILON =
            0.10D;

    private static final int[][] HORIZONTAL_DIRECTIONS = {
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1}
    };

    private DAI_PathFinder() {
        // Utility class.
    }

    /*
     * ------------------------------------------------------------
     * PATH SEARCH
     * ------------------------------------------------------------
     */

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

        open.add(
                new PathNode(
                        immutableStart,
                        0.0D,
                        heuristic(
                                immutableStart,
                                immutableDestination
                        )
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

            for (PathStep step : steps) {

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

                open.add(
                        new PathNode(
                                neighbor,
                                tentativeCost,
                                tentativeCost
                                        + heuristic(
                                        neighbor,
                                        immutableDestination
                                )
                        )
                );
            }
        }

        return null;
    }

    /*
     * ------------------------------------------------------------
     * NEIGHBOR GENERATION
     * ------------------------------------------------------------
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

        if (
                isWaterPosition(
                        level,
                        start
                )
        ) {

            addWaterNeighbors(
                    level,
                    start,
                    neighbors
            );
        }

        for (int[] direction : HORIZONTAL_DIRECTIONS) {

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

        for (int[] direction : HORIZONTAL_DIRECTIONS) {

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

        if (
                containsPosition(
                        neighbors,
                        candidate
                )
        ) {
            return;
        }

        double cost;

        if (
                isWaterPosition(
                        level,
                        candidate
                )
        ) {

            cost =
                    baseCost
                            + WATER_COST;

        } else {

            cost =
                    baseCost
                            + terrainCost(
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

        boolean currentIsWater =
                isWaterPosition(
                        level,
                        position
                );

        if (currentIsWater) {

            addWaterNeighbors(
                    level,
                    position,
                    neighbors
            );
        }

        for (int[] direction : HORIZONTAL_DIRECTIONS) {

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

                addNeighbor(
                        neighbors,
                        sameLevel,
                        WATER_COST
                );

                continue;
            }

            if (
                    isWalkablePosition(
                            level,
                            sameLevel
                    )
            ) {

                double cost =
                        currentIsWater
                                ? WATER_EXIT_COST
                                : STRAIGHT_COST;

                addNeighbor(
                        neighbors,
                        sameLevel,
                        cost
                                + terrainCost(
                                level,
                                sameLevel
                        )
                );

                continue;
            }

            if (currentIsWater) {

                BlockPos shoreAbove =
                        position.offset(
                                offsetX,
                                1,
                                offsetZ
                        );

                if (
                        canExitWaterTo(
                                level,
                                position,
                                shoreAbove
                        )
                ) {

                    addNeighbor(
                            neighbors,
                            shoreAbove,
                            WATER_EXIT_COST
                                    + JUMP_COST
                                    + terrainCost(
                                    level,
                                    shoreAbove
                            )
                    );

                    continue;
                }
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

                addNeighbor(
                        neighbors,
                        jumpPosition,
                        JUMP_COST
                                + terrainCost(
                                level,
                                jumpPosition
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

                double cost =
                        isWaterPosition(
                                level,
                                dropPosition
                        )
                                ? WATER_COST
                                : DROP_COST
                                + terrainCost(
                                level,
                                dropPosition
                        );

                addNeighbor(
                        neighbors,
                        dropPosition,
                        cost
                );
            }
        }

        return neighbors;
    }

    private static void addWaterNeighbors(
            Level level,
            BlockPos position,
            List<PathStep> neighbors
    ) {

        if (
                level == null
                        || position == null
                        || neighbors == null
                        || !isWaterPosition(
                        level,
                        position
                )
        ) {
            return;
        }

        for (int[] direction : HORIZONTAL_DIRECTIONS) {

            BlockPos horizontal =
                    position.offset(
                            direction[0],
                            0,
                            direction[1]
                    );

            if (
                    isWaterPosition(
                            level,
                            horizontal
                    )
            ) {

                addNeighbor(
                        neighbors,
                        horizontal,
                        WATER_COST
                );
            }
        }

        BlockPos above =
                position.above();

        if (
                isWaterPosition(
                        level,
                        above
                )
        ) {

            addNeighbor(
                    neighbors,
                    above,
                    WATER_VERTICAL_COST
            );
        }

        BlockPos below =
                position.below();

        if (
                isWaterPosition(
                        level,
                        below
                )
        ) {

            addNeighbor(
                    neighbors,
                    below,
                    WATER_VERTICAL_COST
            );
        }
    }

    private static boolean canExitWaterTo(
            Level level,
            BlockPos current,
            BlockPos destination
    ) {

        if (
                level == null
                        || current == null
                        || destination == null
                        || !isWaterPosition(
                        level,
                        current
                )
        ) {
            return false;
        }

        if (
                destination.getY()
                        != current.getY() + 1
        ) {
            return false;
        }

        if (
                !isWalkablePosition(
                        level,
                        destination
                )
        ) {
            return false;
        }

        return isPassable(
                level,
                current.above()
        );
    }

    private static void addNeighbor(
            List<PathStep> neighbors,
            BlockPos position,
            double cost
    ) {

        if (
                neighbors == null
                        || position == null
        ) {
            return;
        }

        if (
                containsPosition(
                        neighbors,
                        position
                )
        ) {
            return;
        }

        neighbors.add(
                new PathStep(
                        position.immutable(),
                        cost
                )
        );
    }

    private static boolean containsPosition(
            List<PathStep> neighbors,
            BlockPos position
    ) {

        for (PathStep existing : neighbors) {

            if (
                    existing.position()
                            .equals(
                                    position
                            )
            ) {
                return true;
            }
        }

        return false;
    }

    /*
     * ------------------------------------------------------------
     * TERRAIN
     * ------------------------------------------------------------
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

        if (feet.is(Blocks.SNOW)) {
            cost += 0.10D;
        }

        if (
                isPassableNonAirBlock(
                        level,
                        position,
                        feet
                )
        ) {

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
                isWaterPosition(
                        level,
                        current
                )
        ) {
            return false;
        }

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

    /*
     * ------------------------------------------------------------
     * APPROACH POSITION SEARCH
     * ------------------------------------------------------------
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

        /*
         * First check the position the player already occupies.
         *
         * This is especially important when looking down at a log or other
         * block that is already reachable. Previously the origin could be
         * ignored by the candidate search.
         */
        if (
                isTraversablePosition(
                        level,
                        origin
                )
                        && canInteractWithBlockFrom(
                        origin,
                        target,
                        interactionDistance
                )
        ) {

            return origin.immutable();
        }

        /*
         * Horizontal interaction radius plus one block gives us enough room
         * to find stable standing positions near the edge of reach.
         *
         * Vertical search is deliberately larger. Targets below a ledge or
         * in a shallow depression should not cause the entire approach
         * search to fail merely because their Y differs significantly from
         * the player's current Y.
         */
        int horizontalRadius =
                Math.max(
                        2,
                        (int) Math.ceil(
                                interactionDistance
                        ) + 1
                );

        int verticalRadius =
                Math.max(
                        horizontalRadius,
                        MAX_DROP_DISTANCE + 2
                );

        BlockPos best =
                null;

        double bestScore =
                Double.POSITIVE_INFINITY;

        for (
                int offsetY = -verticalRadius;
                offsetY <= verticalRadius;
                offsetY++
        ) {

            for (
                    int offsetX = -horizontalRadius;
                    offsetX <= horizontalRadius;
                    offsetX++
            ) {

                for (
                        int offsetZ = -horizontalRadius;
                        offsetZ <= horizontalRadius;
                        offsetZ++
                ) {

                    BlockPos candidate =
                            target.offset(
                                    offsetX,
                                    offsetY,
                                    offsetZ
                            );

                    /*
                     * Never stand inside the target block itself.
                     */
                    if (
                            candidate.equals(
                                    target
                            )
                    ) {
                        continue;
                    }

                    if (
                            !isTraversablePosition(
                                    level,
                                    candidate
                            )
                    ) {
                        continue;
                    }

                    if (
                            !canInteractWithBlockFrom(
                                    candidate,
                                    target,
                                    interactionDistance
                            )
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

                    int pathLength =
                            Math.max(
                                    0,
                                    path.nodes().size() - 1
                            );

                    double verticalTravel =
                            Math.abs(
                                    origin.getY()
                                            - candidate.getY()
                            );

                    double interactionRange =
                            distanceFromEyeToBlock(
                                    candidate,
                                    target
                            );

                    double waterPenalty =
                            isWaterPosition(
                                    level,
                                    candidate
                            )
                                    ? 1.5D
                                    : 0.0D;

                    /*
                     * Reachable short paths dominate selection.
                     *
                     * Vertical travel and water are discouraged, while
                     * being comfortably inside interaction range provides
                     * a small secondary preference.
                     */
                    double score =
                            pathLength
                                    + verticalTravel * 0.35D
                                    + interactionRange * 0.05D
                                    + waterPenalty;

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
     * Tests reach against the target block's volume rather than its center.
     *
     * The nearest point of a full block can be considerably closer than the
     * center, particularly when the target is above or below the player.
     */
    private static boolean canInteractWithBlockFrom(
            BlockPos standingPosition,
            BlockPos target,
            double interactionDistance
    ) {

        return distanceFromEyeToBlock(
                standingPosition,
                target
        )
                <= interactionDistance
                + APPROACH_RANGE_EPSILON;
    }

    private static double distanceFromEyeToBlock(
            BlockPos standingPosition,
            BlockPos target
    ) {

        Vec3 eye =
                Vec3.atBottomCenterOf(
                        standingPosition
                ).add(
                        0.0D,
                        1.62D,
                        0.0D
                );

        double nearestX =
                clamp(
                        eye.x,
                        target.getX(),
                        target.getX() + 1.0D
                );

        double nearestY =
                clamp(
                        eye.y,
                        target.getY(),
                        target.getY() + 1.0D
                );

        double nearestZ =
                clamp(
                        eye.z,
                        target.getZ(),
                        target.getZ() + 1.0D
                );

        double deltaX =
                eye.x - nearestX;

        double deltaY =
                eye.y - nearestY;

        double deltaZ =
                eye.z - nearestZ;

        return Math.sqrt(
                deltaX * deltaX
                        + deltaY * deltaY
                        + deltaZ * deltaZ
        );
    }

    private static double clamp(
            double value,
            double minimum,
            double maximum
    ) {

        return Math.max(
                minimum,
                Math.min(
                        maximum,
                        value
                )
        );
    }

    /*
     * ------------------------------------------------------------
     * SAFE APPROACH RECOVERY
     * ------------------------------------------------------------
     */

    /**
     * Finds a temporary reachable staging position that improves the player's
     * ability to path toward the target.
     *
     * This is deliberately NON-DESTRUCTIVE.
     *
     * It is used when no standing position within interaction range of the
     * target is currently reachable.
     *
     * Typical example:
     *
     * player on tree canopy
     *      ↓
     * target several blocks below
     *      ↓
     * no direct interaction-position path
     *      ↓
     * walk toward a reachable lower/closer staging position
     *      ↓
     * rebuild normal approach from there
     *
     * The returned position must be reachable by the ordinary pathfinder, so
     * all normal walking/jumping/drop safety rules remain authoritative.
     */
    public static BlockPos findRecoveryPosition(
            Level level,
            BlockPos origin,
            BlockPos target
    ) {

        if (
                level == null
                        || origin == null
                        || target == null
        ) {
            return null;
        }

        boolean targetBelow =
                target.getY()
                        < origin.getY();

        double originTargetDistance =
                blockDistance(
                        origin,
                        target
                );

        BlockPos best =
                null;

        double bestScore =
                Double.POSITIVE_INFINITY;

        /*
         * Search around the PLAYER rather than around the target.
         *
         * The purpose of recovery is not to find the final interaction
         * position. It is to find somewhere reachable from which normal
         * approach planning has a better chance of succeeding.
         */
        for (
                int offsetY = -RECOVERY_VERTICAL_RANGE;
                offsetY <= 1;
                offsetY++
        ) {

            for (
                    int offsetX = -RECOVERY_SEARCH_RADIUS;
                    offsetX <= RECOVERY_SEARCH_RADIUS;
                    offsetX++
            ) {

                for (
                        int offsetZ = -RECOVERY_SEARCH_RADIUS;
                        offsetZ <= RECOVERY_SEARCH_RADIUS;
                        offsetZ++
                ) {

                    BlockPos candidate =
                            origin.offset(
                                    offsetX,
                                    offsetY,
                                    offsetZ
                            );

                    if (
                            candidate.equals(
                                    origin
                            )
                    ) {
                        continue;
                    }

                    if (
                            !isTraversablePosition(
                                    level,
                                    candidate
                            )
                    ) {
                        continue;
                    }

                    /*
                     * Recovery itself must be reachable through the ordinary
                     * movement graph.
                     *
                     * This is what prevents recovery from inventing unsafe
                     * drops or teleport-like transitions.
                     */
                    DAI_Path recoveryPath =
                            find(
                                    level,
                                    origin,
                                    candidate,
                                    RECOVERY_SEARCH_RADIUS + 4,
                                    RECOVERY_MAX_EXPANDED_NODES
                            );

                    if (
                            recoveryPath == null
                                    || recoveryPath.nodes().size() <= 1
                    ) {
                        continue;
                    }

                    double candidateTargetDistance =
                            blockDistance(
                                    candidate,
                                    target
                            );

                    double targetProgress =
                            originTargetDistance
                                    - candidateTargetDistance;

                    int descent =
                            origin.getY()
                                    - candidate.getY();

                    /*
                     * Recovery must actually improve something.
                     *
                     * When the target is below us, descending is useful even
                     * when the Euclidean target distance improves only slightly.
                     *
                     * Otherwise require genuine progress toward the target.
                     */
                    boolean meaningfulProgress;

                    if (targetBelow) {

                        /*
                         * Descending is useful only when it does not undo the
                         * progress of the previous staging move. The old
                         * `descent > 0` rule allowed a lower-but-farther tile
                         * to win, then an upper-but-closer tile to win on the
                         * next pass, producing a two-position recovery loop.
                         */
                        boolean usefulDescent =
                                descent > 0
                                        && candidateTargetDistance
                                        <= originTargetDistance + 0.35D;

                        meaningfulProgress =
                                targetProgress > 0.75D
                                        || usefulDescent;

                    } else {

                        meaningfulProgress =
                                targetProgress > 0.75D;
                    }

                    if (!meaningfulProgress) {
                        continue;
                    }

                    int pathLength =
                            recoveryPath.nodes().size()
                                    - 1;

                    double waterPenalty =
                            isWaterPosition(
                                    level,
                                    candidate
                            )
                                    ? 2.0D
                                    : 0.0D;

                    /*
                     * Prefer:
                     *
                     * 1. short safe recovery routes
                     * 2. positions closer to the real target
                     * 3. useful descent when the target is below
                     * 4. land over water
                     */
                    double descentBonus =
                            targetBelow
                                    ? Math.max(
                                    0,
                                    descent
                            ) * 0.45D
                                    : 0.0D;

                    double score =
                            pathLength
                                    + candidateTargetDistance * 0.15D
                                    + waterPenalty
                                    - descentBonus;

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
     * Simple block-center distance used only for recovery candidate ranking.
     */
    private static double blockDistance(
            BlockPos first,
            BlockPos second
    ) {

        if (
                first == null
                        || second == null
        ) {
            return Double.POSITIVE_INFINITY;
        }

        double deltaX =
                first.getX()
                        - second.getX();

        double deltaY =
                first.getY()
                        - second.getY();

        double deltaZ =
                first.getZ()
                        - second.getZ();

        return Math.sqrt(
                deltaX * deltaX
                        + deltaY * deltaY
                        + deltaZ * deltaZ
        );
    }

    /*
     * ------------------------------------------------------------
     * DESTRUCTIVE APPROACH FALLBACK
     * ------------------------------------------------------------
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

        for (
                int deltaY = -1;
                deltaY <= 2;
                deltaY++
        ) {

            for (
                    int deltaX = -radius;
                    deltaX <= radius;
                    deltaX++
            ) {

                for (
                        int deltaZ = -radius;
                        deltaZ <= radius;
                        deltaZ++
                ) {

                    BlockPos candidate =
                            target.offset(
                                    deltaX,
                                    deltaY,
                                    deltaZ
                            );

                    if (
                            !canInteractWithBlockFrom(
                                    candidate,
                                    target,
                                    interactionDistance
                            )
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

                    if (
                            score
                                    >= bestScore
                    ) {
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

    /*
     * ------------------------------------------------------------
     * SEGMENT TESTING
     * ------------------------------------------------------------
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

        if (
                horizontalDistance
                        <= 0.001D
        ) {
            return true;
        }

        int samples =
                Math.max(
                        1,
                        (int) Math.ceil(
                                horizontalDistance / 0.25D
                        )
                );

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

        for (
                int sample = 0;
                sample <= samples;
                sample++
        ) {

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

    /*
     * ------------------------------------------------------------
     * POSITION CLASSIFICATION
     * ------------------------------------------------------------
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

        if (
                !level.getFluidState(
                        position
                ).is(
                        FluidTags.WATER
                )
        ) {
            return false;
        }

        BlockState state =
                level.getBlockState(
                        position
                );

        return state.getCollisionShape(
                level,
                position
        ).isEmpty();
    }

    private static boolean isPassable(
            Level level,
            BlockPos position
    ) {

        BlockState state =
                level.getBlockState(
                        position
                );

        if (
                level.getFluidState(
                        position
                ).is(
                        FluidTags.LAVA
                )
        ) {
            return false;
        }

        /*
         * A vanilla snow layer has a thin collision shape, but a player's
         * feet remain in that block coordinate while standing/walking across
         * it. Treating every non-empty collision shape as impassable made
         * snowy terrain look like a field of walls.
         */
        if (
                state.is(Blocks.SNOW)
                        && state.getFluidState().isEmpty()
        ) {
            return true;
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

    /*
     * ------------------------------------------------------------
     * PATH HELPERS
     * ------------------------------------------------------------
     */

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