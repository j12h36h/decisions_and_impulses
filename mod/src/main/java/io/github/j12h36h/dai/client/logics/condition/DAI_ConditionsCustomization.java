package io.github.j12h36h.dai.client.logics.condition;

import io.github.j12h36h.dai.client.customization.DAI_GameCustomizationLogic;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationRegistry;

/** Conditions for querying the 1.9 customization registry/state layer. */
public final class DAI_ConditionsCustomization {

    private DAI_ConditionsCustomization() {}

    public static void registerAll() {
        DAI_ConditionRegistry.register(
                "customization_exists",
                (context, condition) -> {
                    DAI_GameCustomizationKind kind = DAI_GameCustomizationKind.parse(condition.target());
                    return DAI_ConditionValue.bool(
                            kind != null && DAI_GameCustomizationRegistry.get(kind, condition.stringValue()) != null
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "customization_active",
                (context, condition) -> {
                    DAI_GameCustomizationKind kind = DAI_GameCustomizationKind.parse(condition.target());
                    return DAI_ConditionValue.bool(
                            kind != null && DAI_GameCustomizationLogic.isActive(kind, condition.stringValue())
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "customization_count",
                (context, condition) -> {
                    DAI_GameCustomizationKind kind = DAI_GameCustomizationKind.parse(condition.stringValue());
                    return kind == null
                            ? DAI_ConditionValue.missing()
                            : DAI_ConditionValue.number(DAI_GameCustomizationRegistry.size(kind));
                }
        );
    }
}
