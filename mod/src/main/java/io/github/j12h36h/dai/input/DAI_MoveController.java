package io.github.j12h36h.dai.input;

import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.client.Minecraft;

public final class DAI_MoveController {

    private static boolean active;
    private static int remainingTicks;

    private static boolean swimToggle;

    private DAI_MoveController() {
        // Utility class.
    }

    public static void start(
            float forward,
            float sideways,
            int ticks
    ) {

        if (ticks <= 0) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Ignoring movement with non-positive duration {}.",
                    ticks
            );

            stop();
            return;
        }

        DAI_InputController.movement()
                .setMovement(forward, sideways);

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

        tickMovement();
        tickSwim();
    }

    private static void tickMovement() {

        if (!active) {
            return;
        }

        remainingTicks--;

        if (remainingTicks <= 0) {
            stop();
        }
    }

    private static void tickSwim() {

        Minecraft minecraft = Minecraft.getInstance();

        boolean swimJump =
                swimToggle
                        && minecraft.player != null
                        && minecraft.player.isInWater();

        DAI_InputController.movement()
                .setJump(swimJump);
    }

    public static void toggleSwim() {

        setSwim(!swimToggle);
    }

    public static void setSwim(
            boolean enabled
    ) {

        swimToggle = enabled;

        if (!swimToggle) {
            DAI_InputController.movement()
                    .setJump(false);
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Swim assist {}.",
                swimToggle ? "enabled" : "disabled"
        );
    }

    public static boolean isSwimEnabled() {
        return swimToggle;
    }

    public static void stop() {

        DAI_InputController.movement()
                .clearMovement();

        remainingTicks = 0;

        if (active) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Stopping movement."
            );
        }

        active = false;

        // Restore swim jump immediately if timed movement ended in water.
        tickSwim();
    }

    public static void reset() {

        active = false;
        remainingTicks = 0;
        swimToggle = false;
        DAI_ApproachController.reset();

        DAI_InputController.movement()
                .clearMovement();

        DAI_InputController.movement()
                .setJump(false);

        DAI_InputController.movement()
                .setSneak(false);

        DAI_InputController.movement()
                .setSprint(false);

        DAI_Core.LOGGER.debug(
                "<DAI>: Reset all movement input."
        );
    }
}