package io.github.j12h36h.dai.client.api;

import io.github.j12h36h.dai.animations.DAI_AnimationSink;
import io.github.j12h36h.dai.client.animations.DAI_AnimationRuntime;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionRegistry;
import io.github.j12h36h.dai.client.logics.condition.DAI_ConditionProvider;
import io.github.j12h36h.dai.client.logics.condition.DAI_ConditionRegistry;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;

import java.util.Set;
import java.util.function.Consumer;

/** Client-only extension facade for automation, perception, and rendering. */
public final class DAI_ClientAPI {

    private DAI_ClientAPI() {
        // Utility class.
    }

    public static void registerActionType(
            String id,
            Consumer<DAI_ActionDefinition> executor
    ) {
        DAI_ActionRegistry.register(id, executor);
    }

    public static void registerCondition(
            String id,
            DAI_ConditionProvider provider
    ) {
        DAI_ConditionRegistry.register(id, provider);
    }

    public static void registerAnimationSink(DAI_AnimationSink sink) {
        DAI_AnimationRuntime.registerSink(sink);
    }

    public static Set<String> actionTypes() {
        return DAI_ActionRegistry.ids();
    }

    public static Set<String> conditionTypes() {
        return DAI_ConditionRegistry.ids();
    }
}
