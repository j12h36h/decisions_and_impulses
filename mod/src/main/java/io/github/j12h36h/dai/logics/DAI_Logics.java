package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;

/** Public facade for reusable execution logic. */
public final class DAI_Logics {

    private DAI_Logics() {
        // Utility class.
    }

    public static void tick() {
        DAI_ActionQueue.tick();
    }

    public static void clearQueue() {
        DAI_ActionQueue.clear();
    }
}
