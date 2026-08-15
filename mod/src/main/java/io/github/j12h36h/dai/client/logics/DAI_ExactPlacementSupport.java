package io.github.j12h36h.dai.client.logics;

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

    /**
     * Existing ground-placement policy: use the nearest valid support.
     * Survival/adventure behavior intentionally remains unchanged.
     */
    public static @Nullable Support findBest(
            Minecraft minecraft,
            BlockPos destination
    ) {

        return candidates(
                minecraft,
                destination
        )
                .min(
                        Comparator.comparingDouble(
                                support -> supportDistanceSqr(
                                        minecraft,
                                        support
                                )
                        )
                )
                .orElse(
                        null
                );
    }

    /**
     * Creative construction policy.
     *
     * Prefer the block directly below the destination whenever it exists.
     * This keeps floors/foundations in a stable top-down hover instead of
     * switching to the side of a newly placed neighbor after every cell.
     * Horizontal supports remain valid fallbacks for walls/details, while an
     * overhead support is the least desirable option.
     */
    public static @Nullable Support findBestCreative(
            Minecraft minecraft,
            BlockPos destination
    ) {

        return candidates(
                minecraft,
                destination
        )
                .min(
                        Comparator.comparingDouble(
                                support -> creativeScore(
                                        minecraft,
                                        support
                                )
                        )
                )
                .orElse(
                        null
                );
    }

    private static java.util.stream.Stream<Support> candidates(
            Minecraft minecraft,
            BlockPos destination
    ) {

        if (
                minecraft == null
                        || minecraft.player == null
                        || minecraft.level == null
                        || destination == null
        ) {
            return java.util.stream.Stream.empty();
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
                );
    }

    private static double creativeScore(
            Minecraft minecraft,
            Support support
    ) {

        double facePenalty;

        if (support.face() == Direction.UP) {
            /* Support is directly below the destination: ideal. */
            facePenalty = 0.0D;
        } else if (
                support.face() == Direction.NORTH
                        || support.face() == Direction.SOUTH
                        || support.face() == Direction.EAST
                        || support.face() == Direction.WEST
        ) {
            /* Useful for walls/details that have no support beneath them. */
            facePenalty = 100.0D;
        } else {
            /* Clicking downward from an overhead support is last resort. */
            facePenalty = 250.0D;
        }

        return facePenalty
                + supportDistanceSqr(
                        minecraft,
                        support
                ) * 0.05D;
    }

    private static double supportDistanceSqr(
            Minecraft minecraft,
            Support support
    ) {

        return minecraft.player
                .getEyePosition()
                .distanceToSqr(
                        Vec3.atCenterOf(
                                support.position()
                        )
                );
    }

    public record Support(
            BlockPos position,
            Direction face
    ) {
    }
}
