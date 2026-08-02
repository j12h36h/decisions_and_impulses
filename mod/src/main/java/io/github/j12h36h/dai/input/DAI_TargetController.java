package io.github.j12h36h.dai.input;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.UUID;

public final class DAI_TargetController {

    private static UUID targetId;

    private DAI_TargetController() {
        // Utility class.
    }

    public static void select(Entity entity) {
        targetId = entity == null ? null : entity.getUUID();
    }

    public static @Nullable Entity selected() {
        Minecraft minecraft = Minecraft.getInstance();
        if (targetId == null || minecraft.level == null) {
            return null;
        }

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (targetId.equals(entity.getUUID()) && !entity.isRemoved()) {
                return entity;
            }
        }

        clear();
        return null;
    }

    public static void clear() {
        targetId = null;
    }
}
