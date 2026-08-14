package io.github.j12h36h.dai.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public record DAI_Reference(
        Type type,
        UUID entityId,
        BlockPos blockPosition,
        Vec3 position,
        long capturedGameTime
) {

    public enum Type {
        ENTITY,
        BLOCK,
        POSITION
    }

    public DAI_Reference {

        if (type == null) {
            throw new IllegalArgumentException(
                    "Reference type cannot be null."
            );
        }

        blockPosition =
                blockPosition == null
                        ? null
                        : blockPosition.immutable();

        position =
                position == null
                        ? Vec3.ZERO
                        : position;
    }

    public static DAI_Reference entity(
            UUID entityId,
            Vec3 position,
            long gameTime
    ) {

        if (entityId == null) {
            throw new IllegalArgumentException(
                    "Entity reference requires an entity UUID."
            );
        }

        return new DAI_Reference(
                Type.ENTITY,
                entityId,
                null,
                position,
                gameTime
        );
    }

    public static DAI_Reference block(
            BlockPos position,
            long gameTime
    ) {

        if (position == null) {
            throw new IllegalArgumentException(
                    "Block reference requires a block position."
            );
        }

        return new DAI_Reference(
                Type.BLOCK,
                null,
                position,
                Vec3.atCenterOf(position),
                gameTime
        );
    }

    public static DAI_Reference position(
            Vec3 position,
            long gameTime
    ) {

        if (position == null) {
            throw new IllegalArgumentException(
                    "Position reference requires a position."
            );
        }

        return new DAI_Reference(
                Type.POSITION,
                null,
                null,
                position,
                gameTime
        );
    }
}
