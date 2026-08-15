package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.client.logics.controller.DAI_ScaffoldController;
import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.util.List;

/** Queue/barrier facade for bounded scaffold recovery. */
public final class DAI_ScaffoldLogic {

    private static final int DEFAULT_TIMEOUT_TICKS = 360;
    private static final int BARRIER_POLL_TICKS = 1;

    private DAI_ScaffoldLogic() {
        // Utility class.
    }

    public static void ascendToTarget(
            DAI_ActionDefinition action
    ) {

        int timeout = action != null && action.ticks() > 0
                ? action.ticks()
                : DEFAULT_TIMEOUT_TICKS;

        int maxBlocks = action != null && action.value() > 0.0D
                ? (int) Math.round(action.value())
                : 0;

        boolean started =
                DAI_ScaffoldController.startAscent(
                        timeout,
                        maxBlocks
                );

        if (!started) {
            return;
        }

        bindFollowingWait(
                "wait_for_vertical_scaffold",
                DAI_ScaffoldController.generation()
        );
    }

    public static void descend(
            DAI_ActionDefinition action
    ) {

        int timeout = action != null && action.ticks() > 0
                ? action.ticks()
                : DEFAULT_TIMEOUT_TICKS;

        boolean started =
                DAI_ScaffoldController.startDescent(
                        timeout
                );

        if (!started) {
            return;
        }

        bindFollowingWait(
                "wait_for_scaffold_descent",
                DAI_ScaffoldController.generation()
        );
    }

    public static void waitForAscent(
            DAI_ActionDefinition action
    ) {
        waitForGeneration(
                action,
                "wait_for_vertical_scaffold"
        );
    }

    public static void waitForDescent(
            DAI_ActionDefinition action
    ) {
        waitForGeneration(
                action,
                "wait_for_scaffold_descent"
        );
    }

    private static void bindFollowingWait(
            String waitType,
            int generation
    ) {

        boolean promoted =
                DAI_ActionQueue.promoteHeadToBarrier(
                        waitType,
                        generation,
                        BARRIER_POLL_TICKS
                );

        if (!promoted) {

            DAI_ScaffoldController.reset();

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Started scaffold generation={} but could not promote following '{}' into a queue barrier.",
                    generation,
                    waitType
            );

            return;
        }

        DAI_ActionStatus.set(
                DAI_ScaffoldController.isActive()
                        ? DAI_ActionResult.RUNNING
                        : DAI_ScaffoldController.resultForGeneration(
                        generation
                )
        );
    }

    private static void waitForGeneration(
            DAI_ActionDefinition action,
            String waitType
    ) {

        if (action == null) {
            releaseBarrierIf(waitType);
            DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
            return;
        }

        int expectedGeneration = action.slot();
        int currentGeneration = DAI_ScaffoldController.generation();

        if (expectedGeneration <= 0) {

            if (!DAI_ScaffoldController.isActive()) {
                releaseBarrierIf(waitType);
                DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
                return;
            }

            expectedGeneration = currentGeneration;
        }

        if (currentGeneration > expectedGeneration) {

            DAI_ActionResult result =
                    DAI_ScaffoldController.resultForGeneration(
                            expectedGeneration
                    );

            releaseBarrier(waitType, expectedGeneration);
            DAI_ActionStatus.set(result);
            return;
        }

        if (currentGeneration < expectedGeneration) {
            releaseBarrier(waitType, expectedGeneration);
            DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
            return;
        }

        if (DAI_ScaffoldController.isActive()) {

            DAI_ActionQueue.holdBarrier(
                    createWaitAction(
                            waitType,
                            expectedGeneration
                    ),
                    BARRIER_POLL_TICKS
            );

            DAI_ActionStatus.set(
                    DAI_ActionResult.RUNNING
            );

            return;
        }

        DAI_ActionResult result =
                DAI_ScaffoldController.resultForGeneration(
                        expectedGeneration
                );

        releaseBarrier(waitType, expectedGeneration);
        DAI_ActionStatus.set(result);
    }

    private static void releaseBarrier(
            String waitType,
            int generation
    ) {

        if (DAI_ActionQueue.barrierIs(waitType, generation)) {
            DAI_ActionQueue.releaseBarrier();
        }
    }

    private static void releaseBarrierIf(
            String waitType
    ) {

        if (DAI_ActionQueue.barrierIs(waitType)) {
            DAI_ActionQueue.releaseBarrier();
        }
    }

    private static DAI_ActionDefinition createWaitAction(
            String type,
            int generation
    ) {

        return new DAI_ActionDefinition(
                type,
                "",
                List.of(),
                List.of(),
                "",
                "",
                0.0F,
                0.0F,
                "",
                0,
                Math.max(0, generation),
                false,
                0.0D
        );
    }
}
