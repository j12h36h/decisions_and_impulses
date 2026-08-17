package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.client.config.DAI_PlayerControls;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionResolver;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.client.logics.controller.*;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.core.DAI_RuntimeTelemetry;
import io.github.j12h36h.dai.client.logics.input.DAI_InputState;
import io.github.j12h36h.dai.client.menus.system.DAI_TargetState;
import io.github.j12h36h.dai.client.menus.system.DAI_SpatialState;

import java.util.List;
import java.util.Locale;

public final class DAI_AutomationLogic {

    private static final String SURVIVAL_LOOP =
            "decisions_and_impulses:gameplay_loop";

    private static final String SPEEDRUN_LOOP =
            "decisions_and_impulses:speedrun_loop";

    private static final String CREATIVE_LOOP =
            "decisions_and_impulses:creative_builder_loop";

    private static final String ADVENTURE_LOOP =
            "decisions_and_impulses:adventure_loop";

    private static final String SURVIVAL_CYCLE =
            "decisions_and_impulses:fp_cycle";

    private static final String SPEEDRUN_CYCLE =
            "decisions_and_impulses:sr_cycle";

    private static final String CREATIVE_CYCLE =
            "decisions_and_impulses:cb_cycle";

    private static final String ADVENTURE_CYCLE =
            "decisions_and_impulses:ad_cycle";

    /*
     * Only reseed after a full second of genuine runtime idleness.
     *
     * This is deliberately long enough to ignore one-tick request-style
     * controllers while still recovering quickly from a missing datapack
     * continuation.
     */
    private static final int IDLE_RESEED_TICKS =
            20;

    /*
     * A menu action is an explicit user override. DAI dispatches the menu
     * action immediately, but the automation watchdog must not reseed the
     * cancelled autonomous queue while the user is still navigating toward
     * another menu command (especially Stop).
     *
     * Five seconds covers normal menu navigation without permanently
     * suspending an automation after an incidental manual command.
     */
    private static final int MENU_INTERRUPT_HOLD_TICKS =
            100;

    private static Mode mode =
            Mode.NONE;

    private static boolean active;
    private static int generation;
    private static int idleTicks;
    private static int menuInterruptHoldTicks;

    private DAI_AutomationLogic() {
        // Utility class.
    }

    public static void startVanillaGameplay(
            DAI_ActionDefinition action
    ) {

        startAutomation(
                Mode.SURVIVAL,
                SURVIVAL_LOOP
        );
    }

    public static void startSpeedrun(
            DAI_ActionDefinition action
    ) {

        startAutomation(
                Mode.SPEEDRUN,
                SPEEDRUN_LOOP
        );
    }

    public static void startCreativeBuilder(
            DAI_ActionDefinition action
    ) {

        startAutomation(
                Mode.CREATIVE,
                CREATIVE_LOOP
        );
    }

    public static void startAdventure(
            DAI_ActionDefinition action
    ) {

        startAutomation(
                Mode.ADVENTURE,
                ADVENTURE_LOOP
        );
    }

    private static void startAutomation(
            Mode requestedMode,
            String rootAction
    ) {

        if (!DAI_PlayerControls.automationEnabled()) {
            DAI_ActionStatus.set(
                    DAI_ActionResult.CANCELLED
            );
            DAI_Core.LOGGER.info(
                    "<DAI>: {} automation start was blocked by player/experience controls.",
                    requestedMode.displayName()
            );
            return;
        }

        if (
                active
                        && mode == requestedMode
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.SUCCESS
            );

            DAI_Core.debug(
                    "<DAI>: {} automation is already active (generation={}); duplicate start ignored.",
                    requestedMode.displayName(),
                    generation
            );

            return;
        }

        if (active) {

            DAI_RuntimeTelemetry.stop(
                    "automation_switch"
            );
        }

        /*
         * Switching profiles is intentionally a hard ownership boundary.
         * Speedrun may never inherit Survival home/mine/table waypoints, and
         * Survival may never inherit an in-flight Speedrun portal route.
         */
        stopRuntimeState(
                false
        );

        List<DAI_ActionDefinition> resolved =
                DAI_ActionResolver.resolve(
                        rootAction
                );

        if (resolved.isEmpty()) {

            active =
                    false;

            mode =
                    Mode.NONE;

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.error(
                    "<DAI>: Could not start {} automation because '{}' resolved to no executable actions.",
                    requestedMode.displayName(),
                    rootAction
            );

            return;
        }

        generation =
                nextGeneration(
                        generation
                );

        mode =
                requestedMode;

        active =
                true;

        idleTicks =
                0;

        menuInterruptHoldTicks =
                0;

        DAI_RuntimeTelemetry.start(
                generation
        );

        DAI_ActionQueue.enqueueFirstAll(
                resolved
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.LOGGER.info(
                "<DAI>: {} automation started (generation={}, queuedActions={}).",
                requestedMode.displayName(),
                generation,
                resolved.size()
        );
    }

    public static void continueAutomation(
            DAI_ActionDefinition action
    ) {

        if (!active) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.SUCCESS
            );

            return;
        }

        String cycle =
                currentCycle();

        if (cycle.isBlank()) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            return;
        }

        DAI_ActionQueue.enqueueDeferredReference(
                cycle
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );
    }

    public static void stop(
            DAI_ActionDefinition action
    ) {

        boolean wasActive =
                active;

        Mode previousMode =
                mode;

        active =
                false;

        mode =
                Mode.NONE;

        idleTicks =
                0;

        menuInterruptHoldTicks =
                0;

        generation =
                nextGeneration(
                        generation
                );

        stopRuntimeState(
                true
        );

        DAI_RuntimeTelemetry.stop(
                "automation_stop"
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        if (wasActive) {

            DAI_Core.LOGGER.info(
                    "<DAI>: {} automation stopped (generation={}).",
                    previousMode.displayName(),
                    generation
            );

        } else {

            DAI_Core.debug(
                    "<DAI>: Automation stop requested while no lifecycle was active."
            );
        }
    }

    /**
     * Immediately cancels in-flight autonomous ownership for a user-selected
     * menu command without erasing profile waypoints. Automation start/stop
     * actions may subsequently perform the full lifecycle reset themselves.
     */
    public static void interruptWorkForMenuAction() {

        idleTicks = 0;

        menuInterruptHoldTicks =
                MENU_INTERRUPT_HOLD_TICKS;

        DAI_ExploreController.reset();
        DAI_ApproachController.reset();
        DAI_PathController.reset();
        DAI_ScaffoldController.reset();
        DAI_CombatController.reset();
        DAI_UseController.reset();
        DAI_ItemController.reset();
        DAI_InteractionController.reset();
        DAI_BreakController.reset();
        DAI_BuildController.reset();
        DAI_ItemCollectionLogic.reset();
        DAI_ExactPlacementLogic.reset();
        DAI_CreativeFlightController.reset();
        DAI_CreativeBuildController.reset();
        DAI_CreativeInputState.reset();
        DAI_MoveController.reset();
        DAI_LookController.reset();

        DAI_InputState.movement().clear();
        DAI_TargetState.clear();
        DAI_ActionStatus.reset();

        DAI_Core.LOGGER.info(
                "<DAI>: Menu action preempted active runtime work immediately."
        );
    }

    public static void resetSessionLifecycle() {

        boolean wasActive =
                active;

        Mode previousMode =
                mode;

        active =
                false;

        mode =
                Mode.NONE;

        idleTicks =
                0;

        menuInterruptHoldTicks =
                0;

        generation =
                nextGeneration(
                        generation
                );

        DAI_RuntimeTelemetry.stop(
                "session_reset"
        );

        if (wasActive) {

            DAI_Core.debug(
                    "<DAI>: {} automation lifecycle invalidated by client session reset (generation={}).",
                    previousMode.displayName(),
                    generation
            );
        }
    }

    public static void tickWatchdog() {

        if (active && !DAI_PlayerControls.automationEnabled()) {
            stop(null);
            DAI_Core.LOGGER.info(
                    "<DAI>: Active automation stopped because player/experience controls disabled automation."
            );
            return;
        }

        if (!active) {

            idleTicks =
                    0;

            menuInterruptHoldTicks =
                    0;

            return;
        }

        enforcePlayerControls();

        if (menuInterruptHoldTicks > 0) {

            menuInterruptHoldTicks--;

            idleTicks =
                    0;

            return;
        }

        boolean runtimeBusy =
                !DAI_ActionQueue.isEmpty()
                        || DAI_PathController.isActive()
                        || DAI_ExploreController.isActive()
                        || DAI_ApproachController.isActive()
                        || DAI_ScaffoldController.isActive()
                        || DAI_BreakController.isActive()
                        || DAI_ItemCollectionLogic.isActive()
                        || DAI_UseController.isActive()
                        || DAI_MoveController.isActive()
                        || DAI_CombatController.isCombatActive()
                        || DAI_CreativeFlightController.isActive()
                        || DAI_CreativeBuildController.isActive();

        if (runtimeBusy) {

            idleTicks =
                    0;

            return;
        }

        idleTicks++;

        if (idleTicks < IDLE_RESEED_TICKS) {
            return;
        }

        idleTicks =
                0;

        String cycle =
                currentCycle();

        if (cycle.isBlank()) {
            return;
        }

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_ActionQueue.enqueueDeferredReference(
                cycle
        );

        DAI_Core.LOGGER.warn(
                "<DAI>: Active {} automation became fully idle; re-seeded fail-safe cycle '{}'.",
                mode.displayName(),
                cycle
        );
    }

    private static void enforcePlayerControls() {
        if (!DAI_PlayerControls.automationMovement()) {
            DAI_ExploreController.reset();
            DAI_ApproachController.reset();
            DAI_PathController.reset();
            DAI_ScaffoldController.reset();
            DAI_CreativeFlightController.reset();
            DAI_MoveController.reset();
            DAI_LookController.reset();
            DAI_InputState.movement().clear();
        }

        if (!DAI_PlayerControls.automationCombat()) {
            DAI_CombatController.reset();
        }

        if (!DAI_PlayerControls.automationWorldEditing()) {
            DAI_BreakController.reset();
            DAI_BuildController.reset();
            DAI_ScaffoldController.reset();
            DAI_CreativeBuildController.reset();
            DAI_ExactPlacementLogic.reset();
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static int generation() {
        return generation;
    }

    public static String modeName() {

        return mode.name()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    public static boolean ownsGeneration(
            int expectedGeneration
    ) {

        return active
                && expectedGeneration > 0
                && expectedGeneration == generation;
    }

    private static String currentCycle() {

        return switch (mode) {
            case SURVIVAL -> SURVIVAL_CYCLE;
            case SPEEDRUN -> SPEEDRUN_CYCLE;
            case CREATIVE -> CREATIVE_CYCLE;
            case ADVENTURE -> ADVENTURE_CYCLE;
            case NONE -> "";
        };
    }

    private static void stopRuntimeState(
            boolean clearQueue
    ) {

        DAI_ExploreController.reset();
        DAI_ApproachController.reset();
        DAI_PathController.reset();
        DAI_ScaffoldController.reset();

        DAI_CombatController.reset();
        DAI_UseController.reset();
        DAI_ItemController.reset();
        DAI_InteractionController.reset();
        DAI_BreakController.reset();
        DAI_BuildController.reset();

        DAI_ItemCollectionLogic.reset();
        DAI_ExactPlacementLogic.reset();
        DAI_CreativeFlightController.reset();
        DAI_CreativeBuildController.reset();
        DAI_CreativeInputState.reset();

        DAI_ActionQueue.clear();
        DAI_ActionStatus.reset();

        DAI_MoveController.reset();
        DAI_LookController.reset();

        DAI_InputState
                .movement()
                .clear();

        DAI_TargetState.clear();
        DAI_SpatialState.clear();

        DAI_Core.debug(
                "<DAI>: Automation runtime state cleared (requestedClearQueue={}).",
                clearQueue
        );
    }

    private static int nextGeneration(
            int current
    ) {

        return current == Integer.MAX_VALUE
                ? 1
                : current + 1;
    }

    private enum Mode {

        NONE("No"),
        SURVIVAL("Survival"),
        SPEEDRUN("Speedrun"),
        CREATIVE("Creative Builder"),
        ADVENTURE("Adventure");

        private final String displayName;

        Mode(
                String displayName
        ) {
            this.displayName =
                    displayName;
        }

        private String displayName() {
            return displayName;
        }
    }
}
