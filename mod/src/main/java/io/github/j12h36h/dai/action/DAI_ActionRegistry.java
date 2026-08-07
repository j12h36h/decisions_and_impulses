package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.core.DAI_Core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class DAI_ActionRegistry {

    private static final Map<
            String,
            Consumer<DAI_ActionDefinition>
            > ACTIONS =
            new HashMap<>();

    private DAI_ActionRegistry() {
        // Utility class.
    }

    public static void register(
            String id,
            Consumer<DAI_ActionDefinition> executor
    ) {

        if (
                id == null
                        || id.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Action type id cannot be null or blank."
            );
        }

        if (executor == null) {

            throw new IllegalArgumentException(
                    "Action executor cannot be null."
            );
        }

        String normalizedId =
                normalize(
                        id
                );

        Consumer<DAI_ActionDefinition> previous =
                ACTIONS.put(
                        normalizedId,
                        executor
                );

        if (previous == null) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Registered action type '{}'.",
                    normalizedId
            );

        } else {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Replaced existing action type '{}'.",
                    normalizedId
            );
        }
    }

    public static void execute(
            DAI_ActionDefinition action
    ) {

        if (action == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.error(
                    "<DAI>: Cannot execute a null action."
            );

            return;
        }

        String type =
                normalize(
                        action.type()
                );

        if (type.isEmpty()) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot execute an action with an empty type."
            );

            return;
        }

        Consumer<DAI_ActionDefinition> executor =
                ACTIONS.get(
                        type
                );

        if (executor == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Unknown action type '{}'.",
                    type
            );

            return;
        }

        /*
         * Preserve the result from the action that ran immediately
         * before this one, then initialize the new current action as
         * successful.
         *
         * Flow actions and last_action_* conditions inspect the
         * previous result. The current handler may replace the new
         * current result with RUNNING, FAILURE, CANCELLED, or
         * TIMED_OUT.
         */
        DAI_ActionStatus.begin();

        DAI_Core.LOGGER.debug(
                "<DAI>: Executing registered action type '{}'.",
                type
        );

        try {

            executor.accept(
                    action
            );

        } catch (RuntimeException exception) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.error(
                    "<DAI>: Action type '{}' failed during execution.",
                    type,
                    exception
            );
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Registered action type '{}' completed dispatch with currentResult={} and previousResult={}.",
                type,
                DAI_ActionStatus.get(),
                DAI_ActionStatus.previous()
        );
    }

    public static boolean contains(
            String id
    ) {

        if (
                id == null
                        || id.isBlank()
        ) {
            return false;
        }

        return ACTIONS.containsKey(
                normalize(
                        id
                )
        );
    }

    public static int size() {
        return ACTIONS.size();
    }

    public static void clear() {

        int removed =
                ACTIONS.size();

        ACTIONS.clear();

        DAI_Core.LOGGER.debug(
                "<DAI>: Cleared {} registered action type(s).",
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