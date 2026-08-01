package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.input.Input_Manager;
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
                "look_nearest_entity",
                action -> {
                    Entity entity = DAI_Targeting.nearestEntity();

                    if (entity == null) {
                        return;
                    }

                    Input_Manager.look().setRotation(
                            DAI_Targeting.yawTo(entity),
                            DAI_Targeting.pitchTo(entity)
                    );
                }
        );

        DAI_ActionRegistry.register(
                "attack",
                action -> Input_Manager.action().attack(true)
        );

        DAI_ActionRegistry.register(
                "delay",
                action -> DAI_ActionQueue.delay(action.ticks())
        );
    }
}
