package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.controller.DAI_ApproachController;
import io.github.j12h36h.dai.client.menus.system.DAI_FailedTargetMemory;
import io.github.j12h36h.dai.client.menus.system.DAI_TargetState;
import io.github.j12h36h.dai.client.menus.system.DAI_WaypointMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class DAI_WaypointLogic {

    private DAI_WaypointLogic() {
        // Utility class.
    }

    /**
     * Remembers the player's current position as a named waypoint.
     *
     * This does not modify the active temporary target.
     */
    public static void rememberPlayerPosition(
            String name
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            fail(
                    "Cannot remember waypoint '{}' because no player/world is available.",
                    name
            );

            return;
        }

        DAI_WaypointMemory.remember(
                name,
                minecraft.level.dimension(),
                minecraft.player.blockPosition()
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );
    }

    /**
     * Remembers the currently selected block as a named waypoint.
     *
     * This allows recognition, exploration, or other target-selection
     * systems to discover a position first and then promote it into
     * persistent world memory.
     */
    public static void rememberSelectedBlock(
            String name
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.level == null) {

            fail(
                    "Cannot remember waypoint '{}' because no world is available.",
                    name
            );

            return;
        }

        BlockPos selected =
                DAI_TargetState.selectedBlock();

        if (selected == null) {

            fail(
                    "Cannot remember waypoint '{}' because no block target is selected.",
                    name
            );

            return;
        }

        DAI_WaypointMemory.remember(
                name,
                minecraft.level.dimension(),
                selected
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );
    }

    /**
     * Copies a persistent waypoint into the existing temporary
     * block-target system.
     *
     * Once selected, the normal approach/path/look/interact/mining
     * systems can operate on the position without knowing anything
     * about waypoint memory.
     */
    public static void selectWaypoint(
            String name
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.level == null) {

            fail(
                    "Cannot select waypoint '{}' because no world is available.",
                    name
            );

            return;
        }

        DAI_WaypointMemory.DAI_Waypoint waypoint =
                DAI_WaypointMemory.get(
                        name
                );

        if (waypoint == null) {

            fail(
                    "Cannot select unknown waypoint '{}'.",
                    name
            );

            return;
        }

        if (
                !waypoint.dimension()
                        .equals(
                                minecraft.level.dimension()
                        )
        ) {

            fail(
                    "Cannot select waypoint '{}' because it belongs to dimension '{}' instead of '{}'.",
                    name,
                    waypoint.dimension(),
                    minecraft.level.dimension()
            );

            return;
        }

        /*
         * A failed target memory entry means the approach subsystem has
         * already proved that this exact block cannot currently be used from
         * the player's reachable interaction space. Persistent waypoints must
         * respect that result; otherwise a crafting table/furnace waypoint can
         * override the blacklist forever and create a one-target retry loop.
         */
        if (
                DAI_FailedTargetMemory.contains(
                        waypoint.position()
                )
        ) {

            DAI_TargetState.clearBlock();

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.debug(
                    "<DAI>: Rejected waypoint '{}' at {} because the target is temporarily blacklisted.",
                    waypoint.name(),
                    waypoint.position()
            );

            return;
        }

        /*
         * A waypoint selection is a new authoritative target. Drop any
         * completed/active approach ownership from the previous block first
         * so camera/interact logic immediately resolves to this waypoint.
         */
        DAI_ApproachController.discardTargetOwnership();

        DAI_TargetState.selectBlock(
                waypoint.position()
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.debug(
                "<DAI>: Selected waypoint '{}' as temporary block target at {}.",
                waypoint.name(),
                waypoint.position()
        );
    }

    /**
     * Forgets a waypoint only when its exact block position is currently in
     * failed-target memory. This is intentionally idempotent so datapack gates
     * can invoke it every cycle without disturbing a healthy workstation.
     */
    public static void forgetFailedWaypoint(
            String name
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.level == null) {

            fail(
                    "Cannot validate failed waypoint '{}' because no world is available.",
                    name
            );

            return;
        }

        DAI_WaypointMemory.DAI_Waypoint waypoint =
                DAI_WaypointMemory.getInDimension(
                        name,
                        minecraft.level.dimension()
                );

        if (waypoint == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.SUCCESS
            );

            return;
        }

        if (
                !DAI_FailedTargetMemory.contains(
                        waypoint.position()
                )
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.SUCCESS
            );

            return;
        }

        DAI_ApproachController.discardTargetOwnership();
        DAI_TargetState.clearBlock();

        DAI_WaypointMemory.forget(
                name
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Forgot failed waypoint '{}' at {}; a fresh workstation/location may now be selected.",
                name,
                waypoint.position()
        );
    }

    /**
     * Removes a named waypoint.
     */
    public static void forgetWaypoint(
            String name
    ) {

        boolean removed =
                DAI_WaypointMemory.forget(
                        name
                );

        if (!removed) {

            fail(
                    "Cannot forget unknown waypoint '{}'.",
                    name
            );

            return;
        }

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );
    }

    /**
     * Returns true when the waypoint exists in any dimension.
     */
    public static boolean known(
            String name
    ) {

        return DAI_WaypointMemory.contains(
                name
        );
    }

    /**
     * Returns true only when the waypoint exists in the player's
     * current dimension.
     */
    public static boolean knownInCurrentDimension(
            String name
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.level == null) {
            return false;
        }

        return DAI_WaypointMemory.containsInDimension(
                name,
                minecraft.level.dimension()
        );
    }

    /**
     * Returns the player's straight-line distance to a waypoint.
     *
     * NaN means the waypoint cannot currently be reached directly,
     * either because it is unknown, the player/world is unavailable,
     * or it belongs to another dimension.
     */
    public static double distanceTo(
            String name
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {
            return Double.NaN;
        }

        DAI_WaypointMemory.DAI_Waypoint waypoint =
                DAI_WaypointMemory.getInDimension(
                        name,
                        minecraft.level.dimension()
                );

        if (waypoint == null) {
            return Double.NaN;
        }

        BlockPos playerPosition =
                minecraft.player.blockPosition();

        return Math.sqrt(
                playerPosition.distSqr(
                        waypoint.position()
                )
        );
    }

    /**
     * Tests whether the player is within the supplied radius
     * of a named waypoint.
     */
    public static boolean atWaypoint(
            String name,
            double radius
    ) {

        if (
                !Double.isFinite(
                        radius
                )
                        || radius < 0.0D
        ) {
            return false;
        }

        double distance =
                distanceTo(
                        name
                );

        return Double.isFinite(
                distance
        )
                && distance <= radius;
    }

    private static void fail(
            String message,
            Object... arguments
    ) {

        DAI_ActionStatus.set(
                DAI_ActionResult.FAILURE
        );

        DAI_Core.debug(
                "<DAI>: " + message,
                arguments
        );
    }
}