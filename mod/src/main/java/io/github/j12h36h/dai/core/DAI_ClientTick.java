package io.github.j12h36h.dai.core;

import io.github.j12h36h.dai.action.DAI_ActionController;
import io.github.j12h36h.dai.action.DAI_ActionQueue;
import io.github.j12h36h.dai.input.DAI_LookController;
import io.github.j12h36h.dai.input.DAI_MoveController;
import io.github.j12h36h.dai.system.DAI_ClientRuntime;
import io.github.j12h36h.dai.ui.DAI_ScreenManager;

public final class DAI_ClientTick {

    private DAI_ClientTick() {
        // Utility class.
    }

    public static void tick() {
        DAI_ClientRuntime.tick();
        DAI_ScreenManager.tick();
        DAI_MoveController.tick();
        DAI_ActionController.tick();
        DAI_ActionQueue.tick();
        DAI_LookController.tick();
    }
}
