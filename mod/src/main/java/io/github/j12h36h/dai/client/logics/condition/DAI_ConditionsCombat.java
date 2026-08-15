package io.github.j12h36h.dai.client.logics.condition;

public final class DAI_ConditionsCombat {

    private DAI_ConditionsCombat() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "attack_cooldown",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .getAttackStrengthScale(0.0F)
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "attack_ready",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .getAttackStrengthScale(0.0F)
                                    >= 1.0F
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_blocking",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player().isBlocking()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_recently_hurt",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player().hurtTime > 0
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_hurt_time",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player().hurtTime
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_in_melee_reach",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasTarget()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    double reach =
                            context.player()
                                    .entityInteractionRange();

                    double distance =
                            context.player()
                                    .distanceTo(
                                            context.target()
                                    );

                    return DAI_ConditionValue.bool(
                            distance <= reach
                    );
                }
        );
    }
}
