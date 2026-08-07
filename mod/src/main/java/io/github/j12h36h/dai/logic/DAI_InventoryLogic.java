package io.github.j12h36h.dai.logic;

import io.github.j12h36h.dai.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.controller.DAI_ItemController;
import io.github.j12h36h.dai.controller.DAI_UseController;
import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.ui.DAI_ScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public final class DAI_InventoryLogic {

    private DAI_InventoryLogic() {
        // Utility class.
    }

    public static void openInventory(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot open inventory because the player is null."
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Opening inventory screen."
        );

        DAI_ScreenManager.openTemporary(
                minecraft.gui.screen(),
                new InventoryScreen(
                        minecraft.player
                )
        );
    }

    public static void selectHotbarSlot(
            DAI_ActionDefinition action
    ) {

        DAI_HotbarLogic.select(
                action.slot()
        );
    }

    public static void selectNextHotbarSlot(
            DAI_ActionDefinition action
    ) {

        DAI_HotbarLogic.selectNext();
    }

    public static void selectPreviousHotbarSlot(
            DAI_ActionDefinition action
    ) {

        DAI_HotbarLogic.selectPrevious();
    }

    public static void useItem(
            DAI_ActionDefinition action
    ) {

        DAI_UseController.requestUse();
    }

    public static void startUsingItem(
            DAI_ActionDefinition action
    ) {

        DAI_UseController.startUse();
    }

    public static void stopUsingItem(
            DAI_ActionDefinition action
    ) {

        DAI_UseController.stopUse();
    }

    public static void dropItem(
            DAI_ActionDefinition action
    ) {

        DAI_ItemController.requestDrop();
    }

    public static void swapHands(
            DAI_ActionDefinition action
    ) {

        DAI_ItemController.requestSwap();
    }
}
