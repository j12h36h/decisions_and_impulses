package io.github.j12h36h.dai.condition;

import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

public final class DAI_ConditionsEntity {

    private DAI_ConditionsEntity() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "target_entity_type",
                (context, condition) -> {

                    if (!context.hasTarget()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.string(
                            context.target()
                                    .getType()
                                    .builtInRegistryHolder()
                                    .key()
                                    .identifier()
                                    .toString()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_is_living",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                context.hasTarget()
                                        && context.target()
                                        instanceof LivingEntity
                        )
        );

        DAI_ConditionRegistry.register(
                "target_is_player",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                context.hasTarget()
                                        && context.target()
                                        instanceof Player
                        )
        );

        DAI_ConditionRegistry.register(
                "target_is_hostile",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                context.hasTarget()
                                        && context.target()
                                        instanceof Enemy
                        )
        );

        DAI_ConditionRegistry.register(
                "target_is_animal",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                context.hasTarget()
                                        && context.target()
                                        instanceof Animal
                        )
        );

        DAI_ConditionRegistry.register(
                "target_is_baby",
                (context, condition) -> {

                    if (
                            !context.hasTarget()
                                    || !(context.target()
                                    instanceof AgeableMob ageableTarget)
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            ageableTarget.isBaby()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_is_adult",
                (context, condition) -> {

                    if (
                            !context.hasTarget()
                                    || !(context.target()
                                    instanceof AgeableMob ageableTarget)
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            !ageableTarget.isBaby()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_name",
                (context, condition) -> {

                    if (!context.hasTarget()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.string(
                            context.target()
                                    .getName()
                                    .getString()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_custom_name",
                (context, condition) -> {

                    if (!context.hasTarget()) {
                        return DAI_ConditionValue.missing();
                    }

                    if (!context.target().hasCustomName()) {
                        return DAI_ConditionValue.string("");
                    }

                    return DAI_ConditionValue.string(
                            context.target()
                                    .getCustomName()
                                    .getString()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_has_custom_name",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                context.hasTarget()
                                        && context.target()
                                        .hasCustomName()
                        )
        );
    }
}
