package io.github.j12h36h.dai.client.logics.condition;

import io.github.j12h36h.dai.reactions.DAI_ReactionContext;
import io.github.j12h36h.dai.client.reactions.DAI_ReactionRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Minecraft;
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
                "reaction_has_block",
                (context, condition) -> {
                    DAI_ReactionContext reaction = DAI_ReactionRuntime.current();
                    return DAI_ConditionValue.bool(reaction != null && reaction.blockPos() != null);
                }
        );

        DAI_ConditionRegistry.register(
                "reaction_block_id",
                (context, condition) -> {
                    DAI_ReactionContext reaction = DAI_ReactionRuntime.current();
                    Minecraft minecraft = Minecraft.getInstance();
                    if (reaction == null || reaction.blockPos() == null
                            || minecraft == null || minecraft.level == null) {
                        return DAI_ConditionValue.missing();
                    }
                    var block = minecraft.level.getBlockState(reaction.blockPos()).getBlock();
                    Identifier identifier = BuiltInRegistries.BLOCK.getKey(block);
                    return identifier == null
                            ? DAI_ConditionValue.missing()
                            : DAI_ConditionValue.string(identifier.toString());
                }
        );

        DAI_ConditionRegistry.register(
                "reaction_block_x",
                (context, condition) -> {
                    DAI_ReactionContext reaction = DAI_ReactionRuntime.current();
                    return reaction == null || reaction.blockPos() == null
                            ? DAI_ConditionValue.missing()
                            : DAI_ConditionValue.number(reaction.blockPos().getX());
                }
        );

        DAI_ConditionRegistry.register(
                "reaction_block_y",
                (context, condition) -> {
                    DAI_ReactionContext reaction = DAI_ReactionRuntime.current();
                    return reaction == null || reaction.blockPos() == null
                            ? DAI_ConditionValue.missing()
                            : DAI_ConditionValue.number(reaction.blockPos().getY());
                }
        );

        DAI_ConditionRegistry.register(
                "reaction_block_z",
                (context, condition) -> {
                    DAI_ReactionContext reaction = DAI_ReactionRuntime.current();
                    return reaction == null || reaction.blockPos() == null
                            ? DAI_ConditionValue.missing()
                            : DAI_ConditionValue.number(reaction.blockPos().getZ());
                }
        );

        DAI_ConditionRegistry.register(
                "reaction_has_item",
                (context, condition) -> {
                    DAI_ReactionContext reaction = DAI_ReactionRuntime.current();
                    return DAI_ConditionValue.bool(
                            reaction != null && !reaction.itemId().isBlank()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "reaction_item_id",
                (context, condition) -> {
                    DAI_ReactionContext reaction = DAI_ReactionRuntime.current();
                    if (reaction == null || reaction.itemId().isBlank()) {
                        return DAI_ConditionValue.missing();
                    }
                    return DAI_ConditionValue.string(reaction.itemId());
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
