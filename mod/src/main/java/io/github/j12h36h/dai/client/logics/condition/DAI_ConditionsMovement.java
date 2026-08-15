package io.github.j12h36h.dai.client.logics.condition;

import net.minecraft.world.phys.Vec3;

public final class DAI_ConditionsMovement {

    private static final double MOVEMENT_EPSILON_SQUARED =
            0.0001D;

    private DAI_ConditionsMovement() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "player_moving",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    Vec3 movement =
                            context.player()
                                    .getDeltaMovement();

                    double horizontalSpeedSquared =
                            movement.x * movement.x
                                    + movement.z * movement.z;

                    return DAI_ConditionValue.bool(
                            horizontalSpeedSquared
                                    > MOVEMENT_EPSILON_SQUARED
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_stationary",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    Vec3 movement =
                            context.player()
                                    .getDeltaMovement();

                    double horizontalSpeedSquared =
                            movement.x * movement.x
                                    + movement.z * movement.z;

                    return DAI_ConditionValue.bool(
                            horizontalSpeedSquared
                                    <= MOVEMENT_EPSILON_SQUARED
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_in_air",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            !context.player().onGround()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_falling",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            !context.player().onGround()
                                    && context.player()
                                    .getDeltaMovement()
                                    .y < 0.0D
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_rising",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            !context.player().onGround()
                                    && context.player()
                                    .getDeltaMovement()
                                    .y > 0.0D
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_horizontal_speed",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    Vec3 movement =
                            context.player()
                                    .getDeltaMovement();

                    return DAI_ConditionValue.number(
                            Math.sqrt(
                                    movement.x * movement.x
                                            + movement.z * movement.z
                            )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_vertical_speed",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .getDeltaMovement()
                                    .y
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_speed",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .getDeltaMovement()
                                    .length()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_swimming",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .isSwimming()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_fall_flying",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .isFallFlying()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_climbing",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .onClimbable()
                    );
                }
        );
    }
}
