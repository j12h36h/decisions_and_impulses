package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.client.api.DAI_EntityTargetResolver;
import io.github.j12h36h.dai.client.animations.DAI_AnimationRuntime;
import io.github.j12h36h.dai.animations.eras.DAI_ErasCinematicRegistry;
import io.github.j12h36h.dai.client.animations.eras.DAI_ErasCinematicRuntime;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import net.minecraft.world.entity.Entity;

public final class DAI_AnimationLogic {
    private DAI_AnimationLogic() {}

    public static void play(DAI_ActionDefinition action) {
        if (DAI_ErasCinematicRegistry.contains(action.action())) {
            DAI_ActionStatus.set(
                    DAI_ErasCinematicRuntime.play(action.action(), action.arguments())
                            ? DAI_ActionResult.SUCCESS
                            : DAI_ActionResult.FAILURE
            );
            return;
        }
        Entity target = DAI_EntityTargetResolver.resolve(action.target());
        DAI_ActionStatus.set(
                DAI_AnimationRuntime.play(target, action.action())
                        ? DAI_ActionResult.SUCCESS
                        : DAI_ActionResult.FAILURE
        );
    }

    public static void stop(DAI_ActionDefinition action) {
        if (DAI_ErasCinematicRegistry.contains(action.action())) {
            DAI_ActionStatus.set(
                    DAI_ErasCinematicRuntime.stop(action.action())
                            ? DAI_ActionResult.SUCCESS
                            : DAI_ActionResult.FAILURE
            );
            return;
        }
        Entity target = DAI_EntityTargetResolver.resolve(action.target());
        DAI_ActionStatus.set(
                DAI_AnimationRuntime.stop(target, action.action())
                        ? DAI_ActionResult.SUCCESS
                        : DAI_ActionResult.FAILURE
        );
    }

    public static void pause(DAI_ActionDefinition action) {
        if (DAI_ErasCinematicRegistry.contains(action.action())) {
            DAI_ActionStatus.set(
                    DAI_ErasCinematicRuntime.pause(action.action())
                            ? DAI_ActionResult.SUCCESS
                            : DAI_ActionResult.FAILURE
            );
            return;
        }
        Entity target = DAI_EntityTargetResolver.resolve(action.target());
        DAI_ActionStatus.set(
                DAI_AnimationRuntime.pause(target, action.action())
                        ? DAI_ActionResult.SUCCESS
                        : DAI_ActionResult.FAILURE
        );
    }

    public static void resume(DAI_ActionDefinition action) {
        if (DAI_ErasCinematicRegistry.contains(action.action())) {
            DAI_ActionStatus.set(
                    DAI_ErasCinematicRuntime.resume(action.action())
                            ? DAI_ActionResult.SUCCESS
                            : DAI_ActionResult.FAILURE
            );
            return;
        }
        Entity target = DAI_EntityTargetResolver.resolve(action.target());
        DAI_ActionStatus.set(
                DAI_AnimationRuntime.resume(target, action.action())
                        ? DAI_ActionResult.SUCCESS
                        : DAI_ActionResult.FAILURE
        );
    }

    public static void waitFor(DAI_ActionDefinition action) {
        if (DAI_ErasCinematicRegistry.contains(action.action())) {
            if (DAI_ErasCinematicRuntime.isPlaying(action.action())) {
                DAI_ActionStatus.set(DAI_ActionResult.RUNNING);
                DAI_ActionQueue.holdBarrier(action, 1);
                return;
            }
            DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
            if (DAI_ActionQueue.barrierIs("wait_for_animation")) {
                DAI_ActionQueue.releaseBarrier();
            }
            return;
        }

        Entity target = DAI_EntityTargetResolver.resolve(action.target());
        if (target == null) {
            DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
            if (DAI_ActionQueue.barrierIs("wait_for_animation")) {
                DAI_ActionQueue.releaseBarrier();
            }
            return;
        }
        if (DAI_AnimationRuntime.isPlaying(target, action.action())) {
            DAI_ActionStatus.set(DAI_ActionResult.RUNNING);
            DAI_ActionQueue.holdBarrier(action, 1);
            return;
        }
        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
        if (DAI_ActionQueue.barrierIs("wait_for_animation")) {
            DAI_ActionQueue.releaseBarrier();
        }
    }
}
