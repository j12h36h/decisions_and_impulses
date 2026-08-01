package io.github.j12h36h.dai.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public final class DAI_Targeting {

    private DAI_Targeting() {
    }

    public static @Nullable Entity nearestEntity() {

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }

        Entity nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;

        for (Entity entity : minecraft.level.entitiesForRendering()) {

            if (entity == minecraft.player) {
                continue;
            }

            double distanceSq = minecraft.player.distanceToSqr(entity);

            if (distanceSq < nearestDistanceSq) {
                nearestDistanceSq = distanceSq;
                nearest = entity;
            }
        }

        return nearest;
    }

    public static float yawTo(Entity entity) {

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            return 0.0F;
        }

        Vec3 eyes = minecraft.player.getEyePosition();
        Vec3 target = entity.getEyePosition();

        double dx = target.x - eyes.x;
        double dz = target.z - eyes.z;

        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    public static float pitchTo(Entity entity) {

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            return 0.0F;
        }

        Vec3 eyes = minecraft.player.getEyePosition();
        Vec3 target = entity.getEyePosition();

        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;

        double horizontal = Math.sqrt(dx * dx + dz * dz);

        return (float) -Math.toDegrees(Math.atan2(dy, horizontal));
    }
}