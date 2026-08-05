package io.github.j12h36h.dai.condition;

public final class DAI_ConditionsTarget {

    private DAI_ConditionsTarget() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "target_exists",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                context.hasTarget()
                        )
        );

        DAI_ConditionRegistry.register(
                "target_alive",
                (context, condition) -> {

                    if (!context.hasTarget()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.target().isAlive()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_health",
                (context, condition) -> {

                    if (
                            !context.hasTarget()
                                    || !(context.target()
                                    instanceof net.minecraft.world.entity.LivingEntity livingTarget)
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            livingTarget.getHealth()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_distance",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasTarget()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .distanceTo(
                                            context.target()
                                    )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_visible",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasTarget()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .hasLineOfSight(
                                            context.target()
                                    )
                    );
                }
        );
    }
}