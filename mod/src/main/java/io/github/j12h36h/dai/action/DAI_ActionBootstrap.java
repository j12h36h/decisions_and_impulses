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
                "set_look",
                DAI_ActionLogic::requestSetLook
        );

        register(
                "add_look",
                DAI_ActionLogic::requestAddLook
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
                "attack_target",
                DAI_ActionLogic::requestTargetAttack
        );

        register(
                "attack_basic",
                DAI_ActionLogic::requestBasicAttack
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
                "crouch_set",
                DAI_ActionLogic::requestCrouchSet
        );

        register(
                "sprint_toggle",
                DAI_ActionLogic::requestSprintToggle
        );

        register(
                "sprint_set",
                DAI_ActionLogic::requestSprintSet
        );

        register(
                "swim_toggle",
                DAI_ActionLogic::requestSwimToggle
        );

        register(
                "swim_set",
                DAI_ActionLogic::requestSwimSet
        );

        register(
                "target_clear",
                DAI_ActionLogic::requestTargetClear
        );

        register(
                "hotbar_select",
                DAI_ActionLogic::requestHotbarSelect
        );

        register(
                "hotbar_next",
                DAI_ActionLogic::requestHotbarNext
        );

        register(
                "hotbar_previous",
                DAI_ActionLogic::requestHotbarPrevious
        );

        register(
                "attack_start",
                DAI_ActionLogic::requestAttackStart
        );

        register(
                "attack_stop",
                DAI_ActionLogic::requestAttackStop
        );

        register(
                "use_start",
                DAI_ActionLogic::requestUseStart
        );

        register(
                "use_stop",
                DAI_ActionLogic::requestUseStop
        );

        register(
                "input_stop_all",
                DAI_ActionLogic::requestInputStopAll
        );

        register(
                "item_use",
                DAI_ActionLogic::requestItemUse
        );

        register(
                "item_drop",
                DAI_ActionLogic::requestItemDrop
        );

        register(
                "item_swap",
                DAI_ActionLogic::requestHandSwap
        );

        register(
                "break_once",
                DAI_ActionLogic::requestBreakOnce
        );

        register(
                "break_start",
                DAI_ActionLogic::requestBreakStart
        );

        register(
                "break_stop",
                DAI_ActionLogic::requestBreakStop
        );

        register(
                "place",
                DAI_ActionLogic::requestPlace
        );

        register(
                "interact",
                DAI_ActionLogic::requestInteract
        );

        DAI_ActionRegistry.register(
                "recognize_target",
                DAI_ActionLogic::requestRecognizeTarget
        );

        DAI_ActionRegistry.register(
                "open_chat",
                DAI_ActionLogic::openChat
        );

        register(
                "pick_block",
                DAI_ActionLogic::requestPickBlock
        );

        DAI_ActionRegistry.register(
                "enqueue_action",
                DAI_ActionLogic::enqueueAction
        );

        DAI_ActionRegistry.register(
                "queue_clear",
                DAI_ActionLogic::clearQueue
        );

        DAI_ActionRegistry.register(
                "random_action",
                DAI_ActionLogic::requestRandomAction
        );

        DAI_ActionRegistry.register(
                "objective_execute",
                DAI_ActionLogic::requestObjectiveExecute
        );

        DAI_ActionRegistry.register(
                "craft_recipe",
                DAI_ActionLogic::craftRecipe
        );

        DAI_ActionRegistry.register(
                "craft_take_result",
                DAI_ActionLogic::craftTakeResult
        );

        register(
                "hotbar_select_item",
                DAI_ActionLogic::requestHotbarSelectItem
        );

        DAI_ActionRegistry.register(
                "equip_best_tool",
                DAI_ActionLogic::requestEquipBestTool
        );

        DAI_ActionRegistry.register(
                "equip_best_weapon",
                DAI_ActionLogic::requestEquipBestWeapon
        );

        DAI_ActionRegistry.register(
                "equip_best_food",
                DAI_ActionLogic::requestEquipBestFood
        );

        DAI_ActionRegistry.register(
                "equip_best_block",
                DAI_ActionLogic::requestEquipBestBlock
        );

        DAI_ActionRegistry.register(
                "equip_best_tool_for_block",
                DAI_ActionLogic::requestEquipBestToolForBlock
        );

        DAI_ActionRegistry.register(
                "mine_targeted_block",
                DAI_ActionLogic::requestMineTargetedBlock
        );

        DAI_ActionRegistry.register(
                "place_targeted_block",
                DAI_ActionLogic::requestPlaceTargetedBlock
        );

        DAI_ActionRegistry.register(
                "approach_target_block",
                DAI_ActionLogic::requestApproachTargetBlock
        );

        DAI_ActionRegistry.register(
                "recognize_block",
                DAI_ActionLogic::requestRecognizeBlock
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Action handlers registered."
        );

        DAI_ActionRegistry.register(
                "wait_for_approach",
                DAI_ActionLogic::requestWaitForApproach
        );

        DAI_ActionRegistry.register(
                "wait_for_target_block",
                DAI_ActionLogic::requestWaitForTargetBlock
        );

        DAI_ActionRegistry.register(
                "mine_nearest_block",
                DAI_ActionLogic::requestMineNearestBlock
        );

        DAI_ActionRegistry.register(
                "place_nearest_block",
                DAI_ActionLogic::requestPlaceNearestBlock
        );

        DAI_ActionRegistry.register(
                "eat_best_food",
                DAI_ActionLogic::requestEatBestFood
        );

        DAI_ActionRegistry.register(
                "attack_target",
                DAI_ActionLogic::requestAttackTarget
        );

        DAI_ActionRegistry.register(
                "open_container",
                DAI_ActionLogic::requestOpenContainer
        );

        DAI_ActionRegistry.register(
                "wait_for_container",
                DAI_ActionLogic::requestWaitForContainer
        );

        DAI_ActionRegistry.register(
                "harvest_crop",
                DAI_ActionLogic::requestHarvestCrop
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