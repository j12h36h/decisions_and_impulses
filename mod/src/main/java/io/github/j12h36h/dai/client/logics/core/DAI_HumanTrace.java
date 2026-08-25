package io.github.j12h36h.dai.client.logics.core;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.core.DAI_Config;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DAI_HumanTrace {

    private static final int ACTIVE_SAMPLE_TICKS = 2;
    private static final int IDLE_SAMPLE_TICKS = 20;

    private static final Path LOG_DIRECTORY =
            Path.of("logs", "DAI_Log");

    private static final Path HUMAN_LATEST_LOG =
            LOG_DIRECTORY.resolve("human_latest.log");

    private static final long MAX_HUMAN_LOG_BYTES =
            16L * 1024L * 1024L;

    private static boolean ioFailed;
    private static String sessionKey = "";
    private static int sampleTicks;
    private static InputFrame lastInput;
    private static String lastCrosshair = "";
    private static String lastScreen = "";
    private static String lastMenu = "";
    private static String lastInventorySlots = "";
    private static Map<String, Integer> lastInventoryTotals = Map.of();
    private static float lastHealth = Float.NaN;
    private static int lastFood = Integer.MIN_VALUE;
    private static int lastHotbar = -1;
    private static float lastYaw = Float.NaN;
    private static float lastPitch = Float.NaN;
    private static Vec3 lastPosition;

    private static final LinkedHashMap<BlockPos, String> WATCHED_BLOCKS =
            new LinkedHashMap<>();
    private static int watchedEntityId = -1;
    private static float watchedEntityHealth = Float.NaN;

    private DAI_HumanTrace() {
        // Utility class.
    }

    public static void tick(Minecraft minecraft) {
        if (!DAI_Config.isDebuggingEnabled()
                || minecraft.player == null
                || minecraft.level == null) {
            return;
        }

        ensureSession(minecraft);

        InputFrame currentInput = input(minecraft);
        String crosshair = DAI_HumanTraceFormat.describeHit(minecraft);
        String screen = DAI_HumanTraceFormat.screenState(minecraft);
        String menu = DAI_HumanTraceFormat.menuState(minecraft);
        String inventorySlots = DAI_HumanTraceFormat.inventorySlots(minecraft);
        Map<String, Integer> inventoryTotals =
                DAI_HumanTraceFormat.inventoryTotals(minecraft);

        logInputTransitions(minecraft, currentInput, crosshair);

        int hotbar = minecraft.player.getInventory().getSelectedSlot();
        if (lastHotbar >= 0 && hotbar != lastHotbar) {
            emit(
                    minecraft,
                    "hotbar_changed",
                    "from=" + lastHotbar
                            + " to=" + hotbar
                            + " held="
                            + DAI_HumanTraceFormat.item(
                            minecraft.player.getMainHandItem()
                    )
            );
        }

        if (!Objects.equals(lastScreen, screen)) {
            emit(
                    minecraft,
                    "screen_changed",
                    "from=" + lastScreen + " to=" + screen
            );
        }

        if (!Objects.equals(lastMenu, menu)) {
            emit(minecraft, "menu_slots_changed", "menu=" + menu);
        }

        if (!lastInventorySlots.isEmpty()
                && !lastInventorySlots.equals(inventorySlots)) {
            emit(
                    minecraft,
                    "inventory_slots_changed",
                    "before=" + lastInventorySlots
                            + " after=" + inventorySlots
            );
        }

        if (!lastInventoryTotals.isEmpty()
                && !lastInventoryTotals.equals(inventoryTotals)) {
            emit(
                    minecraft,
                    "inventory_delta",
                    "delta="
                            + DAI_HumanTraceFormat.inventoryDelta(
                            lastInventoryTotals,
                            inventoryTotals
                    )
                            + " inventory=" + inventorySlots
            );
        }

        float health = minecraft.player.getHealth();
        int food = minecraft.player.getFoodData().getFoodLevel();

        if (!Float.isNaN(lastHealth)
                && (Float.compare(health, lastHealth) != 0 || food != lastFood)) {
            emit(
                    minecraft,
                    "vitals_changed",
                    "health="
                            + DAI_HumanTraceFormat.number(lastHealth)
                            + "->"
                            + DAI_HumanTraceFormat.number(health)
                            + " food=" + lastFood + "->" + food
            );
        }

        if (!lastCrosshair.isEmpty() && !lastCrosshair.equals(crosshair)) {
            emit(
                    minecraft,
                    "crosshair_changed",
                    "from=" + lastCrosshair + " to=" + crosshair
            );
        }

        checkWatchedBlocks(minecraft);
        checkWatchedEntity(minecraft);

        boolean active = currentInput.anyGameplayInput()
                || cameraMoved(minecraft)
                || playerMoved(minecraft);

        int interval = active ? ACTIVE_SAMPLE_TICKS : IDLE_SAMPLE_TICKS;
        if (++sampleTicks >= interval) {
            sampleTicks = 0;
            emit(
                    minecraft,
                    "sample",
                    "inventory="
                            + DAI_HumanTraceFormat.inventoryCompact(
                            inventoryTotals
                    )
            );
        }

        lastInput = currentInput;
        lastCrosshair = crosshair;
        lastScreen = screen;
        lastMenu = menu;
        lastInventorySlots = inventorySlots;
        lastInventoryTotals = Map.copyOf(inventoryTotals);
        lastHealth = health;
        lastFood = food;
        lastHotbar = hotbar;
        lastYaw = minecraft.player.getYRot();
        lastPitch = minecraft.player.getXRot();
        lastPosition = minecraft.player.position();
    }

    /**
     * Current vanilla crosshair/raytrace hit rendered as a stable diagnostic
     * description. The learning runtime uses this to ground language in the
     * object the player is actually pointing at.
     */
    public static String crosshairDescription(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return "null";
        }
        return DAI_HumanTraceFormat.describeHit(minecraft);
    }

    public static InputFrame input(Minecraft minecraft) {
        return new InputFrame(
                minecraft.options.keyUp.isDown(),
                minecraft.options.keyDown.isDown(),
                minecraft.options.keyLeft.isDown(),
                minecraft.options.keyRight.isDown(),
                minecraft.options.keyJump.isDown(),
                minecraft.options.keyShift.isDown(),
                minecraft.options.keySprint.isDown(),
                minecraft.options.keyAttack.isDown(),
                minecraft.options.keyUse.isDown(),
                minecraft.options.keyPickItem.isDown(),
                minecraft.options.keyDrop.isDown(),
                minecraft.options.keySwapOffhand.isDown(),
                minecraft.options.keyInventory.isDown()
        );
    }

    private static void ensureSession(Minecraft minecraft) {
        String currentKey = System.identityHashCode(minecraft.level)
                + ":" + minecraft.player.getId()
                + ":" + minecraft.level.dimension().identifier();

        if (currentKey.equals(sessionKey)) {
            return;
        }

        sessionKey = currentKey;
        reset();
        beginSessionLog();

        emit(
                minecraft,
                "session_start",
                "inventory="
                        + DAI_HumanTraceFormat.inventorySlots(minecraft)
                        + " menu="
                        + DAI_HumanTraceFormat.menuState(minecraft)
        );
    }

    private static void reset() {
        sampleTicks = 0;
        lastInput = null;
        lastCrosshair = "";
        lastScreen = "";
        lastMenu = "";
        lastInventorySlots = "";
        lastInventoryTotals = Map.of();
        lastHealth = Float.NaN;
        lastFood = Integer.MIN_VALUE;
        lastHotbar = -1;
        lastYaw = Float.NaN;
        lastPitch = Float.NaN;
        lastPosition = null;
        WATCHED_BLOCKS.clear();
        watchedEntityId = -1;
        watchedEntityHealth = Float.NaN;
    }

    private static void logInputTransitions(
            Minecraft minecraft,
            InputFrame current,
            String crosshair
    ) {
        if (lastInput == null || !lastInput.equals(current)) {
            emit(
                    minecraft,
                    "input_changed",
                    "previous=" + lastInput + " current=" + current
            );
        }

        if (current.attack() && (lastInput == null || !lastInput.attack())) {
            observeTarget(minecraft);
            emit(minecraft, "attack_start", "target=" + crosshair);
        }

        if (lastInput != null && lastInput.attack() && !current.attack()) {
            emit(minecraft, "attack_stop", "target=" + crosshair);
        }

        if (current.use() && (lastInput == null || !lastInput.use())) {
            observeTarget(minecraft);
            emit(minecraft, "use_start", "target=" + crosshair);
        }

        if (lastInput != null && lastInput.use() && !current.use()) {
            emit(minecraft, "use_stop", "target=" + crosshair);
        }

        risingEdge(
                minecraft,
                "pick_block",
                current.pick(),
                lastInput != null && lastInput.pick()
        );
        risingEdge(
                minecraft,
                "drop",
                current.drop(),
                lastInput != null && lastInput.drop()
        );
        risingEdge(
                minecraft,
                "swap_hands",
                current.swap(),
                lastInput != null && lastInput.swap()
        );
        risingEdge(
                minecraft,
                "inventory_key",
                current.inventory(),
                lastInput != null && lastInput.inventory()
        );
    }

    private static void risingEdge(
            Minecraft minecraft,
            String event,
            boolean current,
            boolean previous
    ) {
        if (current && !previous) {
            emit(
                    minecraft,
                    event,
                    "target=" + DAI_HumanTraceFormat.describeHit(minecraft)
            );
        }
    }

    private static void observeTarget(Minecraft minecraft) {
        if (minecraft.hitResult instanceof BlockHitResult blockHit) {
            watchBlock(minecraft, blockHit.getBlockPos());
            watchBlock(
                    minecraft,
                    blockHit.getBlockPos().relative(blockHit.getDirection())
            );
        }

        if (minecraft.hitResult instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            watchedEntityId = entity.getId();
            watchedEntityHealth = DAI_HumanTraceFormat.entityHealth(entity);
        }
    }

    private static void watchBlock(
            Minecraft minecraft,
            BlockPos position
    ) {
        if (WATCHED_BLOCKS.size() >= 24) {
            BlockPos oldest = WATCHED_BLOCKS.keySet().iterator().next();
            WATCHED_BLOCKS.remove(oldest);
        }

        WATCHED_BLOCKS.put(
                position.immutable(),
                DAI_HumanTraceFormat.block(minecraft, position)
        );
    }

    private static void checkWatchedBlocks(Minecraft minecraft) {
        for (Map.Entry<BlockPos, String> entry
                : List.copyOf(WATCHED_BLOCKS.entrySet())) {
            String current =
                    DAI_HumanTraceFormat.block(minecraft, entry.getKey());

            if (current.equals(entry.getValue())) {
                continue;
            }

            emit(
                    minecraft,
                    "world_block_changed",
                    "pos=" + entry.getKey()
                            + " from=" + entry.getValue()
                            + " to=" + current
            );
            WATCHED_BLOCKS.put(entry.getKey(), current);
        }
    }

    private static void checkWatchedEntity(Minecraft minecraft) {
        if (watchedEntityId < 0 || minecraft.level == null) {
            return;
        }

        Entity entity = minecraft.level.getEntity(watchedEntityId);
        if (entity == null) {
            emit(
                    minecraft,
                    "observed_entity_gone",
                    "entity_id=" + watchedEntityId
            );
            watchedEntityId = -1;
            watchedEntityHealth = Float.NaN;
            return;
        }

        float health = DAI_HumanTraceFormat.entityHealth(entity);
        if (Float.compare(health, watchedEntityHealth) == 0) {
            return;
        }

        emit(
                minecraft,
                "observed_entity_health",
                "entity="
                        + DAI_HumanTraceFormat.entity(minecraft, entity)
                        + " health="
                        + DAI_HumanTraceFormat.number(watchedEntityHealth)
                        + "->"
                        + DAI_HumanTraceFormat.number(health)
        );
        watchedEntityHealth = health;
    }

    private static void emit(
            Minecraft minecraft,
            String event,
            String details
    ) {

        String line =
                Instant.now()
                        + " <DAI:HUMAN> event=" + event
                        + " " + details
                        + " "
                        + DAI_HumanTraceFormat.context(
                        minecraft,
                        input(minecraft)
                )
                        + " terrain="
                        + DAI_HumanTraceFormat.terrainPatch(minecraft)
                        + " nearby="
                        + DAI_HumanTraceFormat.nearbyLiving(minecraft);

        writeHumanLine(
                line
        );
    }

    private static void beginSessionLog() {

        if (!DAI_Config.isDebuggingEnabled()) {
            return;
        }

        try {
            Files.createDirectories(
                    LOG_DIRECTORY
            );

            Files.writeString(
                    HUMAN_LATEST_LOG,
                    "",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );

            ioFailed = false;

        } catch (IOException exception) {
            warnIoOnce(
                    "Could not initialize human trace log",
                    exception
            );
        }
    }

    private static void writeHumanLine(
            String line
    ) {

        if (
                !DAI_Config.isDebuggingEnabled()
                        || ioFailed
                        || line == null
        ) {
            return;
        }

        try {
            Files.createDirectories(
                    LOG_DIRECTORY
            );

            byte[] encoded =
                    (line + System.lineSeparator())
                            .getBytes(
                                    StandardCharsets.UTF_8
                            );

            if (
                    Files.exists(HUMAN_LATEST_LOG)
                            && Files.size(HUMAN_LATEST_LOG)
                            + encoded.length
                            > MAX_HUMAN_LOG_BYTES
            ) {
                Files.writeString(
                        HUMAN_LATEST_LOG,
                        Instant.now()
                                + " <DAI:HUMAN> event=log_rollover reason=max_bytes"
                                + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                );
            }

            Files.write(
                    HUMAN_LATEST_LOG,
                    encoded,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

        } catch (IOException exception) {
            warnIoOnce(
                    "Could not write human trace log",
                    exception
            );
        }
    }

    private static void warnIoOnce(
            String message,
            IOException exception
    ) {

        if (ioFailed) {
            return;
        }

        ioFailed = true;

        DAI_Core.LOGGER.warn(
                "<DAI>: {} '{}'.",
                message,
                HUMAN_LATEST_LOG,
                exception
        );
    }

    private static boolean cameraMoved(Minecraft minecraft) {
        if (Float.isNaN(lastYaw) || Float.isNaN(lastPitch)) {
            return true;
        }

        return Math.abs(minecraft.player.getYRot() - lastYaw) >= 0.10F
                || Math.abs(minecraft.player.getXRot() - lastPitch) >= 0.10F;
    }

    private static boolean playerMoved(Minecraft minecraft) {
        return lastPosition == null
                || minecraft.player.position().distanceToSqr(lastPosition)
                >= 0.0004D;
    }

    public record InputFrame(
            boolean forward,
            boolean backward,
            boolean left,
            boolean right,
            boolean jump,
            boolean sneak,
            boolean sprint,
            boolean attack,
            boolean use,
            boolean pick,
            boolean drop,
            boolean swap,
            boolean inventory
    ) {
        boolean anyGameplayInput() {
            return forward || backward || left || right || jump
                    || sneak || sprint || attack || use || pick
                    || drop || swap;
        }

        @Override
        public String toString() {
            return "{w=" + forward
                    + ",s=" + backward
                    + ",a=" + left
                    + ",d=" + right
                    + ",jump=" + jump
                    + ",sneak=" + sneak
                    + ",sprint=" + sprint
                    + ",attack=" + attack
                    + ",use=" + use
                    + ",pick=" + pick
                    + ",drop=" + drop
                    + ",swap=" + swap
                    + ",inventory=" + inventory
                    + "}";
        }
    }
}
