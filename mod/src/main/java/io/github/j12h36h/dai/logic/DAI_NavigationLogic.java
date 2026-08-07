package io.github.j12h36h.dai.logic;

import io.github.j12h36h.dai.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.action.DAI_ActionQueue;
import io.github.j12h36h.dai.action.DAI_ActionResult;
import io.github.j12h36h.dai.action.DAI_ActionStatus;
import io.github.j12h36h.dai.controller.DAI_ApproachController;
import io.github.j12h36h.dai.controller.DAI_ExploreController;
import io.github.j12h36h.dai.core.DAI_Core;

import java.util.List;

public final class DAI_NavigationLogic {

    private static final int DEFAULT_APPROACH_TIMEOUT_TICKS =
            200;

    private static final int DEFAULT_APPROACH_WAIT_CHECKS =
            200;

    private static final int DEFAULT_EXPLORE_TIMEOUT_TICKS =
            1200;

    private static final int DEFAULT_EXPLORE_RADIUS =
            24;

    private DAI_NavigationLogic() {
        // Utility class.
    }

    public static void approachTargetBlock(
            DAI_ActionDefinition action
    ) {

        double stopDistance =
                action.value();

        int timeoutTicks =
                action.ticks() > 0
                        ? action.ticks()
                        : DEFAULT_APPROACH_TIMEOUT_TICKS;

        DAI_ApproachController.startSelectedBlock(
                stopDistance,
                timeoutTicks
        );

        if (DAI_ApproachController.isActive()) {

            int generation =
                    DAI_ApproachController.generation();

            if (
                    !DAI_ActionQueue.bindHeadSlot(
                            "wait_for_approach",
                            generation
                    )
            ) {

                DAI_ActionStatus.set(
                        DAI_ActionResult.FAILURE
                );

                DAI_Core.LOGGER.warn(
                        "<DAI>: Approach generation={} started without an immediately-following wait_for_approach; stopping it to avoid orphaned asynchronous navigation.",
                        generation
                );

                DAI_ApproachController.stop();

                return;
            }

            /*
             * Starting the controller is a successful atomic dispatch.
             *
             * The controller itself remains active, but the following
             * wait_for_approach action must be allowed through its normal
             * last_action_success condition. Leaving the global action status
             * as RUNNING here caused that wait to be skipped, allowing the
             * rest of the objective/gameplay queue to execute concurrently
             * with navigation.
             *
             * wait_for_approach takes ownership on the next queue tick and
             * sets RUNNING while it polls this exact generation.
             */
            DAI_ActionStatus.set(
                    DAI_ActionResult.SUCCESS
            );
        }
    }

    /**
     * Searches for a requested block or block tag while navigating
     * outward through reachable terrain.
     *
     * action.action() = block id or block tag
     * action.value()  = recognition/exploration radius
     * action.ticks()  = total exploration timeout
     */
    public static void exploreForBlock(
            DAI_ActionDefinition action
    ) {

        if (
                action == null
                        || !action.hasAction()
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: explore_for_block requires a block id or block tag in 'action'."
            );

            return;
        }

        int searchRadius =
                action.value() > 0.0D
                        ? (int) Math.round(
                        action.value()
                )
                        : DEFAULT_EXPLORE_RADIUS;

        int timeoutTicks =
                action.ticks() > 0
                        ? action.ticks()
                        : DEFAULT_EXPLORE_TIMEOUT_TICKS;

        DAI_ExploreController.start(
                action.action(),
                searchRadius,
                timeoutTicks
        );
    }

    public static void waitForApproach(
            DAI_ActionDefinition action
    ) {

        int currentGeneration =
                DAI_ApproachController.generation();

        /*
         * slot is unused by wait_for_approach's public datapack behavior,
         * so internally it carries the approach-generation token.
         *
         * A zero token means this is the first wait action from the datapack;
         * bind it to the approach that is active right now.
         */
        int expectedGeneration =
                action.slot();

        /*
         * A normal approach_target_block should already have bound this wait
         * before it reaches the front of the queue.
         *
         * If an unbound wait still arrives, bind it once to the currently
         * active generation and defer the bound copy. This avoids allowing
         * the same mutable slot=0 action to repeatedly attach itself to
         * whichever approach happens to exist later.
         */
        /*
         * approach_target_block binds only its immediately-following wait via
         * DAI_ActionQueue.bindHeadSlot(). An unbound wait reaching execution
         * is stale/malformed and must never attach itself to whichever
         * approach happens to be active later.
         */
        if (expectedGeneration <= 0) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Rejected unbound wait_for_approach; approach waits must be bound by their immediately-preceding approach action."
            );

            return;
        }

        /*
         * If a newer approach has started, this queued wait belongs to an
         * older controller instance. Discard it without touching the newer
         * approach.
         *
         * Registry.begin() has already moved the newer controller's status
         * into previous, so restore that result before returning.
         */
        if (
                expectedGeneration > 0
                        && currentGeneration
                        != expectedGeneration
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionStatus.previous()
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Discarded stale wait_for_approach generation={} because current generation={}.",
                    expectedGeneration,
                    currentGeneration
            );

            return;
        }

        if (DAI_ApproachController.isActive()) {

            int checksRemaining =
                    action.ticks() > 0
                            ? action.ticks()
                            : DEFAULT_APPROACH_WAIT_CHECKS;

            if (checksRemaining <= 1) {

                /*
                 * Only the wait that belongs to this exact approach may
                 * timeout/cancel it.
                 */
                if (
                        DAI_ApproachController.generation()
                                == expectedGeneration
                ) {

                    DAI_ApproachController.stop();
                }

                DAI_ActionStatus.set(
                        DAI_ActionResult.TIMED_OUT
                );

                DAI_Core.LOGGER.warn(
                        "<DAI>: Timed out waiting for block approach generation={} to finish.",
                        expectedGeneration
                );

                return;
            }

            DAI_ActionStatus.set(
                    DAI_ActionResult.RUNNING
            );

            DAI_ActionQueue.deferFirst(
                    createWaitForApproachAction(
                            checksRemaining - 1,
                            expectedGeneration
                    ),
                    1
            );

            return;
        }

        /*
         * DAI_ActionRegistry.begin() runs before this wait handler. If the
         * approach controller completed between queue dispatches, its result
         * has just been moved from current into previous.
         */
        DAI_ActionResult result =
                DAI_ActionStatus.previous();

        if (result == DAI_ActionResult.SUCCESS) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Block approach completed successfully."
            );

            return;
        }

        if (result == DAI_ActionResult.RUNNING) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Block approach became inactive without a completion result."
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Block approach finished with result={}.",
                result
        );
    }

    public static void waitForTargetBlock(
            DAI_ActionDefinition action
    ) {

        DAI_ApproachController.requestWaitForTargetBlock(
                action
        );
    }

    private static DAI_ActionDefinition createWaitForApproachAction(
            int checksRemaining,
            int generation
    ) {

        return new DAI_ActionDefinition(
                "wait_for_approach",
                "",
                List.of(),
                List.of(),
                "",
                "",
                0.0F,
                0.0F,
                "",
                Math.max(
                        0,
                        checksRemaining
                ),
                Math.max(
                        0,
                        generation
                ),
                false,
                0.0D
        );
    }

    public static void waitForExploration(
            DAI_ActionDefinition action
    ) {

        if (DAI_ExploreController.isActive()) {

            int checksRemaining =
                    action.ticks() > 0
                            ? action.ticks()
                            : DEFAULT_EXPLORE_TIMEOUT_TICKS;

            if (checksRemaining <= 1) {

                DAI_ExploreController.stop();

                DAI_ActionStatus.set(
                        DAI_ActionResult.TIMED_OUT
                );

                DAI_Core.LOGGER.warn(
                        "<DAI>: Timed out waiting for exploration to finish."
                );

                return;
            }

            DAI_ActionStatus.set(
                    DAI_ActionResult.RUNNING
            );

            /*
             * Exploration owns the front of the queue until its controller
             * finishes. This prevents unrelated queued navigation/objective
             * actions from starting while exploration is still active.
             */
            DAI_ActionQueue.deferFirst(
                    createWaitForExplorationAction(
                            checksRemaining - 1
                    ),
                    1
            );

            return;
        }

        /*
         * Registry dispatch moves a controller completion result into
         * previous before this wait handler executes.
         */
        DAI_ActionResult result =
                DAI_ActionStatus.previous();

        if (result == DAI_ActionResult.SUCCESS) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Exploration completed successfully."
            );

            return;
        }

        if (result == DAI_ActionResult.RUNNING) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Exploration became inactive without a completion result."
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Exploration finished with result={}.",
                result
        );
    }

    private static DAI_ActionDefinition createWaitForExplorationAction(
            int checksRemaining
    ) {

        return new DAI_ActionDefinition(
                "wait_for_exploration",
                "",
                List.of(),
                List.of(),
                "",
                "",
                0.0F,
                0.0F,
                "",
                Math.max(
                        0,
                        checksRemaining
                ),
                0,
                false,
                0.0D
        );
    }
}