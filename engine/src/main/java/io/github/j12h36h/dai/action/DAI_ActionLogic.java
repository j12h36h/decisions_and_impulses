package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.core.DAI;
import io.github.j12h36h.dai.input.DAI_MoveController;
import io.github.j12h36h.dai.input.Input_Manager;
import io.github.j12h36h.dai.ui.DAI_Menu;
import io.github.j12h36h.dai.ui.DAI_ScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.util.Map;
import java.util.function.Consumer;

public final class DAI_ActionLogic {

    private DAI_ActionLogic() {
        // Utility class.
    }

    private static final Map<String, Consumer<DAI_Action>> ACTIONS = Map.of(
            "open_inventory", action -> openInventory(),
            "pause_menu", action -> openPauseMenu(),
            "update_menu", action -> updateMenu(action.menu(), action.open()),
            "look", DAI_ActionLogic::requestLook,
            "sequence", DAI_ActionLogic::requestSequence
    );

    public static void execute(DAI_Action action) {

        DAI.LOGGER.info(
                "<DAI>: Executing action type={} sequence={}",
                action.type(),
                action.sequence().size()
        );

        DAI_ActionRegistry.execute(action);
    }

    public static void requestUpdateMenu(DAI_Action action) {
        updateMenu(action.menu(), action.open());
    }

    public static void requestOpenPause(DAI_Action action) {
        openPauseMenu();
    }
    public static void requestOpenInventory(DAI_Action action) {
        openInventory();
    }

    public static void requestLook(DAI_Action action) {
        look(
                action.yaw(),
                action.pitch()
        );
    }

    private static void openInventory() {

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

    public static void requestSequence(DAI_Action action) {

        DAI.LOGGER.info(
                "<DAI>: Sequence size {}",
                action.sequence().size()
        );

        for (DAI_Action child : action.sequence()) {

            DAI.LOGGER.info(
                    "<DAI>: Sequence child {}",
                    child.type()
            );

            execute(child);
        }
    }

    private static void openPauseMenu() {

        Minecraft minecraft = Minecraft.getInstance();

        DAI_ScreenManager.push(minecraft.gui.screen());

        DAI_ScreenManager.open(
                new PauseScreen(true)
        );
    }

    private static void updateMenu(String menu, String category) {

        Minecraft minecraft = Minecraft.getInstance();

        DAI_Menu daiMenu = null;

        if (minecraft.gui.screen() instanceof DAI_Menu currentMenu) {
            daiMenu = currentMenu;
        } else if (DAI_ScreenManager.peek() instanceof DAI_Menu stackedMenu) {
            daiMenu = stackedMenu;
        }

        if (daiMenu == null) {
            DAI.LOGGER.warn("<DAI>: No active DAI menu found to update.");
            return;
        }

        daiMenu.updateMenu(menu, category);
    }

    private static void look(float yaw, float pitch) {

        Input_Manager.look().setRotation(
                yaw,
                pitch
        );
    }

    public static void move(DAI_Action action) {

        switch (action.direction()) {

            case "forward" ->
                    DAI_MoveController.start(
                            1F,
                            0F,
                            action.ticks()
                    );

            case "backward" ->
                    DAI_MoveController.start(
                            -1F,
                            0F,
                            action.ticks()
                    );

            case "left" ->
                    DAI_MoveController.start(
                            0F,
                            1F,
                            action.ticks()
                    );

            case "right" ->
                    DAI_MoveController.start(
                            0F,
                            -1F,
                            action.ticks()
                    );

            default ->
                    DAI.LOGGER.warn(
                            "<DAI>: Unknown movement direction '{}'",
                            action.direction()
                    );
        }
    }

    public static void delay(DAI_Action action) {
        // Pause execution for action.ticks() ticks.
    }
}