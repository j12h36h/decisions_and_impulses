package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.core.DAI;
import io.github.j12h36h.dai.input.DAI_MoveController;
import io.github.j12h36h.dai.input.Input_Manager;
import io.github.j12h36h.dai.ui.DAI_Menu;
import io.github.j12h36h.dai.ui.DAI_ScreenManager;
import io.github.j12h36h.dai.util.DAI_Targeting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.function.Consumer;
public final class DAI_ActionLogic {

    private DAI_ActionLogic() {
        // Utility class.
    }


    public static void execute(DAI_Action action) {

        DAI.LOGGER.info(
                "<DAI>: Executing action type={} sequence={}",
                action.type(),
                action.sequence().size()
        );

        DAI_ActionRegistry.execute(action);
    }

    public static void attack(DAI_Action action) {

        Entity target = DAI_Targeting.nearestEntity();

        if (target == null) {

            DAI.LOGGER.warn(
                    "<DAI>: No attack target"
            );

            return;
        }


        if (!(target instanceof LivingEntity)) {

            DAI.LOGGER.warn(
                    "<DAI>: Target is not living, skipping attack"
            );

            return;
        }


        DAI.LOGGER.info(
                "<DAI>: Attacking {}",
                target.getName().getString()
        );


        Input_Manager.action().attack(true);
    }


    public static void requestUpdateMenu(DAI_Action action) {

        updateMenu(
                action.menu(),
                action.open()
        );
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

    public static void requestSequence(DAI_Action action) {

        DAI.LOGGER.info(
                "<DAI>: Expanding sequence ({})",
                action.sequence().size()
        );

        DAI_ActionQueue.enqueueExpanded(action);
    }


    private static void openInventory() {

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {

            DAI.LOGGER.warn(
                    "<DAI>: Cannot open inventory, player is null"
            );

            return;
        }

        DAI_ScreenManager.push(
                minecraft.gui.screen()
        );

        DAI_ScreenManager.open(
                new InventoryScreen(minecraft.player)
        );
    }


    private static void openPauseMenu() {

        Minecraft minecraft = Minecraft.getInstance();

        DAI_ScreenManager.push(
                minecraft.gui.screen()
        );

        DAI_ScreenManager.open(
                new PauseScreen(true)
        );
    }


    private static void updateMenu(
            String menu,
            String category
    ) {

        Minecraft minecraft = Minecraft.getInstance();

        DAI_Menu daiMenu = null;

        if (minecraft.gui.screen() instanceof DAI_Menu currentMenu) {

            daiMenu = currentMenu;

        } else if (DAI_ScreenManager.peek() instanceof DAI_Menu stackedMenu) {

            daiMenu = stackedMenu;
        }


        if (daiMenu == null) {

            DAI.LOGGER.warn(
                    "<DAI>: No active DAI menu found to update."
            );

            return;
        }


        daiMenu.updateMenu(
                menu,
                category
        );
    }


    private static void look(
            float yaw,
            float pitch
    ) {

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

        DAI_ActionQueue.delay(
                action.ticks()
        );
    }
}