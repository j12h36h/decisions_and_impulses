package io.github.j12h36h.dai.input;

import io.github.j12h36h.dai.core.DAI_Core;

public final class DAI_MoveController {

    private static boolean active;
    private static int remainingTicks;

    private DAI_MoveController() {
        // Utility class.
    }

    public static void start(float forward, float sideways, int ticks) {
        if (ticks <= 0) {
            DAI_Core.LOGGER.warn("<DAI>: Ignoring movement with non-positive duration {}.", ticks);
            stop();
            return;
        }

        DAI_InputController.movement().setMovement(forward, sideways);
        remainingTicks = ticks;
        active = true;

        DAI_Core.LOGGER.debug(
                "<DAI>: Starting movement (forward={}, sideways={}, ticks={}).",
                forward,
                sideways,
                ticks
        );
    }

    public static void tick() {
        if (!active) {
            return;
        }
        remainingTicks--;
        if (remainingTicks <= 0) {
            stop();
        }
    }

    public static void stop() {
        DAI_InputController.movement().clearMovement();
        remainingTicks = 0;
        if (active) {
            DAI_Core.LOGGER.debug("<DAI>: Stopping movement.");
        }
        active = false;
    }
}
