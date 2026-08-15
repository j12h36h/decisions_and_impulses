package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

public final class DAI_ContainerLogic {

    private static final int DEFAULT_OPEN_RETRIES =
            20;

    private static final int INTERACTION_DELAY =
            2;

    private DAI_ContainerLogic() {
        // Utility class.
    }

    public static void openContainer(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || minecraft.gameMode == null
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot open a container without an active player, level, and game mode."
            );

            return;
        }

        HitResult hitResult =
                minecraft.hitResult;

        if (
                !(
                        hitResult instanceof BlockHitResult
                                || hitResult instanceof EntityHitResult
                )
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.debug(
                    "<DAI>: Cannot open a container because no block or entity is targeted."
            );

            return;
        }

        int retries =
                action != null
                        && action.ticks() > 0
                        ? action.ticks()
                        : DEFAULT_OPEN_RETRIES;

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        createAction(
                                "interact",
                                0
                        ),
                        createAction(
                                "delay",
                                INTERACTION_DELAY
                        ),
                        createAction(
                                "wait_for_container",
                                retries
                        )
                )
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.debug(
                "<DAI>: Queued container interaction with {} retry check(s).",
                retries
        );
    }

    public static void waitForContainer(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot wait for a container without an active player."
            );

            return;
        }

        /*
         * InventoryMenu is the player's normal 2x2 inventory crafting menu.
         *
         * Any other active container menu is treated generically as an
         * external container or workstation. This intentionally supports
         * vanilla and modded menu implementations.
         */
        if (
                !(
                        minecraft.player.containerMenu
                                instanceof InventoryMenu
                )
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.SUCCESS
            );

            DAI_Core.debug(
                    "<DAI>: Container menu opened: {}.",
                    minecraft.player
                            .containerMenu
                            .getClass()
                            .getSimpleName()
            );

            return;
        }

        int retriesRemaining =
                action != null
                        ? action.ticks()
                        : 0;

        if (retriesRemaining <= 1) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.TIMED_OUT
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Timed out waiting for a container menu to open."
            );

            return;
        }

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        createAction(
                                "delay",
                                1
                        ),
                        createAction(
                                "wait_for_container",
                                retriesRemaining - 1
                        )
                )
        );

        DAI_Core.debug(
                "<DAI>: Container menu has not opened yet; {} check(s) remaining.",
                retriesRemaining - 1
        );
    }

    /**
     * Closes the player's currently active external container/workstation.
     *
     * Using LocalPlayer.closeContainer() rather than directly replacing the
     * screen preserves Minecraft's normal container-close synchronization and
     * therefore also works with modded AbstractContainerMenu implementations.
     */
    public static void closeContainer(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot close a container without an active player."
            );

            return;
        }

        if (
                minecraft.player.containerMenu
                        instanceof InventoryMenu
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.SUCCESS
            );

            DAI_Core.debug(
                    "<DAI>: No external container menu is currently open."
            );

            return;
        }

        String menuName =
                minecraft.player
                        .containerMenu
                        .getClass()
                        .getSimpleName();

        minecraft.player.closeContainer();

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.debug(
                "<DAI>: Closed container menu {}.",
                menuName
        );
    }

    private static DAI_ActionDefinition createAction(
            String type,
            int ticks
    ) {

        return new DAI_ActionDefinition(
                type,
                "",
                List.of(),
                List.of(),
                "",
                "",
                0.0F,
                0.0F,
                "",
                ticks,
                0,
                false,
                0.0D
        );
    }
}