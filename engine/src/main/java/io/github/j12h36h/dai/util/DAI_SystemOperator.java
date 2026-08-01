package io.github.j12h36h.dai.util;

import io.github.j12h36h.dai.core.Config;
import io.github.j12h36h.dai.core.DAI;
import io.github.j12h36h.dai.input.DAI_Keyboard;
import io.github.j12h36h.dai.ui.DAI_Menu;
import net.minecraft.client.Minecraft;

public final class DAI_SystemOperator {

    private DAI_SystemOperator() {
        // Utility class.
    }

    /**
     * Initializes the DAI client runtime.
     *
     * Called after login and respawn.
     */
    public static void initialize() {

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }

        // Open the DAI menu.
        minecraft.gui.setScreen(new DAI_Menu());

        // Install the custom keyboard.
        if (!(minecraft.player.input instanceof DAI_Keyboard)) {
            minecraft.player.input = new DAI_Keyboard(minecraft.options);

            DAI.LOGGER.info("<DAI>: Keyboard = Active");
        }

        // Update mouse capture.
        if (Config.TOGGLE_KEYBINDS.getAsBoolean()) {

            if (minecraft.mouseHandler.isMouseGrabbed()) {
                minecraft.mouseHandler.releaseMouse();
            }

        } else {

            if (!minecraft.mouseHandler.isMouseGrabbed()) {
                minecraft.mouseHandler.grabMouse();
            }
        }
    }
}