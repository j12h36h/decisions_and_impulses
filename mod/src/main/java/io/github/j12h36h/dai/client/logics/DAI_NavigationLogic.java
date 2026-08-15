package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.client.logics.controller.DAI_ApproachController;
import io.github.j12h36h.dai.client.logics.controller.DAI_ExploreController;
import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.util.List;

public final class DAI_NavigationLogic {

    private static final int DEFAULT_APPROACH_TIMEOUT_TICKS =
            200;

    private static final int DEFAULT_EXPLORE_TIMEOUT_TICKS =
            1200;

    private static final int DEFAULT_EXPLORE_RADIUS =
            24;

    private static final int BARRIER_POLL_TICKS =
            1;

    private DAI_NavigationLogic() {
        // Utility class.
    }

    /*
     * ------------------------------------------------------------
     * BLOCK APPROACH
     * ------------------------------------------------------------
     */

    public static void approachTargetBlock(
            DAI_ActionDefinition action
    ) {

        double stopDistance =
                action != null
                        ? action.value()
                        : 0.0D;

        int timeoutTicks =
                action != null
                        && action.ticks() > 0
                        ? action.ticks()
                        : DEFAULT_APPROACH_TIMEOUT_TICKS;

        boolean started =
                DAI_ApproachController.startSelectedBlock(
                        stopDistance,
                        timeoutTicks
                );

        if (!started) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.debug(
                    "<DAI>: Block approach failed to start."
            );

            return;
        }

        int generation =
                DAI_ApproachController.generation();

        /*
         * Promote the immediately-following wait directly into the hard
         * barrier before this start action returns.
         *
         * This removes the one-tick ownership gap that previously existed
         * between starting the controller and executing wait_for_approach.
         */
        boolean promoted =
                DAI_ActionQueue.promoteHeadToBarrier(
                        "wait_for_approach",
                        generation,
                        BARRIER_POLL_TICKS
                );

        if (!promoted) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Started block approach generation={} but could not promote the following wait_for_approach into a barrier.",
                    generation
            );

            /*
             * An asynchronous controller without its queue barrier is unsafe:
             * later actions could advance and overwrite movement/target state.
             *
             * Cancel immediately rather than allowing an orphaned approach.
             */
            DAI_ApproachController.stop();

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            return;
        }

        DAI_ActionStatus.set(
                DAI_ApproachController.isActive()
                        ? DAI_ActionResult.RUNNING
                        : DAI_ApproachController.resultForGeneration(
                        generation
                )
        );

        DAI_Core.debug(
                "<DAI>: Block approach generation={} started with immediate queue barrier ownership.",
                generation
        );
    }

    /**
     * Hard queue barrier for block approach.
     *
     * The approach controller owns timeout/lifecycle.
     * This wait owns queue sequencing only.
     */
    public static void waitForApproach(
            DAI_ActionDefinition action
    ) {

        if (action == null) {

            releaseApproachBarrierIfPresent();

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            return;
        }

        int expectedGeneration =
                action.slot();

        int currentGeneration =
                DAI_ApproachController.generation();

        /*
         * Defensive compatibility path.
         *
         * Normal current execution should already be generation-bound when
         * approachTargetBlock() promotes the wait into the barrier.
         */
        if (expectedGeneration <= 0) {

            if (!DAI_ApproachController.isActive()) {

                releaseApproachBarrierIfPresent();

                DAI_ActionStatus.set(
                        DAI_ActionResult.FAILURE
                );

                DAI_Core.debug(
                        "<DAI>: Discarded unbound wait_for_approach because no approach is active."
                );

                return;
            }

            expectedGeneration =
                    currentGeneration;

            action =
                    createWaitForApproachAction(
                            expectedGeneration
                    );

            DAI_Core.debug(
                    "<DAI>: Late-bound wait_for_approach to generation={}.",
                    expectedGeneration
            );
        }

        /*
         * Controller has advanced beyond this wait's generation.
         */
        if (
                currentGeneration
                        > expectedGeneration
        ) {

            DAI_ActionResult result =
                    DAI_ApproachController.resultForGeneration(
                            expectedGeneration
                    );

            releaseApproachBarrier(
                    expectedGeneration
            );

            DAI_ActionStatus.set(
                    result
            );

            DAI_Core.debug(
                    "<DAI>: Resolved historical wait_for_approach generation={} result={}.",
                    expectedGeneration,
                    result
            );

            return;
        }

        /*
         * A wait cannot belong to a future generation.
         */
        if (
                currentGeneration
                        < expectedGeneration
        ) {

            releaseApproachBarrier(
                    expectedGeneration
            );

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Rejected wait_for_approach for future generation={} currentGeneration={}.",
                    expectedGeneration,
                    currentGeneration
            );

            return;
        }

        /*
         * Exact generation remains active.
         *
         * Refresh the existing barrier with the next poll action. No normal
         * queue action can run while this remains installed.
         */
        if (DAI_ApproachController.isActive()) {

            DAI_ActionDefinition nextPoll =
                    createWaitForApproachAction(
                            expectedGeneration
                    );

            DAI_ActionQueue.holdBarrier(
                    nextPoll,
                    BARRIER_POLL_TICKS
            );

            DAI_ActionStatus.set(
                    DAI_ActionResult.RUNNING
            );

            return;
        }

        /*
         * Exact generation reached a terminal result.
         */
        DAI_ActionResult result =
                DAI_ApproachController.resultForGeneration(
                        expectedGeneration
                );

        releaseApproachBarrier(
                expectedGeneration
        );

        DAI_ActionStatus.set(
                result
        );

        DAI_Core.debug(
                "<DAI>: Block approach generation={} barrier completed with result={}.",
                expectedGeneration,
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

    public static void stopApproach() {

        if (DAI_ApproachController.isActive()) {

            int generation =
                    DAI_ApproachController.generation();

            DAI_ApproachController.stop();

            releaseApproachBarrier(
                    generation
            );

            DAI_Core.debug(
                    "<DAI>: Explicitly stopped block approach generation={}.",
                    generation
            );
        }

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );
    }

    /*
     * ------------------------------------------------------------
     * EXPLORATION
     * ------------------------------------------------------------
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

        boolean started =
                DAI_ExploreController.start(
                        action.action(),
                        searchRadius,
                        timeoutTicks
                );

        if (!started) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.debug(
                    "<DAI>: Exploration failed to start for '{}'.",
                    action.action()
            );

            return;
        }

        int generation =
                DAI_ExploreController.generation();

        /*
         * Same ownership rule as approach:
         *
         * exploration begins with its following wait already promoted into
         * the hard queue barrier.
         */
        boolean promoted =
                DAI_ActionQueue.promoteHeadToBarrier(
                        "wait_for_exploration",
                        generation,
                        BARRIER_POLL_TICKS
                );

        if (!promoted) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Started exploration generation={} but could not promote the following wait_for_exploration into a barrier.",
                    generation
            );

            DAI_ExploreController.stop();

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            return;
        }

        DAI_ActionStatus.set(
                DAI_ExploreController.isActive()
                        ? DAI_ActionResult.RUNNING
                        : DAI_ExploreController.resultForGeneration(
                        generation
                )
        );

        DAI_Core.debug(
                "<DAI>: Exploration generation={} started with immediate queue barrier ownership.",
                generation
        );
    }

    /**
     * Hard queue barrier for exploration.
     *
     * ExplorationController owns timeout/lifecycle.
     * This wait owns queue sequencing only.
     */
    public static void waitForExploration(
            DAI_ActionDefinition action
    ) {

        if (action == null) {

            releaseExplorationBarrierIfPresent();

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            return;
        }

        int expectedGeneration =
                action.slot();

        int currentGeneration =
                DAI_ExploreController.generation();

        if (expectedGeneration <= 0) {

            if (!DAI_ExploreController.isActive()) {

                releaseExplorationBarrierIfPresent();

                DAI_ActionStatus.set(
                        DAI_ActionResult.FAILURE
                );

                DAI_Core.debug(
                        "<DAI>: Discarded unbound wait_for_exploration because no exploration is active."
                );

                return;
            }

            expectedGeneration =
                    currentGeneration;

            action =
                    createWaitForExplorationAction(
                            expectedGeneration
                    );

            DAI_Core.debug(
                    "<DAI>: Late-bound wait_for_exploration to generation={}.",
                    expectedGeneration
            );
        }

        if (
                currentGeneration
                        > expectedGeneration
        ) {

            DAI_ActionResult result =
                    DAI_ExploreController.resultForGeneration(
                            expectedGeneration
                    );

            releaseExplorationBarrier(
                    expectedGeneration
            );

            DAI_ActionStatus.set(
                    result
            );

            DAI_Core.debug(
                    "<DAI>: Resolved historical wait_for_exploration generation={} result={}.",
                    expectedGeneration,
                    result
            );

            return;
        }

        if (
                currentGeneration
                        < expectedGeneration
        ) {

            releaseExplorationBarrier(
                    expectedGeneration
            );

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Rejected wait_for_exploration for future generation={} currentGeneration={}.",
                    expectedGeneration,
                    currentGeneration
            );

            return;
        }

        if (DAI_ExploreController.isActive()) {

            DAI_ActionDefinition nextPoll =
                    createWaitForExplorationAction(
                            expectedGeneration
                    );

            DAI_ActionQueue.holdBarrier(
                    nextPoll,
                    BARRIER_POLL_TICKS
            );

            DAI_ActionStatus.set(
                    DAI_ActionResult.RUNNING
            );

            return;
        }

        DAI_ActionResult result =
                DAI_ExploreController.resultForGeneration(
                        expectedGeneration
                );

        releaseExplorationBarrier(
                expectedGeneration
        );

        DAI_ActionStatus.set(
                result
        );

        DAI_Core.debug(
                "<DAI>: Exploration generation={} barrier completed with result={}.",
                expectedGeneration,
                result
        );
    }

    public static void stopExploration() {

        if (DAI_ExploreController.isActive()) {

            int generation =
                    DAI_ExploreController.generation();

            DAI_ExploreController.stop();

            releaseExplorationBarrier(
                    generation
            );

            DAI_Core.debug(
                    "<DAI>: Explicitly stopped exploration generation={}.",
                    generation
            );
        }

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );
    }

    /*
     * ------------------------------------------------------------
     * BARRIER HELPERS
     * ------------------------------------------------------------
     */

    private static void releaseApproachBarrier(
            int generation
    ) {

        if (
                DAI_ActionQueue.barrierIs(
                        "wait_for_approach",
                        generation
                )
        ) {

            DAI_ActionQueue.releaseBarrier();
        }
    }

    private static void releaseApproachBarrierIfPresent() {

        if (
                DAI_ActionQueue.barrierIs(
                        "wait_for_approach"
                )
        ) {

            DAI_ActionQueue.releaseBarrier();
        }
    }

    private static void releaseExplorationBarrier(
            int generation
    ) {

        if (
                DAI_ActionQueue.barrierIs(
                        "wait_for_exploration",
                        generation
                )
        ) {

            DAI_ActionQueue.releaseBarrier();
        }
    }

    private static void releaseExplorationBarrierIfPresent() {

        if (
                DAI_ActionQueue.barrierIs(
                        "wait_for_exploration"
                )
        ) {

            DAI_ActionQueue.releaseBarrier();
        }
    }

    /*
     * ------------------------------------------------------------
     * WAIT ACTION FACTORIES
     * ------------------------------------------------------------
     */

    private static DAI_ActionDefinition createWaitForApproachAction(
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
                0,
                Math.max(
                        0,
                        generation
                ),
                false,
                0.0D
        );
    }

    private static DAI_ActionDefinition createWaitForExplorationAction(
            int generation
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
                0,
                Math.max(
                        0,
                        generation
                ),
                false,
                0.0D
        );
    }
}