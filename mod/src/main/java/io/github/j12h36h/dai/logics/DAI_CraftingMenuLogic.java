package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class DAI_CraftingMenuLogic {

    private DAI_CraftingMenuLogic() {
        // Utility class.
    }

    public static boolean isCraftingMenu(
            AbstractContainerMenu menu
    ) {

        if (menu == null) {
            return false;
        }

        String menuName =
                menu.getClass()
                        .getSimpleName();

        return "InventoryMenu".equals(
                menuName
        )
                || "CraftingMenu".equals(
                menuName
        );
    }

    public static Identifier itemId(
            ItemStack stack
    ) {

        if (
                stack == null
                        || stack.isEmpty()
        ) {
            return null;
        }

        return stack.getItem()
                .builtInRegistryHolder()
                .key()
                .identifier();
    }

    public static Identifier parseResultId(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {
            return null;
        }

        String normalized =
                value.trim();

        if (
                !normalized.contains(
                        ":"
                )
        ) {

            normalized =
                    "minecraft:"
                            + normalized;
        }

        return Identifier.tryParse(
                normalized
        );
    }

    public static DAI_ActionDefinition createTakeResultAction(
            int retries,
            int containerId
    ) {

        return new DAI_ActionDefinition(
                "craft_take_result",
                "",
                List.of(),
                List.of(),
                "",
                "",
                0.0F,
                0.0F,
                "",
                Math.max(
                        0,
                        retries
                ),
                Math.max(
                        0,
                        containerId
                ),
                false,
                0.0D
        );
    }

    public static void releaseCraftBarrier() {

        if (
                DAI_ActionQueue.barrierIs(
                        "craft_take_result"
                )
        ) {

            DAI_ActionQueue.releaseBarrier();
        }
    }
}