package io.github.j12h36h.dai.menus.system;

import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.condition.DAI_ConditionMemory;
import io.github.j12h36h.dai.logics.controller.DAI_ApproachController;
import io.github.j12h36h.dai.logics.controller.DAI_BreakController;
import io.github.j12h36h.dai.logics.controller.DAI_BuildController;
import io.github.j12h36h.dai.logics.controller.DAI_CombatController;
import io.github.j12h36h.dai.logics.controller.DAI_ExploreController;
import io.github.j12h36h.dai.logics.controller.DAI_InteractionController;
import io.github.j12h36h.dai.logics.controller.DAI_ItemController;
import io.github.j12h36h.dai.logics.controller.DAI_LookController;
import io.github.j12h36h.dai.logics.controller.DAI_MoveController;
import io.github.j12h36h.dai.logics.controller.DAI_PathController;
import io.github.j12h36h.dai.logics.controller.DAI_UseController;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.input.DAI_InputState;
import io.github.j12h36h.dai.logics.DAI_AutomationLogic;
import io.github.j12h36h.dai.logics.DAI_ItemCollectionLogic;
import io.github.j12h36h.dai.logics.DAI_ExactPlacementLogic;
import io.github.j12h36h.dai.logics.navigation.DAI_ExplorationMemory;
import io.github.j12h36h.dai.menus.system.DAI_FailedTargetMemory;
import io.github.j12h36h.dai.logics.input.DAI_KeyboardInput;
import io.github.j12h36h.dai.menus.DAI_ScreenManager;
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

        initializationPending =
                true;

        DAI_Core.LOGGER.debug(
                "<DAI>: Client runtime initialization requested."
        );
    }

    /**
     * Completes pending initialization at a safe point in the client
     * tick and advances persistent navigation controllers.
     */
    public static void tick() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft == null
                        || minecraft.player == null
                        || minecraft.level == null
        ) {
            return;
        }

        if (initializationPending) {

            initialize(
                    minecraft
            );

            initializationPending =
                    false;
        }
        /*
         * Persistent controller ticking is centralized in DAI_ClientTick.
         *
         * ClientRuntime owns session initialization/reset and mouse capture
         * only. Keeping controller advancement in one tick coordinator makes
         * execution order explicit and prevents controllers from being
         * advanced from multiple lifecycle layers.
         */

        updateMouseCapture(
                minecraft
        );
    }

    private static void initialize(
            Minecraft minecraft
    ) {

        resetSession();

        /*
         * Begin managed look input from the player's current rotation
         * rather than from the reset zero rotation.
         */
        DAI_InputState
                .look()
                .setRotation(
                        minecraft.player.getYRot(),
                        minecraft.player.getXRot()
                );

        if (
                !(minecraft.player.input
                        instanceof DAI_KeyboardInput)
        ) {

            minecraft.player.input =
                    new DAI_KeyboardInput(
                            minecraft.options
                    );

            DAI_Core.LOGGER.info(
                    "<DAI>: Movement input installed."
            );
        }

        /*
         * Gameplay always begins with the mouse captured.
         *
         * The dedicated DAI menu key is responsible for opening the
         * DAI menu and releasing the cursor when needed.
         */
        DAI_InputState.setCursorReleased(
                false
        );

        updateMouseCapture(
                minecraft
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Client runtime initialized."
        );
    }

    /**
     * Clears all transient client runtime state.
     *
     * This method should be used whenever the current gameplay session
     * begins or ends so queued actions, controller operations, targets,
     * inputs, and condition history cannot leak between sessions.
     */
    public static void resetSession() {

        /*
         * A Minecraft world/session boundary is also an automation ownership
         * boundary. Invalidate the Play lifecycle before clearing its runtime
         * state so the next world can start a fresh gameplay generation.
         */
        DAI_AutomationLogic.resetSessionLifecycle();

        initializationPending =
                false;

        DAI_InputState.setManagedOverride(
                false
        );

        DAI_ActionQueue.clear();

        DAI_CombatController.reset();
        DAI_UseController.reset();
        DAI_ItemController.reset();
        DAI_InteractionController.reset();
        DAI_BreakController.reset();
        DAI_BuildController.reset();

        /*
         * Item collection owns persistent path/movement state across queue
         * ticks even though it is implemented as logic rather than a
         * controller. A world/session reset must cancel it as well.
         */
        DAI_ItemCollectionLogic.reset();
        DAI_ExactPlacementLogic.reset();

        /*
         * Exploration owns resource-search state and may currently own
         * an active path, so reset it before the path controller.
         */
        DAI_ExploreController.reset();
        DAI_PathController.reset();

        DAI_ApproachController.reset();
        DAI_MoveController.reset();

        /*
         * Reset the shared status after controllers. Controller reset
         * methods may report CANCELLED when they terminate active work,
         * but a full session reset should end with a neutral status.
         */
        DAI_ActionStatus.reset();

        DAI_InputState
                .movement()
                .clear();

        /*
         * Preserve the player's actual camera as the neutral DAI look state.
         * Resetting to absolute 0/0 could cause a visible snap when managed
         * look input becomes active again in the next session.
         */
        DAI_LookController.reset();

        DAI_InputState.setCursorReleased(
                false
        );

        DAI_TargetState.clear();

        /*
         * These memories contain world-coordinate history. They must never
         * leak across disconnects or world changes, where identical
         * coordinates refer to unrelated terrain/targets.
         *
         * Waypoints intentionally survive dimension changes because they
         * retain their own dimension identity, but they are cleared at the
         * world/session boundary with the rest of the spatial memory.
         */
        DAI_ExplorationMemory.clear();
        DAI_FailedTargetMemory.clear();
        DAI_WaypointMemory.clear();
        DAI_SpatialState.clear();

        DAI_ConditionMemory.clear();
        DAI_ScreenManager.clear();

        DAI_Core.LOGGER.debug(
                "<DAI>: Client runtime session reset."
        );
    }

    /**
     * Synchronizes Minecraft's actual mouse-grab state with the cursor
     * state requested by DAI.
     *
     * Input override and cursor visibility are deliberately independent.
     */
    public static void updateMouseCapture() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft == null) {
            return;
        }

        updateMouseCapture(
                minecraft
        );
    }

    private static void updateMouseCapture(
            Minecraft minecraft
    ) {

        boolean cursorReleased =
                DAI_InputState.isCursorReleased();

        boolean grabbed =
                minecraft.mouseHandler
                        .isMouseGrabbed();

        if (
                cursorReleased
                        && grabbed
        ) {

            minecraft.mouseHandler
                    .releaseMouse();

            DAI_Core.LOGGER.debug(
                    "<DAI>: Mouse cursor released."
            );

            return;
        }

        if (
                !cursorReleased
                        && !grabbed
                        && minecraft.gui.screen() == null
        ) {

            minecraft.mouseHandler
                    .grabMouse();

            DAI_Core.LOGGER.debug(
                    "<DAI>: Mouse cursor grabbed."
            );
        }
    }
}