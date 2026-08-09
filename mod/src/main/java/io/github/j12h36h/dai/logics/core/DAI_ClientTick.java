package io.github.j12h36h.dai.logics.core;

import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.condition.DAI_ConditionMemory;
import io.github.j12h36h.dai.logics.controller.DAI_ApproachController;
import io.github.j12h36h.dai.logics.controller.DAI_BreakController;
import io.github.j12h36h.dai.logics.controller.DAI_BuildController;
import io.github.j12h36h.dai.logics.controller.DAI_CombatController;
import io.github.j12h36h.dai.logics.controller.DAI_ExploreController;
import io.github.j12h36h.dai.logics.controller.DAI_InteractionController;
import io.github.j12h36h.dai.logics.controller.DAI_ItemController;
import io.github.j12h36h.dai.logics.controller.DAI_LookController;
import io.github.j12h36h.dai.logics.controller.DAI_MoveController;
import io.github.j12h36h.dai.logics.controller.DAI_PathController;
import io.github.j12h36h.dai.logics.controller.DAI_UseController;
import io.github.j12h36h.dai.menus.system.DAI_ClientRuntime;
import io.github.j12h36h.dai.menus.DAI_ScreenManager;
import net.minecraft.client.Minecraft;

public final class DAI_ClientTick {

    private static boolean sessionActive;

    private DAI_ClientTick() {
        // Utility class.
    }

    public static void tick() {

        Minecraft minecraft =
                Minecraft.getInstance();

        boolean playerAvailable =
                minecraft.player != null
                        && minecraft.level != null;

        /*
         * Detect the end of a gameplay session.
         *
         * This covers disconnecting, returning to the title screen,
         * closing a world, and the temporary player removal that can
         * occur during session transitions.
         */
        if (
                sessionActive
                        && !playerAvailable
        ) {

            DAI_ClientRuntime.resetSession();

            sessionActive =
                    false;

            DAI_Core.LOGGER.debug(
                    "<DAI>: Client gameplay session ended."
            );

            return;
        }

        if (!playerAvailable) {

            /*
             * Runtime initialization may still be pending while the
             * client waits for its player and level to become ready.
             */
            DAI_ClientRuntime.tick();

            return;
        }

        sessionActive =
                true;

        DAI_ClientRuntime.tick();
        DAI_ScreenManager.tick();

        /*
         * Persistent navigation advances before queue dispatch so controller
         * completion/failure results are available to wait actions during
         * this same tick.
         *
         * Path runs before exploration so an exploration-owned path can
         * complete or fail first and exploration can react immediately.
         */
        DAI_PathController.tick();
        DAI_ExploreController.tick();

        DAI_ConditionMemory.tick();

        /*
         * Active block breaking temporarily owns action execution.
         *
         * Otherwise dispatch one queued action before ticking the transient
         * request-style controllers below. This lets a request created by the
         * queue be consumed during this same client tick instead of lingering
         * pending until the next tick while control flow has already moved on.
         */
        if (!DAI_BreakController.isActive()) {
            DAI_ActionQueue.tick();
        }

        DAI_MoveController.tick();
        DAI_CombatController.tick();
        DAI_UseController.tick();
        DAI_ItemController.tick();
        DAI_InteractionController.tick();
        DAI_BreakController.tick();
        DAI_BuildController.tick();

        /*
         * Approach may request a new camera orientation while it advances.
         * Tick it before DAI_LookController so that request is applied during
         * this same client tick.
         */
        DAI_ApproachController.tick();

        if (!DAI_BreakController.isActive()) {
            DAI_LookController.tick();
        }

        DAI_Debug.tick();
    }

    public static void reset() {

        sessionActive =
                false;
    }
}