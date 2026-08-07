package io.github.j12h36h.dai.logic;

import io.github.j12h36h.dai.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.action.DAI_ActionQueue;
import io.github.j12h36h.dai.action.DAI_ActionResolver;
import io.github.j12h36h.dai.action.DAI_ActionResult;
import io.github.j12h36h.dai.action.DAI_ActionStatus;
import io.github.j12h36h.dai.core.DAI_Core;

import java.util.List;

public final class DAI_FlowLogic {

    private DAI_FlowLogic() {
        // Utility class.
    }

    /**
     * Clears all remaining queued actions when the previous action
     * failed, was cancelled, or timed out.
     *
     * The flow action itself completes successfully after clearing
     * the queue.
     */
    public static void stopIfFailure(
            DAI_ActionDefinition action
    ) {

        if (!DAI_ActionStatus.previousFailed()) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: stop_if_failure skipped because previousResult={}.",
                    DAI_ActionStatus.previous()
            );

            return;
        }

        DAI_ActionResult previousResult =
                DAI_ActionStatus.previous();

        DAI_ActionQueue.clear();

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Cleared action queue because the previous action finished with result={}.",
                previousResult
        );
    }

    /**
     * Clears all remaining queued actions when the previous action
     * completed successfully.
     */
    public static void stopIfSuccess(
            DAI_ActionDefinition action
    ) {

        if (!DAI_ActionStatus.previousSucceeded()) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: stop_if_success skipped because previousResult={}.",
                    DAI_ActionStatus.previous()
            );

            return;
        }

        DAI_ActionQueue.clear();

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Cleared action queue because the previous action succeeded."
        );
    }

    /**
     * Prepends the referenced action or sequence when the previous
     * action failed, was cancelled, or timed out.
     *
     * The referenced action identifier is supplied through
     * action.action().
     */
    public static void runIfFailure(
            DAI_ActionDefinition action
    ) {

        if (!DAI_ActionStatus.previousFailed()) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: run_if_failure skipped because previousResult={}.",
                    DAI_ActionStatus.previous()
            );

            return;
        }

        enqueueReferencedAction(
                action,
                "failure"
        );
    }

    /**
     * Prepends the referenced action or sequence when the previous
     * action completed successfully.
     *
     * The referenced action identifier is supplied through
     * action.action().
     */
    public static void runIfSuccess(
            DAI_ActionDefinition action
    ) {

        if (!DAI_ActionStatus.previousSucceeded()) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: run_if_success skipped because previousResult={}.",
                    DAI_ActionStatus.previous()
            );

            return;
        }

        enqueueReferencedAction(
                action,
                "success"
        );
    }

    private static void enqueueReferencedAction(
            DAI_ActionDefinition action,
            String branch
    ) {

        if (
                action == null
                        || !action.hasAction()
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: run_if_{} requires an action reference in 'action'.",
                    branch
            );

            return;
        }

        String actionId =
                action.action();

        List<DAI_ActionDefinition> resolved =
                DAI_ActionResolver.resolve(
                        actionId
                );

        if (resolved.isEmpty()) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Conditional {} branch '{}' could not be resolved into executable actions.",
                    branch,
                    actionId
            );

            return;
        }

        DAI_ActionQueue.enqueueFirstAll(
                resolved
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Prepended conditional {} branch '{}' containing {} atomic action(s).",
                branch,
                actionId,
                resolved.size()
        );
    }
}