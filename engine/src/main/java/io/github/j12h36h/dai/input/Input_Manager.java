package io.github.j12h36h.dai.input;

import io.github.j12h36h.dai.core.Config;
import io.github.j12h36h.dai.ui.DAI_Menu;
import net.minecraft.client.Minecraft;

import static io.github.j12h36h.dai.core.DAI.LOGGER;

public final class Input_Manager {

    private static final Input_Movement MOVEMENT = new Input_Movement();
    private static final Input_Action ACTION = new Input_Action();
    private static final Input_Look LOOK = new Input_Look();

    private Input_Manager() {
        // Utility class.
    }

    /**
     * Returns the current movement state.
     */
    public static Input_Movement movement() {
        return MOVEMENT;
    }

    /**
     * Returns the current action state.
     */
    public static Input_Action action() {
        return ACTION;
    }

    /**
     * Returns the current look state.
     */
    public static Input_Look look() {
        return LOOK;
    }

    /**
     * Clears all active movement.
     */
    public static void clearMovement() {
        MOVEMENT.clear();
    }

    /**
     * Clears all active actions.
     */
    public static void clearActions() {
        ACTION.clear();
    }

    /**
     * Resets the current look direction.
     */
    public static void clearLook() {
        LOOK.clear();
    }

    /**
     * Clears all DAI input.
     */
    public static void clear() {
        clearMovement();
        clearActions();
        clearLook();
    }


}