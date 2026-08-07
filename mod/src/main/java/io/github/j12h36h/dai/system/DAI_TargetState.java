package io.github.j12h36h.dai.system;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.UUID;

public final class DAI_TargetState {

    private static UUID entityTargetId;
    private static BlockPos blockTarget;

    private DAI_TargetState() {
        // Utility class.
    }

    public static void select(
            Entity entity
    ) {

        entityTargetId =
                entity == null
                        ? null
                        : entity.getUUID();
    }

    public static @Nullable Entity selected() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                entityTargetId == null
                        || minecraft.level == null
        ) {
            return null;
        }

        for (
                Entity entity
                : minecraft.level.entitiesForRendering()
        ) {

            if (
                    entityTargetId.equals(entity.getUUID())
                            && !entity.isRemoved()
            ) {
                return entity;
            }
        }

        clearEntity();

        return null;
    }

    public static void clearEntity() {

        entityTargetId = null;
    }

    public static void selectBlock(
            BlockPos position
    ) {

        blockTarget =
                position == null
                        ? null
                        : position.immutable();
    }

    public static @Nullable BlockPos selectedBlock() {

        return blockTarget;
    }

    public static boolean hasSelectedBlock() {

        return blockTarget != null;
    }

    public static void clearBlock() {

        blockTarget = null;
    }

    public static void clear() {

        clearEntity();
        clearBlock();
    }
}