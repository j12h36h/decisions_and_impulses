package io.github.j12h36h.dai.logic;

import io.github.j12h36h.dai.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.action.DAI_ActionQueue;
import io.github.j12h36h.dai.action.DAI_ActionResolver;
import io.github.j12h36h.dai.action.DAI_ActionResult;
import io.github.j12h36h.dai.action.DAI_ActionStatus;
import io.github.j12h36h.dai.controller.DAI_ApproachController;
import io.github.j12h36h.dai.controller.DAI_BreakController;
import io.github.j12h36h.dai.controller.DAI_BuildController;
import io.github.j12h36h.dai.controller.DAI_CombatController;
import io.github.j12h36h.dai.controller.DAI_ExploreController;
import io.github.j12h36h.dai.controller.DAI_InteractionController;
import io.github.j12h36h.dai.controller.DAI_ItemController;
import io.github.j12h36h.dai.controller.DAI_LookController;
import io.github.j12h36h.dai.controller.DAI_MoveController;
import io.github.j12h36h.dai.controller.DAI_PathController;
import io.github.j12h36h.dai.controller.DAI_UseController;
import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.input.DAI_InputState;
import io.github.j12h36h.dai.system.DAI_TargetState;

import java.util.List;

public final class DAI_AutomationLogic {

    private static final String VANILLA_GAMEPLAY_LOOP =
            "decisions_and_impulses:gameplay_loop";

    private static boolean active;
    private static int generation;

    private DAI_AutomationLogic() {
        // Utility class.
    }

    /**
     * Starts the vanilla-gameplay automation as one owned lifecycle.
     *
     * Repeated Play requests are intentionally idempotent: an already active
     * gameplay session is left alone instead of enqueueing another copy of the
     * self-replicating gameplay loop.
     */
    public static void startVanillaGameplay(
            DAI_ActionDefinition action
    ) {

        if (active) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.SUCCESS
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Vanilla gameplay is already active (generation={}); duplicate start ignored.",
                    generation
            );

            return;
        }

        /*
         * Begin from a deterministic state. No stale navigation, breaking,
         * target, movement, or queued action may survive into a new gameplay
         * lifecycle.
         */
        stopRuntimeState(
                false
        );

        List<DAI_ActionDefinition> resolved =
                DAI_ActionResolver.resolve(
                        VANILLA_GAMEPLAY_LOOP
                );

        if (resolved.isEmpty()) {

            active =
                    false;

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.error(
                    "<DAI>: Could not start vanilla gameplay because '{}' resolved to no executable actions.",
                    VANILLA_GAMEPLAY_LOOP
            );

            return;
        }

        generation =
                nextGeneration(
                        generation
                );

        active =
                true;

        DAI_ActionQueue.enqueueFirstAll(
                resolved
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Vanilla gameplay started (generation={}, queuedActions={}).",
                generation,
                resolved.size()
        );
    }

    public static void stop(
            DAI_ActionDefinition action
    ) {

        boolean wasActive =
                active;

        active =
                false;

        generation =
                nextGeneration(
                        generation
                );

        stopRuntimeState(
                true
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        if (wasActive) {

            DAI_Core.LOGGER.info(
                    "<DAI>: Automation stopped (generation={}).",
                    generation
            );

        } else {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Automation stop requested while no gameplay lifecycle was active."
            );
        }
    }

    /**
     * Invalidates automation ownership when the Minecraft gameplay session
     * itself ends.
     *
     * DAI_ClientRuntime performs the actual controller/input/session cleanup.
     * This method only closes the automation lifecycle so reconnecting or
     * entering another world cannot inherit active=true from the old world
     * and incorrectly reject the next Play request as a duplicate.
     */
    public static void resetSessionLifecycle() {

        boolean wasActive =
                active;

        active =
                false;

        generation =
                nextGeneration(
                        generation
                );

        if (wasActive) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Automation lifecycle invalidated by client session reset (generation={}).",
                    generation
            );
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static int generation() {
        return generation;
    }

    /**
     * Returns true only when the supplied token still belongs to the currently
     * active gameplay lifecycle. Future self-requeue logic can use this to
     * reject stale gameplay-loop generations after Stop/Restart.
     */
    public static boolean ownsGeneration(
            int expectedGeneration
    ) {

        return active
                && expectedGeneration > 0
                && expectedGeneration == generation;
    }

    private static void stopRuntimeState(
            boolean clearQueue
    ) {

        /*
         * Stop/reset every controller that can retain autonomous work.
         *
         * Automation Stop must be a hard ownership boundary: after this
         * method returns, no controller from the previous gameplay session
         * may still be able to move, rotate, attack, use, interact, build,
         * break, explore, or follow a path.
         */
        DAI_ExploreController.reset();
        DAI_ApproachController.reset();
        DAI_PathController.reset();

        DAI_CombatController.reset();
        DAI_UseController.reset();
        DAI_ItemController.reset();
        DAI_InteractionController.reset();
        DAI_BreakController.reset();
        DAI_BuildController.reset();

        /*
         * Item collection is persistent asynchronous logic rather than a
         * controller class, but it owns movement/path state across ticks and
         * must obey the same lifecycle boundary.
         */
        DAI_ItemCollectionLogic.reset();

        /*
         * A new gameplay lifecycle also requires a clean queue. Keep the
         * parameter for call-site intent/readability, but both start and stop
         * deliberately discard stale queued work.
         */
        DAI_ActionQueue.clear();

        /*
         * Do not allow last_action_success / last_action_failure from the
         * previous automation session to leak across this hard lifecycle
         * boundary.
         */
        DAI_ActionStatus.reset();

        DAI_MoveController.reset();

        /*
         * Synchronize the stored autonomous look request to the player's
         * current camera so a stale yaw/pitch cannot be replayed when managed
         * input is enabled again.
         */
        DAI_LookController.reset();

        DAI_InputState
                .movement()
                .clear();

        /*
         * Lifecycle teardown clears raw target state directly. Calling the
         * action-facing DAI_TargetLogic.clear() here would report SUCCESS and
         * overwrite the neutral ActionStatus established above.
         */
        DAI_TargetState.clear();

        DAI_Core.LOGGER.debug(
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
}