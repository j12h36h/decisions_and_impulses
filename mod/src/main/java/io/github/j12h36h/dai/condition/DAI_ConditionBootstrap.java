package io.github.j12h36h.dai.condition;

public final class DAI_ConditionBootstrap {

    private DAI_ConditionBootstrap() {
        // Utility class.
    }

    public static void initialize() {
        DAI_ConditionRegistry.register("always", () -> true);
        DAI_ConditionRegistry.register("never", () -> false);
    }
}
