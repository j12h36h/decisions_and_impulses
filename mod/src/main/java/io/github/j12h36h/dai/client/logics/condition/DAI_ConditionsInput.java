package io.github.j12h36h.dai.client.logics.condition;

import io.github.j12h36h.dai.client.logics.input.DAI_KeybindStateTracker;
import io.github.j12h36h.dai.client.logics.input.DAI_KeyMappings;
import net.minecraft.client.KeyMapping;

public final class DAI_ConditionsInput {

    private DAI_ConditionsInput() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "keybind_exists",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                DAI_KeybindStateTracker.exists(
                                        condition.parameter()
                                )
                        )
        );

        DAI_ConditionRegistry.register(
                "keybind_held",
                (context, condition) ->
                        readBoolean(
                                condition.parameter(),
                                DAI_KeybindStateTracker::isHeld
                        )
        );

        DAI_ConditionRegistry.register(
                "keybind_pressed",
                (context, condition) ->
                        readBoolean(
                                condition.parameter(),
                                DAI_KeybindStateTracker::wasPressed
                        )
        );

        DAI_ConditionRegistry.register(
                "keybind_released",
                (context, condition) ->
                        readBoolean(
                                condition.parameter(),
                                DAI_KeybindStateTracker::wasReleased
                        )
        );
    }

    private static DAI_ConditionValue readBoolean(
            String id,
            KeyStateReader reader
    ) {

        KeyMapping mapping =
                DAI_KeyMappings.get(id);

        if (mapping == null) {
            return DAI_ConditionValue.missing();
        }

        return DAI_ConditionValue.bool(
                reader.read(id)
        );
    }

    @FunctionalInterface
    private interface KeyStateReader {
        boolean read(String id);
    }
}
