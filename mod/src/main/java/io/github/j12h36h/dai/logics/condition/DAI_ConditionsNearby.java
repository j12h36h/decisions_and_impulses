package io.github.j12h36h.dai.logics.condition;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

public final class DAI_ConditionsNearby {

    private static final double DEFAULT_RADIUS =
            16.0D;

    private DAI_ConditionsNearby() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "nearby_entity_count",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !context.hasPlayer()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    double radius =
                            condition.parameterNumber() > 0.0D
                                    ? condition.parameterNumber()
                                    : DEFAULT_RADIUS;

                    double radiusSquared =
                            radius * radius;

                    String filter =
                            normalize(
                                    condition.parameter()
                            );

                    int count = 0;

                    for (
                            Entity entity
                            : context.level()
                            .entitiesForRendering()
                    ) {

                        if (
                                entity == context.player()
                                        || entity.isRemoved()
                        ) {
                            continue;
                        }

                        if (
                                context.player()
                                        .distanceToSqr(entity)
                                        > radiusSquared
                        ) {
                            continue;
                        }

                        if (!matches(entity, filter)) {
                            continue;
                        }

                        count++;
                    }

                    return DAI_ConditionValue.number(
                            count
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "nearest_entity_distance",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !context.hasPlayer()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    double radius =
                            condition.parameterNumber() > 0.0D
                                    ? condition.parameterNumber()
                                    : DEFAULT_RADIUS;

                    double radiusSquared =
                            radius * radius;

                    String filter =
                            normalize(
                                    condition.parameter()
                            );

                    double nearestDistanceSquared =
                            Double.POSITIVE_INFINITY;

                    for (
                            Entity entity
                            : context.level()
                            .entitiesForRendering()
                    ) {

                        if (
                                entity == context.player()
                                        || entity.isRemoved()
                        ) {
                            continue;
                        }

                        double distanceSquared =
                                context.player()
                                        .distanceToSqr(entity);

                        if (
                                distanceSquared > radiusSquared
                                        || !matches(entity, filter)
                        ) {
                            continue;
                        }

                        nearestDistanceSquared =
                                Math.min(
                                        nearestDistanceSquared,
                                        distanceSquared
                                );
                    }

                    if (
                            !Double.isFinite(
                                    nearestDistanceSquared
                            )
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            Math.sqrt(
                                    nearestDistanceSquared
                            )
                    );
                }
        );
    }

    private static boolean matches(
            Entity entity,
            String filter
    ) {

        if (
                filter.isEmpty()
                        || filter.equals("any")
        ) {
            return true;
        }

        return switch (filter) {

            case "living" ->
                    entity instanceof LivingEntity;

            case "hostile", "enemy" ->
                    entity instanceof Enemy;

            case "animal", "passive" ->
                    entity instanceof Animal;

            case "player" ->
                    entity instanceof Player;

            default ->
                    filter.equals(
                            entityId(entity)
                    );
        };
    }

    private static String entityId(
            Entity entity
    ) {

        return entity.getType()
                .builtInRegistryHolder()
                .key()
                .identifier()
                .toString();
    }

    private static String normalize(
            String value
    ) {

        return value == null
                ? ""
                : value.trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }
}
