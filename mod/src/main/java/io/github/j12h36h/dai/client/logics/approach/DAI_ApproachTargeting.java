package io.github.j12h36h.dai.client.logics.approach;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.input.DAI_InputState;
import io.github.j12h36h.dai.client.menus.system.DAI_TargetState;
import io.github.j12h36h.dai.client.menus.system.DAI_TargetVisibility;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class DAI_ApproachTargeting {

    private static final int DEFAULT_ALIGNMENT_CHECKS =
            20;

    private static final int APPROACH_ALIGNMENT_GRACE_TICKS =
            8;

    /*
     * Limit precise target-facing rotation per tick so block targeting
     * remains smooth instead of snapping directly to the destination angle.
     */
    private static final float MAX_LOOK_YAW_STEP =
            8.0F;

    private static final float MAX_LOOK_PITCH_STEP =
            6.0F;

    private DAI_ApproachTargeting() {
        // Utility class.
    }

    /*
     * ------------------------------------------------------------
     * TARGET OWNERSHIP
     * ------------------------------------------------------------
     */

    public static BlockPos interactionTarget() {

        if (
                DAI_ApproachState.active()
                        && DAI_ApproachState.target() != null
        ) {

            return DAI_ApproachState.target();
        }

        /*
         * A newly selected block is authoritative once no approach is
         * active. completedTarget exists only to bridge the short handoff
         * after a successful approach; it must never shadow a later
         * recognition/waypoint selection.
         */
        BlockPos selected =
                DAI_TargetState.selectedBlock();

        if (selected != null) {
            return selected;
        }

        return DAI_ApproachState.completedTarget();
    }

    /*
     * ------------------------------------------------------------
     * FACING
     * ------------------------------------------------------------
     */

    public static void faceSelectedBlock() {

        faceBlock(
                interactionTarget()
        );
    }

    public static void faceBlock(
            BlockPos blockPos
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || blockPos == null
        ) {
            return;
        }

        DAI_TargetVisibility.Result visibility =
                DAI_TargetVisibility.inspect(
                        blockPos
                );

        Vec3 lookPosition =
                visibility.visible()
                        && visibility.visiblePoint() != null
                        ? visibility.visiblePoint()
                        : Vec3.atCenterOf(
                        blockPos
                );

        rotateToward(
                minecraft,
                lookPosition
        );
    }

    /*
     * ------------------------------------------------------------
     * CROSSHAIR TESTING
     * ------------------------------------------------------------
     */

    public static boolean isLookingAtSelectedBlock() {

        return isLookingAtBlock(
                interactionTarget()
        );
    }

    public static boolean isLookingAtBlock(
            BlockPos blockPos
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || blockPos == null
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

        return blockPos.equals(
                blockHitResult.getBlockPos()
        );
    }

    /*
     * ------------------------------------------------------------
     * ACTIVE APPROACH TARGETING
     * ------------------------------------------------------------
     */

    /**
     * Evaluates the current standing position against the active approach
     * target.
     *
     * This owns:
     *
     * - target visibility
     * - camera alignment
     * - immediate obstruction clearing
     * - deciding whether another standing position is required
     *
     * It does not build paths itself.
     */
    public static ApproachTargetResult tick(
            Minecraft minecraft
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || DAI_ApproachState.target() == null
        ) {

            DAI_ApproachState.resetAlignmentTicks();

            return ApproachTargetResult.NOT_HANDLED;
        }

        BlockPos target =
                DAI_ApproachState.target();

        /*
         * Target evaluation intentionally owns this tick.
         */
        DAI_ApproachRecovery.resetStuckDetection(
                minecraft
        );

        stopMovement();

        DAI_TargetVisibility.Result visibility =
                DAI_TargetVisibility.inspect(
                        target
                );

        /*
         * --------------------------------------------------------
         * VISIBLE TARGET
         * --------------------------------------------------------
         */

        if (
                visibility.visible()
                        && visibility.visiblePoint() != null
        ) {

            rotateToward(
                    minecraft,
                    visibility.visiblePoint()
            );

            if (
                    isLookingAtBlock(
                            target
                    )
            ) {

                DAI_ApproachState.resetAlignmentTicks();

                return ApproachTargetResult.SUCCESS;
            }

            int alignmentTicks =
                    DAI_ApproachState.incrementAlignmentTicks();

            if (
                    alignmentTicks
                            < APPROACH_ALIGNMENT_GRACE_TICKS
            ) {

                return ApproachTargetResult.WAITING_FOR_ALIGNMENT;
            }

            DAI_Core.debug(
                    "<DAI>: Target {} is visible from the current position but could not be placed beneath the crosshair after {} tick(s); requesting reposition.",
                    target,
                    APPROACH_ALIGNMENT_GRACE_TICKS
            );

            DAI_ApproachState.resetAlignmentTicks();

            return ApproachTargetResult.REPOSITION;
        }

        /*
         * --------------------------------------------------------
         * BLOCKED TARGET
         * --------------------------------------------------------
         */

        DAI_ApproachState.resetAlignmentTicks();

        if (visibility.blocked()) {

            BlockPos blocker =
                    visibility.blocker();

            if (blocker != null) {

                /*
                 * Never treat the actual target as an obstruction.
                 */
                if (target.equals(blocker)) {

                    DAI_Core.debug(
                            "<DAI>: Visibility inspection returned target {} as its own blocker; requesting reposition instead of clearing it.",
                            target
                    );

                    return ApproachTargetResult.REPOSITION;
                }

                if (
                        DAI_ApproachObstruction.canClear(
                                minecraft,
                                blocker
                        )
                ) {

                    DAI_ApproachObstruction.ClearResult clearResult =
                            DAI_ApproachObstruction.clear(
                                    minecraft,
                                    blocker
                            );

                    return clearResult
                            == DAI_ApproachObstruction.ClearResult.REPOSITION
                            ? ApproachTargetResult.REPOSITION
                            : ApproachTargetResult.OBSTRUCTION_HANDLED;
                }

                DAI_Core.debug(
                        "<DAI>: Target {} is blocked by {}, but the obstruction cannot safely be cleared from the current position; requesting reposition.",
                        target,
                        blocker
                );

                return ApproachTargetResult.REPOSITION;
            }
        }

        /*
         * Visibility inspection found neither a usable target point nor a
         * concrete obstruction that can be handled here.
         */
        DAI_Core.debug(
                "<DAI>: No usable visibility point was found for target {} from the current position; requesting reposition.",
                target
        );

        return ApproachTargetResult.REPOSITION;
    }

    /*
     * ------------------------------------------------------------
     * POST-APPROACH ALIGNMENT
     * ------------------------------------------------------------
     */

    /**
     * Handles wait_for_target_block.
     *
     * Unlike the previous implementation, this uses the same visibility
     * information as active approach targeting instead of repeatedly
     * rotating toward the block center regardless of obstruction state.
     */
    public static void requestWaitForTargetBlock(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        BlockPos selected =
                interactionTarget();

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || selected == null
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot wait for block alignment because the player, level, or target is unavailable."
            );

            return;
        }

        stopMovement();

        DAI_TargetVisibility.Result visibility =
                DAI_TargetVisibility.inspect(
                        selected
                );

        /*
         * If the target is visible, always rotate toward the exact visible
         * point rather than the block center.
         */
        if (
                visibility.visible()
                        && visibility.visiblePoint() != null
        ) {

            rotateToward(
                    minecraft,
                    visibility.visiblePoint()
            );

            if (
                    isLookingAtBlock(
                            selected
                    )
            ) {

                DAI_ActionStatus.set(
                        DAI_ActionResult.SUCCESS
                );

                DAI_Core.debug(
                        "<DAI>: Camera aligned with selected block {}.",
                        selected
                );

                return;
            }

        } else if (visibility.blocked()) {

            BlockPos blocker =
                    visibility.blocker();

            /*
             * wait_for_target_block may clear the same safe obstruction
             * that active approach targeting would clear.
             *
             * This prevents the sequence from sitting still until timeout
             * when a leaf, grass block, or other safe obstruction is the
             * only thing preventing exact alignment.
             */
            if (
                    blocker != null
                            && !selected.equals(
                            blocker
                    )
                            && DAI_ApproachObstruction.canClear(
                            minecraft,
                            blocker
                    )
            ) {

                DAI_ApproachObstruction.ClearResult clearResult =
                        DAI_ApproachObstruction.clear(
                                minecraft,
                                blocker
                        );

                if (
                        clearResult
                                == DAI_ApproachObstruction.ClearResult.REPOSITION
                ) {
                    DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
                    return;
                }
            }

        } else {

            /*
             * No useful visibility result yet. Still face the target center
             * so the camera remains sensible while the wait action retries.
             */
            rotateToward(
                    minecraft,
                    Vec3.atCenterOf(
                            selected
                    )
            );
        }

        int checksRemaining =
                action != null
                        && action.ticks() > 0
                        ? action.ticks()
                        : DEFAULT_ALIGNMENT_CHECKS;

        if (checksRemaining <= 1) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.TIMED_OUT
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Timed out aligning with selected block {}.",
                    selected
            );

            return;
        }

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        createDelayAction(),
                        createAlignmentWaitAction(
                                checksRemaining - 1
                        )
                )
        );
    }

    /*
     * ------------------------------------------------------------
     * LOOK CONTROL
     * ------------------------------------------------------------
     */

    public static void rotateToward(
            Minecraft minecraft,
            Vec3 targetPosition
    ) {

        if (
                minecraft.player == null
                        || targetPosition == null
        ) {
            return;
        }

        Vec3 eyePosition =
                minecraft.player.getEyePosition();

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

        float targetYaw =
                Mth.wrapDegrees(
                        yaw
                );

        float targetPitch =
                Mth.clamp(
                        pitch,
                        -90.0F,
                        90.0F
                );

        float currentYaw =
                minecraft.player.getYRot();

        float currentPitch =
                minecraft.player.getXRot();

        float yawDelta =
                Mth.wrapDegrees(
                        targetYaw
                                - currentYaw
                );

        float pitchDelta =
                targetPitch
                        - currentPitch;

        float nextYaw =
                Mth.wrapDegrees(
                        currentYaw
                                + Mth.clamp(
                                yawDelta,
                                -MAX_LOOK_YAW_STEP,
                                MAX_LOOK_YAW_STEP
                        )
                );

        float nextPitch =
                Mth.clamp(
                        currentPitch
                                + Mth.clamp(
                                pitchDelta,
                                -MAX_LOOK_PITCH_STEP,
                                MAX_LOOK_PITCH_STEP
                        ),
                        -90.0F,
                        90.0F
                );

        DAI_InputState
                .look()
                .setRotation(
                        nextYaw,
                        nextPitch
                );
    }

    /*
     * ------------------------------------------------------------
     * INPUT HELPERS
     * ------------------------------------------------------------
     */

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

    /*
     * ------------------------------------------------------------
     * QUEUED WAIT HELPERS
     * ------------------------------------------------------------
     */

    private static DAI_ActionDefinition createDelayAction() {

        return new DAI_ActionDefinition(
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
        );
    }

    private static DAI_ActionDefinition createAlignmentWaitAction(
            int checksRemaining
    ) {

        return new DAI_ActionDefinition(
                "wait_for_target_block",
                "",
                List.of(),
                List.of(),
                "",
                "",
                0.0F,
                0.0F,
                "",
                Math.max(
                        0,
                        checksRemaining
                ),
                0,
                false,
                0.0D
        );
    }

    /*
     * ------------------------------------------------------------
     * RESULT
     * ------------------------------------------------------------
     */

    public enum ApproachTargetResult {

        SUCCESS,

        WAITING_FOR_ALIGNMENT,

        REPOSITION,

        OBSTRUCTION_HANDLED,

        NOT_HANDLED
    }
}