package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.condition.DAI_ConditionEvaluator;
import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DAI_ActionResolver {

    private static final int MAX_DEPTH =
            64;

    private static final int MAX_EXPANDED_ACTIONS =
            1024;

    private DAI_ActionResolver() {
        // Utility class.
    }

    /**
     * Resolves a registered action by identifier into its executable
     * atomic actions.
     */
    public static List<DAI_ActionDefinition> resolve(
            String actionId
    ) {

        Identifier id =
                parseReference(
                        actionId
                );

        if (id == null) {

            DAI_Core.LOGGER.error(
                    "<DAI>: Invalid action id '{}'.",
                    actionId
            );

            return List.of();
        }

        DAI_ActionDefinition action =
                DAI_ActionLibrary.get(
                        id
                );

        if (action == null) {

            DAI_Core.LOGGER.error(
                    "<DAI>: Unknown action '{}'.",
                    id
            );

            return List.of();
        }

        return resolve(
                action
        );
    }

    /**
     * Resolves an action definition into its executable atomic actions.
     */
    public static List<DAI_ActionDefinition> resolve(
            DAI_ActionDefinition action
    ) {

        List<DAI_ActionDefinition> resolved =
                new ArrayList<>();

        resolve(
                action,
                resolved,
                new HashSet<>(),
                0
        );

        return List.copyOf(
                resolved
        );
    }

    private static void resolve(
            DAI_ActionDefinition action,
            List<DAI_ActionDefinition> output,
            Set<Identifier> resolving,
            int depth
    ) {

        if (action == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot resolve a null action."
            );

            return;
        }

        if (depth >= MAX_DEPTH) {

            DAI_Core.LOGGER.error(
                    "<DAI>: Action resolution exceeded maximum depth of {}.",
                    MAX_DEPTH
            );

            return;
        }

        if (output.size() >= MAX_EXPANDED_ACTIONS) {

            DAI_Core.LOGGER.error(
                    "<DAI>: Action resolution exceeded {} atomic actions.",
                    MAX_EXPANDED_ACTIONS
            );

            return;
        }

        /*
         * Sequence-container conditions are evaluated during
         * resolution because they determine whether the entire
         * sequence should be expanded.
         *
         * Typed runtime actions that also carry a sequence, such as
         * random_action, remain atomic and keep their conditions for
         * queue-time evaluation.
         */
        if (
                "sequence".equals(
                        action.type()
                )
                        || (
                        !action.hasType()
                                && action.hasSequence()
                )
        ) {

            if (
                    !DAI_ConditionEvaluator.evaluateAll(
                            action.conditions()
                    )
            ) {
                return;
            }

            for (
                    DAI_ActionDefinition child
                    : action.sequence()
            ) {

                if (
                        output.size()
                                >= MAX_EXPANDED_ACTIONS
                ) {

                    DAI_Core.LOGGER.error(
                            "<DAI>: Action resolution exceeded {} atomic actions.",
                            MAX_EXPANDED_ACTIONS
                    );

                    return;
                }

                resolve(
                        child,
                        output,
                        resolving,
                        depth + 1
                );
            }

            return;
        }

        /*
         * Pure reference-node conditions are evaluated during
         * resolution because they determine whether the referenced
         * action or sequence should be expanded.
         *
         * A typed runtime action may also use the action field as
         * payload. Those nodes remain atomic.
         */
        if (
                !action.hasType()
                        && action.hasAction()
        ) {

            if (
                    !DAI_ConditionEvaluator.evaluateAll(
                            action.conditions()
                    )
            ) {
                return;
            }

            Identifier id =
                    parseReference(
                            action.action()
                    );

            if (id == null) {

                DAI_Core.LOGGER.error(
                        "<DAI>: Invalid action reference '{}'.",
                        action.action()
                );

                return;
            }

            if (!resolving.add(id)) {

                DAI_Core.LOGGER.error(
                        "<DAI>: Circular action reference '{}'.",
                        id
                );

                return;
            }

            try {

                DAI_ActionDefinition referenced =
                        DAI_ActionLibrary.get(
                                id
                        );

                if (referenced == null) {

                    DAI_Core.LOGGER.error(
                            "<DAI>: Unknown action reference '{}'.",
                            id
                    );

                    return;
                }

                resolve(
                        referenced,
                        output,
                        resolving,
                        depth + 1
                );

            } finally {

                resolving.remove(
                        id
                );
            }

            return;
        }

        /*
         * A node without a type, sequence, or action reference
         * cannot be executed.
         */
        if (!action.hasType()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Ignoring action node without a type, sequence, or reference."
            );

            return;
        }

        /*
         * Atomic-action conditions are intentionally not evaluated
         * here. They stay attached to the definition and are checked
         * by DAI_ActionQueue immediately before execution.
         *
         * This allows runtime conditions such as last_action_success
         * and last_action_failure to observe the result of the action
         * that executed immediately before this one.
         */
        output.add(
                action
        );
    }

    public static Identifier parseReference(
            String reference
    ) {

        if (
                reference == null
                        || reference.isBlank()
        ) {
            return null;
        }

        String normalized =
                reference.trim();

        if (!normalized.contains(":")) {

            normalized =
                    DAI_Core.MODID
                            + ":"
                            + normalized;
        }

        return Identifier.tryParse(
                normalized
        );
    }
}