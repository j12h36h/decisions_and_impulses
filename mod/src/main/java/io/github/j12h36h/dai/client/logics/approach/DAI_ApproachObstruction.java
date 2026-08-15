package io.github.j12h36h.dai.client.logics.approach;

import io.github.j12h36h.dai.client.logics.controller.DAI_BreakController;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.input.DAI_InputState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class DAI_ApproachObstruction {

    private static final int ALIGNMENT_BUDGET_TICKS = 12;

    private static BlockPos alignmentBlocker;
    private static int alignmentTicks;

    private DAI_ApproachObstruction() {
        // Utility class.
    }

    public enum ClearResult {
        ALIGNING,
        BREAKING,
        REPOSITION
    }

    public static boolean canClear(Minecraft minecraft, BlockPos blocker) {
        if (minecraft.level == null || blocker == null) return false;
        BlockState state = minecraft.level.getBlockState(blocker);
        if (state.isAir()) return false;
        if (state.is(BlockTags.LEAVES)) return true;
        if (state.canBeReplaced() && state.getFluidState().isEmpty()) {
            return state.getDestroySpeed(minecraft.level, blocker) >= 0.0F;
        }
        return false;
    }

    /**
     * Bounded obstruction ownership. The same blocker gets only a short
     * camera-alignment budget; inability to put it under the crosshair asks
     * the outer approach logic to reposition instead of burning the full
     * approach timeout.
     */
    public static ClearResult clear(Minecraft minecraft, BlockPos blocker) {
        if (minecraft.player == null || minecraft.level == null || blocker == null) {
            resetAlignment();
            return ClearResult.REPOSITION;
        }

        if (!blocker.equals(alignmentBlocker)) {
            alignmentBlocker = blocker.immutable();
            alignmentTicks = 0;
        }

        if (minecraft.level.getBlockState(blocker).isAir()) {
            resetAlignment();
            return ClearResult.BREAKING;
        }

        stopMovement();

        if (DAI_BreakController.isActive()) {
            return ClearResult.BREAKING;
        }

        if (!minecraft.player.onGround()) {
            return tickAlignmentFailure(blocker);
        }

        DAI_ApproachTargeting.rotateToward(minecraft, Vec3.atCenterOf(blocker));

        if (
                minecraft.hitResult instanceof BlockHitResult hit
                        && blocker.equals(hit.getBlockPos())
        ) {
            resetAlignment();
            DAI_Core.LOGGER.info(
                    "<DAI>: Beginning exact obstruction break {} before approaching target {}.",
                    blocker,
                    DAI_ApproachState.target()
            );
            DAI_BreakController.breakOnce(blocker);
            return ClearResult.BREAKING;
        }

        return tickAlignmentFailure(blocker);
    }

    private static ClearResult tickAlignmentFailure(BlockPos blocker) {
        alignmentTicks++;
        if (alignmentTicks < ALIGNMENT_BUDGET_TICKS) {
            return ClearResult.ALIGNING;
        }

        DAI_Core.LOGGER.info(
                "<DAI>: Could not align with safe obstruction {} within {} tick(s); requesting reposition.",
                blocker,
                ALIGNMENT_BUDGET_TICKS
        );
        resetAlignment();
        return ClearResult.REPOSITION;
    }

    public static void reset() {
        resetAlignment();
    }

    private static void resetAlignment() {
        alignmentBlocker = null;
        alignmentTicks = 0;
    }

    private static void stopMovement() {
        DAI_InputState.movement().setMovement(0.0F, 0.0F);
        DAI_InputState.movement().setJump(false);
    }
}
