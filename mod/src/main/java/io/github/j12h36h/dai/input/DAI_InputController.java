package io.github.j12h36h.dai.input;

import io.github.j12h36h.dai.core.DAI_Config;

public final class DAI_InputController {

    private static final DAI_InputMovement MOVEMENT =
            new DAI_InputMovement();

    private static final DAI_InputLook LOOK =
            new DAI_InputLook();

    private DAI_InputController() {
        // Utility class.
    }

    public static DAI_InputMovement movement() {
        return MOVEMENT;
    }

    public static DAI_InputLook look() {
        return LOOK;
    }

    public static boolean isOverrideEnabled() {

        try {

            return DAI_Config.TOGGLE_KEYBINDS.get();

        } catch (IllegalStateException exception) {

            // Configuration has not finished loading yet.
            return false;
        }
    }
}
