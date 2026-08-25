package io.github.j12h36h.dai.client.logics.condition;

import io.github.j12h36h.dai.client.logics.input.DAI_KeybindStateTracker;
import io.github.j12h36h.dai.client.logics.input.DAI_KeyMappings;
import io.github.j12h36h.dai.client.logics.input.DAI_KeybindRegistry;
import io.github.j12h36h.dai.client.logics.input.DAI_RawKeyStateTracker;
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
                                DAI_KeybindStateTracker.exists(condition.parameter())
                                        || DAI_KeybindRegistry.get(condition.parameter()) != null
                        )
        );

        DAI_ConditionRegistry.register(
                "keybind_held",
                (context, condition) ->
                        readBoolean(
                                condition.parameter(), "held"
                        )
        );

        DAI_ConditionRegistry.register(
                "keybind_pressed",
                (context, condition) ->
                        readBoolean(
                                condition.parameter(), "pressed"
                        )
        );

        DAI_ConditionRegistry.register(
                "keybind_released",
                (context, condition) ->
                        readBoolean(
                                condition.parameter(), "released"
                        )
        );

        DAI_ConditionRegistry.register(
                "raw_key_exists",
                (context, condition) -> DAI_ConditionValue.bool(DAI_RawKeyStateTracker.exists(condition.parameter()))
        );
        DAI_ConditionRegistry.register(
                "raw_key_held",
                (context, condition) -> DAI_ConditionValue.bool(DAI_RawKeyStateTracker.isHeld(condition.parameter()))
        );
        DAI_ConditionRegistry.register(
                "raw_key_pressed",
                (context, condition) -> DAI_ConditionValue.bool(DAI_RawKeyStateTracker.wasPressed(condition.parameter()))
        );
        DAI_ConditionRegistry.register(
                "raw_key_released",
                (context, condition) -> DAI_ConditionValue.bool(DAI_RawKeyStateTracker.wasReleased(condition.parameter()))
        );
    }

    private static DAI_ConditionValue readBoolean(String id, String mode) {
        KeyMapping mapping = DAI_KeyMappings.get(id);
        if (mapping != null) {
            return DAI_ConditionValue.bool(switch (mode) {
                case "pressed" -> DAI_KeybindStateTracker.wasPressed(id);
                case "released" -> DAI_KeybindStateTracker.wasReleased(id);
                default -> DAI_KeybindStateTracker.isHeld(id);
            });
        }
        var definition = DAI_KeybindRegistry.get(id);
        if (definition == null) return DAI_ConditionValue.missing();
        String raw = definition.rawKeyId();
        return DAI_ConditionValue.bool(switch (mode) {
            case "pressed" -> DAI_RawKeyStateTracker.wasPressed(raw);
            case "released" -> DAI_RawKeyStateTracker.wasReleased(raw);
            default -> DAI_RawKeyStateTracker.isHeld(raw);
        });
    }

}
