package io.github.j12h36h.dai.client.logics.condition;

public final class DAI_ConditionsRegistry {

    private DAI_ConditionsRegistry() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "always",
                (context, condition) ->
                        DAI_ConditionValue.bool(true)
        );

        DAI_ConditionRegistry.register(
                "never",
                (context, condition) ->
                        DAI_ConditionValue.bool(false)
        );
    }
}
