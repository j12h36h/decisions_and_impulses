package io.github.j12h36h.dai.logics.controller;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.menus.system.DAI_WaypointMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class DAI_CreativeBuildController {

    private static final int MAX_CELL_RETRIES = 3;
    private static final int VERIFY_DELAY_TICKS = 2;

    private static boolean active;
    private static BlockPos origin;
    private static List<DAI_ActionDefinition> cells = List.of();
    private static final List<Integer> repairIndices = new ArrayList<>();
    private static int index;
    private static int repairCursor;
    private static int retries;
    private static int waitTicks;
    private static boolean repairPass;
    private static int permanentFailures;
    private static int generation;
    private static int completedGeneration;
    private static DAI_ActionResult completedResult = DAI_ActionResult.SUCCESS;
    private static String blueprintName = "creative_blueprint";

    private DAI_CreativeBuildController() {
        // Utility class.
    }

    public static int start(DAI_ActionDefinition action) {
        Minecraft minecraft = Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || minecraft.getConnection() == null
                        || action == null
                        || action.sequence().isEmpty()
        ) {
            finish(DAI_ActionResult.FAILURE);
            return generation;
        }

        DAI_WaypointMemory.DAI_Waypoint waypoint =
                DAI_WaypointMemory.getInDimension(action.action(), minecraft.level.dimension());
        if (waypoint == null) {
            finish(DAI_ActionResult.FAILURE);
            return generation;
        }

        generation = generation == Integer.MAX_VALUE ? 1 : generation + 1;
        origin = waypoint.position();
        cells = List.copyOf(action.sequence());
        repairIndices.clear();
        index = 0;
        repairCursor = 0;
        retries = 0;
        waitTicks = 0;
        repairPass = false;
        permanentFailures = 0;
        blueprintName = action.menu().isBlank() ? "creative_blueprint" : action.menu();
        active = true;
        DAI_ActionStatus.set(DAI_ActionResult.RUNNING);

        DAI_Core.LOGGER.info(
                "<DAI>: Creative blueprint '{}' generation={} started at {} with {} cell(s).",
                blueprintName, generation, origin, cells.size()
        );
        return generation;
    }

    public static void tick() {
        if (!active) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.getConnection() == null || origin == null) {
            finish(DAI_ActionResult.FAILURE);
            return;
        }

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        int cellIndex = currentCellIndex();
        if (cellIndex < 0) {
            if (!repairPass && !repairIndices.isEmpty()) {
                repairPass = true;
                repairCursor = 0;
                retries = 0;
                DAI_Core.LOGGER.info(
                        "<DAI>: Creative blueprint '{}' entering repair pass for {} cell(s).",
                        blueprintName, repairIndices.size()
                );
                return;
            }
            finish(permanentFailures == 0 ? DAI_ActionResult.SUCCESS : DAI_ActionResult.FAILURE);
            return;
        }

        DAI_ActionDefinition cell = cells.get(cellIndex);
        BlockPos offset = parseOffset(cell.direction());
        String state = cell.action();

        if (offset == null || state == null || state.isBlank()) {
            failCell(cellIndex, "invalid offset/state");
            return;
        }

        BlockPos position = origin.offset(offset);
        if (matches(minecraft, position, state)) {
            advanceCell();
            return;
        }

        if (retries >= MAX_CELL_RETRIES) {
            failCell(cellIndex, "verification failed after bounded retries");
            return;
        }

        minecraft.getConnection().sendCommand(
                "setblock "
                        + position.getX() + " "
                        + position.getY() + " "
                        + position.getZ() + " "
                        + state + " replace"
        );

        retries++;
        waitTicks = VERIFY_DELAY_TICKS;
    }

    private static int currentCellIndex() {
        if (!repairPass) return index < cells.size() ? index : -1;
        return repairCursor < repairIndices.size() ? repairIndices.get(repairCursor) : -1;
    }

    private static void advanceCell() {
        retries = 0;
        if (!repairPass) {
            index++;
            if (index > 0 && index % 25 == 0) {
                DAI_Core.LOGGER.info(
                        "<DAI>: Creative blueprint '{}' progress {}/{}.",
                        blueprintName, index, cells.size()
                );
            }
        } else {
            repairCursor++;
        }
    }

    private static void failCell(int cellIndex, String reason) {
        if (!repairPass) {
            if (!repairIndices.contains(cellIndex)) repairIndices.add(cellIndex);
            index++;
        } else {
            permanentFailures++;
            repairCursor++;
        }
        retries = 0;
        DAI_Core.LOGGER.warn(
                "<DAI>: Creative blueprint '{}' cell {} deferred/failed: {}.",
                blueprintName, cellIndex, reason
        );
    }

    private static boolean matches(Minecraft minecraft, BlockPos position, String expected) {
        String base = expected.trim();
        int stateStart = base.indexOf('[');
        if (stateStart >= 0) base = base.substring(0, stateStart);
        if (!base.contains(":")) base = "minecraft:" + base;

        if (base.equals("minecraft:air")) {
            return minecraft.level.getBlockState(position).isAir();
        }

        String actual = minecraft.level.getBlockState(position)
                .getBlock().builtInRegistryHolder().key().identifier().toString();
        return base.equals(actual);
    }

    private static BlockPos parseOffset(String value) {
        if (value == null || value.isBlank()) return null;
        String[] parts = value.trim().split("\\s*,\\s*");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            );
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static void finish(DAI_ActionResult result) {
        if (active) {
            completedGeneration = generation;
            completedResult = result;
            DAI_Core.LOGGER.info(
                    "<DAI>: Creative blueprint '{}' generation={} finished result={} permanentFailures={}.",
                    blueprintName, generation, result, permanentFailures
            );
        }
        active = false;
        origin = null;
        cells = List.of();
        repairIndices.clear();
        DAI_ActionStatus.set(result);
    }

    public static void reset() {
        active = false;
        origin = null;
        cells = List.of();
        repairIndices.clear();
        index = 0;
        repairCursor = 0;
        retries = 0;
        waitTicks = 0;
        repairPass = false;
        permanentFailures = 0;
    }

    public static boolean isActive() { return active; }
    public static int generation() { return generation; }

    public static DAI_ActionResult resultForGeneration(int requested) {
        if (active && requested == generation) return DAI_ActionResult.RUNNING;
        if (requested == completedGeneration) return completedResult;
        return DAI_ActionResult.FAILURE;
    }
}
