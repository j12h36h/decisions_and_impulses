package io.github.j12h36h.dai.logics.core;

import com.google.gson.JsonObject;
import io.github.j12h36h.dai.logics.DAI_AutomationLogic;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionGovernor;
import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.controller.DAI_ApproachController;
import io.github.j12h36h.dai.logics.controller.DAI_CreativeFlightController;
import io.github.j12h36h.dai.logics.controller.DAI_ExploreController;
import io.github.j12h36h.dai.logics.controller.DAI_ScaffoldController;
import io.github.j12h36h.dai.logics.input.DAI_InputState;
import io.github.j12h36h.dai.menus.system.DAI_TargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Streaming, capped diagnostic telemetry for autonomous reliability testing.
 *
 * No world/entity references or event lists are retained. Each event is
 * serialized and appended immediately, and file growth is capped so telemetry
 * cannot become an unbounded-memory or unbounded-disk subsystem.
 */
public final class DAI_RuntimeTelemetry {

    public static final String DATAPACK_REVISION =
            "creative-player-pyramid-placement-flight-v6-2026-08-12";

    public static final String DATAPACK_SHA256 =
            "f6bb43a6a8961b0568323a0afcf3b339faba8d8ce68d081929a487d6333d1e5b";

    private static final Path LOG_DIRECTORY =
            Path.of("logs", "DAI_Log");

    private static final long MAX_FILE_BYTES =
            4L * 1024L * 1024L;

    /*
     * Runtime telemetry is intentionally sparse. The previous two-second
     * full snapshots duplicated the legacy debug stream and added allocation
     * pressure during already-heavy navigation/crafting bursts.
     */
    private static final int NORMAL_SNAPSHOT_TICKS = 100;
    private static final int PRESSURE_SNAPSHOT_TICKS = 400;
    private static final int QUEUE_WARNING_SIZE = 96;

    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .withZone(ZoneOffset.UTC);

    private static boolean active;
    private static boolean ioFailed;
    private static int tickCounter;
    private static int queueWarningCooldown;
    private static String lastApproachFailureReason = "";
    private static int repeatedApproachFailures;
    private static String lastPlacementFailureKey = "";
    private static int repeatedPlacementFailures;
    private static String runId = "";
    private static Path runJsonl;
    private static Path runLog;
    private static Path latestJsonl;
    private static Path latestLog;

    private DAI_RuntimeTelemetry() {
        // Utility class.
    }

    public static void start(
            int generation
    ) {

        stop("restart");

        if (!DAI_Config.isDebuggingEnabled()) {
            resetState();
            return;
        }

        try {
            Files.createDirectories(LOG_DIRECTORY);
        } catch (IOException exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not create telemetry directory '{}'.",
                    LOG_DIRECTORY,
                    exception
            );
            return;
        }

        String timestamp = FILE_TIME.format(Instant.now());
        runId = UUID.randomUUID().toString();
        String stem = "dai_" + timestamp + "_" + runId.substring(0, 8);

        runJsonl = LOG_DIRECTORY.resolve(stem + ".jsonl");
        runLog = LOG_DIRECTORY.resolve(stem + ".log");
        latestJsonl = LOG_DIRECTORY.resolve("dai_latest.jsonl");
        latestLog = LOG_DIRECTORY.resolve("dai_latest.log");

        active = true;
        ioFailed = false;
        tickCounter = 0;
        queueWarningCooldown = 0;
        lastApproachFailureReason = "";
        repeatedApproachFailures = 0;
        lastPlacementFailureKey = "";
        repeatedPlacementFailures = 0;

        truncate(latestJsonl);
        truncate(latestLog);

        JsonObject event = baseEvent("run_start");
        event.addProperty("run_id", runId);
        event.addProperty("generation", generation);
        event.addProperty("automation_mode", DAI_AutomationLogic.modeName());
        event.addProperty("datapack_revision", DATAPACK_REVISION);
        event.addProperty("datapack_sha256", DATAPACK_SHA256);
        write(event, "run_start generation=" + generation);

        DAI_Core.LOGGER.info(
                "<DAI>: Started bounded telemetry run '{}' in '{}'.",
                runId,
                LOG_DIRECTORY
        );
    }

    public static void stop(
            String reason
    ) {

        if (!active) {
            return;
        }

        if (DAI_Config.isDebuggingEnabled()) {
            JsonObject event = baseEvent("run_stop");
            event.addProperty("reason", safe(reason));
            write(event, "run_stop reason=" + safe(reason));
        }

        resetState();
    }

    public static void tick() {

        if (!DAI_Config.isDebuggingEnabled()) {
            resetState();
            return;
        }

        if (!DAI_AutomationLogic.isActive()) {
            return;
        }

        if (!active) {
            start(DAI_AutomationLogic.generation());
            return;
        }

        long used = heapUsed();
        long max = heapMax();
        double pressure = max > 0L
                ? (double) used / (double) max
                : 0.0D;

        int interval = pressure >= 0.82D
                ? PRESSURE_SNAPSHOT_TICKS
                : NORMAL_SNAPSHOT_TICKS;

        tickCounter++;

        if (queueWarningCooldown > 0) {
            queueWarningCooldown--;
        }

        if (
                DAI_ActionQueue.size() >= QUEUE_WARNING_SIZE
                        && queueWarningCooldown <= 0
        ) {

            JsonObject warning = baseEvent("queue_pressure");
            warning.addProperty("queue_size", DAI_ActionQueue.size());
            warning.addProperty("barrier", barrierType());
            write(
                    warning,
                    "queue_pressure size=" + DAI_ActionQueue.size()
            );

            queueWarningCooldown = 200;
        }

        if (tickCounter < interval) {
            return;
        }

        tickCounter = 0;
        snapshot(pressure, used, max);
    }

    public static void approachStart(
            BlockPos target,
            int generation
    ) {

        /*
         * Approach starts are intentionally omitted from bounded telemetry.
         * The five-second state snapshot already records active/generation/
         * target, and logging every start doubled long-run line counts.
         */
    }

    public static void approachFinish(
            BlockPos target,
            int generation,
            DAI_ActionResult result,
            String reason
    ) {

        if (!isActive()) {
            return;
        }

        if (result == DAI_ActionResult.SUCCESS) {
            return;
        }

        String normalizedReason = safe(reason);

        if (!normalizedReason.equals(lastApproachFailureReason)) {
            lastApproachFailureReason = normalizedReason;
            repeatedApproachFailures = 1;
        } else {
            repeatedApproachFailures++;
        }

        /*
         * Failure storms used to dominate dai_latest.log/jsonl. Keep the first
         * occurrence of a reason and then one sample per twenty repeats. The
         * sample still carries the current target and repeat count.
         */
        if (
                repeatedApproachFailures != 1
                        && repeatedApproachFailures % 20 != 0
        ) {
            return;
        }

        JsonObject event = baseEvent("approach_failure");
        event.addProperty("approach_generation", generation);
        event.addProperty("target", position(target));
        event.addProperty("result", String.valueOf(result));
        event.addProperty("reason", normalizedReason);
        event.addProperty("repeat_count", repeatedApproachFailures);
        write(
                event,
                "approach_failure target=" + position(target)
                        + " result=" + result
                        + " repeats=" + repeatedApproachFailures
                        + " reason=" + normalizedReason
        );
    }

    /**
     * Compact event-driven placement diagnostics. Repeated failures with the
     * same root cause are sampled so a bad build cannot flood the log.
     */
    public static void placementFailure(
            BlockPos destination,
            Identifier itemId,
            BlockPos support,
            Direction face,
            double reach,
            String reason,
            String detail
    ) {

        if (!isActive()) {
            return;
        }

        String key = safe(reason)
                + "|"
                + (itemId == null ? "" : itemId)
                + "|"
                + (face == null ? "" : face);

        if (!key.equals(lastPlacementFailureKey)) {
            lastPlacementFailureKey = key;
            repeatedPlacementFailures = 1;
        } else {
            repeatedPlacementFailures++;
        }

        /* First occurrence, then one sample per ten identical failures. */
        if (
                repeatedPlacementFailures != 1
                        && repeatedPlacementFailures % 10 != 0
        ) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Vec3 playerPosition = minecraft.player == null
                ? null
                : minecraft.player.position();

        JsonObject event = baseEvent("place_fail");
        event.addProperty("destination", position(destination));
        event.addProperty("item", itemId == null ? "" : itemId.toString());
        event.addProperty("support", position(support));
        event.addProperty("face", face == null ? "" : face.toString());
        event.addProperty("reach", reach);
        event.addProperty("reason", safe(reason));
        event.addProperty("detail", truncateText(detail, 180));
        event.addProperty("repeat_count", repeatedPlacementFailures);

        if (playerPosition != null) {
            event.addProperty("player_x", playerPosition.x);
            event.addProperty("player_y", playerPosition.y);
            event.addProperty("player_z", playerPosition.z);
        }

        write(
                event,
                "place_fail dst=" + position(destination)
                        + " item=" + (itemId == null ? "" : itemId)
                        + " support=" + position(support)
                        + " face=" + (face == null ? "" : face)
                        + " reach=" + formatDouble(reach)
                        + " repeats=" + repeatedPlacementFailures
                        + " reason=" + safe(reason)
                        + (safe(detail).isBlank() ? "" : " detail=" + truncateText(detail, 80))
        );
    }

    /** A verified placement breaks the repeated-failure sampling streak. */
    public static void placementSuccess() {
        if (!isActive()) {
            return;
        }

        lastPlacementFailureKey = "";
        repeatedPlacementFailures = 0;
    }

    public static void scaffoldEvent(
            String type,
            BlockPos position,
            int count,
            String detail
    ) {

        if (!isActive()) {
            return;
        }

        JsonObject event = baseEvent(type);
        event.addProperty("position", position(position));
        event.addProperty("scaffold_blocks", count);
        event.addProperty("detail", safe(detail));
        write(
                event,
                safe(type)
                        + " position=" + position(position)
                        + " blocks=" + count
                        + " " + safe(detail)
        );
    }

    public static void actionFailure(
            String type,
            String payload,
            Throwable throwable
    ) {

        if (!isActive()) {
            return;
        }

        JsonObject event = baseEvent("action_exception");
        event.addProperty("action_type", safe(type));
        event.addProperty("action_payload", safe(payload));

        if (throwable != null) {
            event.addProperty(
                    "exception",
                    throwable.getClass().getName()
            );
            event.addProperty(
                    "message",
                    truncateText(throwable.getMessage(), 512)
            );
        }

        write(
                event,
                "action_exception type=" + safe(type)
                        + " message=" + (
                        throwable == null
                                ? ""
                                : truncateText(throwable.getMessage(), 180)
                )
        );
    }

    public static boolean isEnabled() {
        return DAI_Config.isDebuggingEnabled();
    }

    private static boolean isActive() {
        return active && isEnabled();
    }

    private static void resetState() {
        active = false;
        ioFailed = false;
        tickCounter = 0;
        queueWarningCooldown = 0;
        lastApproachFailureReason = "";
        repeatedApproachFailures = 0;
        lastPlacementFailureKey = "";
        repeatedPlacementFailures = 0;
        runId = "";
        runJsonl = null;
        runLog = null;
        latestJsonl = null;
        latestLog = null;
    }

    private static void snapshot(
            double pressure,
            long used,
            long max
    ) {

        Minecraft minecraft = Minecraft.getInstance();
        JsonObject event = baseEvent("state_snapshot");

        event.addProperty("heap_used", used);
        event.addProperty("heap_max", max);
        event.addProperty("heap_pressure", pressure);
        event.addProperty("queue_size", DAI_ActionQueue.size());
        event.addProperty("queue_delay", DAI_ActionQueue.delayTicks());
        event.addProperty("barrier", barrierType());
        event.addProperty("semantic_actions_started", DAI_ActionGovernor.semanticActionsStarted());
        event.addProperty("semantic_throttled_ticks", DAI_ActionGovernor.throttledTicks());
        event.addProperty("semantic_interval_ticks", DAI_ActionGovernor.currentIntervalTicks());
        event.addProperty("semantic_limit_per_second", DAI_ActionGovernor.currentActionLimitPerSecond());
        event.addProperty("action_current", String.valueOf(DAI_ActionStatus.get()));
        event.addProperty("action_previous", String.valueOf(DAI_ActionStatus.previous()));
        event.addProperty("approach_active", DAI_ApproachController.isActive());
        event.addProperty("approach_generation", DAI_ApproachController.generation());
        event.addProperty("explore_active", DAI_ExploreController.isActive());
        event.addProperty("explore_generation", DAI_ExploreController.generation());
        event.addProperty("scaffold_active", DAI_ScaffoldController.isActive());
        event.addProperty("scaffold_blocks", DAI_ScaffoldController.usedCount());
        event.addProperty("creative_flight_active", DAI_CreativeFlightController.isActive());
        event.addProperty("creative_flight_generation", DAI_CreativeFlightController.generation());
        event.addProperty("creative_flight_distance", DAI_CreativeFlightController.distanceToTarget());
        event.addProperty("creative_flight_segment_distance", DAI_CreativeFlightController.distanceToMovementTarget());
        event.addProperty("creative_flight_phase", DAI_CreativeFlightController.phaseName());
        event.addProperty("creative_flight_stalled_ticks", DAI_CreativeFlightController.stalledTicks());
        event.addProperty("creative_flight_velocity_assist", DAI_CreativeFlightController.velocityAssistActive());
        event.addProperty("selected_target", position(DAI_TargetState.selectedBlock()));

        if (
                minecraft.player != null
                        && minecraft.level != null
        ) {

            event.addProperty("dimension", minecraft.level.dimension().identifier().toString());
            event.addProperty("x", minecraft.player.getX());
            event.addProperty("y", minecraft.player.getY());
            event.addProperty("z", minecraft.player.getZ());
            event.addProperty("yaw", minecraft.player.getYRot());
            event.addProperty("pitch", minecraft.player.getXRot());
            event.addProperty("health", minecraft.player.getHealth());
            event.addProperty("food", minecraft.player.getFoodData().getFoodLevel());
            event.addProperty("on_ground", minecraft.player.onGround());
            event.addProperty("input_forward", DAI_InputState.movement().forward());
            event.addProperty("input_strafe", DAI_InputState.movement().strafe());
            event.addProperty("input_jump", DAI_InputState.movement().jump());

            if (pressure < 0.90D) {
                event.add("inventory", inventorySnapshot(minecraft.player.getInventory()));
                event.addProperty("queue_head", actionDescription(DAI_ActionQueue.peek()));
            }
        }

        write(
                event,
                "state_snapshot queue=" + DAI_ActionQueue.size()
                        + " heap=" + used + "/" + max
                        + " target=" + position(DAI_TargetState.selectedBlock())
        );
    }

    private static JsonObject inventorySnapshot(
            Inventory inventory
    ) {

        JsonObject result = new JsonObject();

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {

            ItemStack stack = inventory.getItem(slot);

            if (stack.isEmpty()) {
                continue;
            }

            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            int previous = result.has(id)
                    ? result.get(id).getAsInt()
                    : 0;
            result.addProperty(id, previous + stack.getCount());
        }

        return result;
    }

    private static JsonObject baseEvent(
            String type
    ) {

        /*
         * Run identity/mode/revision/hash are emitted once by run_start. The
         * latest files are truncated at each run, so repeating ~200 bytes of
         * identical metadata on every event only bloats diagnostics.
         */
        JsonObject event = new JsonObject();
        event.addProperty("timestamp", Instant.now().toString());
        event.addProperty("event", safe(type));
        return event;
    }

    private static void write(
            JsonObject event,
            String human
    ) {

        if (!isActive() || ioFailed || event == null) {
            return;
        }

        try {
            appendCapped(runJsonl, event + System.lineSeparator());
            appendCapped(latestJsonl, event + System.lineSeparator());

            String line = Instant.now() + " " + safe(human) + System.lineSeparator();
            appendCapped(runLog, line);
            appendCapped(latestLog, line);

        } catch (IOException exception) {

            ioFailed = true;

            DAI_Core.LOGGER.warn(
                    "<DAI>: Telemetry disabled for this run after an I/O failure.",
                    exception
            );
        }
    }

    private static void appendCapped(
            Path path,
            String text
    ) throws IOException {

        if (path == null || text == null) {
            return;
        }

        if (
                Files.exists(path)
                        && Files.size(path) >= MAX_FILE_BYTES
        ) {
            return;
        }

        Files.writeString(
                path,
                text,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private static void truncate(
            Path path
    ) {

        if (path == null) {
            return;
        }

        try {
            Files.writeString(
                    path,
                    "",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException exception) {
            DAI_Core.debug(
                    "<DAI>: Could not reset telemetry file '{}'.",
                    path,
                    exception
            );
        }
    }

    private static String barrierType() {

        DAI_ActionDefinition barrier = DAI_ActionQueue.barrier();

        return barrier == null
                ? ""
                : barrier.type();
    }

    private static String actionDescription(
            DAI_ActionDefinition action
    ) {

        if (action == null) {
            return "";
        }

        return action.type()
                + (action.hasAction()
                ? "(" + action.action() + ")"
                : "");
    }

    private static long heapUsed() {

        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static long heapMax() {
        return Runtime.getRuntime().maxMemory();
    }

    private static String position(
            BlockPos position
    ) {

        if (position == null) {
            return "";
        }

        return position.getX()
                + ","
                + position.getY()
                + ","
                + position.getZ();
    }

    private static String formatDouble(
            double value
    ) {
        if (!Double.isFinite(value) || value < 0.0D) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String safe(
            String value
    ) {
        return value == null ? "" : value;
    }

    private static String truncateText(
            String value,
            int maximum
    ) {

        String safe = safe(value);

        return safe.length() <= maximum
                ? safe
                : safe.substring(0, maximum);
    }
}
