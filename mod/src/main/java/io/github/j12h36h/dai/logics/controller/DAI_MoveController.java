package io.github.j12h36h.dai.logics.controller;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.input.DAI_InputState;
import net.minecraft.client.Minecraft;

public final class DAI_MoveController {

    private static boolean active;
    private static int remainingTicks;

    /*
     * Persistent directional movement ownership.
     */
    private static boolean held;

    private static float heldForward;
    private static float heldSideways;

    /*
     * Swim assist is now treated as emergency/surface assistance rather
     * than a permanent "jump whenever touching water" command.
     *
     * Navigation/path following may independently control jump/sneak while
     * swimming through planned water nodes.
     */
    private static boolean swimToggle;

    private DAI_MoveController() {
        // Utility class.
    }

    /*
     * ------------------------------------------------------------
     * TIMED MOVEMENT
     * ------------------------------------------------------------
     */

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

        clearHeldState();

        DAI_InputState
                .movement()
                .setMovement(
                        forward,
                        sideways
                );

        remainingTicks =
                ticks;

        active =
                true;

        DAI_Core.debug(
                "<DAI>: Starting timed movement (forward={}, sideways={}, ticks={}).",
                forward,
                sideways,
                ticks
        );
    }

    public static void tick() {

        tickMovement();

        tickSwimAssist();
    }

    private static void tickMovement() {

        /*
         * Persistent movement owned by this controller.
         */
        if (held) {

            DAI_InputState
                    .movement()
                    .setMovement(
                            heldForward,
                            heldSideways
                    );

            return;
        }

        /*
         * CRITICAL:
         *
         * If this controller does not own directional movement, leave the
         * shared movement state alone.
         *
         * Approach/pathing/item collection may currently own it.
         */
        if (!active) {
            return;
        }

        remainingTicks--;

        if (remainingTicks <= 0) {

            stopTimedMovement();
        }
    }

    /*
     * ------------------------------------------------------------
     * SWIM ASSIST
     * ------------------------------------------------------------
     */

    private static void tickSwimAssist() {

        if (!swimToggle) {
            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }

        /*
         * Swim assist is intended to help escape/surface.
         *
         * Do not constantly write jump when the player is merely touching
         * water. That would fight planned vertical swim pathing.
         */
        if (!minecraft.player.isUnderWater()) {

            /*
             * Once breathing space has been reached, the assist should no
             * longer force vertical input.
             */
            return;
        }

        /*
         * Emergency swim assist means upward movement.
         *
         * Explicit path navigation can still overwrite this later in the
         * same tick when it owns a deliberate descent.
         */
        DAI_InputState
                .movement()
                .setJump(
                        true
                );

        DAI_InputState
                .movement()
                .setSneak(
                        false
                );
    }

    public static void toggleSwim() {

        setSwim(
                !swimToggle
        );
    }

    public static void setSwim(
            boolean enabled
    ) {

        swimToggle =
                enabled;

        /*
         * Disabling swim assist only clears the vertical input if this
         * controller had actually been using it.
         *
         * It must not clear horizontal path movement.
         */
        if (!swimToggle) {

            DAI_InputState
                    .movement()
                    .setJump(
                            false
                    );

            DAI_InputState
                    .movement()
                    .setSneak(
                            false
                    );
        }

        DAI_Core.debug(
                "<DAI>: Swim assist {}.",
                swimToggle
                        ? "enabled"
                        : "disabled"
        );
    }

    public static boolean isSwimEnabled() {

        return swimToggle;
    }

    /*
     * ------------------------------------------------------------
     * STOP / RESET
     * ------------------------------------------------------------
     */

    /**
     * Stops directional movement owned by this controller.
     */
    public static void stop() {

        boolean hadMovement =
                active
                        || held;

        active =
                false;

        remainingTicks =
                0;

        clearHeldState();

        /*
         * This method is explicitly relinquishing movement that THIS
         * controller owns, so clearing directional movement is correct here.
         */
        DAI_InputState
                .movement()
                .clearMovement();

        if (hadMovement) {

            DAI_Core.debug(
                    "<DAI>: Stopped managed directional movement."
            );
        }

        /*
         * Preserve active swim assist semantics after stopping only the
         * directional movement owner.
         */
        tickSwimAssist();
    }

    /**
     * Stops only finite directional movement.
     */
    public static void stopTimedMovement() {

        if (!active) {
            return;
        }

        active =
                false;

        remainingTicks =
                0;

        if (!held) {

            DAI_InputState
                    .movement()
                    .clearMovement();
        }

        DAI_Core.debug(
                "<DAI>: Timed movement completed."
        );

        tickSwimAssist();
    }

    /**
     * Full movement-controller reset.
     */
    public static void reset() {

        active =
                false;

        remainingTicks =
                0;

        clearHeldState();

        swimToggle =
                false;

        DAI_InputState
                .movement()
                .clearMovement();

        DAI_InputState
                .movement()
                .setJump(
                        false
                );

        DAI_InputState
                .movement()
                .setSneak(
                        false
                );

        DAI_InputState
                .movement()
                .setSprint(
                        false
                );

        DAI_Core.debug(
                "<DAI>: Reset all movement input."
        );
    }

    /*
     * ------------------------------------------------------------
     * PERSISTENT MOVEMENT
     * ------------------------------------------------------------
     */

    public static void hold(
            float forward,
            float sideways
    ) {

        active =
                false;

        remainingTicks =
                0;

        held =
                true;

        heldForward =
                forward;

        heldSideways =
                sideways;

        DAI_InputState
                .movement()
                .setMovement(
                        heldForward,
                        heldSideways
                );

        DAI_Core.debug(
                "<DAI>: Holding managed movement (forward={}, sideways={}).",
                heldForward,
                heldSideways
        );
    }

    public static void updateHold(
            float forward,
            float sideways
    ) {

        if (!held) {

            hold(
                    forward,
                    sideways
            );

            return;
        }

        heldForward =
                forward;

        heldSideways =
                sideways;

        DAI_InputState
                .movement()
                .setMovement(
                        heldForward,
                        heldSideways
                );
    }

    public static void releaseHold() {

        if (!held) {
            return;
        }

        held =
                false;

        heldForward =
                0.0F;

        heldSideways =
                0.0F;

        if (!active) {

            DAI_InputState
                    .movement()
                    .clearMovement();
        }

        DAI_Core.debug(
                "<DAI>: Released persistent movement hold."
        );
    }

    /*
     * ------------------------------------------------------------
     * STATE
     * ------------------------------------------------------------
     */

    public static boolean isActive() {

        return active;
    }

    public static boolean isHeld() {

        return held;
    }

    public static boolean hasMovementOwner() {

        return active
                || held;
    }

    public static int remainingTicks() {

        return remainingTicks;
    }

    private static void clearHeldState() {

        held =
                false;

        heldForward =
                0.0F;

        heldSideways =
                0.0F;
    }
}