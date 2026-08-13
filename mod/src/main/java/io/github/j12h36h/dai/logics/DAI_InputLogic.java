package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.input.DAI_KeyInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;

public final class DAI_InputLogic {

    private DAI_InputLogic() {
        // Utility class.
    }

    public static void keyClick(DAI_ActionDefinition action) {
        if (!valid(action)) return;
        DAI_KeyInput.click(action.action());
        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
    }

    public static void keyPress(DAI_ActionDefinition action) {
        if (!valid(action)) return;
        DAI_KeyInput.press(action.action());
        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
    }

    public static void keyRelease(DAI_ActionDefinition action) {
        if (!valid(action)) return;
        DAI_KeyInput.release(action.action());
        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
    }

    public static void typeText(DAI_ActionDefinition action) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.gui.screen();

        if (screen == null || action == null || !action.hasAction()) {
            DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
            DAI_Core.LOGGER.warn("<DAI>: type_text requires an open screen and text in 'action'.");
            return;
        }

        boolean accepted = true;
        for (char character : action.action().toCharArray()) {
            accepted &= screen.charTyped(
                    new CharacterEvent(
                            character
                    )
            );
        }

        DAI_ActionStatus.set(
                accepted
                        ? DAI_ActionResult.SUCCESS
                        : DAI_ActionResult.FAILURE
        );
    }

    private static boolean valid(DAI_ActionDefinition action) {
        if (action != null && action.hasAction()) return true;
        DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
        DAI_Core.LOGGER.warn("<DAI>: Key action requires a key mapping id in 'action'.");
        return false;
    }
}
