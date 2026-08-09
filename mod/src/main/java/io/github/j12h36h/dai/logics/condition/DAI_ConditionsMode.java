package io.github.j12h36h.dai.logics.condition;

import net.minecraft.world.level.GameType;

public final class DAI_ConditionsMode {

    private DAI_ConditionsMode() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "player_game_mode",
                (context, condition) -> {

                    GameType gameType =
                            gameType(context);

                    if (gameType == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.string(
                            gameType.getName()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_survival",
                (context, condition) -> {

                    GameType gameType =
                            gameType(context);

                    if (gameType == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            gameType == GameType.SURVIVAL
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_creative",
                (context, condition) -> {

                    GameType gameType =
                            gameType(context);

                    if (gameType == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            gameType == GameType.CREATIVE
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_adventure",
                (context, condition) -> {

                    GameType gameType =
                            gameType(context);

                    if (gameType == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            gameType == GameType.ADVENTURE
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_spectator",
                (context, condition) -> {

                    GameType gameType =
                            gameType(context);

                    if (gameType == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            gameType == GameType.SPECTATOR
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_flying",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .getAbilities()
                                    .flying
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_can_fly",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .getAbilities()
                                    .mayfly
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_invulnerable",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .getAbilities()
                                    .invulnerable
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_instabuild",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .getAbilities()
                                    .instabuild
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_may_build",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .getAbilities()
                                    .mayBuild
                    );
                }
        );
    }

    private static GameType gameType(
            DAI_ConditionContext context
    ) {

        if (context.gameMode() == null) {
            return null;
        }

        return context.gameMode()
                .getPlayerMode();
    }
}
