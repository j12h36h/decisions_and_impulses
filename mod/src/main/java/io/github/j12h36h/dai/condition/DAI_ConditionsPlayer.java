package io.github.j12h36h.dai.condition;

public final class DAI_ConditionsPlayer {

    private DAI_ConditionsPlayer() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "player_exists",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                context.hasPlayer()
                        )
        );

        DAI_ConditionRegistry.register(
                "player_alive",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                context.hasPlayer()
                                        && context.player().isAlive()
                        )
        );

        DAI_ConditionRegistry.register(
                "player_health",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player().getHealth()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_on_ground",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player().onGround()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_sprinting",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player().isSprinting()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_sneaking",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player().isShiftKeyDown()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_using_item",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player().isUsingItem()
                    );
                }
        );

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
    }
}
