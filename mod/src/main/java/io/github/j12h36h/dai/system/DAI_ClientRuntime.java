package io.github.j12h36h.dai.system;

import io.github.j12h36h.dai.action.DAI_ActionController;
import io.github.j12h36h.dai.action.DAI_ActionQueue;
import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.input.DAI_InputController;
import io.github.j12h36h.dai.input.DAI_MoveController;
import io.github.j12h36h.dai.input.DAI_MovementInput;
import io.github.j12h36h.dai.input.DAI_TargetController;
import io.github.j12h36h.dai.ui.DAI_MenuCore;
import io.github.j12h36h.dai.ui.DAI_ScreenManager;
import net.minecraft.client.Minecraft;

public final class DAI_ClientRuntime {

    private static boolean initializationPending;

    private DAI_ClientRuntime() {
        // Utility class.
    }

    /**
     * Requests initialization once Minecraft and its player are ready.
     */
    public static void requestInitialize() {

        initializationPending = true;

        DAI_Core.LOGGER.debug(
                "<DAI>: Client runtime initialization requested."
        );
    }

    /**
     * Completes pending initialization at a safe point in the client tick.
     */
    public static void tick() {

        if (!initializationPending) {
            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft == null
                        || minecraft.player == null
                        || minecraft.level == null
        ) {
            return;
        }

        initialize(
                minecraft
        );

        initializationPending = false;
    }

    private static void initialize(
            Minecraft minecraft
    ) {

        resetSession();

        DAI_InputController
                .look()
                .setRotation(
                        minecraft.player.getYRot(),
                        minecraft.player.getXRot()
                );

        if (
                !(minecraft.player.input
                        instanceof DAI_MovementInput)
        ) {

            minecraft.player.input =
                    new DAI_MovementInput(
                            minecraft.options
                    );

            DAI_Core.LOGGER.info(
                    "<DAI>: Movement input installed."
            );
        }

        DAI_ScreenManager.open(
                new DAI_MenuCore()
        );

        updateMouseCapture(
                minecraft
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Client runtime initialized."
        );
    }

    public static void resetSession() {

        initializationPending = false;

        DAI_ActionQueue.clear();
        DAI_ActionController.reset();
        DAI_MoveController.stop();
        DAI_InputController.movement().clear();
        DAI_TargetController.clear();
        DAI_ScreenManager.clear();
    }

    private static void updateMouseCapture(
            Minecraft minecraft
    ) {

        boolean overrideEnabled =
                DAI_InputController.isOverrideEnabled();

        boolean grabbed =
                minecraft.mouseHandler.isMouseGrabbed();

        if (overrideEnabled && grabbed) {

            minecraft.mouseHandler.releaseMouse();

        } else if (!overrideEnabled && !grabbed) {

            minecraft.mouseHandler.grabMouse();
        }
    }
}