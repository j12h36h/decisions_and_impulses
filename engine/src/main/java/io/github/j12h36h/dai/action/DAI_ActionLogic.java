package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.core.DAI;
import io.github.j12h36h.dai.ui.DAI_Menu;
import io.github.j12h36h.dai.ui.DAI_ScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public final class DAI_ActionLogic {

    private DAI_ActionLogic() {
        // Utility class.
    }

    public static void openInventory() {

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            DAI.LOGGER.warn("<DAI>: Cannot open inventory, player is null");
            return;
        }

        DAI_ScreenManager.push(minecraft.gui.screen());

        DAI_ScreenManager.open(
                new InventoryScreen(minecraft.player)
        );
    }

    public static void openPauseMenu() {

        Minecraft minecraft = Minecraft.getInstance();

        DAI_ScreenManager.push(minecraft.gui.screen());

        DAI_ScreenManager.open(
                new PauseScreen(true)
        );
    }

    public static void updateMenu(String menu, String category) {

        Minecraft minecraft = Minecraft.getInstance();

        DAI_Menu daiMenu = null;

        // If the DAI menu is currently open.
        if (minecraft.gui.screen() instanceof DAI_Menu currentMenu) {
            daiMenu = currentMenu;
        }

        // If the DAI menu is stored while another screen is open.
        else if (DAI_ScreenManager.peek() instanceof DAI_Menu stackedMenu) {
            daiMenu = stackedMenu;
        }

        if (daiMenu == null) {
            DAI.LOGGER.warn("<DAI>: No active DAI menu found to update.");
            return;
        }

        daiMenu.updateMenu(menu, category);
    }
}