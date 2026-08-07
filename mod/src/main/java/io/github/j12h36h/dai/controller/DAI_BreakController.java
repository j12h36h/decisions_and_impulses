package io.github.j12h36h.dai.controller;

import io.github.j12h36h.dai.action.DAI_ActionResult;
import io.github.j12h36h.dai.action.DAI_ActionStatus;
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

        Minecraft minecraft =
                Minecraft.getInstance();

        /*
         * Do not restart an active single-block break when another
         * break_once request arrives for the same target.
         *
         * Restarting would call stopDestroyBlock() and erase the
         * accumulated breaking progress.
         */
        if (
                mode == BreakMode.ONCE
                        && breakingPos != null
                        && minecraft.hitResult
                        instanceof BlockHitResult blockHitResult
                        && breakingPos.equals(
                        blockHitResult.getBlockPos()
                )
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.RUNNING
            );

            return;
        }

        /*
         * A previous request may have set ONCE mode but not yet reached
         * its first controller tick, so there is no captured block yet.
         * Do not restart that pending request either.
         */
        if (
                mode == BreakMode.ONCE
                        && breakingPos == null
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.RUNNING
            );

            return;
        }

        cancelCurrentOperation(
                false
        );

        mode =
                BreakMode.ONCE;

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Complete single-block break requested."
        );
    }

    public static void start() {

        cancelCurrentOperation(
                false
        );

        mode =
                BreakMode.CONTINUOUS;

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Continuous block breaking started."
        );
    }

    public static void stop() {

        boolean wasActive =
                mode != BreakMode.NONE;

        cancelCurrentOperation(
                false
        );

        if (wasActive) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.CANCELLED
            );

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

        boolean wasActive =
                mode != BreakMode.NONE;

        cancelCurrentOperation(
                false
        );

        if (wasActive) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.CANCELLED
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Active block-breaking operation cancelled during reset."
            );
        }
    }

    public static boolean isActive() {
        return mode != BreakMode.NONE;
    }

    private static void tickBreakOnce() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (!isReady(minecraft)) {

            finishOnce(
                    DAI_ActionResult.FAILURE,
                    "player, level, or game mode became unavailable"
            );

            return;
        }

        if (breakingPos == null) {

            if (!captureTarget(minecraft)) {

                finishOnce(
                        DAI_ActionResult.FAILURE,
                        "no block is targeted"
                );

                return;
            }

            startBreaking(
                    minecraft
            );

            return;
        }

        /*
         * Check whether the captured block was destroyed before checking
         * whether the crosshair still points at it.
         *
         * Minecraft updates hitResult as soon as the block disappears, so
         * checking targeting first incorrectly reports a successful break as
         * "breaking target was lost".
         */
        if (isTargetGone(minecraft)) {

            finishOnce(
                    DAI_ActionResult.SUCCESS,
                    "target block was destroyed"
            );

            return;
        }

        if (
                !isStillTargetingBreakingBlock(
                        minecraft
                )
        ) {

            finishOnce(
                    DAI_ActionResult.CANCELLED,
                    "breaking target was lost"
            );

            return;
        }

        continueBreaking(
                minecraft
        );
    }

    private static void tickContinuous() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (!isReady(minecraft)) {

            finishContinuous(
                    DAI_ActionResult.FAILURE,
                    "player, level, or game mode became unavailable"
            );

            return;
        }

        if (
                !(minecraft.hitResult
                        instanceof BlockHitResult blockHitResult)
        ) {

            cancelCurrentBlock(
                    minecraft
            );

            /*
             * Continuous breaking remains active while the player
             * temporarily stops targeting a block.
             */
            DAI_ActionStatus.set(
                    DAI_ActionResult.RUNNING
            );

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
                        || !breakingPos.equals(
                        currentPos
                )
        ) {

            cancelCurrentBlock(
                    minecraft
            );

            breakingPos =
                    currentPos;

            breakingDirection =
                    currentDirection;

            startBreaking(
                    minecraft
            );

            return;
        }

        if (isTargetGone(minecraft)) {

            BlockPos finishedPos =
                    breakingPos;

            clearTarget();

            /*
             * A block was completed, but the continuous action remains
             * active and may begin breaking another targeted block.
             */
            DAI_ActionStatus.set(
                    DAI_ActionResult.RUNNING
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Finished breaking block at {}; continuous breaking remains active.",
                    finishedPos
            );

            return;
        }

        continueBreaking(
                minecraft
        );
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

        return yawDifference
                > CAMERA_CHANGE_TOLERANCE
                || pitchDifference
                > CAMERA_CHANGE_TOLERANCE;
    }

    private static void startBreaking(
            Minecraft minecraft
    ) {

        if (
                breakingPos == null
                        || breakingDirection == null
        ) {

            if (mode == BreakMode.ONCE) {

                finishOnce(
                        DAI_ActionResult.FAILURE,
                        "captured block target was invalid"
                );

            } else {

                finishContinuous(
                        DAI_ActionResult.FAILURE,
                        "captured block target was invalid"
                );
            }

            return;
        }

        boolean started =
                minecraft.gameMode
                        .startDestroyBlock(
                                breakingPos,
                                breakingDirection
                        );

        if (!started) {

            if (mode == BreakMode.ONCE) {

                finishOnce(
                        DAI_ActionResult.FAILURE,
                        "Minecraft rejected the block-breaking request"
                );

            } else {

                cancelCurrentBlock(
                        minecraft
                );

                DAI_ActionStatus.set(
                        DAI_ActionResult.RUNNING
                );

                DAI_Core.LOGGER.debug(
                        "<DAI>: Could not start breaking block at {}; continuous breaking remains active.",
                        breakingPos
                );
            }

            return;
        }

        minecraft.player.swing(
                InteractionHand.MAIN_HAND
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Started breaking block at {}.",
                breakingPos
        );
    }

    private static void continueBreaking(
            Minecraft minecraft
    ) {

        if (
                breakingPos == null
                        || breakingDirection == null
        ) {

            if (mode == BreakMode.ONCE) {

                finishOnce(
                        DAI_ActionResult.FAILURE,
                        "active block target became invalid"
                );

            } else {

                cancelCurrentBlock(
                        minecraft
                );

                DAI_ActionStatus.set(
                        DAI_ActionResult.RUNNING
                );
            }

            return;
        }

        boolean continuing =
                minecraft.gameMode
                        .continueDestroyBlock(
                                breakingPos,
                                breakingDirection
                        );

        if (!continuing) {

            if (mode == BreakMode.ONCE) {

                /*
                 * continueDestroyBlock may return false on the same tick the
                 * server/client removes the completed block. Treat that as a
                 * successful one-shot break rather than a failure.
                 */
                if (isTargetGone(minecraft)) {

                    finishOnce(
                            DAI_ActionResult.SUCCESS,
                            "target block was destroyed"
                    );

                    return;
                }

                DAI_Core.LOGGER.info(
                        "<DAI>: continueDestroyBlock returned false at {}; stateAir={}.",
                        breakingPos,
                        minecraft.level
                                .getBlockState(
                                        breakingPos
                                )
                                .isAir()
                );

                finishOnce(
                        DAI_ActionResult.FAILURE,
                        "Minecraft could not continue breaking the block"
                );

            } else {

                BlockPos failedPos =
                        breakingPos;

                cancelCurrentBlock(
                        minecraft
                );

                DAI_ActionStatus.set(
                        DAI_ActionResult.RUNNING
                );

                DAI_Core.LOGGER.debug(
                        "<DAI>: Could not continue breaking block at {}; continuous breaking remains active.",
                        failedPos
                );
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
                .getBlockState(
                        breakingPos
                )
                .isAir();
    }

    private static boolean isReady(
            Minecraft minecraft
    ) {

        return minecraft.player != null
                && minecraft.level != null
                && minecraft.gameMode != null;
    }

    private static void finishOnce(
            DAI_ActionResult result,
            String reason
    ) {

        BlockPos finishedPos =
                breakingPos;

        cancelCurrentOperation(
                false
        );

        DAI_ActionStatus.set(
                result
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Finished single-block breaking at {} with result={}: {}.",
                finishedPos,
                result,
                reason
        );
    }

    private static void finishContinuous(
            DAI_ActionResult result,
            String reason
    ) {

        BlockPos finishedPos =
                breakingPos;

        cancelCurrentOperation(
                false
        );

        DAI_ActionStatus.set(
                result
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Finished continuous block breaking at {} with result={}: {}.",
                finishedPos,
                result,
                reason
        );
    }

    private static void cancelCurrentBlock(
            Minecraft minecraft
    ) {

        if (minecraft.gameMode != null) {

            minecraft.gameMode
                    .stopDestroyBlock();
        }

        clearTarget();
    }

    private static void cancelCurrentOperation(
            boolean reportCancellation
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        boolean wasActive =
                mode != BreakMode.NONE;

        if (minecraft.gameMode != null) {

            minecraft.gameMode
                    .stopDestroyBlock();
        }

        mode =
                BreakMode.NONE;

        clearTarget();

        if (
                reportCancellation
                        && wasActive
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.CANCELLED
            );
        }
    }

    private static void clearTarget() {

        breakingPos =
                null;

        breakingDirection =
                null;

        breakingYaw =
                0.0F;

        breakingPitch =
                0.0F;
    }

    private static boolean isStillTargetingBreakingBlock(
            Minecraft minecraft
    ) {

        if (breakingPos == null) {
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

        return breakingPos.equals(
                blockHitResult.getBlockPos()
        );
    }
}