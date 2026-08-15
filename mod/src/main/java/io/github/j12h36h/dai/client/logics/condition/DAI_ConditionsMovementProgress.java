package io.github.j12h36h.dai.client.logics.condition;

public final class DAI_ConditionsMovementProgress {

    private static final int DEFAULT_WINDOW_TICKS =
            100;

    private static final double DEFAULT_STUCK_DISTANCE =
            0.75D;

    private DAI_ConditionsMovementProgress() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "player_stuck",
                (context, condition) -> {

                    int ticks =
                            readTicks(
                                    condition.parameterNumber()
                            );

                    double maximumDistance =
                            condition.numberValue() > 0.0D
                                    ? condition.numberValue()
                                    : DEFAULT_STUCK_DISTANCE;

                    return DAI_ConditionValue.bool(
                            DAI_ConditionMemory.isStuck(
                                    ticks,
                                    maximumDistance
                            )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "distance_moved_recently",
                (context, condition) -> {

                    int ticks =
                            readTicks(
                                    condition.parameterNumber()
                            );

                    double distance =
                            DAI_ConditionMemory.distanceMoved(
                                    ticks
                            );

                    return DAI_ConditionValue.number(
                            distance
                    );
                }
        );
    }

    private static int readTicks(
            double value
    ) {

        if (
                !Double.isFinite(value)
                        || value <= 0.0D
        ) {
            return DEFAULT_WINDOW_TICKS;
        }

        return Math.max(
                1,
                (int) Math.round(value)
        );
    }
}
