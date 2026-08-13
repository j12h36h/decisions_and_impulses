package io.github.j12h36h.dai.logics.condition;

import io.github.j12h36h.dai.reactions.DAI_ReactionContext;
import io.github.j12h36h.dai.reactions.DAI_ReactionRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

public final class DAI_ConditionsReaction {

    private DAI_ConditionsReaction() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "reaction_active",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                DAI_ReactionRuntime.current()
                                        != null
                        )
        );

        DAI_ConditionRegistry.register(
                "reaction_event",
                (context, condition) -> {

                    DAI_ReactionContext reaction =
                            DAI_ReactionRuntime.current();

                    if (reaction == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.string(
                            reaction.event()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "reaction_phase",
                (context, condition) -> {

                    DAI_ReactionContext reaction =
                            DAI_ReactionRuntime.current();

                    if (
                            reaction == null
                                    || reaction.phase() == null
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.string(
                            reaction.phase()
                                    .id()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "reaction_has_entity",
                (context, condition) -> {

                    DAI_ReactionContext reaction =
                            DAI_ReactionRuntime.current();

                    return DAI_ConditionValue.bool(
                            reaction != null
                                    && reaction.entity() != null
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "reaction_entity_type",
                (context, condition) -> {

                    DAI_ReactionContext reaction =
                            DAI_ReactionRuntime.current();

                    if (
                            reaction == null
                                    || reaction.entity() == null
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    Identifier identifier =
                            BuiltInRegistries.ENTITY_TYPE
                                    .getKey(
                                            reaction.entity()
                                                    .getType()
                                    );

                    if (identifier == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.string(
                            identifier.toString()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "reaction_entity_living",
                (context, condition) -> {

                    DAI_ReactionContext reaction =
                            DAI_ReactionRuntime.current();

                    return DAI_ConditionValue.bool(
                            reaction != null
                                    && reaction.entity()
                                    instanceof LivingEntity
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "reaction_entity_health",
                (context, condition) -> {

                    DAI_ReactionContext reaction =
                            DAI_ReactionRuntime.current();

                    if (
                            reaction == null
                                    || !(
                                    reaction.entity()
                                            instanceof LivingEntity living
                            )
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            living.getHealth()
                    );
                }
        );
    }
}
