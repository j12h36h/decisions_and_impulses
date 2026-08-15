package io.github.j12h36h.dai.client.logics.input;

import io.github.j12h36h.dai.client.logics.approach.DAI_ApproachProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.function.Predicate;

public final class DAI_InputTargeting {

    public record Rotation(
            float yaw,
            float pitch
    ) {
    }

    private DAI_InputTargeting() {
        // Utility class.
    }

    public static @Nullable Entity nearestEntity() {

        return nearestEntity(
                entity -> true
        );
    }

    public static @Nullable LivingEntity nearestLivingEntity() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {
            return null;
        }

        LivingEntity nearest =
                null;

        double nearestDistanceSquared =
                Double.MAX_VALUE;

        for (
                Entity entity
                : minecraft.level.entitiesForRendering()
        ) {

            if (
                    entity == minecraft.player
                            || entity.isRemoved()
                            || !(
                            entity instanceof LivingEntity living
                    )
                            || !living.isAlive()
            ) {
                continue;
            }

            DAI_ApproachProfile profile =
                    DAI_ApproachProfile.forEntity(
                            living
                    );

            double recognitionRadius =
                    profile.recognitionRadius();

            double distanceSquared =
                    minecraft.player.distanceToSqr(
                            living
                    );

            if (
                    distanceSquared
                            > recognitionRadius
                            * recognitionRadius
            ) {
                continue;
            }

            if (
                    distanceSquared
                            >= nearestDistanceSquared
            ) {
                continue;
            }

            nearestDistanceSquared =
                    distanceSquared;

            nearest =
                    living;
        }

        return nearest;
    }

    public static @Nullable LivingEntity nearestLivingEntity(
            Predicate<LivingEntity> filter
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {
            return null;
        }

        LivingEntity nearest =
                null;

        double nearestDistanceSquared =
                Double.MAX_VALUE;

        for (
                Entity entity
                : minecraft.level.entitiesForRendering()
        ) {

            if (
                    entity == minecraft.player
                            || entity.isRemoved()
                            || !(
                            entity instanceof LivingEntity living
                    )
                            || !living.isAlive()
                            || filter == null
                            || !filter.test(
                            living
                    )
            ) {
                continue;
            }

            DAI_ApproachProfile profile =
                    DAI_ApproachProfile.forEntity(
                            living
                    );

            double recognitionRadius =
                    profile.recognitionRadius();

            double distanceSquared =
                    minecraft.player.distanceToSqr(
                            living
                    );

            if (
                    distanceSquared
                            > recognitionRadius
                            * recognitionRadius
            ) {
                continue;
            }

            if (
                    distanceSquared
                            >= nearestDistanceSquared
            ) {
                continue;
            }

            nearestDistanceSquared =
                    distanceSquared;

            nearest =
                    living;
        }

        return nearest;
    }

    public static @Nullable LivingEntity nearestLivingEntity(
            double maximumDistance
    ) {

        return nearestLivingEntity(
                living -> true,
                maximumDistance
        );
    }

    public static @Nullable LivingEntity nearestLivingEntity(
            Predicate<LivingEntity> filter,
            double maximumDistance
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || maximumDistance <= 0.0D
        ) {
            return null;
        }

        LivingEntity nearest =
                null;

        double nearestDistanceSquared =
                Double.MAX_VALUE;

        double requestedMaximumDistanceSquared =
                maximumDistance
                        * maximumDistance;

        for (
                Entity entity
                : minecraft.level.entitiesForRendering()
        ) {

            if (
                    entity == minecraft.player
                            || entity.isRemoved()
                            || !(
                            entity instanceof LivingEntity living
                    )
                            || !living.isAlive()
            ) {
                continue;
            }

            if (
                    filter != null
                            && !filter.test(
                            living
                    )
            ) {
                continue;
            }

            DAI_ApproachProfile profile =
                    DAI_ApproachProfile.forEntity(
                            living
                    );

            double profileRecognitionDistance =
                    profile.recognitionRadius();

            double allowedDistance =
                    Math.min(
                            maximumDistance,
                            profileRecognitionDistance
                    );

            double allowedDistanceSquared =
                    allowedDistance
                            * allowedDistance;

            double distanceSquared =
                    minecraft.player.distanceToSqr(
                            living
                    );

            if (
                    distanceSquared
                            > requestedMaximumDistanceSquared
                            || distanceSquared
                            > allowedDistanceSquared
            ) {
                continue;
            }

            if (
                    distanceSquared
                            >= nearestDistanceSquared
            ) {
                continue;
            }

            nearestDistanceSquared =
                    distanceSquared;

            nearest =
                    living;
        }

        return nearest;
    }

    public static @Nullable Entity nearestEntity(
            Predicate<Entity> filter
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {
            return null;
        }

        Entity nearest =
                null;

        double nearestDistanceSquared =
                Double.MAX_VALUE;

        for (
                Entity entity
                : minecraft.level.entitiesForRendering()
        ) {

            if (
                    entity == minecraft.player
                            || entity.isRemoved()
                            || filter == null
                            || !filter.test(
                            entity
                    )
            ) {
                continue;
            }

            /*
             * Living entities use their category-specific recognition
             * radius. Non-living entities are still permitted, but use
             * a conservative fixed radius so arbitrary rendered entities
             * do not become distant interaction candidates.
             */
            double maximumDistance =
                    entity instanceof LivingEntity living
                            ? DAI_ApproachProfile
                            .forEntity(
                                    living
                            )
                            .recognitionRadius()
                            : 16.0D;

            double distanceSquared =
                    minecraft.player.distanceToSqr(
                            entity
                    );

            if (
                    distanceSquared
                            > maximumDistance
                            * maximumDistance
            ) {
                continue;
            }

            if (
                    distanceSquared
                            >= nearestDistanceSquared
            ) {
                continue;
            }

            nearestDistanceSquared =
                    distanceSquared;

            nearest =
                    entity;
        }

        return nearest;
    }

    public static @Nullable Rotation rotationTo(
            Entity entity
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                entity == null
                        || minecraft.player == null
        ) {
            return null;
        }

        Vec3 playerEyes =
                minecraft.player
                        .getEyePosition();

        Vec3 targetEyes =
                entity.getEyePosition();

        double deltaX =
                targetEyes.x
                        - playerEyes.x;

        double deltaY =
                targetEyes.y
                        - playerEyes.y;

        double deltaZ =
                targetEyes.z
                        - playerEyes.z;

        double horizontalDistance =
                Math.sqrt(
                        deltaX * deltaX
                                + deltaZ * deltaZ
                );

        float yaw =
                (float) Math.toDegrees(
                        Math.atan2(
                                -deltaX,
                                deltaZ
                        )
                );

        float pitch =
                (float) -Math.toDegrees(
                        Math.atan2(
                                deltaY,
                                horizontalDistance
                        )
                );

        return new Rotation(
                yaw,
                pitch
        );
    }
}