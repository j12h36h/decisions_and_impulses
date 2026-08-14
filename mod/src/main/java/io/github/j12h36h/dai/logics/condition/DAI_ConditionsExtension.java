package io.github.j12h36h.dai.logics.condition;

import io.github.j12h36h.dai.api.DAI_CapabilityStore;
import io.github.j12h36h.dai.api.DAI_Reference;
import io.github.j12h36h.dai.api.DAI_ReferenceStore;
import io.github.j12h36h.dai.api.DAI_StateStore;
import io.github.j12h36h.dai.api.DAI_StateValue;
import io.github.j12h36h.dai.api.DAI_EntityTargetResolver;
import io.github.j12h36h.dai.attributes.DAI_AttributeRegistry;
import io.github.j12h36h.dai.attributes.DAI_AttributeStore;
import io.github.j12h36h.dai.attributes.DAI_NativeAttributeSupport;
import io.github.j12h36h.dai.animations.DAI_AnimationRegistry;
import io.github.j12h36h.dai.animations.DAI_AnimationRuntime;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.content.DAI_ContentRuntime;
import io.github.j12h36h.dai.content.DAI_ContentStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class DAI_ConditionsExtension {

    private DAI_ConditionsExtension() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "state",
                (context, condition) ->
                        readState(
                                condition.parameter()
                        )
        );

        DAI_ConditionRegistry.register(
                "state_exists",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                DAI_StateStore.contains(
                                        condition.parameter()
                                )
                        )
        );

        DAI_ConditionRegistry.register(
                "capability",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                DAI_CapabilityStore.has(
                                        condition.parameter()
                                )
                        )
        );

        DAI_ConditionRegistry.register(
                "reference_exists",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                DAI_ReferenceStore.contains(
                                        condition.parameter()
                                )
                        )
        );

        DAI_ConditionRegistry.register(
                "reference_type",
                (context, condition) -> {

                    DAI_Reference reference =
                            DAI_ReferenceStore.get(
                                    condition.parameter()
                            );

                    return reference == null
                            ? DAI_ConditionValue.missing()
                            : DAI_ConditionValue.string(
                                    reference.type()
                                            .name()
                                            .toLowerCase()
                            );
                }
        );

        DAI_ConditionRegistry.register(
                "reference_age",
                (context, condition) -> {

                    if (
                            !DAI_ReferenceStore.contains(
                                    condition.parameter()
                            )
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            DAI_ReferenceStore.ageTicks(
                                    condition.parameter()
                            )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "reference_distance",
                (context, condition) -> {

                    Minecraft minecraft =
                            Minecraft.getInstance();

                    Vec3 target =
                            DAI_ReferenceStore.resolvePosition(
                                    condition.parameter()
                            );

                    if (
                            minecraft == null
                                    || minecraft.player == null
                                    || target == null
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            minecraft.player
                                    .position()
                                    .distanceTo(
                                            target
                                    )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "reference_entity_alive",
                (context, condition) -> {

                    DAI_Reference reference =
                            DAI_ReferenceStore.get(
                                    condition.parameter()
                            );

                    if (
                            reference == null
                                    || reference.type()
                                    != DAI_Reference.Type.ENTITY
                    ) {
                        return DAI_ConditionValue.bool(false);
                    }

                    Entity entity =
                            DAI_ReferenceStore.resolveEntity(
                                    condition.parameter()
                            );

                    return DAI_ConditionValue.bool(
                            entity != null
                                    && entity.isAlive()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "attribute",
                (context, condition) -> {
                    Entity entity = DAI_EntityTargetResolver.resolve(condition.target());
                    double value = DAI_AttributeStore.value(entity, condition.parameter());
                    return Double.isFinite(value)
                            ? DAI_ConditionValue.number(value)
                            : DAI_ConditionValue.missing();
                }
        );

        DAI_ConditionRegistry.register(
                "attribute_exists",
                (context, condition) -> DAI_ConditionValue.bool(
                        DAI_AttributeRegistry.contains(condition.parameter())
                )
        );

        DAI_ConditionRegistry.register(
                "attribute_modifier",
                (context, condition) -> {
                    Entity entity = DAI_EntityTargetResolver.resolve(condition.target());
                    return DAI_ConditionValue.bool(
                            DAI_AttributeStore.hasModifier(
                                    entity,
                                    condition.parameter(),
                                    condition.stringValue()
                            )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "native_attribute",
                (context, condition) -> {
                    Entity entity = DAI_EntityTargetResolver.resolve(condition.target());
                    if (!(entity instanceof LivingEntity living)) return DAI_ConditionValue.missing();
                    double value = DAI_NativeAttributeSupport.read(living, condition.parameter());
                    return Double.isFinite(value)
                            ? DAI_ConditionValue.number(value)
                            : DAI_ConditionValue.missing();
                }
        );

        DAI_ConditionRegistry.register(
                "native_attribute_modifier",
                (context, condition) -> {
                    Entity entity = DAI_EntityTargetResolver.resolve(condition.target());
                    return DAI_ConditionValue.bool(
                            entity instanceof LivingEntity living
                                    && DAI_NativeAttributeSupport.hasModifier(
                                            living,
                                            condition.parameter(),
                                            condition.stringValue()
                                    )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "animation_playing",
                (context, condition) -> DAI_ConditionValue.bool(
                        DAI_AnimationRuntime.isPlaying(
                                DAI_EntityTargetResolver.resolve(condition.target()),
                                condition.parameter()
                        )
                )
        );

        DAI_ConditionRegistry.register(
                "animation_finished",
                (context, condition) -> DAI_ConditionValue.bool(
                        DAI_AnimationRuntime.finished(
                                DAI_EntityTargetResolver.resolve(condition.target()),
                                condition.parameter()
                        )
                )
        );

        DAI_ConditionRegistry.register(
                "animation_paused",
                (context, condition) -> DAI_ConditionValue.bool(
                        DAI_AnimationRuntime.isPaused(
                                DAI_EntityTargetResolver.resolve(condition.target()),
                                condition.parameter()
                        )
                )
        );

        DAI_ConditionRegistry.register(
                "animation_tick",
                (context, condition) -> DAI_ConditionValue.number(
                        DAI_AnimationRuntime.tickOf(
                                DAI_EntityTargetResolver.resolve(condition.target()),
                                condition.parameter()
                        )
                )
        );

        DAI_ConditionRegistry.register(
                "content_exists",
                (context, condition) -> DAI_ConditionValue.bool(
                        DAI_ContentRegistry.contains(condition.parameter())
                )
        );

        DAI_ConditionRegistry.register(
                "content_active",
                (context, condition) -> DAI_ConditionValue.bool(
                        DAI_ContentRuntime.isActive(
                                DAI_EntityTargetResolver.resolve(condition.target()),
                                condition.parameter()
                        )
                )
        );

        DAI_ConditionRegistry.register(
                "content_kind",
                (context, condition) -> {
                    DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(condition.parameter());
                    return entry == null
                            ? DAI_ConditionValue.missing()
                            : DAI_ConditionValue.string(entry.kind().id());
                }
        );

        DAI_ConditionRegistry.register(
                "content_tag",
                (context, condition) -> {
                    DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(condition.parameter());
                    return DAI_ConditionValue.bool(
                            entry != null
                                    && entry.definition().tags().stream()
                                    .anyMatch(tag -> tag.equalsIgnoreCase(condition.stringValue()))
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "content_capability",
                (context, condition) -> {
                    DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(condition.parameter());
                    return DAI_ConditionValue.bool(
                            entry != null
                                    && entry.definition().capabilities().stream()
                                    .anyMatch(capability -> capability.equalsIgnoreCase(condition.stringValue()))
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "held_content",
                (context, condition) -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft == null || minecraft.player == null) return DAI_ConditionValue.missing();
                    String id = DAI_ContentStack.id(minecraft.player.getMainHandItem());
                    return id.isBlank() ? DAI_ConditionValue.missing() : DAI_ConditionValue.string(id);
                }
        );

        DAI_ConditionRegistry.register(
                "holding_content",
                (context, condition) -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft == null || minecraft.player == null) return DAI_ConditionValue.bool(false);
                    return DAI_ConditionValue.bool(
                            condition.parameter().equalsIgnoreCase(
                                    DAI_ContentStack.id(minecraft.player.getMainHandItem())
                            )
                    );
                }
        );
    }

    private static DAI_ConditionValue readState(
            String key
    ) {

        DAI_StateValue value =
                DAI_StateStore.get(
                        key
                );

        return switch (value.type()) {

            case BOOLEAN ->
                    DAI_ConditionValue.bool(
                            value.booleanValue()
                    );

            case NUMBER ->
                    DAI_ConditionValue.number(
                            value.numberValue()
                    );

            case STRING ->
                    DAI_ConditionValue.string(
                            value.stringValue()
                    );

            case MISSING ->
                    DAI_ConditionValue.missing();
        };
    }
}
