package io.github.j12h36h.dai.logic;

import io.github.j12h36h.dai.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.ui.DAI_MenuCore;
import io.github.j12h36h.dai.ui.DAI_ScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;

public final class DAI_MenuLogic {

    private DAI_MenuLogic() {
        // Utility class.
    }

    public static void openPauseMenu(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        DAI_Core.LOGGER.debug(
                "<DAI>: Opening pause screen."
        );

        DAI_ScreenManager.openTemporary(
                minecraft.gui.screen(),
                new PauseScreen(true)
        );
    }

    public static void openChat(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot open chat because the player is null."
            );

            return;
        }

        Screen currentScreen =
                minecraft.gui.screen();

        if (currentScreen instanceof DAI_MenuCore) {

            DAI_ScreenManager.push(
                    currentScreen
            );
        }

        DAI_ScreenManager.open(
                new ChatScreen(
                        "",
                        false,
                        true
                )
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Opened chat screen."
        );
    }

    public static void updateMenu(
            DAI_ActionDefinition action
    ) {

        updateMenu(
                action.menu(),
                action.open()
        );
    }

    private static void updateMenu(
            String menu,
            String open
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        DAI_MenuCore daiMenu =
                findActiveMenu(
                        minecraft
                );

        if (daiMenu == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: No active DAI menu was found to update."
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Updating menu='{}', open='{}'.",
                menu,
                open
        );

        daiMenu.updateMenu(
                menu,
                open
        );
    }

    private static DAI_MenuCore findActiveMenu(
            Minecraft minecraft
    ) {

        if (
                minecraft.gui.screen()
                        instanceof DAI_MenuCore currentMenu
        ) {
            return currentMenu;
        }

        if (
                DAI_ScreenManager.peek()
                        instanceof DAI_MenuCore stackedMenu
        ) {
            return stackedMenu;
        }

        return null;
    }
}
