package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.content.DAI_ContentRuntime;
import io.github.j12h36h.dai.content.DAI_ContentStack;
import net.minecraft.client.Minecraft;
import io.github.j12h36h.dai.logics.controller.DAI_ItemController;
import io.github.j12h36h.dai.logics.controller.DAI_UseController;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.menus.DAI_ScreenManager;
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

        DAI_Core.debug(
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

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null) {
            String contentId = DAI_ContentStack.id(minecraft.player.getMainHandItem());
            if (!contentId.isBlank() && DAI_ContentRegistry.contains(contentId)) {
                DAI_ContentRuntime.emit(minecraft.player, contentId, "use");
            }
        }

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
