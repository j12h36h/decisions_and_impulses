package io.github.j12h36h.dai.logics.approach;

import io.github.j12h36h.dai.logics.controller.DAI_BreakController;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.input.DAI_InputState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class DAI_ApproachObstruction {

    private DAI_ApproachObstruction() {
        // Utility class.
    }

    /**
     * Returns whether the supplied obstruction is safe for the normal
     * approach controller to clear automatically.
     *
     * This intentionally remains conservative. Normal obstruction clearing
     * handles leaves and lightweight replaceable vegetation, while more
     * destructive recovery is handled separately by DAI_ApproachRecovery.
     */
    public static boolean canClear(
            Minecraft minecraft,
            BlockPos blocker
    ) {

        if (
                minecraft.level == null
                        || blocker == null
        ) {
            return false;
        }

        BlockState state =
                minecraft.level.getBlockState(
                        blocker
                );

        if (state.isAir()) {
            return false;
        }

        /*
         * Leaves are considered safe temporary obstructions.
         */
        if (
                state.is(
                        BlockTags.LEAVES
                )
        ) {
            return true;
        }

        /*
         * Lightweight replaceable vegetation may also be cleared.
         *
         * Fluids remain explicitly excluded.
         */
        if (
                state.canBeReplaced()
                        && state.getFluidState()
                        .isEmpty()
        ) {

            return state.getDestroySpeed(
                    minecraft.level,
                    blocker
            ) >= 0.0F;
        }

        return false;
    }

    /**
     * Attempts to clear a known safe obstruction while approaching the
     * currently selected target.
     *
     * Movement is stopped while aiming and breaking so the player does not
     * drift away from the confirmed interaction position.
     */
    public static void clear(
            Minecraft minecraft,
            BlockPos blocker
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || blocker == null
        ) {
            return;
        }

        /*
         * Do not begin a block break while the player is airborne.
         */
        if (!minecraft.player.onGround()) {

            stopMovement();

            return;
        }

        if (
                minecraft.level
                        .getBlockState(
                                blocker
                        )
                        .isAir()
        ) {
            return;
        }

        /*
         * Never restart a break already owned by DAI_BreakController.
         */
        if (DAI_BreakController.isActive()) {

            stopMovement();

            return;
        }

        Vec3 blockerCenter =
                Vec3.atCenterOf(
                        blocker
                );

        DAI_ApproachTargeting.rotateToward(
                minecraft,
                blockerCenter
        );

        stopMovement();

        /*
         * Minecraft's hit result is authoritative for whether the requested
         * rotation has actually placed this obstruction under the crosshair.
         */
        if (
                !(
                        minecraft.hitResult
                                instanceof BlockHitResult blockHitResult
                )
        ) {
            return;
        }

        if (
                !blocker.equals(
                        blockHitResult.getBlockPos()
                )
        ) {
            return;
        }

        DAI_Core.LOGGER.info(
                "<DAI>: Clearing obstruction {} before approaching target {}.",
                blocker,
                DAI_ApproachState.target()
        );

        DAI_BreakController.breakOnce();
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
}
