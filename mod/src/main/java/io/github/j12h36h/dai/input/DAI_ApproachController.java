package io.github.j12h36h.dai.input;

import io.github.j12h36h.dai.action.DAI_ActionCore;
import io.github.j12h36h.dai.action.DAI_ActionQueue;
import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class DAI_ApproachController {

    private static final double DEFAULT_STOP_DISTANCE =
            3.25D;

    private static final int DEFAULT_TIMEOUT_TICKS =
            200;

    private static BlockPos target;

    private static double stopDistance;

    private static int ticksRemaining;

    private static boolean active;

    private DAI_ApproachController() {
        // Utility class.
    }

    public static void start(
            BlockPos blockPos,
            double requestedStopDistance,
            int timeoutTicks
    ) {

        if (blockPos == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot approach a null block target."
            );

            return;
        }

        target =
                blockPos.immutable();

        stopDistance =
                requestedStopDistance > 0.0D
                        ? requestedStopDistance
                        : DEFAULT_STOP_DISTANCE;

        ticksRemaining =
                timeoutTicks > 0
                        ? timeoutTicks
                        : DEFAULT_TIMEOUT_TICKS;

        active =
                true;

        DAI_Core.LOGGER.debug(
                "<DAI>: Started approaching block {} with stopDistance={} and timeout={} tick(s).",
                target,
                stopDistance,
                ticksRemaining
        );
    }

    public static void startSelectedBlock(
            double stopDistance,
            int timeoutTicks
    ) {

        BlockPos selected =
                DAI_TargetController.selectedBlock();

        if (selected == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot approach because no block target is selected."
            );

            return;
        }

        start(
                selected,
                stopDistance,
                timeoutTicks
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
                        || target == null
        ) {

            stop(
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

            stop(
                    "target block no longer exists"
            );

            return;
        }

        if (ticksRemaining-- <= 0) {

            stop(
                    "approach timed out"
            );

            return;
        }

        Vec3 playerPosition =
                minecraft.player.position();

        Vec3 targetPosition =
                Vec3.atCenterOf(
                        target
                );

        double horizontalDistance =
                horizontalDistance(
                        playerPosition,
                        targetPosition
                );

        if (horizontalDistance <= stopDistance) {

            rotateToward(
                    minecraft,
                    targetPosition
            );

            stop(
                    "target reached"
            );

            return;
        }

        rotateToward(
                minecraft,
                targetPosition
        );

        DAI_InputController
                .movement()
                .setMovement(
                        1.0F,
                        0.0F
                );
    }

    public static void stop() {

        stop(
                "stopped manually"
        );
    }

    public static void reset() {

        active =
                false;

        target =
                null;

        stopDistance =
                DEFAULT_STOP_DISTANCE;

        ticksRemaining =
                0;

        DAI_InputController
                .movement()
                .setMovement(
                        0.0F,
                        0.0F
                );
    }

    public static boolean isActive() {
        return active;
    }

    private static void stop(
            String reason
    ) {

        DAI_InputController
                .movement()
                .setMovement(
                        0.0F,
                        0.0F
                );

        active =
                false;

        DAI_Core.LOGGER.debug(
                "<DAI>: Stopped approaching block {}: {}.",
                target,
                reason
        );

        target =
                null;

        ticksRemaining =
                0;
    }

    private static void rotateToward(
            Minecraft minecraft,
            Vec3 targetPosition
    ) {

        Vec3 eyePosition =
                minecraft.player
                        .getEyePosition();

        double deltaX =
                targetPosition.x
                        - eyePosition.x;

        double deltaY =
                targetPosition.y
                        - eyePosition.y;

        double deltaZ =
                targetPosition.z
                        - eyePosition.z;

        double horizontal =
                Math.sqrt(
                        deltaX * deltaX
                                + deltaZ * deltaZ
                );

        float yaw =
                (float) (
                        Math.toDegrees(
                                Math.atan2(
                                        deltaZ,
                                        deltaX
                                )
                        )
                                - 90.0D
                );

        float pitch =
                (float) (
                        -Math.toDegrees(
                                Math.atan2(
                                        deltaY,
                                        horizontal
                                )
                        )
                );

        float finalYaw =
                Mth.wrapDegrees(
                        yaw
                );

        float finalPitch =
                Mth.clamp(
                        pitch,
                        -90.0F,
                        90.0F
                );

        DAI_InputController
                .look()
                .setRotation(
                        finalYaw,
                        finalPitch
                );

        minecraft.player.setYRot(
                finalYaw
        );

        minecraft.player.setXRot(
                finalPitch
        );

        minecraft.player.setYHeadRot(
                finalYaw
        );

        minecraft.player.setYBodyRot(
                finalYaw
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
    public static void faceSelectedBlock() {

        Minecraft minecraft =
                Minecraft.getInstance();

        BlockPos selected =
                DAI_TargetController.selectedBlock();

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || selected == null
        ) {
            return;
        }

        rotateToward(
                minecraft,
                Vec3.atCenterOf(
                        selected
                )
        );
    }

    public static boolean isLookingAtSelectedBlock() {

        Minecraft minecraft =
                Minecraft.getInstance();

        BlockPos selected =
                DAI_TargetController.selectedBlock();

        if (
                minecraft.player == null
                        || selected == null
        ) {
            return false;
        }

        if (
                !(
                        minecraft.hitResult
                                instanceof BlockHitResult blockHitResult
                )
        ) {
            return false;
        }

        return selected.equals(
                blockHitResult.getBlockPos()
        );
    }
    public static void requestWaitForTargetBlock(
            DAI_ActionCore action
    ) {

        BlockPos selected =
                DAI_TargetController.selectedBlock();

        if (selected == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot wait for block alignment because no block is selected."
            );

            return;
        }

        DAI_ApproachController.faceSelectedBlock();

        if (
                DAI_ApproachController
                        .isLookingAtSelectedBlock()
        ) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Camera aligned with selected block {}.",
                    selected
            );

            return;
        }

        int checksRemaining =
                action.ticks() > 0
                        ? action.ticks()
                        : 20;

        if (checksRemaining <= 1) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Timed out aligning with selected block {}.",
                    selected
            );

            return;
        }

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        new DAI_ActionCore(
                                "delay",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                1,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "wait_for_target_block",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                checksRemaining - 1,
                                0,
                                false,
                                0.0D
                        )
                )
        );
    }
}
