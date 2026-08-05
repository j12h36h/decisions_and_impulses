package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.condition.DAI_ConditionEval;
import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DAI_ActionResolver {

    private static final int MAX_DEPTH = 64;
    private static final int MAX_EXPANDED_ACTIONS = 1024;

    private DAI_ActionResolver() {
        // Utility class.
    }

    public static List<DAI_ActionCore> resolve(
            DAI_ActionCore action
    ) {

        List<DAI_ActionCore> resolved =
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
            DAI_ActionCore action,
            List<DAI_ActionCore> output,
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

        if (
                !DAI_ConditionEval.evaluateAll(
                        action.conditions()
                )
        ) {
            return;
        }

        /*
         * Only true sequence containers are expanded immediately.
         *
         * Typed runtime actions such as random_action may also carry
         * a sequence, but must remain atomic until execution.
         */
        if (
                "sequence".equals(action.type())
                        || (
                        !action.hasType()
                                && action.hasSequence()
                )
        ) {

            for (
                    DAI_ActionCore child
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
         * Only a pure reference node is resolved immediately.
         *
         * Example:
         *
         * {
         *   "action": "decisions_and_impulses:attack_basic"
         * }
         *
         * An atomic action may also carry an action id as payload:
         *
         * {
         *   "type": "enqueue_action",
         *   "action": "decisions_and_impulses:survival_wander_b"
         * }
         *
         * Because that node has a type, it must remain atomic and reach
         * DAI_ActionLogic.enqueueAction at runtime.
         */
        if (
                !action.hasType()
                        && action.hasAction()
        ) {

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

                DAI_ActionCore referenced =
                        DAI_ActionManager.get(id);

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

                resolving.remove(id);
            }

            return;
        }

        /*
         * A node without either a type, sequence, or action reference
         * cannot be executed.
         */
        if (!action.hasType()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Ignoring action node without a type, sequence, or reference."
            );

            return;
        }

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
