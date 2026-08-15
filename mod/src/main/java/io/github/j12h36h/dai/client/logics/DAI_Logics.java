package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;

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
