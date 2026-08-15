package io.github.j12h36h.dai.client.logics.condition;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public final class DAI_ConditionsEffect {

    private DAI_ConditionsEffect() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "player_effect_exists",
                (context, condition) -> {

                    MobEffectInstance effect =
                            getPlayerEffect(
                                    context,
                                    condition.stringValue()
                            );

                    return DAI_ConditionValue.bool(
                            effect != null
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_effect_duration",
                (context, condition) -> {

                    MobEffectInstance effect =
                            getPlayerEffect(
                                    context,
                                    condition.stringValue()
                            );

                    if (effect == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            effect.getDuration()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_effect_amplifier",
                (context, condition) -> {

                    MobEffectInstance effect =
                            getPlayerEffect(
                                    context,
                                    condition.stringValue()
                            );

                    if (effect == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            effect.getAmplifier()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_effect_exists",
                (context, condition) -> {

                    MobEffectInstance effect =
                            getTargetEffect(
                                    context,
                                    condition.stringValue()
                            );

                    return DAI_ConditionValue.bool(
                            effect != null
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_effect_duration",
                (context, condition) -> {

                    MobEffectInstance effect =
                            getTargetEffect(
                                    context,
                                    condition.stringValue()
                            );

                    if (effect == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            effect.getDuration()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_effect_amplifier",
                (context, condition) -> {

                    MobEffectInstance effect =
                            getTargetEffect(
                                    context,
                                    condition.stringValue()
                            );

                    if (effect == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            effect.getAmplifier()
                    );
                }
        );
    }

    private static MobEffectInstance getPlayerEffect(
            DAI_ConditionContext context,
            String effectId
    ) {

        if (!context.hasPlayer()) {
            return null;
        }

        Holder<MobEffect> effect =
                resolveEffect(effectId);

        if (effect == null) {
            return null;
        }

        return context.player()
                .getEffect(effect);
    }

    private static MobEffectInstance getTargetEffect(
            DAI_ConditionContext context,
            String effectId
    ) {

        if (
                !context.hasTarget()
                        || !(context.target()
                        instanceof net.minecraft.world.entity.LivingEntity livingTarget)
        ) {
            return null;
        }

        Holder<MobEffect> effect =
                resolveEffect(effectId);

        if (effect == null) {
            return null;
        }

        return livingTarget.getEffect(effect);
    }

    private static Holder<MobEffect> resolveEffect(
            String effectId
    ) {

        Identifier id =
                Identifier.tryParse(effectId);

        if (id == null) {
            return null;
        }

        return BuiltInRegistries.MOB_EFFECT
                .get(id)
                .orElse(null);
    }
}
