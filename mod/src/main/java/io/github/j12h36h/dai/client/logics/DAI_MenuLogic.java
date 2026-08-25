package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.client.menus.system.DAI_ClientRuntime;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.menus.DAI_MenuCore;
import io.github.j12h36h.dai.client.creator.DAI_CreatorScreen;
import io.github.j12h36h.dai.client.creator.DAI_AutomationCreatorScreen;
import io.github.j12h36h.dai.client.config.DAI_ClientConfig;
import io.github.j12h36h.dai.client.logics.input.DAI_InputState;
import io.github.j12h36h.dai.client.menus.DAI_ScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DAI_MenuLogic {

    private DAI_MenuLogic() {
        // Utility class.
    }


    public static void openCreator(DAI_ActionDefinition action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!DAI_ClientConfig.creatorEnabled()) {
            if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("[DAI] Creator is disabled in decisions_and_impulses-client.toml."));
            return;
        }
        Screen current = minecraft.gui.screen();
        DAI_ScreenManager.openTemporary(current, new DAI_CreatorScreen());
    }

    public static void openAutomationCreator(DAI_ActionDefinition action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!DAI_ClientConfig.automationCreatorEnabled()) {
            if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("[DAI] Automation Creator is disabled in decisions_and_impulses-client.toml."));
            return;
        }
        Screen current = minecraft.gui.screen();
        DAI_ScreenManager.openTemporary(current, new DAI_AutomationCreatorScreen());
    }

    public static void openPauseMenu(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        DAI_Core.debug(
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

        DAI_Core.debug(
                "<DAI>: Opened chat screen."
        );
    }

    /**
     * Closes whatever Minecraft screen is currently active.
     *
     * Calling Screen.onClose() allows vanilla and modded screens to perform
     * their own normal cleanup instead of forcibly replacing the screen.
     */
    public static void closeScreen(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        Screen screen =
                minecraft.gui.screen();

        if (screen == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.SUCCESS
            );

            DAI_Core.debug(
                    "<DAI>: No screen is currently open."
            );

            return;
        }

        String screenName =
                screen.getClass()
                        .getSimpleName();

        screen.onClose();

        DAI_InputState.setCursorReleased(false);
        minecraft.execute(DAI_ClientRuntime::updateMouseCapture);

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.debug(
                "<DAI>: Closed screen {}.",
                screenName
        );
    }

    /**
     * Opens DAI's standard menu shell directly at a requested datapack menu.
     * Unlike update_menu this does not require a DAI menu to already be open.
     */
    public static void openDaiMenu(
            DAI_ActionDefinition action
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
            return;
        }

        String menu = action.menu();
        String open = action.open();
        if (menu == null || menu.isBlank() || open == null || open.isBlank()) {
            DAI_Core.LOGGER.warn("<DAI>: open_dai_menu requires both menu and open.");
            DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
            return;
        }

        if (minecraft.gui.screen() instanceof DAI_MenuCore existing) {
            existing.setFocusedMode(action.state());
            existing.updateMenu(menu, open);
        } else {
            DAI_MenuCore daiMenu = new DAI_MenuCore();
            DAI_ScreenManager.openTemporary(minecraft.gui.screen(), daiMenu);
            daiMenu.setFocusedMode(action.state());
            daiMenu.updateMenu(menu, open);
        }

        DAI_InputState.setCursorReleased(true);
        DAI_ClientRuntime.updateMouseCapture();
        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
        DAI_Core.debug("<DAI>: Opened targeted DAI menu '{}:{}'.", menu, open);
    }

    public static void setCursorReleased(DAI_ActionDefinition action) {
        DAI_InputState.setCursorReleased(action.state());
        DAI_ClientRuntime.updateMouseCapture();
        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
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

        DAI_Core.debug(
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