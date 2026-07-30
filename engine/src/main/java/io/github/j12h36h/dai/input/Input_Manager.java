package io.github.j12h36h.dai.input;

import io.github.j12h36h.dai.core.Config;
import io.github.j12h36h.dai.ui.DAI_Menu;
import net.minecraft.client.Minecraft;

import static io.github.j12h36h.dai.core.DAI.LOGGER;

public final class Input_Manager {

    private static final Input_Movement MOVEMENT = new Input_Movement();

    private Input_Manager() {
    }

    /**
     * Installs the DAI keyboard if it is not already active.
     */
    public static void install() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            return;
        }

        Minecraft.getInstance().gui.setScreen(new DAI_Menu());

        if (!(mc.player.input instanceof DAI_Keyboard)) {
            mc.player.input = new DAI_Keyboard(mc.options);
            LOGGER.info("<DAI>: Keyboard = Active");
        }

        if (Config.TOGGLE_KEYBINDS.getAsBoolean()) {
            if (mc.mouseHandler.isMouseGrabbed()) {
                mc.mouseHandler.releaseMouse();
            }
        } else {
            if (!mc.mouseHandler.isMouseGrabbed()) {
                mc.mouseHandler.grabMouse();
            }
        }
    }

    /**
     * Returns the current movement state.
     */
    public static Input_Movement movement() {
        return MOVEMENT;
    }

    /**
     * Clears all active movement.
     */
    public static void clearMovement() {
        MOVEMENT.clear();
    }
}
