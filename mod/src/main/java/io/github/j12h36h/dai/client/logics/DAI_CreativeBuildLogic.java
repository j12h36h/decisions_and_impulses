package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.client.logics.controller.DAI_CreativeBuildController;
import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.util.List;

public final class DAI_CreativeBuildLogic {

    private static final int BARRIER_POLL_TICKS = 1;

    private DAI_CreativeBuildLogic() {
        // Utility class.
    }

    public static void startBlueprint(DAI_ActionDefinition action) {
        int generation = DAI_CreativeBuildController.start(action);
        if (!DAI_CreativeBuildController.isActive()) return;

        boolean promoted = DAI_ActionQueue.promoteHeadToBarrier(
                "wait_for_creative_build",
                generation,
                BARRIER_POLL_TICKS
        );

        if (!promoted) {
            DAI_CreativeBuildController.reset();
            DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
            DAI_Core.LOGGER.warn("<DAI>: Creative blueprint started without its required wait barrier; cancelled.");
        }
    }

    public static void waitForBlueprint(DAI_ActionDefinition action) {
        int expected = action == null ? 0 : action.slot();
        if (expected <= 0) expected = DAI_CreativeBuildController.generation();

        if (DAI_CreativeBuildController.isActive()
                && expected == DAI_CreativeBuildController.generation()) {
            DAI_ActionQueue.holdBarrier(createWait(expected), BARRIER_POLL_TICKS);
            DAI_ActionStatus.set(DAI_ActionResult.RUNNING);
            return;
        }

        DAI_ActionQueue.releaseBarrier();
        DAI_ActionStatus.set(DAI_CreativeBuildController.resultForGeneration(expected));
    }

    private static DAI_ActionDefinition createWait(int generation) {
        return new DAI_ActionDefinition(
                "wait_for_creative_build", "", List.of(), List.of(), "", "",
                0.0F, 0.0F, "", 0, generation, false, 0.0D
        );
    }
}
