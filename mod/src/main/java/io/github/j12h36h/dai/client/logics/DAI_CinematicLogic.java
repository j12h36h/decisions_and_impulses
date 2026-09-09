package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.client.animations.eras.DAI_ErasCinematicRuntime;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;

/** Action bridge for ERAS / S.A.D. cinematic projects. */
public final class DAI_CinematicLogic {
    private DAI_CinematicLogic() {}

    public static void play(DAI_ActionDefinition action) {
        DAI_ActionStatus.set(
                DAI_ErasCinematicRuntime.play(action.action(), action.arguments())
                        ? DAI_ActionResult.SUCCESS
                        : DAI_ActionResult.FAILURE
        );
    }

    public static void stop(DAI_ActionDefinition action) {
        DAI_ActionStatus.set(
                DAI_ErasCinematicRuntime.stop(action.action())
                        ? DAI_ActionResult.SUCCESS
                        : DAI_ActionResult.FAILURE
        );
    }

    public static void pause(DAI_ActionDefinition action) {
        DAI_ActionStatus.set(
                DAI_ErasCinematicRuntime.pause(action.action())
                        ? DAI_ActionResult.SUCCESS
                        : DAI_ActionResult.FAILURE
        );
    }

    public static void resume(DAI_ActionDefinition action) {
        DAI_ActionStatus.set(
                DAI_ErasCinematicRuntime.resume(action.action())
                        ? DAI_ActionResult.SUCCESS
                        : DAI_ActionResult.FAILURE
        );
    }

    public static void waitFor(DAI_ActionDefinition action) {
        if (DAI_ErasCinematicRuntime.isPlaying(action.action())) {
            DAI_ActionStatus.set(DAI_ActionResult.RUNNING);
            DAI_ActionQueue.holdBarrier(action, 1);
            return;
        }
        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
        if (DAI_ActionQueue.barrierIs("wait_for_cinematic")) {
            DAI_ActionQueue.releaseBarrier();
        }
    }
}
