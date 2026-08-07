package io.github.j12h36h.dai.system;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

public final class DAI_TargetVisibility {

    private static final double FACE_OFFSET =
            0.45D;

    private DAI_TargetVisibility() {
        // Utility class.
    }

    public static Result inspect(
            BlockPos target
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || target == null
        ) {
            return Result.unavailable();
        }

        Vec3 center =
                Vec3.atCenterOf(
                        target
                );

        List<Vec3> points =
                List.of(
                        center,

                        center.add(
                                FACE_OFFSET,
                                0.0D,
                                0.0D
                        ),

                        center.add(
                                -FACE_OFFSET,
                                0.0D,
                                0.0D
                        ),

                        center.add(
                                0.0D,
                                FACE_OFFSET,
                                0.0D
                        ),

                        center.add(
                                0.0D,
                                -FACE_OFFSET,
                                0.0D
                        ),

                        center.add(
                                0.0D,
                                0.0D,
                                FACE_OFFSET
                        ),

                        center.add(
                                0.0D,
                                0.0D,
                                -FACE_OFFSET
                        )
                );

        BlockPos nearestBlocker =
                null;

        double nearestBlockerDistance =
                Double.MAX_VALUE;

        Vec3 eyePosition =
                minecraft.player
                        .getEyePosition();

        for (Vec3 point : points) {

            BlockHitResult hitResult =
                    minecraft.level.clip(
                            new ClipContext(
                                    eyePosition,
                                    point,
                                    ClipContext.Block.OUTLINE,
                                    ClipContext.Fluid.NONE,
                                    minecraft.player
                            )
                    );

            if (
                    hitResult.getType()
                            != HitResult.Type.BLOCK
            ) {
                continue;
            }

            BlockPos hitPos =
                    hitResult.getBlockPos()
                            .immutable();

            if (target.equals(hitPos)) {

                return Result.visible(
                        point
                );
            }

            double distance =
                    eyePosition.distanceToSqr(
                            hitResult.getLocation()
                    );

            if (
                    distance
                            < nearestBlockerDistance
            ) {

                nearestBlockerDistance =
                        distance;

                nearestBlocker =
                        hitPos;
            }
        }

        if (nearestBlocker != null) {

            return Result.blocked(
                    nearestBlocker
            );
        }

        return Result.unavailable();
    }

    public record Result(
            boolean visible,
            @Nullable Vec3 visiblePoint,
            @Nullable BlockPos blocker
    ) {

        public static Result visible(
                Vec3 point
        ) {

            return new Result(
                    true,
                    point,
                    null
            );
        }

        public static Result blocked(
                BlockPos blocker
        ) {

            return new Result(
                    false,
                    null,
                    blocker
            );
        }

        public static Result unavailable() {

            return new Result(
                    false,
                    null,
                    null
            );
        }

        public boolean blocked() {

            return !visible
                    && blocker != null;
        }
    }
}