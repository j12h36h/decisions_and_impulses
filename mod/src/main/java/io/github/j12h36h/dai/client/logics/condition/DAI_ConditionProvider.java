package io.github.j12h36h.dai.client.logics.condition;

import io.github.j12h36h.dai.logics.condition.DAI_ConditionDefinition;

@FunctionalInterface
public interface DAI_ConditionProvider {

    DAI_ConditionValue read(
            DAI_ConditionContext context,
            DAI_ConditionDefinition condition
    );
}
