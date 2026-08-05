package io.github.j12h36h.dai.input;

import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

public final class DAI_BreakController {

    private enum BreakMode {
        NONE,
        ONCE,
        CONTINUOUS
    }

    private static final float CAMERA_CHANGE_TOLERANCE =
            0.01F;

    private static BreakMode mode =
            BreakMode.NONE;

    private static BlockPos breakingPos;
    private static Direction breakingDirection;

    private static float breakingYaw;
    private static float breakingPitch;

    private DAI_BreakController() {
        // Utility class.
    }

    public static void breakOnce() {

        stopInternal();

        mode =
                BreakMode.ONCE;

        DAI_Core.LOGGER.debug(
                "<DAI>: Complete single-block break requested."
        );
    }

    public static void start() {

        stopInternal();

        mode =
                BreakMode.CONTINUOUS;

        DAI_Core.LOGGER.debug(
                "<DAI>: Continuous block breaking started."
        );
    }

    public static void stop() {

        boolean wasActive =
                mode != BreakMode.NONE;

        stopInternal();

        if (wasActive) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Block breaking stopped."
            );
        }
    }

    public static void tick() {

        switch (mode) {

            case ONCE ->
                    tickBreakOnce();

            case CONTINUOUS ->
                    tickContinuous();

            case NONE -> {
                // No active breaking operation.
            }
        }
    }

    public static void reset() {
        stopInternal();
    }

    private static void tickBreakOnce() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (!isReady(minecraft)) {
            stop();
            return;
        }

        if (breakingPos == null) {

            if (!captureTarget(minecraft)) {

                DAI_Core.LOGGER.debug(
                        "<DAI>: Break ignored because no block is targeted."
                );

                stop();

                return;
            }

            startBreaking(minecraft);

            return;
        }

        if (hasCameraChanged(minecraft)) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Single-block break cancelled because the camera moved."
            );

            stop();

            return;
        }

        if (isTargetGone(minecraft)) {

            BlockPos finishedPos =
                    breakingPos;

            stopInternal();

            DAI_Core.LOGGER.debug(
                    "<DAI>: Finished breaking block at {}.",
                    finishedPos
            );

            return;
        }

        continueBreaking(minecraft);
    }

    private static void tickContinuous() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (!isReady(minecraft)) {
            stop();
            return;
        }

        if (
                !(minecraft.hitResult
                        instanceof BlockHitResult blockHitResult)
        ) {

            cancelCurrentBlock(minecraft);

            return;
        }

        BlockPos currentPos =
                blockHitResult
                        .getBlockPos()
                        .immutable();

        Direction currentDirection =
                blockHitResult.getDirection();

        if (
                breakingPos == null
                        || !breakingPos.equals(currentPos)
        ) {

            cancelCurrentBlock(minecraft);

            breakingPos =
                    currentPos;

            breakingDirection =
                    currentDirection;

            startBreaking(minecraft);

            return;
        }

        if (isTargetGone(minecraft)) {

            clearTarget();

            return;
        }

        continueBreaking(minecraft);
    }

    private static boolean captureTarget(
            Minecraft minecraft
    ) {

        if (
                !(minecraft.hitResult
                        instanceof BlockHitResult blockHitResult)
        ) {
            return false;
        }

        breakingPos =
                blockHitResult
                        .getBlockPos()
                        .immutable();

        breakingDirection =
                blockHitResult.getDirection();

        breakingYaw =
                minecraft.player.getYRot();

        breakingPitch =
                minecraft.player.getXRot();

        return true;
    }

    private static boolean hasCameraChanged(
            Minecraft minecraft
    ) {

        float yawDifference =
                Math.abs(
                        Mth.wrapDegrees(
                                minecraft.player.getYRot()
                                        - breakingYaw
                        )
                );

        float pitchDifference =
                Math.abs(
                        minecraft.player.getXRot()
                                - breakingPitch
                );

        return yawDifference > CAMERA_CHANGE_TOLERANCE
                || pitchDifference > CAMERA_CHANGE_TOLERANCE;
    }

    private static void startBreaking(
            Minecraft minecraft
    ) {

        minecraft.gameMode.startDestroyBlock(
                breakingPos,
                breakingDirection
        );

        minecraft.player.swing(
                InteractionHand.MAIN_HAND
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Started breaking block at {}.",
                breakingPos
        );
    }

    private static void continueBreaking(
            Minecraft minecraft
    ) {

        boolean continuing =
                minecraft.gameMode.continueDestroyBlock(
                        breakingPos,
                        breakingDirection
                );

        if (!continuing) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Could not continue breaking block at {}.",
                    breakingPos
            );

            if (mode == BreakMode.ONCE) {
                stop();
            } else {
                cancelCurrentBlock(minecraft);
            }

            return;
        }

        minecraft.player.swing(
                InteractionHand.MAIN_HAND
        );
    }

    private static boolean isTargetGone(
            Minecraft minecraft
    ) {

        return breakingPos == null
                || minecraft.level
                .getBlockState(breakingPos)
                .isAir();
    }

    private static boolean isReady(
            Minecraft minecraft
    ) {

        return minecraft.player != null
                && minecraft.level != null
                && minecraft.gameMode != null;
    }

    private static void cancelCurrentBlock(
            Minecraft minecraft
    ) {

        if (minecraft.gameMode != null) {
            minecraft.gameMode.stopDestroyBlock();
        }

        clearTarget();
    }

    private static void stopInternal() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.gameMode != null) {
            minecraft.gameMode.stopDestroyBlock();
        }

        mode =
                BreakMode.NONE;

        clearTarget();
    }

    private static void clearTarget() {

        breakingPos = null;
        breakingDirection = null;

        breakingYaw = 0.0F;
        breakingPitch = 0.0F;
    }
}