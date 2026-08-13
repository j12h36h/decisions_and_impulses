package io.github.j12h36h.dai.logics.controller;

import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

public final class DAI_BreakController {

    private enum BreakMode {
        NONE,
        ONCE,
        CONTINUOUS
    }

    private static BreakMode mode =
            BreakMode.NONE;

    private static BlockPos breakingPos;
    private static Direction breakingDirection;

    /*
     * True when a one-shot break was explicitly bound to a BlockPos.
     *
     * Generic break_once remains crosshair-driven.
     */
    private static boolean explicitTarget;

    private DAI_BreakController() {
        // Utility class.
    }

    /*
     * ------------------------------------------------------------
     * ONE-SHOT BREAKING
     * ------------------------------------------------------------
     */

    /**
     * Starts a generic one-shot break against the block currently under
     * the player's crosshair.
     */
    public static void breakOnce() {

        Minecraft minecraft =
                Minecraft.getInstance();

        /*
         * Do not restart an active generic one-shot break against the same
         * crosshair target.
         */
        if (
                mode == BreakMode.ONCE
                        && !explicitTarget
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
         * A generic one-shot request may be waiting for its first
         * controller tick to capture the crosshair target.
         */
        if (
                mode == BreakMode.ONCE
                        && !explicitTarget
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

        explicitTarget =
                false;

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.debug(
                "<DAI>: Complete crosshair single-block break requested."
        );
    }

    /**
     * Starts a one-shot break against an exact block position.
     *
     * Unlike generic breakOnce(), the identity of the block is retained
     * across action delays and controller ticks.
     *
     * The player must still actually be looking at the requested block
     * before Minecraft's destroy request is sent.
     */
    public static void breakOnce(
            BlockPos target
    ) {

        if (target == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot request targeted block breaking for a null position."
            );

            return;
        }

        BlockPos immutableTarget =
                target.immutable();

        /*
         * Repeated requests for the same explicit target must not reset
         * accumulated block-breaking progress.
         */
        if (
                mode == BreakMode.ONCE
                        && explicitTarget
                        && immutableTarget.equals(
                        breakingPos
                )
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

        explicitTarget =
                true;

        breakingPos =
                immutableTarget;

        breakingDirection =
                null;

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.debug(
                "<DAI>: Targeted single-block break requested for {}.",
                breakingPos
        );
    }

    /*
     * ------------------------------------------------------------
     * CONTINUOUS BREAKING
     * ------------------------------------------------------------
     */

    public static void start() {

        cancelCurrentOperation(
                false
        );

        mode =
                BreakMode.CONTINUOUS;

        explicitTarget =
                false;

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.debug(
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

            DAI_Core.debug(
                    "<DAI>: Block breaking stopped."
            );
        }
    }

    /*
     * ------------------------------------------------------------
     * TICK
     * ------------------------------------------------------------
     */

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

            DAI_Core.debug(
                    "<DAI>: Active block-breaking operation cancelled during reset."
            );
        }
    }

    public static boolean isActive() {

        return mode != BreakMode.NONE;
    }

    /*
     * ------------------------------------------------------------
     * ONE-SHOT TICK
     * ------------------------------------------------------------
     */

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

        /*
         * Generic one-shot breaking captures the crosshair target lazily.
         */
        if (
                breakingPos == null
                        && !explicitTarget
        ) {

            if (!captureCrosshairTarget(minecraft)) {

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

        if (breakingPos == null) {

            finishOnce(
                    DAI_ActionResult.FAILURE,
                    "block target became unavailable"
            );

            return;
        }

        /*
         * Always test destruction before crosshair state. Minecraft updates
         * hitResult immediately when a block disappears.
         */
        if (isTargetGone(minecraft)) {

            finishOnce(
                    DAI_ActionResult.SUCCESS,
                    "target block was destroyed"
            );

            return;
        }

        /*
         * Both generic and explicit one-shot breaks still require the player
         * to actually be looking at the intended block.
         *
         * The difference is that an explicit break never silently changes
         * ownership to another block.
         */
        BlockHitResult hitResult =
                currentBlockHit(
                        minecraft
                );

        if (
                hitResult == null
                        || !breakingPos.equals(
                        hitResult.getBlockPos()
                )
        ) {

            finishOnce(
                    DAI_ActionResult.CANCELLED,
                    explicitTarget
                            ? "explicit breaking target is no longer under the crosshair"
                            : "breaking target was lost"
            );

            return;
        }

        /*
         * An explicit request already supplied the position, but its face
         * direction should come from Minecraft's actual hit result at the
         * moment breaking begins.
         */
        if (breakingDirection == null) {

            breakingDirection =
                    hitResult.getDirection();

            startBreaking(
                    minecraft
            );

            return;
        }

        continueBreaking(
                minecraft
        );
    }

    /*
     * ------------------------------------------------------------
     * CONTINUOUS TICK
     * ------------------------------------------------------------
     */

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

        BlockHitResult blockHitResult =
                currentBlockHit(
                        minecraft
                );

        if (blockHitResult == null) {

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

            DAI_ActionStatus.set(
                    DAI_ActionResult.RUNNING
            );

            DAI_Core.debug(
                    "<DAI>: Finished breaking block at {}; continuous breaking remains active.",
                    finishedPos
            );

            return;
        }

        continueBreaking(
                minecraft
        );
    }

    /*
     * ------------------------------------------------------------
     * TARGET CAPTURE
     * ------------------------------------------------------------
     */

    private static boolean captureCrosshairTarget(
            Minecraft minecraft
    ) {

        BlockHitResult blockHitResult =
                currentBlockHit(
                        minecraft
                );

        if (blockHitResult == null) {
            return false;
        }

        breakingPos =
                blockHitResult
                        .getBlockPos()
                        .immutable();

        breakingDirection =
                blockHitResult.getDirection();

        return true;
    }

    private static BlockHitResult currentBlockHit(
            Minecraft minecraft
    ) {

        if (
                minecraft.hitResult
                        instanceof BlockHitResult blockHitResult
        ) {

            return blockHitResult;
        }

        return null;
    }

    /*
     * ------------------------------------------------------------
     * BREAK EXECUTION
     * ------------------------------------------------------------
     */

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

                DAI_Core.debug(
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

        DAI_Core.debug(
                "<DAI>: Started breaking block at {}{}.",
                breakingPos,
                explicitTarget
                        ? " (explicit target)"
                        : ""
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

                DAI_Core.debug(
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

    /*
     * ------------------------------------------------------------
     * STATE TESTS
     * ------------------------------------------------------------
     */

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

    /*
     * ------------------------------------------------------------
     * COMPLETION
     * ------------------------------------------------------------
     */

    private static void finishOnce(
            DAI_ActionResult result,
            String reason
    ) {

        BlockPos finishedPos =
                breakingPos;

        boolean wasExplicit =
                explicitTarget;

        cancelCurrentOperation(
                false
        );

        DAI_ActionStatus.set(
                result
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Finished single-block breaking at {} with result={}: {}{}.",
                finishedPos,
                result,
                reason,
                wasExplicit
                        ? " [explicit target]"
                        : ""
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

        DAI_Core.debug(
                "<DAI>: Finished continuous block breaking at {} with result={}: {}.",
                finishedPos,
                result,
                reason
        );
    }

    /*
     * ------------------------------------------------------------
     * RESET HELPERS
     * ------------------------------------------------------------
     */

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

        explicitTarget =
                false;

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
    }
}