package io.github.j12h36h.dai.logics.action;

import io.github.j12h36h.dai.logics.*;
import io.github.j12h36h.dai.logics.core.DAI_Core;

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
        registerWaypointActions();
        registerSpatialActions();
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

        register(
                "close_screen",
                DAI_MenuLogic::closeScreen
        );
    }

    private static void registerLookActions() {

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
                "automation_start_speedrun",
                DAI_AutomationLogic::startSpeedrun
        );

        register(
                "automation_start_creative_builder",
                DAI_AutomationLogic::startCreativeBuilder
        );

        register(
                "automation_start_adventure",
                DAI_AutomationLogic::startAdventure
        );

        register(
                "automation_continue",
                DAI_AutomationLogic::continueAutomation
        );

        register(
                "speedrun_find_portal_site",
                DAI_SpeedrunLogic::findPortalSite
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

        register(
                "run_command",
                DAI_CommandLogic::runCommand
        );

        register(
                "key_click",
                DAI_InputLogic::keyClick
        );

        register(
                "key_press",
                DAI_InputLogic::keyPress
        );

        register(
                "key_release",
                DAI_InputLogic::keyRelease
        );

        register(
                "type_text",
                DAI_InputLogic::typeText
        );

        register(
                "creative_open_inventory",
                DAI_CreativeInventoryLogic::openCreativeInventory
        );

        register(
                "creative_close_inventory",
                DAI_CreativeInventoryLogic::closeCreativeInventory
        );

        register(
                "creative_select_tab",
                DAI_CreativeInventoryLogic::selectTab
        );

        register(
                "creative_search_item",
                DAI_CreativeInventoryLogic::search
        );

        register(
                "creative_take_item",
                DAI_CreativeInventoryLogic::takeVisibleItem
        );

        register(
                "creative_equip_item",
                DAI_CreativeInventoryLogic::equipItem
        );

        register(
                "creative_save_toolbar",
                DAI_CreativeInventoryLogic::saveToolbar
        );

        register(
                "creative_load_toolbar",
                DAI_CreativeInventoryLogic::loadToolbar
        );

        register(
                "creative_pick_block_nbt",
                DAI_CreativeInventoryLogic::pickBlockWithData
        );

        register(
                "creative_remove_block",
                DAI_CreativeInventoryLogic::removeSelectedBlock
        );

        register(
                "creative_place_block",
                DAI_CreativeInventoryLogic::placeSelectedBlock
        );

        register(
                "creative_set_block",
                DAI_CreativeInventoryLogic::setSelectedBlockState
        );

        register(
                "creative_flight_set",
                DAI_CreativeFlightLogic::setFlight
        );

        register(
                "creative_fly_to",
                DAI_CreativeFlightLogic::flyTo
        );

        register(
                "wait_for_creative_flight",
                DAI_CreativeFlightLogic::waitForFlight
        );

        register(
                "creative_hover",
                DAI_CreativeFlightLogic::hover
        );

        register(
                "creative_build_blueprint",
                DAI_CreativeBuildLogic::startBlueprint
        );

        /*
         * Blueprint cells are nested data records consumed by
         * DAI_CreativeBuildController; registering the type keeps datapack
         * validation aware of that payload schema. They are a harmless
         * SUCCESS no-op if ever dispatched independently.
         */
        register(
                "creative_blueprint_cell",
                action -> DAI_ActionStatus.set(DAI_ActionResult.SUCCESS)
        );

        register(
                "wait_for_creative_build",
                DAI_CreativeBuildLogic::waitForBlueprint
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
                "hotbar_normalize",
                DAI_HotbarNormalizeLogic::normalize
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

        register(
                "pick_block",
                DAI_InteractionLogic::pickBlock
        );
    }

    private static void registerMiningActions() {

        /*
         * Compatibility alias used by the targeted mining queue.
         *
         * Keep both IDs registered: existing runtime mining sequences may
         * enqueue break_targeted_once while newer code may use break_once.
         * Both intentionally dispatch the same one-shot break behavior.
         */
        register(
                "break_targeted_once",
                DAI_MiningLogic::breakOnce
        );

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

        register(
                "place_targeted_block",
                DAI_BlockLogic::placeTargetedBlock
        );

        register(
                "place_nearest_block",
                DAI_BlockLogic::placeNearestBlock
        );

        register(
                "place_block_at_selected_position",
                DAI_ExactPlacementLogic::placeAtSelectedPosition
        );

        /* Internal smooth camera alignment before Creative physical placement. */
        register(
                "exact_place_align",
                DAI_ExactPlacementLogic::alignPlacement
        );

        /* Internal continuation used by deterministic blueprint placement. */
        register(
                "exact_place_finish",
                DAI_ExactPlacementLogic::finishPlacement
        );

        /* Internal world-state commit check after the physical use action. */
        register(
                "exact_place_verify",
                DAI_ExactPlacementLogic::verifyPlacement
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

        register(
                "vertical_scaffold_to_target",
                DAI_ScaffoldLogic::ascendToTarget
        );

        register(
                "wait_for_vertical_scaffold",
                DAI_ScaffoldLogic::waitForAscent
        );

        register(
                "vertical_scaffold_descend",
                DAI_ScaffoldLogic::descend
        );

        register(
                "wait_for_scaffold_descent",
                DAI_ScaffoldLogic::waitForDescent
        );
    }

    private static void registerWaypointActions() {

        register(
                "remember_waypoint",
                action ->
                        DAI_WaypointLogic.rememberPlayerPosition(
                                action.action()
                        )
        );

        register(
                "remember_target_waypoint",
                action ->
                        DAI_WaypointLogic.rememberSelectedBlock(
                                action.action()
                        )
        );

        register(
                "select_waypoint",
                action ->
                        DAI_WaypointLogic.selectWaypoint(
                                action.action()
                        )
        );

        register(
                "forget_waypoint",
                action ->
                        DAI_WaypointLogic.forgetWaypoint(
                                action.action()
                        )
        );

        register(
                "forget_failed_waypoint",
                action ->
                        DAI_WaypointLogic.forgetFailedWaypoint(
                                action.action()
                        )
        );
    }

    private static void registerSpatialActions() {

        register(
                "select_waypoint_offset",
                DAI_SpatialLogic::selectWaypointOffset
        );

        register(
                "remember_offset_waypoint",
                DAI_SpatialLogic::rememberOffsetWaypoint
        );

        register(
                "remember_surface_offset_waypoint",
                DAI_SpatialLogic::rememberSurfaceOffsetWaypoint
        );

        register(
                "scan_adjacent_blocks",
                DAI_SpatialLogic::scanAdjacentBlocks
        );

        register(
                "select_adjacent_block",
                DAI_SpatialLogic::selectAdjacentBlock
        );

        register(
                "spatial_clear",
                DAI_SpatialLogic::clearSpatialState
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

        register(
                "wait_for_screen_profile",
                DAI_ScreenProfileLogic::waitForProfile
        );

        register(
                "close_container",
                DAI_ContainerLogic::closeContainer
        );

        register(
                "container_click_slot",
                DAI_ContainerSlotLogic::clickSlot
        );

        register(
                "container_shift_click_slot",
                DAI_ContainerSlotLogic::shiftClickSlot
        );

        register(
                "container_insert_item",
                DAI_ContainerSlotLogic::insertItem
        );

        register(
                "container_take_slot",
                DAI_ContainerSlotLogic::takeSlot
        );

        register(
                "wait_for_container_slot",
                DAI_ContainerSlotLogic::waitForSlot
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