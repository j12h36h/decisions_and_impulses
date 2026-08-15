package io.github.j12h36h.dai.client.logics.input;

import io.github.j12h36h.dai.logics.core.DAI_Config;

public final class DAI_InputState {

    private static final DAI_MovementState MOVEMENT =
            new DAI_MovementState();

    private static final DAI_InputLook LOOK =
            new DAI_InputLook();

    private static boolean managedOverride;

    /**
     * True while the mouse cursor is intentionally released for
     * interacting with the DAI overlay when vanilla keybinds are
     * enabled.
     */
    private static boolean cursorReleased;

    private DAI_InputState() {
        // Utility class.
    }

    public static DAI_MovementState movement() {
        return MOVEMENT;
    }

    public static DAI_InputLook look() {
        return LOOK;
    }

    /**
     * Returns whether DAI should override vanilla movement and look
     * input.
     */
    public static boolean isOverrideEnabled() {

        if (managedOverride) {
            return true;
        }

        try {

            return DAI_Config.TOGGLE_KEYBINDS.get();

        } catch (IllegalStateException exception) {

            // Configuration has not finished loading yet.
            return false;
        }
    }

    /**
     * Returns whether the DAI overlay currently wants the mouse cursor
     * released.
     */
    public static boolean isCursorReleased() {
        return cursorReleased;
    }

    /**
     * Sets whether the DAI overlay wants the mouse cursor released.
     */
    public static void setCursorReleased(
            boolean released
    ) {

        cursorReleased = released;
    }

    public static void setManagedOverride(
            boolean enabled
    ) {

        managedOverride =
                enabled;
    }
}