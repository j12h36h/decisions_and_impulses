package io.github.j12h36h.dai.client.logics.condition;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;

public final class DAI_ConditionsNearbyItem {

    private static final double DEFAULT_RADIUS = 16.0D;

    private DAI_ConditionsNearbyItem() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "nearby_item_count",
                (context, condition) -> {

                    if (!context.hasPlayer() || !context.hasLevel()) {
                        return DAI_ConditionValue.missing();
                    }

                    double radius =
                            condition.parameterNumber() > 0.0D
                                    ? condition.parameterNumber()
                                    : DEFAULT_RADIUS;

                    double radiusSquared =
                            radius * radius;

                    int count = 0;

                    for (Entity entity : context.level().entitiesForRendering()) {

                        if (!(entity instanceof ItemEntity item)) {
                            continue;
                        }

                        if (item.isRemoved()) {
                            continue;
                        }

                        if (context.player().distanceToSqr(item) > radiusSquared) {
                            continue;
                        }

                        count++;
                    }

                    return DAI_ConditionValue.number(count);
                }
        );

        DAI_ConditionRegistry.register(
                "nearest_item_distance",
                (context, condition) -> {

                    if (!context.hasPlayer() || !context.hasLevel()) {
                        return DAI_ConditionValue.missing();
                    }

                    double radius =
                            condition.parameterNumber() > 0.0D
                                    ? condition.parameterNumber()
                                    : DEFAULT_RADIUS;

                    double radiusSquared =
                            radius * radius;

                    double nearest =
                            Double.POSITIVE_INFINITY;

                    for (Entity entity : context.level().entitiesForRendering()) {

                        if (!(entity instanceof ItemEntity item)) {
                            continue;
                        }

                        if (item.isRemoved()) {
                            continue;
                        }

                        double distance =
                                context.player()
                                        .distanceToSqr(item);

                        if (distance > radiusSquared) {
                            continue;
                        }

                        nearest = Math.min(
                                nearest,
                                distance
                        );
                    }

                    if (!Double.isFinite(nearest)) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            Math.sqrt(nearest)
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "nearby_experience_orb_count",
                (context, condition) -> {

                    if (!context.hasPlayer() || !context.hasLevel()) {
                        return DAI_ConditionValue.missing();
                    }

                    double radius =
                            condition.parameterNumber() > 0.0D
                                    ? condition.parameterNumber()
                                    : DEFAULT_RADIUS;

                    double radiusSquared =
                            radius * radius;

                    int count = 0;

                    for (Entity entity : context.level().entitiesForRendering()) {

                        if (!(entity instanceof ExperienceOrb orb)) {
                            continue;
                        }

                        if (orb.isRemoved()) {
                            continue;
                        }

                        if (context.player().distanceToSqr(orb) > radiusSquared) {
                            continue;
                        }

                        count++;
                    }

                    return DAI_ConditionValue.number(count);
                }
        );

        DAI_ConditionRegistry.register(
                "nearest_experience_orb_distance",
                (context, condition) -> {

                    if (!context.hasPlayer() || !context.hasLevel()) {
                        return DAI_ConditionValue.missing();
                    }

                    double radius =
                            condition.parameterNumber() > 0.0D
                                    ? condition.parameterNumber()
                                    : DEFAULT_RADIUS;

                    double radiusSquared =
                            radius * radius;

                    double nearest =
                            Double.POSITIVE_INFINITY;

                    for (Entity entity : context.level().entitiesForRendering()) {

                        if (!(entity instanceof ExperienceOrb orb)) {
                            continue;
                        }

                        if (orb.isRemoved()) {
                            continue;
                        }

                        double distance =
                                context.player()
                                        .distanceToSqr(orb);

                        if (distance > radiusSquared) {
                            continue;
                        }

                        nearest = Math.min(
                                nearest,
                                distance
                        );
                    }

                    if (!Double.isFinite(nearest)) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            Math.sqrt(nearest)
                    );
                }
        );
    }
}
