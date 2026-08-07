package io.github.j12h36h.dai.logic;

import io.github.j12h36h.dai.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.action.DAI_ActionQueue;
import io.github.j12h36h.dai.action.DAI_ActionResult;
import io.github.j12h36h.dai.action.DAI_ActionStatus;
import io.github.j12h36h.dai.core.DAI_Core;
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

            DAI_Core.LOGGER.debug(
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

        DAI_Core.LOGGER.debug(
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
         * The player's normal inventory uses InventoryMenu.
         * Any different active menu means an external container or
         * workstation menu has opened successfully.
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

            DAI_Core.LOGGER.debug(
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

        DAI_Core.LOGGER.debug(
                "<DAI>: Container menu has not opened yet; {} check(s) remaining.",
                retriesRemaining - 1
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