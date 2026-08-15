package io.github.j12h36h.dai.client.logics.condition;

public final class DAI_ConditionsWorld {

    private static final long TICKS_PER_DAY = 24000L;
    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END = 23000L;

    private DAI_ConditionsWorld() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "world_time",
                (context, condition) -> {

                    if (!context.hasLevel()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            Math.floorMod(
                                    context.level()
                                            .getOverworldClockTime(),
                                    TICKS_PER_DAY
                            )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "game_time",
                (context, condition) -> {

                    if (!context.hasLevel()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.level()
                                    .getGameTime()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "day_count",
                (context, condition) -> {

                    if (!context.hasLevel()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            Math.floorDiv(
                                    context.level()
                                            .getOverworldClockTime(),
                                    TICKS_PER_DAY
                            )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "is_day",
                (context, condition) -> {

                    if (!context.hasLevel()) {
                        return DAI_ConditionValue.missing();
                    }

                    long worldTime =
                            Math.floorMod(
                                    context.level()
                                            .getOverworldClockTime(),
                                    TICKS_PER_DAY
                            );

                    return DAI_ConditionValue.bool(
                            worldTime < NIGHT_START
                                    || worldTime >= NIGHT_END
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "is_night",
                (context, condition) -> {

                    if (!context.hasLevel()) {
                        return DAI_ConditionValue.missing();
                    }

                    long worldTime =
                            Math.floorMod(
                                    context.level()
                                            .getOverworldClockTime(),
                                    TICKS_PER_DAY
                            );

                    return DAI_ConditionValue.bool(
                            worldTime >= NIGHT_START
                                    && worldTime < NIGHT_END
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "is_raining",
                (context, condition) -> {

                    if (!context.hasLevel()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.level()
                                    .isRaining()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "is_thundering",
                (context, condition) -> {

                    if (!context.hasLevel()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.level()
                                    .isThundering()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "dimension",
                (context, condition) -> {

                    if (!context.hasLevel()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.string(
                            context.level()
                                    .dimension()
                                    .identifier()
                                    .toString()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "sky_visible",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !context.hasPlayer()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.level()
                                    .canSeeSky(
                                            context.player()
                                                    .blockPosition()
                                    )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "light_level",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !context.hasPlayer()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.level()
                                    .getMaxLocalRawBrightness(
                                            context.player()
                                                    .blockPosition()
                                    )
                    );
                }
        );
    }
}