package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.logic.*;

import java.util.function.Consumer;

public final class DAI_ActionHandler {

    private DAI_ActionHandler() {
        // Utility class.
    }

    public static void initialize() {

        DAI_Core.LOGGER.info(
                "<DAI>: Registering action handlers..."
        );

        registerMenuActions();
        registerLookActions();
        registerMovementActions();
        registerTargetActions();
        registerCombatActions();
        registerInventoryActions();
        registerEquipmentActions();
        registerInteractionActions();
        registerMiningActions();
        registerBlockActions();
        registerNavigationActions();
        registerFoodActions();
        registerContainerActions();
        registerQueueActions();
        registerFlowActions();
        registerCraftingActions();
        registerObjectiveActions();
        registerAutomationActions();
        registerGameModeActions();

        DAI_Core.LOGGER.info(
                "<DAI>: Action handlers registered."
        );
    }

    private static void registerMenuActions() {

        register(
                "pause_menu",
                DAI_MenuLogic::openPauseMenu
        );

        register(
                "update_menu",
                DAI_MenuLogic::updateMenu
        );

        register(
                "open_chat",
                DAI_MenuLogic::openChat
        );
    }

    private static void registerLookActions() {

        /*
         * These remain in DAI_ActionLogic until DAI_LookLogic
         * receives DAI_ActionCore-compatible handler methods.
         */

        register(
                "set_look",
                DAI_LookLogic::setLook
        );

        register(
                "add_look",
                DAI_LookLogic::addLook
        );
    }

    private static void registerMovementActions() {

        /*
         * These remain in DAI_ActionLogic until DAI_MoveLogic
         * receives DAI_ActionCore-compatible handler methods.
         */

        register(
                "move",
                DAI_MoveLogic::move
        );

        register(
                "jump",
                DAI_MoveLogic::jump
        );

        register(
                "crouch_toggle",
                DAI_MoveLogic::crouchToggle
        );

        register(
                "crouch_set",
                DAI_MoveLogic::crouchSet
        );

        register(
                "sprint_toggle",
                DAI_MoveLogic::sprintToggle
        );

        register(
                "sprint_set",
                DAI_MoveLogic::sprintSet
        );

        register(
                "swim_toggle",
                DAI_MoveLogic::swimToggle
        );

        register(
                "swim_set",
                DAI_MoveLogic::swimSet
        );

        register(
                "input_stop_all",
                DAI_MoveLogic::stopAll
        );
    }

    private static void registerTargetActions() {

        register(
                "scan",
                DAI_TargetLogic::execute
        );

        register(
                "target_clear",
                action -> DAI_TargetLogic.clear()
        );

        /*
         * Recognition still remains in DAI_ActionLogic until its
         * existing implementation is moved into targeting logic.
         */

        register(
                "recognize_target",
                DAI_TargetLogic::recognizeTarget
        );

        register(
                "recognize_block",
                DAI_TargetLogic::recognizeBlock
        );
    }

    private static void registerCombatActions() {

        register(
                "attack_target",
                DAI_CombatLogic::attackTarget
        );

        register(
                "attack_basic",
                DAI_CombatLogic::attackBasic
        );

        register(
                "attack_start",
                DAI_CombatLogic::attackStart
        );

        register(
                "attack_stop",
                DAI_CombatLogic::attackStop
        );
    }

    private static void registerAutomationActions() {

        register(
                "automation_start_vanilla_gameplay",
                DAI_AutomationLogic::startVanillaGameplay
        );

        register(
                "automation_stop",
                DAI_AutomationLogic::stop
        );
    }

    private static void registerGameModeActions() {

        register(
                "set_gamemode",
                DAI_GameModeLogic::setGameMode
        );
    }

    private static void registerInventoryActions() {

        register(
                "open_inventory",
                DAI_InventoryLogic::openInventory
        );

        register(
                "hotbar_select",
                DAI_InventoryLogic::selectHotbarSlot
        );

        register(
                "hotbar_next",
                DAI_InventoryLogic::selectNextHotbarSlot
        );

        register(
                "hotbar_previous",
                DAI_InventoryLogic::selectPreviousHotbarSlot
        );

        register(
                "item_use",
                DAI_InventoryLogic::useItem
        );

        register(
                "use_start",
                DAI_InventoryLogic::startUsingItem
        );

        register(
                "use_stop",
                DAI_InventoryLogic::stopUsingItem
        );

        register(
                "item_drop",
                DAI_InventoryLogic::dropItem
        );

        register(
                "item_swap",
                DAI_InventoryLogic::swapHands
        );
    }

    private static void registerEquipmentActions() {

        register(
                "hotbar_select_item",
                DAI_EquipmentLogic::selectItem
        );

        register(
                "equip_best_tool",
                DAI_EquipmentLogic::equipBestTool
        );

        register(
                "equip_best_weapon",
                DAI_EquipmentLogic::equipBestWeapon
        );

        register(
                "equip_best_food",
                DAI_EquipmentLogic::equipBestFood
        );

        register(
                "equip_best_block",
                DAI_EquipmentLogic::equipBestBlock
        );
    }

    private static void registerInteractionActions() {

        register(
                "interact",
                DAI_InteractionLogic::interact
        );

        /*
         * Pick-block remains in DAI_ActionLogic until its existing
         * implementation is moved into interaction or inventory logic.
         */

        register(
                "pick_block",
                DAI_InteractionLogic::pickBlock
        );
    }

    private static void registerMiningActions() {

        register(
                "break_once",
                DAI_MiningLogic::breakOnce
        );

        register(
                "break_start",
                DAI_MiningLogic::breakStart
        );

        register(
                "break_stop",
                DAI_MiningLogic::breakStop
        );

        register(
                "equip_best_tool_for_block",
                DAI_MiningLogic::equipBestToolForBlock
        );

        register(
                "mine_targeted_block",
                DAI_MiningLogic::mineTargetedBlock
        );

        register(
                "mine_nearest_block",
                DAI_MiningLogic::mineNearestBlock
        );

        register(
                "collect_nearby_items",
                DAI_ItemCollectionLogic::collectNearbyItems
        );
    }

    private static void registerBlockActions() {

        register(
                "place",
                DAI_BlockLogic::place
        );

        /*
         * These remain in DAI_ActionLogic until their complete
         * implementations are moved into DAI_BlockLogic.
         */

        register(
                "place_targeted_block",
                DAI_BlockLogic::placeTargetedBlock
        );

        register(
                "place_nearest_block",
                DAI_BlockLogic::placeNearestBlock
        );

        register(
                "harvest_crop",
                DAI_BlockLogic::harvestCrop
        );
    }

    private static void registerNavigationActions() {

        register(
                "approach_target_block",
                DAI_NavigationLogic::approachTargetBlock
        );

        register(
                "wait_for_approach",
                DAI_NavigationLogic::waitForApproach
        );

        register(
                "wait_for_target_block",
                DAI_NavigationLogic::waitForTargetBlock
        );

        register(
                "explore_for_block",
                DAI_NavigationLogic::exploreForBlock
        );

        register(
                "wait_for_exploration",
                DAI_NavigationLogic::waitForExploration
        );
    }

    private static void registerFoodActions() {

        register(
                "eat_best_food",
                DAI_FoodLogic::eatBestFood
        );
    }

    private static void registerContainerActions() {

        register(
                "open_container",
                DAI_ContainerLogic::openContainer
        );

        register(
                "wait_for_container",
                DAI_ContainerLogic::waitForContainer
        );
    }

    private static void registerQueueActions() {

        register(
                "delay",
                DAI_QueueLogic::delay
        );

        register(
                "sequence",
                DAI_QueueLogic::sequence
        );

        register(
                "enqueue_action",
                DAI_QueueLogic::enqueueAction
        );

        register(
                "queue_clear",
                DAI_QueueLogic::clearQueue
        );

        register(
                "random_action",
                DAI_QueueLogic::randomAction
        );
    }

    private static void registerCraftingActions() {

        register(
                "craft_recipe",
                DAI_CraftingLogic::craftRecipe
        );

        register(
                "craft_take_result",
                DAI_CraftingLogic::takeResult
        );
    }

    private static void registerObjectiveActions() {

        register(
                "objective_execute",
                DAI_ObjectiveLogic::execute
        );
    }

    private static void registerFlowActions() {

        register(
                "stop_if_failure",
                DAI_FlowLogic::stopIfFailure
        );

        register(
                "stop_if_success",
                DAI_FlowLogic::stopIfSuccess
        );

        register(
                "run_if_failure",
                DAI_FlowLogic::runIfFailure
        );

        register(
                "run_if_success",
                DAI_FlowLogic::runIfSuccess
        );
    }

    private static void register(
            String id,
            Consumer<DAI_ActionDefinition> handler
    ) {

        DAI_ActionRegistry.register(
                id,
                handler
        );
    }
}