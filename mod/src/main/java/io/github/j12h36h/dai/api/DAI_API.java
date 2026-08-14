package io.github.j12h36h.dai.api;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionRegistry;
import io.github.j12h36h.dai.logics.condition.DAI_ConditionProvider;
import io.github.j12h36h.dai.logics.condition.DAI_ConditionRegistry;
import io.github.j12h36h.dai.reactions.DAI_ReactionEventDefinition;
import io.github.j12h36h.dai.reactions.DAI_ReactionEventRegistry;
import io.github.j12h36h.dai.attributes.DAI_AttributeDefinition;
import io.github.j12h36h.dai.attributes.DAI_AttributeRegistry;
import io.github.j12h36h.dai.animations.DAI_AnimationDefinition;
import io.github.j12h36h.dai.animations.DAI_AnimationRegistry;
import io.github.j12h36h.dai.animations.DAI_AnimationRuntime;
import io.github.j12h36h.dai.animations.DAI_AnimationSink;
import io.github.j12h36h.dai.content.DAI_ContentDefinition;
import io.github.j12h36h.dai.content.DAI_ContentKind;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.registry.DAI_RegistryPreflight;
import net.minecraft.resources.Identifier;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Stable entry point intended for third-party DAI integrations.
 *
 * Internal packages may continue to evolve; external mods should prefer
 * registering through this facade wherever possible.
 */
public final class DAI_API {

    private DAI_API() {
        // Utility class.
    }

    public static void registerActionType(
            String id,
            Consumer<DAI_ActionDefinition> executor
    ) {

        DAI_ActionRegistry.register(
                id,
                executor
        );
    }

    public static void registerCondition(
            String id,
            DAI_ConditionProvider provider
    ) {

        DAI_ConditionRegistry.register(
                id,
                provider
        );
    }

    public static void registerReactionEvent(
            DAI_ReactionEventDefinition definition
    ) {

        DAI_ReactionEventRegistry.register(
                definition
        );
    }

    public static Set<String> actionTypes() {
        return DAI_ActionRegistry.ids();
    }

    public static Set<String> conditionTypes() {
        return DAI_ConditionRegistry.ids();
    }

    public static Set<String> reactionEvents() {
        return DAI_ReactionEventRegistry.ids();
    }

    public static void registerAttribute(
            Identifier id,
            DAI_AttributeDefinition definition
    ) {
        DAI_AttributeRegistry.register(id, definition);
    }

    public static void registerAnimation(
            Identifier id,
            DAI_AnimationDefinition definition
    ) {
        DAI_AnimationRegistry.register(id, definition);
    }

    public static void registerAnimationSink(DAI_AnimationSink sink) {
        DAI_AnimationRuntime.registerSink(sink);
    }

    public static void registerContent(
            DAI_ContentKind kind,
            Identifier id,
            DAI_ContentDefinition definition
    ) {
        DAI_ContentRegistry.register(kind, id, definition);
    }

    public static Set<String> attributes() {
        return DAI_AttributeRegistry.ids();
    }

    public static Set<String> animations() {
        return DAI_AnimationRegistry.ids();
    }

    public static Set<String> content() {
        return DAI_ContentRegistry.ids();
    }

    /** True when this process discovered native ids that require another JVM start. */
    public static boolean registryRestartRequired() {
        return DAI_RegistryPreflight.restartRequired();
    }

    /** Native content ids discovered this session that require another JVM start. */
    public static Set<String> pendingRegistryContent() {
        return DAI_RegistryPreflight.pendingSpecs().values().stream()
                .map(spec -> spec.id())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Native content ids successfully staged and registered by DAI this launch. */
    public static Set<String> registeredNativeContent() {
        return DAI_RegistryPreflight.registeredSpecs().values().stream()
                .map(spec -> spec.id())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}


