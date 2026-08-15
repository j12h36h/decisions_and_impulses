package io.github.j12h36h.dai.client.logics.approach;

import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.input.DAI_InputState;
import io.github.j12h36h.dai.client.logics.navigation.DAI_Path;
import io.github.j12h36h.dai.client.logics.navigation.DAI_PathFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class DAI_ApproachPathing {

    private static final double NODE_REACHED_DISTANCE =
            0.55D;

    private static final double NODE_PASSED_LATERAL_DISTANCE =
            0.90D;

    private static final int MAX_LOOKAHEAD_NODES =
            4;

    private DAI_ApproachPathing() {
        // Utility class.
    }

    public static PathBuildResult rebuild(
            Minecraft minecraft
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || DAI_ApproachState.target() == null
        ) {
            return PathBuildResult.FAILURE;
        }

        BlockPos origin =
                minecraft.player.blockPosition();

        BlockPos destination =
                DAI_PathFinder.findNearestApproachPosition(
                        minecraft.level,
                        origin,
                        DAI_ApproachState.target(),
                        DAI_ApproachState.stopDistance()
                );

        if (destination == null) {

            clear();

            return PathBuildResult.FAILURE;
        }

        /*
         * The player's current block may itself be the nearest valid
         * interaction position.
         *
         * Never build a one-node route back to the position the player
         * already occupies.
         */
        if (
                destination.equals(
                        origin
                )
        ) {

            DAI_ApproachState.setApproachPosition(
                    destination.immutable()
            );

            DAI_ApproachState.setApproachPath(
                    null
            );

            DAI_ApproachState.setPathIndex(
                    0
            );

            DAI_ApproachState.resetRouteProgress();

            DAI_Core.debug(
                    "<DAI>: Current position {} is already the nearest approach position for target {}.",
                    origin,
                    DAI_ApproachState.target()
            );

            return PathBuildResult.ALREADY_AT_DESTINATION;
        }

        DAI_Path path =
                DAI_PathFinder.find(
                        minecraft.level,
                        origin,
                        destination
                );

        if (
                path == null
                        || path.nodes().isEmpty()
        ) {

            clear();

            return PathBuildResult.FAILURE;
        }

        DAI_ApproachState.setApproachPosition(
                destination.immutable()
        );

        DAI_ApproachState.setApproachPath(
                path
        );

        DAI_ApproachState.clearFallback();

        DAI_ApproachState.setPathIndex(
                path.nodes().size() > 1
                        ? 1
                        : path.nodes().size()
        );

        DAI_ApproachState.resetRouteProgress();

        DAI_Core.LOGGER.info(
                "<DAI>: Approach path selected from {} to {} for target {} ({} node(s)).",
                origin,
                destination,
                DAI_ApproachState.target(),
                path.nodes().size()
        );

        return PathBuildResult.PATH_READY;
    }

    public static boolean follow(
            Minecraft minecraft
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || DAI_ApproachState.approachPath() == null
        ) {
            return false;
        }

        List<BlockPos> nodes =
                DAI_ApproachState
                        .approachPath()
                        .nodes();

        advanceCompletedNodes(
                minecraft,
                nodes
        );

        int pathIndex =
                DAI_ApproachState.pathIndex();

        if (pathIndex >= nodes.size()) {

            stopMovement();

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
         * Lookahead may steer beyond the immediate node. If the player has
         * actually reached the lookahead node, all skipped same-level nodes
         * are complete as well.
         */
        if (
                steeringIndex > pathIndex
                        && hasReachedNode(
                        minecraft,
                        steeringNode
                )
        ) {

            DAI_ApproachState.setPathIndex(
                    steeringIndex + 1
            );

            DAI_ApproachState.resetRouteProgress();

            pathIndex =
                    DAI_ApproachState.pathIndex();

            if (pathIndex >= nodes.size()) {

                stopMovement();

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

        float travelYaw =
                DAI_ApproachMovement.maintainTravelLook(
                        minecraft,
                        steeringPoint
                );

        DAI_ApproachMovement.MovementResult movement =
                DAI_ApproachMovement.moveToward(
                        minecraft,
                        steeringPoint,
                        travelYaw
                );

        tickDebugLogging(
                minecraft,
                immediateNode,
                steeringNode,
                steeringPoint,
                movement
        );

        /*
         * Vertical movement remains tied to the immediate path node.
         * Lookahead steering must never invent a jump.
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

        DAI_ApproachRecovery.handleMovement(
                minecraft,
                steeringPoint
        );

        return true;
    }

    public static boolean hasReachedNode(
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
                DAI_ApproachMovement.horizontalDistance(
                        minecraft.player.position(),
                        nodeCenter
                );

        double verticalDistance =
                Math.abs(
                        minecraft.player.getY()
                                - node.getY()
                );

        return horizontalDistance
                <= NODE_REACHED_DISTANCE
                && verticalDistance
                <= 0.75D;
    }

    private static void advanceCompletedNodes(
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

        while (
                DAI_ApproachState.pathIndex()
                        < nodes.size()
        ) {

            int pathIndex =
                    DAI_ApproachState.pathIndex();

            BlockPos currentNode =
                    nodes.get(
                            pathIndex
                    );

            Vec3 currentCenter =
                    Vec3.atBottomCenterOf(
                            currentNode
                    );

            double horizontalDistance =
                    DAI_ApproachMovement.horizontalDistance(
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
                            && horizontalDistance
                            <= NODE_REACHED_DISTANCE
                            && verticalDistance
                            <= 0.75D
            ) {

                DAI_ApproachState.setPathIndex(
                        pathIndex + 1
                );

                DAI_ApproachState.resetRouteProgress();

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
                            && hasPassedSegment(
                            playerPosition,
                            previousCenter,
                            currentCenter
                    )
                            && verticalDistance
                            <= 0.75D
            ) {

                DAI_ApproachState.setPathIndex(
                        pathIndex + 1
                );

                DAI_ApproachState.resetRouteProgress();

                continue;
            }

            return;
        }
    }

    private static void tickDebugLogging(
            Minecraft minecraft,
            BlockPos immediateNode,
            BlockPos steeringNode,
            Vec3 steeringPoint,
            DAI_ApproachMovement.MovementResult movement
    ) {

        if (
                minecraft.player == null
                        || movement == null
        ) {
            return;
        }

        if (
                DAI_ApproachState.decrementDebugLogTicks()
                        > 0
        ) {
            return;
        }

        DAI_ApproachState.setDebugLogTicks(
                20
        );

        Vec3 position =
                minecraft.player.position();

        DAI_Core.LOGGER.info(
                "<DAI:APPROACH> pos=({},{},{}) yaw={} pitch={} target={} approach={} "
                        + "pathIndex={}/{} immediate={} steering={} steeringPoint=({},{},{}) "
                        + "delta=({},{}) worldDir=({},{}) movementYaw={} calculated=(forward={},strafe={}) "
                        + "input=(forward={},strafe={},jump={},sneak={},sprint={}) generation={} status={}",
                formatDebug(position.x),
                formatDebug(position.y),
                formatDebug(position.z),
                formatDebug(minecraft.player.getYRot()),
                formatDebug(minecraft.player.getXRot()),
                DAI_ApproachState.target(),
                DAI_ApproachState.approachPosition(),
                DAI_ApproachState.pathIndex(),
                DAI_ApproachState.approachPath() == null
                        ? 0
                        : DAI_ApproachState.approachPath().nodes().size(),
                immediateNode,
                steeringNode,
                formatDebug(steeringPoint.x),
                formatDebug(steeringPoint.y),
                formatDebug(steeringPoint.z),
                formatDebug(movement.deltaX()),
                formatDebug(movement.deltaZ()),
                formatDebug(movement.worldX()),
                formatDebug(movement.worldZ()),
                formatDebug(movement.yaw()),
                formatDebug(movement.forward()),
                formatDebug(movement.strafe()),
                formatDebug(DAI_InputState.movement().forward()),
                formatDebug(DAI_InputState.movement().strafe()),
                DAI_InputState.movement().jump(),
                DAI_InputState.movement().sneak(),
                DAI_InputState.movement().sprint(),
                DAI_ApproachState.generation(),
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

        int pathIndex =
                DAI_ApproachState.pathIndex();

        int bestIndex =
                pathIndex;

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || pathIndex >= nodes.size()
        ) {
            return bestIndex;
        }

        Vec3 playerFeet =
                minecraft.player.position();

        int maximumIndex =
                Math.min(
                        nodes.size() - 1,
                        pathIndex
                                + MAX_LOOKAHEAD_NODES
                );

        for (
                int candidateIndex =
                pathIndex + 1;
                candidateIndex <= maximumIndex;
                candidateIndex++
        ) {

            BlockPos candidate =
                    nodes.get(
                            candidateIndex
                    );

            /*
             * Never look past a vertical transition.
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

    private static boolean hasPassedSegment(
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

        if (
                segmentLengthSquared
                        <= 0.0001D
        ) {
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

    public static void clear() {

        DAI_ApproachState.setApproachPosition(
                null
        );

        DAI_ApproachState.setApproachPath(
                null
        );

        DAI_ApproachState.setPathIndex(
                0
        );

        DAI_ApproachState.resetRouteProgress();
    }

    private static void stopMovement() {

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
    }

    public enum PathBuildResult {
        PATH_READY,
        ALREADY_AT_DESTINATION,
        FAILURE
    }
}
