package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.client.logics.controller.DAI_CreativeFlightController;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.input.DAI_InputState;
import io.github.j12h36h.dai.client.menus.system.DAI_WaypointMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class DAI_CreativeFlightLogic {

    private static final int BARRIER_POLL_TICKS = 1;

    private DAI_CreativeFlightLogic() {
        // Utility class.
    }

    public static void setFlight(DAI_ActionDefinition action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.getAbilities().mayfly) {
            DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
            return;
        }
        minecraft.player.getAbilities().flying = action != null && action.state();
        minecraft.player.onUpdateAbilities();
        if (!minecraft.player.getAbilities().flying) {
            DAI_InputState.movement().clear();
        }
        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
    }

    public static void flyTo(DAI_ActionDefinition action) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 destination = resolveDestination(minecraft, action);
        if (destination == null) {
            DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
            return;
        }

        int generation = DAI_CreativeFlightController.start(
                destination,
                action.ticks(),
                action.value()
        );

        if (!DAI_CreativeFlightController.isActive()) return;

        boolean promoted = DAI_ActionQueue.promoteHeadToBarrier(
                "wait_for_creative_flight",
                generation,
                BARRIER_POLL_TICKS
        );

        if (!promoted) {
            DAI_CreativeFlightController.reset();
            DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
            DAI_Core.LOGGER.warn("<DAI>: Creative flight started without its required wait barrier; cancelled.");
        }
    }

    public static void waitForFlight(DAI_ActionDefinition action) {
        int expected = action == null ? 0 : action.slot();
        if (expected <= 0) expected = DAI_CreativeFlightController.generation();

        if (DAI_CreativeFlightController.isActive()
                && expected == DAI_CreativeFlightController.generation()) {
            DAI_ActionQueue.holdBarrier(createWait(expected), BARRIER_POLL_TICKS);
            DAI_ActionStatus.set(DAI_ActionResult.RUNNING);
            return;
        }

        DAI_ActionQueue.releaseBarrier();
        DAI_ActionStatus.set(DAI_CreativeFlightController.resultForGeneration(expected));
    }

    public static void hover(DAI_ActionDefinition action) {
        Minecraft minecraft = Minecraft.getInstance();

        DAI_InputState.movement().setMovement(0.0F, 0.0F);
        DAI_InputState.movement().setJump(false);
        DAI_InputState.movement().setSneak(false);

        if (
                minecraft.player != null
                        && minecraft.player.getAbilities().mayfly
        ) {
            if (!minecraft.player.getAbilities().flying) {
                minecraft.player.getAbilities().flying = true;
                minecraft.player.onUpdateAbilities();
            }

            /* Stop residual creative-flight drift before precision placement. */
            minecraft.player.setDeltaMovement(Vec3.ZERO);
        }

        if (action != null && action.ticks() > 0) {
            DAI_ActionQueue.delay(action.ticks());
        }
        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
    }

    private static Vec3 resolveDestination(Minecraft minecraft, DAI_ActionDefinition action) {
        if (minecraft.player == null || minecraft.level == null || action == null) return null;

        Vec3 base = minecraft.player.position();
        if (action.hasAction()) {
            DAI_WaypointMemory.DAI_Waypoint waypoint =
                    DAI_WaypointMemory.getInDimension(action.action(), minecraft.level.dimension());
            if (waypoint == null) return null;
            base = Vec3.atCenterOf(waypoint.position());
        }

        double[] offset = parseVector(action.direction());
        if (offset == null) offset = new double[]{0.0D, 0.0D, 0.0D};
        return base.add(offset[0], offset[1], offset[2]);
    }

    private static double[] parseVector(String value) {
        if (value == null || value.isBlank()) return new double[]{0.0D,0.0D,0.0D};
        String[] parts = value.trim().split("\\s*,\\s*");
        if (parts.length != 3) return null;
        try {
            return new double[]{
                    Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2])
            };
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static DAI_ActionDefinition createWait(int generation) {
        return new DAI_ActionDefinition(
                "wait_for_creative_flight", "", List.of(), List.of(), "", "",
                0.0F, 0.0F, "", 0, generation, false, 0.0D
        );
    }
}
