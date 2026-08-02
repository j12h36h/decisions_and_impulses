package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.scan.DAI_ScanLogic;

import java.util.function.Consumer;

public final class DAI_ActionBootstrap {

    private DAI_ActionBootstrap() {
        // Utility class.
    }

    public static void initialize() {

        DAI_Core.LOGGER.info(
                "<DAI>: Registering action handlers..."
        );

        register(
                "open_inventory",
                DAI_ActionLogic::requestOpenInventory
        );

        register(
                "pause_menu",
                DAI_ActionLogic::requestOpenPause
        );

        register(
                "update_menu",
                DAI_ActionLogic::requestUpdateMenu
        );

        register(
                "look",
                DAI_ActionLogic::requestLook
        );

        register(
                "sequence",
                DAI_ActionLogic::requestSequence
        );

        register(
                "move",
                DAI_ActionLogic::move
        );

        register(
                "scan",
                DAI_ScanLogic::execute
        );

        register(
                "attack",
                DAI_ActionLogic::attack
        );

        register(
                "delay",
                DAI_ActionLogic::delay
        );

        register(
                "jump",
                DAI_ActionLogic::requestJump
        );

        register(
                "crouch_toggle",
                DAI_ActionLogic::requestCrouchToggle
        );

        register(
                "sprint_toggle",
                DAI_ActionLogic::requestSprintToggle
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Action handlers registered."
        );
    }

    private static void register(
            String id,
            Consumer<DAI_ActionCore> handler
    ) {

        DAI_ActionRegistry.register(
                id,
                handler
        );

    }
}
