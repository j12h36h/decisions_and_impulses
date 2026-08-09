package io.github.j12h36h.dai.logics.controller;

import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.input.DAI_InputState;
import io.github.j12h36h.dai.logics.navigation.DAI_Path;
import io.github.j12h36h.dai.logics.navigation.DAI_PathFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class DAI_PathController {

    /*
     * Intermediate nodes are guidance points, not exact destinations.
     *
     * A value greater than half a block prevents the player from
     * orbiting around a node after slightly overshooting its center.
     */
    private static final double NODE_REACH_DISTANCE =
            0.85D;

    private static final double DESTINATION_REACH_DISTANCE =
            0.70D;

    private static final double MINIMUM_PROGRESS_DISTANCE =
            0.05D;

    private static final int STUCK_CHECK_INTERVAL =
            20;

    private static final int MAX_STUCK_CHECKS =
            3;

    private static final int DEBUG_LOG_INTERVAL =
            20;

    private static DAI_Path path;

    private static int nodeIndex;

    private static boolean active;

    private static int stuckCheckTicks;
    private static int stuckChecks;
    private static int debugLogTicks;

    private static Vec3 lastProgressPosition;

    private DAI_PathController() {
        // Utility class.
    }

    public static void start(
            DAI_Path requestedPath
    ) {

        if (
                requestedPath == null
                        || requestedPath.isEmpty()
        ) {

            finish(
                    DAI_ActionResult.FAILURE,
                    "path was null or empty"
            );

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
                    "player or level was unavailable"
            );

            return;
        }

        path =
                requestedPath;

        nodeIndex =
                path.size() > 1
                        ? 1
                        : 0;

        active =
                true;

        DAI_InputState.setManagedOverride(
                true
        );

        stuckCheckTicks =
                STUCK_CHECK_INTERVAL;

        stuckChecks =
                0;

        debugLogTicks =
                DEBUG_LOG_INTERVAL;

        lastProgressPosition =
                minecraft.player.position();

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Started following path with {} node(s).",
                path.size()
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
                        || path == null
        ) {

            finish(
                    DAI_ActionResult.FAILURE,
                    "player, level, or path became unavailable"
            );

            return;
        }

        if (
                nodeIndex < 0
                        || nodeIndex >= path.size()
        ) {

            finish(
                    DAI_ActionResult.FAILURE,
                    "path node index became invalid"
            );

            return;
        }

        Vec3 playerPosition =
                minecraft.player.position();

        /*
         * Advance through any intermediate nodes that are already close
         * enough. This also handles momentum carrying the player through
         * more than one node between ticks.
         */
        advanceReachedNodes(
                playerPosition
        );

        if (!active) {
            return;
        }

        if (
                nodeIndex < 0
                        || nodeIndex >= path.size()
        ) {

            finish(
                    DAI_ActionResult.FAILURE,
                    "path node index became invalid after advancing"
            );

            return;
        }

        BlockPos currentNode =
                path.node(
                        nodeIndex
                );

        Vec3 targetPosition =
                standingCenter(
                        currentNode
                );

        /*
         * Enable swim assist before entering a water node and keep it
         * active while the player remains in water.
         */
        handleSwimming(
                minecraft,
                currentNode
        );

        facePathDirection(
                minecraft,
                targetPosition
        );

        handleJump(
                minecraft,
                currentNode
        );

        /*
         * Move toward the path node in world space instead of steering the
         * camera and blindly holding forward. This prevents the path follower
         * from orbiting nodes while its yaw slowly catches up.
         */
        MovementDebug movementDebug =
                moveToward(
                        minecraft,
                        targetPosition
                );

        tickDebugLogging(
                minecraft,
                currentNode,
                targetPosition,
                movementDebug
        );

        tickStuckDetection(
                minecraft
        );
    }

    private static void advanceReachedNodes(
            Vec3 playerPosition
    ) {

        while (
                active
                        && path != null
                        && nodeIndex >= 0
                        && nodeIndex < path.size()
        ) {

            BlockPos node =
                    path.node(
                            nodeIndex
                    );

            Vec3 target =
                    standingCenter(
                            node
                    );

            double distance =
                    horizontalDistance(
                            playerPosition,
                            target
                    );

            boolean finalNode =
                    nodeIndex
                            >= path.size() - 1;

            double requiredDistance =
                    finalNode
                            ? DESTINATION_REACH_DISTANCE
                            : NODE_REACH_DISTANCE;

            if (
                    distance
                            > requiredDistance
            ) {
                return;
            }

            if (finalNode) {

                finish(
                        DAI_ActionResult.SUCCESS,
                        "destination reached"
                );

                return;
            }

            nodeIndex++;

            stuckCheckTicks =
                    STUCK_CHECK_INTERVAL;

            stuckChecks =
                    0;

            lastProgressPosition =
                    playerPosition;
        }
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

        clearState();

        if (wasActive) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.CANCELLED
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Active path following cancelled during reset."
            );
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static DAI_Path path() {
        return path;
    }

    public static int nodeIndex() {
        return nodeIndex;
    }

    public static BlockPos currentNode() {

        if (
                path == null
                        || path.isEmpty()
                        || nodeIndex < 0
                        || nodeIndex >= path.size()
        ) {
            return null;
        }

        return path.node(
                nodeIndex
        );
    }

    private static void handleSwimming(
            Minecraft minecraft,
            BlockPos targetNode
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {
            return;
        }

        boolean playerInWater =
                minecraft.player.isInWater();

        boolean targetInWater =
                DAI_PathFinder.isWaterPosition(
                        minecraft.level,
                        targetNode
                );

        boolean shouldSwim =
                playerInWater
                        || targetInWater;

        if (
                DAI_MoveController.isSwimEnabled()
                        == shouldSwim
        ) {
            return;
        }

        DAI_MoveController.setSwim(
                shouldSwim
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Path swim assist {}.",
                shouldSwim
                        ? "enabled"
                        : "disabled"
        );
    }

    private static void handleJump(
            Minecraft minecraft,
            BlockPos targetNode
    ) {

        if (
                minecraft.player == null
                        || !minecraft.player.onGround()
        ) {
            return;
        }

        int playerY =
                minecraft.player
                        .blockPosition()
                        .getY();

        if (
                targetNode.getY()
                        <= playerY
        ) {
            return;
        }

        minecraft.player.jumpFromGround();

        DAI_Core.LOGGER.debug(
                "<DAI>: Path follower requested jump toward node {}.",
                targetNode
        );
    }

    private static void tickStuckDetection(
            Minecraft minecraft
    ) {

        if (
                minecraft.player == null
                        || lastProgressPosition == null
        ) {
            return;
        }

        stuckCheckTicks--;

        if (stuckCheckTicks > 0) {
            return;
        }

        stuckCheckTicks =
                STUCK_CHECK_INTERVAL;

        Vec3 currentPosition =
                minecraft.player.position();

        double movement =
                horizontalDistance(
                        lastProgressPosition,
                        currentPosition
                );

        lastProgressPosition =
                currentPosition;

        if (
                movement
                        >= MINIMUM_PROGRESS_DISTANCE
        ) {

            stuckChecks =
                    0;

            return;
        }

        stuckChecks++;

        DAI_Core.LOGGER.debug(
                "<DAI>: Path follower made insufficient progress ({}/{}).",
                stuckChecks,
                MAX_STUCK_CHECKS
        );

        if (
                stuckChecks
                        >= MAX_STUCK_CHECKS
        ) {

            finish(
                    DAI_ActionResult.FAILURE,
                    "player became stuck while following path"
            );
        }
    }

    private static void facePathDirection(
            Minecraft minecraft,
            Vec3 targetPosition
    ) {

        if (
                minecraft.player == null
                        || targetPosition == null
        ) {
            return;
        }

        Vec3 playerPosition =
                minecraft.player.position();

        double deltaX =
                targetPosition.x
                        - playerPosition.x;

        double deltaZ =
                targetPosition.z
                        - playerPosition.z;

        if (
                Math.abs(
                        deltaX
                ) <= 0.001D
                        && Math.abs(
                        deltaZ
                ) <= 0.001D
        ) {
            return;
        }

        float targetYaw =
                (float) (
                        Math.toDegrees(
                                Math.atan2(
                                        deltaZ,
                                        deltaX
                                )
                        )
                                - 90.0D
                );

        float currentYaw =
                minecraft.player.getYRot();

        float yawDifference =
                Mth.wrapDegrees(
                        targetYaw
                                - currentYaw
                );

        /*
         * Camera follows the route gently.
         */
        float maximumYawTurn =
                4.0F;

        float yawStep =
                Mth.clamp(
                        yawDifference,
                        -maximumYawTurn,
                        maximumYawTurn
                );

        float finalYaw =
                Mth.wrapDegrees(
                        currentYaw
                                + yawStep
                );

        /*
         * While travelling, gradually return the camera toward a
         * slightly downward-facing exploration angle.
         */
        float currentPitch =
                minecraft.player.getXRot();

        float targetPitch =
                5.0F;

        float pitchDifference =
                targetPitch
                        - currentPitch;

        float maximumPitchTurn =
                2.0F;

        float pitchStep =
                Mth.clamp(
                        pitchDifference,
                        -maximumPitchTurn,
                        maximumPitchTurn
                );

        float finalPitch =
                Mth.clamp(
                        currentPitch
                                + pitchStep,
                        -90.0F,
                        90.0F
                );

        /*
         * Path following requests camera orientation through DAI_InputState
         * only. DAI_LookController is the single component responsible for
         * applying yaw/pitch to the player.
         */
        DAI_InputState
                .look()
                .setRotation(
                        finalYaw,
                        finalPitch
                );
    }

    private static MovementDebug moveToward(
            Minecraft minecraft,
            Vec3 targetPosition
    ) {

        if (
                minecraft.player == null
                        || targetPosition == null
        ) {
            return null;
        }

        Vec3 playerPosition =
                minecraft.player.position();

        double deltaX =
                targetPosition.x
                        - playerPosition.x;

        double deltaZ =
                targetPosition.z
                        - playerPosition.z;

        double length =
                Math.sqrt(
                        deltaX * deltaX
                                + deltaZ * deltaZ
                );

        if (length <= 0.001D) {

            DAI_MoveController.stop();

            return new MovementDebug(
                    deltaX,
                    deltaZ,
                    length,
                    0.0D,
                    0.0D,
                    minecraft.player.getYRot(),
                    0.0F,
                    0.0F
            );
        }

        double worldX =
                deltaX / length;

        double worldZ =
                deltaZ / length;

        double yawRadians =
                Math.toRadians(
                        minecraft.player.getYRot()
                );

        /*
         * Minecraft yaw convention:
         *
         * yaw   0 = +Z
         * yaw  90 = -X
         *
         * DAI movement uses positive strafe for Minecraft's LEFT input.
         * Therefore the horizontal local basis is:
         *
         * forward = (-sin(yaw), cos(yaw))
         * left    = ( cos(yaw), sin(yaw))
         *
         * Project directly onto the left basis. Negating this projection
         * mirrors lateral movement and can drive the player away from the
         * requested world-space path direction.
         */
        double forwardX =
                -Math.sin(
                        yawRadians
                );

        double forwardZ =
                Math.cos(
                        yawRadians
                );

        double leftX =
                Math.cos(
                        yawRadians
                );

        double leftZ =
                Math.sin(
                        yawRadians
                );

        float forward =
                (float) (
                        worldX * forwardX
                                + worldZ * forwardZ
                );

        float strafe =
                (float) (
                        worldX * leftX
                                + worldZ * leftZ
                );

        forward =
                Mth.clamp(
                        forward,
                        -1.0F,
                        1.0F
                );

        strafe =
                Mth.clamp(
                        strafe,
                        -1.0F,
                        1.0F
                );

        DAI_MoveController.hold(
                forward,
                strafe
        );

        return new MovementDebug(
                deltaX,
                deltaZ,
                length,
                worldX,
                worldZ,
                minecraft.player.getYRot(),
                forward,
                strafe
        );
    }

    private static void tickDebugLogging(
            Minecraft minecraft,
            BlockPos currentNode,
            Vec3 targetPosition,
            MovementDebug movement
    ) {

        if (
                minecraft.player == null
                        || movement == null
        ) {
            return;
        }

        debugLogTicks--;

        if (debugLogTicks > 0) {
            return;
        }

        debugLogTicks =
                DEBUG_LOG_INTERVAL;

        Vec3 position =
                minecraft.player.position();

        DAI_Core.LOGGER.info(
                "<DAI:NAV> pos=({},{},{}) yaw={} pitch={} nodeIndex={}/{} node={} nodeCenter=({},{},{}) "
                        + "delta=({},{}) worldDir=({},{}) local=(forward={},strafe={}) "
                        + "input=(forward={},strafe={},jump={},sneak={},sprint={}) status={}",
                format(position.x),
                format(position.y),
                format(position.z),
                format(minecraft.player.getYRot()),
                format(minecraft.player.getXRot()),
                nodeIndex,
                path == null
                        ? 0
                        : path.size(),
                currentNode,
                format(targetPosition.x),
                format(targetPosition.y),
                format(targetPosition.z),
                format(movement.deltaX()),
                format(movement.deltaZ()),
                format(movement.worldX()),
                format(movement.worldZ()),
                format(movement.forward()),
                format(movement.strafe()),
                format(DAI_InputState.movement().forward()),
                format(DAI_InputState.movement().strafe()),
                DAI_InputState.movement().jump(),
                DAI_InputState.movement().sneak(),
                DAI_InputState.movement().sprint(),
                DAI_ActionStatus.get()
        );
    }

    private static String format(
            double value
    ) {

        return String.format(
                java.util.Locale.ROOT,
                "%.3f",
                value
        );
    }

    private record MovementDebug(
            double deltaX,
            double deltaZ,
            double length,
            double worldX,
            double worldZ,
            float yaw,
            float forward,
            float strafe
    ) {
    }

    private static Vec3 standingCenter(
            BlockPos position
    ) {

        return new Vec3(
                position.getX() + 0.5D,
                position.getY(),
                position.getZ() + 0.5D
        );
    }

    private static double horizontalDistance(
            Vec3 first,
            Vec3 second
    ) {

        double deltaX =
                second.x
                        - first.x;

        double deltaZ =
                second.z
                        - first.z;

        return Math.sqrt(
                deltaX * deltaX
                        + deltaZ * deltaZ
        );
    }

    private static void finish(
            DAI_ActionResult result,
            String reason
    ) {

        BlockPos destination =
                path == null
                        || path.isEmpty()
                        ? null
                        : path.last();

        clearState();

        DAI_ActionStatus.set(
                result
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Finished path following toward {} with result={}: {}.",
                destination,
                result,
                reason
        );
    }

    private static void clearState() {

        DAI_MoveController.setSwim(
                false
        );

        DAI_MoveController.stop();

        DAI_InputState.setManagedOverride(
                false
        );

        path =
                null;

        nodeIndex =
                0;

        active =
                false;

        stuckCheckTicks =
                0;

        stuckChecks =
                0;

        debugLogTicks =
                0;

        lastProgressPosition =
                null;
    }
}