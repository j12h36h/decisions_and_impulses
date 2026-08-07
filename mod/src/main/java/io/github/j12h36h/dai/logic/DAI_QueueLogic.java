package io.github.j12h36h.dai.logic;

import io.github.j12h36h.dai.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.action.DAI_ActionQueue;
import io.github.j12h36h.dai.action.DAI_ActionResolver;
import io.github.j12h36h.dai.core.DAI_Core;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class DAI_QueueLogic {

    private DAI_QueueLogic() {
        // Utility class.
    }

    public static void delay(
            DAI_ActionDefinition action
    ) {

        int ticks =
                action.ticks();

        if (ticks <= 0) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Ignoring queue delay with no duration."
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Delaying action queue for {} tick(s).",
                ticks
        );

        DAI_ActionQueue.delay(
                ticks
        );
    }

    public static void sequence(
            DAI_ActionDefinition action
    ) {

        /*
         * Sequences should normally be expanded by DAI_ActionResolver
         * before reaching the registry.
         */
        DAI_Core.LOGGER.warn(
                "<DAI>: Sequence reached execution without prior resolution."
        );
    }

    public static void enqueueAction(
            DAI_ActionDefinition action
    ) {

        if (!action.hasAction()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: enqueue_action requires an action id."
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Deferred enqueue of action '{}'.",
                action.action()
        );

        DAI_ActionQueue.enqueueAll(
                DAI_ActionResolver.resolve(
                        action.action()
                )
        );
    }

    public static void clearQueue(
            DAI_ActionDefinition action
    ) {

        DAI_ActionQueue.clear();

        DAI_Core.LOGGER.debug(
                "<DAI>: Cleared action queue."
        );
    }

    public static void randomAction(
            DAI_ActionDefinition action
    ) {

        List<DAI_ActionDefinition> choices =
                action.sequence();

        if (choices.isEmpty()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: random_action requires at least one sequence entry."
            );

            return;
        }

        int selectedIndex =
                ThreadLocalRandom.current()
                        .nextInt(
                                choices.size()
                        );

        DAI_ActionDefinition selectedAction =
                choices.get(
                        selectedIndex
                );

        DAI_Core.LOGGER.debug(
                "<DAI>: Randomly selected action branch {}/{}.",
                selectedIndex + 1,
                choices.size()
        );

        List<DAI_ActionDefinition> resolvedActions =
                DAI_ActionResolver.resolve(
                        selectedAction
                );

        if (resolvedActions.isEmpty()) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Randomly selected branch resolved to no executable actions."
            );

            return;
        }

        DAI_ActionQueue.enqueueFirstAll(
                resolvedActions
        );
    }
}
