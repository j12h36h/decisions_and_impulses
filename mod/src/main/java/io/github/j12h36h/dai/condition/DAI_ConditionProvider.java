package io.github.j12h36h.dai.condition;

@FunctionalInterface
public interface DAI_ConditionProvider {

    DAI_ConditionValue read(
            DAI_ConditionContext context,
            DAI_ConditionDefinition condition
    );
}
