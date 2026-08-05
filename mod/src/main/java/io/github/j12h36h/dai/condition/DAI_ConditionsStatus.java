package io.github.j12h36h.dai.condition;

public final class DAI_ConditionsStatus {

    private DAI_ConditionsStatus() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "player_hunger",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .getFoodData()
                                    .getFoodLevel()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_saturation",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .getFoodData()
                                    .getSaturationLevel()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_air",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .getAirSupply()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_max_air",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .getMaxAirSupply()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_absorption",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .getAbsorptionAmount()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_in_water",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .isInWater()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_in_lava",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .isInLava()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_on_fire",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .isOnFire()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_fire_ticks",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .getRemainingFireTicks()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_freezing",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .isFreezing()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_frozen_ticks",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .getTicksFrozen()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_wet",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .isInWaterOrRain()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_sleeping",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .isSleeping()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_riding",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .isPassenger()
                    );
                }
        );
    }
}
