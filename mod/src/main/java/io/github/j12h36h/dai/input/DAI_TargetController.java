package io.github.j12h36h.dai.input;

import io.github.j12h36h.dai.action.DAI_ActionCore;
import io.github.j12h36h.dai.action.DAI_ActionLogic;
import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

public final class DAI_TargetController {

    private static UUID targetEntityId;

    private static BlockPos targetBlockPos;

    private DAI_TargetController() {
        // Utility class.
    }

    public static void select(
            Entity entity
    ) {

        targetEntityId =
                entity == null
                        ? null
                        : entity.getUUID();

        targetBlockPos =
                null;
    }

    public static void selectBlock(
            BlockPos blockPos
    ) {

        targetBlockPos =
                blockPos == null
                        ? null
                        : blockPos.immutable();

        targetEntityId =
                null;
    }

    public static @Nullable Entity selected() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                targetEntityId == null
                        || minecraft.level == null
        ) {
            return null;
        }

        for (
                Entity entity
                : minecraft.level.entitiesForRendering()
        ) {

            if (
                    targetEntityId.equals(
                            entity.getUUID()
                    )
                            && !entity.isRemoved()
            ) {
                return entity;
            }
        }

        targetEntityId =
                null;

        return null;
    }

    public static @Nullable BlockPos selectedBlock() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                targetBlockPos == null
                        || minecraft.level == null
        ) {
            return null;
        }

        if (
                minecraft.level
                        .getBlockState(
                                targetBlockPos
                        )
                        .isAir()
        ) {

            targetBlockPos =
                    null;

            return null;
        }

        return targetBlockPos;
    }

    public static boolean hasEntity() {
        return selected() != null;
    }

    public static boolean hasBlock() {
        return selectedBlock() != null;
    }

    public static void clearEntity() {
        targetEntityId = null;
    }

    public static void clearBlock() {
        targetBlockPos = null;
    }

    public static void clear() {

        targetEntityId = null;
        targetBlockPos = null;
    }


}