package io.github.j12h36h.dai.input;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.function.Predicate;

public final class DAI_InputTargeting {

    public record Rotation(float yaw, float pitch) {
    }

    private DAI_InputTargeting() {
        // Utility class.
    }

    public static @Nullable Entity nearestEntity() {
        return nearestEntity(entity -> true);
    }

    public static @Nullable LivingEntity nearestLivingEntity() {
        Entity entity = nearestEntity(candidate -> candidate instanceof LivingEntity living && living.isAlive());
        return entity instanceof LivingEntity living ? living : null;
    }

    public static @Nullable Entity nearestEntity(Predicate<Entity> filter) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }

        Entity nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity == minecraft.player || entity.isRemoved() || !filter.test(entity)) {
                continue;
            }

            double distanceSquared = minecraft.player.distanceToSqr(entity);
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = entity;
            }
        }

        return nearest;
    }

    public static @Nullable Rotation rotationTo(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entity == null || minecraft.player == null) {
            return null;
        }

        Vec3 playerEyes = minecraft.player.getEyePosition();
        Vec3 targetEyes = entity.getEyePosition();
        double deltaX = targetEyes.x - playerEyes.x;
        double deltaY = targetEyes.y - playerEyes.y;
        double deltaZ = targetEyes.z - playerEyes.z;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float yaw = (float) Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));
        return new Rotation(yaw, pitch);
    }
}
