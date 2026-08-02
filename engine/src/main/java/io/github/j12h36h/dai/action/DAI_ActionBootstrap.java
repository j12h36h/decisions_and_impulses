package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.input.Input_Manager;
import io.github.j12h36h.dai.util.DAI_ScanLogic;
import io.github.j12h36h.dai.util.DAI_Targeting;
import net.minecraft.world.entity.Entity;

import static io.github.j12h36h.dai.action.DAI_ActionLogic.requestUpdateMenu;

public final class DAI_ActionBootstrap {

    public static void init() {

        DAI_ActionRegistry.register(
                "open_inventory",
                DAI_ActionLogic::requestOpenInventory
        );


        DAI_ActionRegistry.register(
                "pause_menu",
                DAI_ActionLogic::requestOpenPause
        );

        DAI_ActionRegistry.register(
                "update_menu",
                DAI_ActionLogic::requestUpdateMenu
        );

        DAI_ActionRegistry.register(
                "look",
                DAI_ActionLogic::requestLook
        );

        DAI_ActionRegistry.register(
                "sequence",
                DAI_ActionLogic::requestSequence
        );

        DAI_ActionRegistry.register(
                "move",
                DAI_ActionLogic::move
        );

        DAI_ActionRegistry.register(
                "scan",
                DAI_ScanLogic::execute
        );

        DAI_ActionRegistry.register(
                "attack",
                DAI_ActionLogic::attack
        );

        DAI_ActionRegistry.register(
                "delay",
                action -> DAI_ActionQueue.delay(action.ticks())
        );
    }
}
