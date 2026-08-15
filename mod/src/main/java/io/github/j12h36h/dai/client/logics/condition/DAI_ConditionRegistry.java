package io.github.j12h36h.dai.client.logics.condition;

import io.github.j12h36h.dai.logics.condition.DAI_ConditionDefinition;

import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DAI_ConditionRegistry {

    private static final Map<String, DAI_ConditionProvider> CONDITIONS =
            new HashMap<>();

    private DAI_ConditionRegistry() {
        // Utility class.
    }

    public static void register(
            String id,
            DAI_ConditionProvider provider
    ) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Condition id cannot be null or blank."
            );
        }

        if (provider == null) {
            throw new IllegalArgumentException(
                    "Condition provider cannot be null."
            );
        }

        String normalizedId =
                normalize(id);

        DAI_ConditionProvider previous =
                CONDITIONS.put(
                        normalizedId,
                        provider
                );

        if (previous == null) {

            DAI_Core.debug(
                    "<DAI>: Registered condition '{}'.",
                    normalizedId
            );

        } else {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Replaced condition '{}'.",
                    normalizedId
            );
        }
    }

    public static DAI_ConditionValue read(
            DAI_ConditionContext context,
            DAI_ConditionDefinition condition
    ) {

        if (condition == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot read a null condition."
            );

            return DAI_ConditionValue.missing();
        }

        if (context == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot read condition '{}' with a null context.",
                    condition.type()
            );

            return DAI_ConditionValue.missing();
        }

        String type =
                normalize(
                        condition.type()
                );

        DAI_ConditionProvider provider =
                CONDITIONS.get(type);

        if (provider == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Unknown condition '{}'.",
                    type
            );

            return DAI_ConditionValue.missing();
        }

        try {

            DAI_ConditionValue value =
                    provider.read(
                            context,
                            condition
                    );

            if (value == null) {

                DAI_Core.LOGGER.warn(
                        "<DAI>: Condition provider '{}' returned null.",
                        type
                );

                return DAI_ConditionValue.missing();
            }

            DAI_Core.debug(
                    "<DAI>: Read condition '{}' as {}.",
                    type,
                    value
            );

            return value;

        } catch (RuntimeException exception) {

            DAI_Core.LOGGER.error(
                    "<DAI>: Condition provider '{}' failed.",
                    type,
                    exception
            );

            return DAI_ConditionValue.missing();
        }
    }

    public static boolean contains(
            String id
    ) {

        return id != null
                && CONDITIONS.containsKey(
                normalize(id)
        );
    }

    public static int size() {
        return CONDITIONS.size();
    }

    public static Set<String> ids() {
        return Set.copyOf(
                CONDITIONS.keySet()
        );
    }

    public static void clear() {

        int removed =
                CONDITIONS.size();

        CONDITIONS.clear();

        DAI_Core.debug(
                "<DAI>: Cleared condition registry (removed {}).",
                removed
        );
    }

    private static String normalize(
            String value
    ) {

        return value == null
                ? ""
                : value.trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }
}