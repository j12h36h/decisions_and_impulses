package io.github.j12h36h.dai.client.logics.condition;

public final class DAI_ConditionsPosition {

    private DAI_ConditionsPosition() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "player_x",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player().getX()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_y",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player().getY()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_z",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player().getZ()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_block_x",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .blockPosition()
                                    .getX()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_block_y",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .blockPosition()
                                    .getY()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_block_z",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .blockPosition()
                                    .getZ()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_yaw",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player().getYRot()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_pitch",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player().getXRot()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_facing",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.string(
                            context.player()
                                    .getDirection()
                                    .getSerializedName()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_x",
                (context, condition) -> {

                    if (!context.hasTarget()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.target().getX()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_y",
                (context, condition) -> {

                    if (!context.hasTarget()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.target().getY()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_z",
                (context, condition) -> {

                    if (!context.hasTarget()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.target().getZ()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_above_player",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasTarget()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.target().getY()
                                    > context.player().getY()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_below_player",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasTarget()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.target().getY()
                                    < context.player().getY()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_height_difference",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasTarget()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.target().getY()
                                    - context.player().getY()
                    );
                }
        );
    }
}
