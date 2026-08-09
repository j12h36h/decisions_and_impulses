package io.github.j12h36h.dai.logics;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Comparator;

/** Support-face selection for deterministic exact-position placement. */
public final class DAI_ExactPlacementSupport {

    private DAI_ExactPlacementSupport() {
        // Utility class.
    }

    public static @Nullable Support findBest(
            Minecraft minecraft,
            BlockPos destination
    ) {

        if (
                minecraft == null
                        || minecraft.player == null
                        || minecraft.level == null
                        || destination == null
        ) {
            return null;
        }

        return Arrays.stream(
                        Direction.values()
                )
                .map(
                        face -> {

                            BlockPos support =
                                    destination.relative(
                                            face.getOpposite()
                                    );

                            BlockState state =
                                    minecraft.level.getBlockState(
                                            support
                                    );

                            if (
                                    state.isAir()
                                            || state.canBeReplaced()
                            ) {
                                return null;
                            }

                            return new Support(
                                    support.immutable(),
                                    face
                            );
                        }
                )
                .filter(
                        support -> support != null
                )
                .min(
                        Comparator.comparingDouble(
                                support ->
                                        minecraft.player
                                                .getEyePosition()
                                                .distanceToSqr(
                                                        Vec3.atCenterOf(
                                                                support.position()
                                                        )
                                                )
                        )
                )
                .orElse(
                        null
                );
    }

    public record Support(
            BlockPos position,
            Direction face
    ) {
    }
}
