package io.github.j12h36h.dai.logic;

import io.github.j12h36h.dai.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.action.DAI_ActionResult;
import io.github.j12h36h.dai.action.DAI_ActionRegistry;
import io.github.j12h36h.dai.action.DAI_ActionStatus;
import io.github.j12h36h.dai.core.DAI_Core;

public final class DAI_ActionLogic {

    private DAI_ActionLogic() {
        // Utility class.
    }

    /**
     * Executes one resolved atomic action.
     */
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

        if (!action.hasType()) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot execute an action without a type."
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Executing action type='{}'.",
                action.type()
        );

        /*
         * DAI_ActionRegistry is responsible for:
         *
         * - assigning the default SUCCESS state
         * - dispatching the registered handler
         * - converting handler exceptions into FAILURE
         *
         * This class simply validates the action before dispatch.
         */
        DAI_ActionRegistry.execute(
                action
        );
    }
}