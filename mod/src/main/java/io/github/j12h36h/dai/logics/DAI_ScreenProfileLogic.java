package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.menus.DAI_ScreenState;
import io.github.j12h36h.dai.menus.DAI_ScreenProfileManager;

import java.util.List;

public final class DAI_ScreenProfileLogic {

    private static final int DEFAULT_WAIT_TICKS =
            100;

    private static final int BARRIER_POLL_TICKS =
            1;

    private DAI_ScreenProfileLogic() {
        // Utility class.
    }

    /*
     * ------------------------------------------------------------
     * WAIT FOR PROFILE
     * ------------------------------------------------------------
     */

    /**
     * Waits until the currently open screen/menu matches the requested
     * datapack-defined screen profile.
     *
     * Expected JSON:
     *
     * {
     *   "type": "wait_for_screen_profile",
     *   "action": "decisions_and_impulses:minecraft/crafting_table",
     *   "ticks": 100
     * }
     */
    public static void waitForProfile(
            DAI_ActionDefinition action
    ) {

        if (action == null) {

            releaseBarrier();

            fail(
                    "wait_for_screen_profile requires an action."
            );

            return;
        }

        String profileId =
                normalizeProfileId(
                        action.action()
                );

        if (profileId.isBlank()) {

            releaseBarrier();

            fail(
                    "wait_for_screen_profile requires a profile id in 'action'."
            );

            return;
        }

        if (
                !DAI_ScreenProfileManager.contains(
                        profileId
                )
        ) {

            releaseBarrier();

            fail(
                    "Unknown screen profile '"
                            + profileId
                            + "'."
            );

            return;
        }

        /*
         * Success requires an actual open screen.
         *
         * The player's InventoryMenu may exist while no GUI is visible,
         * so checking the menu alone would produce false positives.
         */
        if (
                DAI_ScreenState.hasOpenContainerScreen()
                        && DAI_ScreenProfileManager.matchesProfile(
                        profileId
                )
        ) {

            releaseBarrier();

            DAI_ActionStatus.set(
                    DAI_ActionResult.SUCCESS
            );

            DAI_Core.LOGGER.info(
                    "<DAI>: Screen profile '{}' matched screen='{}' menu='{}' containerId={} slots={}.",
                    profileId,
                    DAI_ScreenState.screenSimpleClass(),
                    DAI_ScreenState.menuSimpleClass(),
                    DAI_ScreenState.containerId(),
                    DAI_ScreenState.slotCount()
            );

            return;
        }

        int remainingTicks =
                action.ticks() > 0
                        ? action.ticks()
                        : DEFAULT_WAIT_TICKS;

        remainingTicks--;

        if (remainingTicks <= 0) {

            String screen =
                    DAI_ScreenState.screenSimpleClass();

            String menu =
                    DAI_ScreenState.menuSimpleClass();

            int slots =
                    DAI_ScreenState.slotCount();

            releaseBarrier();

            DAI_ActionStatus.set(
                    DAI_ActionResult.TIMED_OUT
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Timed out waiting for screen profile '{}'. Current screen='{}' menu='{}' slots={}.",
                    profileId,
                    screen,
                    menu,
                    slots
            );

            return;
        }

        /*
         * Hard queue synchronization.
         *
         * Nothing behind this wait may execute until the requested UI is
         * actually present or the wait reaches a terminal result.
         */
        DAI_ActionQueue.holdBarrier(
                createPollAction(
                        profileId,
                        remainingTicks
                ),
                BARRIER_POLL_TICKS
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );
    }

    /*
     * ------------------------------------------------------------
     * IMMEDIATE CHECK
     * ------------------------------------------------------------
     */

    public static boolean matches(
            String profileId
    ) {

        String normalized =
                normalizeProfileId(
                        profileId
                );

        if (
                normalized.isBlank()
                        || !DAI_ScreenState.hasOpenContainerScreen()
        ) {
            return false;
        }

        return DAI_ScreenProfileManager.matchesProfile(
                normalized
        );
    }

    /*
     * ------------------------------------------------------------
     * POLL ACTION
     * ------------------------------------------------------------
     */

    private static DAI_ActionDefinition createPollAction(
            String profileId,
            int remainingTicks
    ) {

        return new DAI_ActionDefinition(
                "wait_for_screen_profile",
                profileId,
                List.of(),
                List.of(),
                "",
                "",
                0.0F,
                0.0F,
                "",
                Math.max(
                        0,
                        remainingTicks
                ),
                0,
                false,
                0.0D
        );
    }

    /*
     * ------------------------------------------------------------
     * BARRIER
     * ------------------------------------------------------------
     */

    private static void releaseBarrier() {

        if (
                DAI_ActionQueue.barrierIs(
                        "wait_for_screen_profile"
                )
        ) {

            DAI_ActionQueue.releaseBarrier();
        }
    }

    /*
     * ------------------------------------------------------------
     * PROFILE ID
     * ------------------------------------------------------------
     */

    private static String normalizeProfileId(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {
            return "";
        }

        String normalized =
                value.trim()
                        .toLowerCase();

        if (
                !normalized.contains(
                        ":"
                )
        ) {

            normalized =
                    "decisions_and_impulses:"
                            + normalized;
        }

        return normalized;
    }

    /*
     * ------------------------------------------------------------
     * FAILURE
     * ------------------------------------------------------------
     */

    private static void fail(
            String reason
    ) {

        DAI_ActionStatus.set(
                DAI_ActionResult.FAILURE
        );

        DAI_Core.LOGGER.warn(
                "<DAI>: {}",
                reason
        );
    }
}