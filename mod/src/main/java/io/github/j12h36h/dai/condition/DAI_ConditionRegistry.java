package io.github.j12h36h.dai.condition;

import io.github.j12h36h.dai.core.DAI_Core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class DAI_ConditionRegistry {

    private static final Map<String, BooleanSupplier> CONDITIONS =
            new HashMap<>();

    private DAI_ConditionRegistry() {
        // Utility class.
    }

    public static void register(
            String id,
            BooleanSupplier evaluator
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Condition id cannot be null or blank.");
        }
        if (evaluator == null) {
            throw new IllegalArgumentException("Condition evaluator cannot be null.");
        }

        String normalizedId = normalize(id);
        BooleanSupplier previous = CONDITIONS.put(normalizedId, evaluator);

        if (previous == null) {
            DAI_Core.LOGGER.debug("<DAI>: Registered condition '{}'.", normalizedId);
        } else {
            DAI_Core.LOGGER.warn("<DAI>: Replaced condition '{}'.", normalizedId);
        }
    }

    public static boolean evaluate(DAI_Condition condition) {
        if (condition == null) {
            DAI_Core.LOGGER.warn("<DAI>: Cannot evaluate a null condition.");
            return false;
        }

        String type = normalize(condition.type());
        BooleanSupplier evaluator = CONDITIONS.get(type);

        if (evaluator == null) {
            DAI_Core.LOGGER.warn("<DAI>: Unknown condition '{}'.", type);
            return false;
        }

        boolean result = evaluator.getAsBoolean();
        DAI_Core.LOGGER.debug("<DAI>: Condition '{}' = {}.", type, result);
        return result;
    }

    public static boolean contains(String id) {
        return id != null && CONDITIONS.containsKey(normalize(id));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
