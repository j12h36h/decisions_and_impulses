package io.github.j12h36h.dai.logics.controller;

import io.github.j12h36h.dai.logics.DAI_HotbarLogic;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.core.DAI_RuntimeTelemetry;
import io.github.j12h36h.dai.logics.input.DAI_InputState;
import io.github.j12h36h.dai.menus.system.DAI_TargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded emergency vertical-scaffold controller.
 *
 * This is deliberately not part of normal A* path construction. Datapack
 * recovery may start it only after an ordinary approach has failed. It builds
 * a single vertical column under the player, never replaces a non-replaceable
 * target block, records every placed block, and can dismantle that column one
 * block at a time for a controlled one-block descent.
 */
public final class DAI_ScaffoldController {

    private static final int DEFAULT_TIMEOUT_TICKS = 360;
    private static final int DEFAULT_MAX_BLOCKS = 8;
    private static final int ABSOLUTE_MAX_BLOCKS = 12;
    private static final int MAX_RESULT_HISTORY = 8;
    private static final int MAX_PLACE_ATTEMPTS = 3;
    private static final double TARGET_REACH_DISTANCE = 4.35D;
    private static final double MAX_HORIZONTAL_TARGET_DISTANCE = 4.25D;
    private static final double JUMP_CLEARANCE = 1.02D;

    private static final Identifier[] MATERIALS = ids(
            "minecraft:cobblestone",
            "minecraft:cobbled_deepslate",
            "minecraft:dirt",
            "minecraft:stone",
            "minecraft:deepslate",
            "minecraft:oak_planks",
            "minecraft:spruce_planks",
            "minecraft:birch_planks",
            "minecraft:jungle_planks",
            "minecraft:acacia_planks",
            "minecraft:dark_oak_planks",
            "minecraft:mangrove_planks",
            "minecraft:cherry_planks",
            "minecraft:pale_oak_planks",
            "minecraft:bamboo_planks",
            "minecraft:crimson_planks",
            "minecraft:warped_planks",
            "minecraft:sand",
            "minecraft:gravel",
            "minecraft:netherrack",
            "minecraft:blackstone"
    );

    private enum Mode {
        NONE,
        ASCEND,
        DESCEND
    }

    private enum Phase {
        IDLE,
        WAIT_GROUND,
        JUMPING,
        SETTLING,
        FACE_BREAK,
        BREAKING,
        FALLING
    }

    private static Mode mode = Mode.NONE;
    private static Phase phase = Phase.IDLE;
    private static boolean active;
    private static int generation;
    private static int ticksRemaining;
    private static int maxBlocks;
    private static int phaseTicks;
    private static int placeAttempts;
    private static double stepBaseY;
    private static BlockPos target;
    private static BlockPos towerFeetOrigin;
    private static String dimensionId = "";
    private static final List<BlockPos> placedBlocks = new ArrayList<>();
    private static final Map<Integer, DAI_ActionResult> resultHistory =
            new LinkedHashMap<>();

    private DAI_ScaffoldController() {
        // Utility class.
    }

    public static boolean startAscent(
            int timeoutTicks,
            int requestedMaxBlocks
    ) {

        Minecraft minecraft = Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || minecraft.gameMode == null
        ) {
            failStart("player, level, or game mode unavailable");
            return false;
        }

        BlockPos selected = DAI_TargetState.selectedBlock();

        if (selected == null) {
            failNewGeneration("no selected block target");
            return false;
        }

        if (active) {
            failStart("another scaffold operation is already active");
            return false;
        }

        if (!placedBlocks.isEmpty()) {
            failNewGeneration("a previous scaffold still requires cleanup");
            return false;
        }

        if (availableMaterialCount() <= 0) {
            failNewGeneration("no safe scaffold material is available");
            return false;
        }

        Vec3 playerPos = minecraft.player.position();
        Vec3 targetCenter = Vec3.atCenterOf(selected);

        double horizontal = Math.sqrt(
                square(targetCenter.x - playerPos.x)
                        + square(targetCenter.z - playerPos.z)
        );

        if (horizontal > MAX_HORIZONTAL_TARGET_DISTANCE) {
            failNewGeneration("selected target is too far horizontally for a vertical fallback");
            return false;
        }

        if (targetCenter.y <= minecraft.player.getEyePosition().y) {
            failNewGeneration("selected target does not require upward scaffold recovery");
            return false;
        }

        generation = nextGeneration(generation);
        mode = Mode.ASCEND;
        phase = Phase.WAIT_GROUND;
        active = true;
        ticksRemaining = timeoutTicks > 0
                ? timeoutTicks
                : DEFAULT_TIMEOUT_TICKS;
        maxBlocks = Math.max(
                1,
                Math.min(
                        requestedMaxBlocks > 0
                                ? requestedMaxBlocks
                                : DEFAULT_MAX_BLOCKS,
                        ABSOLUTE_MAX_BLOCKS
                )
        );
        phaseTicks = 0;
        placeAttempts = 0;
        target = selected.immutable();
        towerFeetOrigin = minecraft.player.blockPosition().immutable();
        stepBaseY = minecraft.player.getY();
        dimensionId = minecraft.level.dimension().identifier().toString();

        DAI_PathController.reset();
        DAI_ApproachController.discardTargetOwnership();
        DAI_InputState.setManagedOverride(true);
        stopHorizontalInput();

        DAI_ActionStatus.set(DAI_ActionResult.RUNNING);

        DAI_RuntimeTelemetry.scaffoldEvent(
                "scaffold_start",
                target,
                placedBlocks.size(),
                "maxBlocks=" + maxBlocks
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Started vertical scaffold recovery toward {} (generation={}, maxBlocks={}).",
                target,
                generation,
                maxBlocks
        );

        return true;
    }

    public static boolean startDescent(
            int timeoutTicks
    ) {

        Minecraft minecraft = Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || minecraft.gameMode == null
        ) {
            failStart("cannot descend without player, level, and game mode");
            return false;
        }

        if (active) {
            failStart("another scaffold operation is already active");
            return false;
        }

        generation = nextGeneration(generation);

        if (placedBlocks.isEmpty()) {
            rememberResult(generation, DAI_ActionResult.SUCCESS);
            DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
            return true;
        }

        if (
                !dimensionId.isEmpty()
                        && !dimensionId.equals(
                        minecraft.level.dimension().identifier().toString()
                )
        ) {
            failStart("recorded scaffold belongs to another dimension");
            return false;
        }

        mode = Mode.DESCEND;
        phase = Phase.WAIT_GROUND;
        active = true;
        ticksRemaining = timeoutTicks > 0
                ? timeoutTicks
                : DEFAULT_TIMEOUT_TICKS;
        phaseTicks = 0;
        placeAttempts = 0;

        DAI_PathController.reset();
        DAI_ApproachController.discardTargetOwnership();
        DAI_InputState.setManagedOverride(true);
        stopHorizontalInput();

        DAI_ActionStatus.set(DAI_ActionResult.RUNNING);

        DAI_RuntimeTelemetry.scaffoldEvent(
                "scaffold_descent_start",
                lastPlacedBlock(),
                placedBlocks.size(),
                ""
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Started controlled scaffold descent (generation={}, blocks={}).",
                generation,
                placedBlocks.size()
        );

        return true;
    }

    public static void tick() {

        if (!active) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || minecraft.gameMode == null
        ) {
            finish(DAI_ActionResult.FAILURE, "runtime context disappeared");
            return;
        }

        if (
                !dimensionId.isEmpty()
                        && !dimensionId.equals(
                        minecraft.level.dimension().identifier().toString()
                )
        ) {
            finish(DAI_ActionResult.FAILURE, "dimension changed during scaffold operation");
            return;
        }

        if (ticksRemaining-- <= 0) {
            finish(DAI_ActionResult.TIMED_OUT, "scaffold operation timed out");
            return;
        }

        stopHorizontalInput();

        if (mode == Mode.ASCEND) {
            tickAscent(minecraft);
        } else if (mode == Mode.DESCEND) {
            tickDescent(minecraft);
        }
    }

    private static void tickAscent(
            Minecraft minecraft
    ) {

        if (target == null) {
            finish(DAI_ActionResult.FAILURE, "selected target was lost");
            return;
        }

        if (
                minecraft.level.getBlockState(target).isAir()
        ) {
            finish(DAI_ActionResult.FAILURE, "selected target disappeared");
            return;
        }

        if (
                minecraft.player.getEyePosition()
                        .distanceTo(Vec3.atCenterOf(target))
                        <= TARGET_REACH_DISTANCE
        ) {
            finish(DAI_ActionResult.SUCCESS, "target entered interaction range");
            return;
        }

        if (placedBlocks.size() >= maxBlocks) {
            finish(DAI_ActionResult.FAILURE, "maximum scaffold height reached");
            return;
        }

        switch (phase) {

            case WAIT_GROUND -> {

                DAI_InputState.movement().setJump(false);

                if (!minecraft.player.onGround()) {
                    return;
                }

                stepBaseY = minecraft.player.getY();
                phaseTicks = 0;
                placeAttempts = 0;
                phase = Phase.JUMPING;
                DAI_InputState.movement().setJump(true);
            }

            case JUMPING -> {

                DAI_InputState.movement().setJump(true);
                phaseTicks++;

                if (
                        minecraft.player.getY()
                                < stepBaseY + JUMP_CLEARANCE
                ) {

                    if (
                            phaseTicks > 30
                                    && minecraft.player.onGround()
                    ) {
                        finish(DAI_ActionResult.FAILURE, "could not gain jump clearance for scaffold placement");
                    }

                    return;
                }

                DAI_InputState.movement().setJump(false);

                if (tryPlaceNextBlock(minecraft)) {
                    phaseTicks = 0;
                    phase = Phase.SETTLING;
                    return;
                }

                placeAttempts++;

                if (placeAttempts >= MAX_PLACE_ATTEMPTS) {
                    finish(DAI_ActionResult.FAILURE, "scaffold block placement failed repeatedly");
                }
            }

            case SETTLING -> {

                DAI_InputState.movement().setJump(false);
                phaseTicks++;

                if (
                        minecraft.player.onGround()
                                && minecraft.player.getY()
                                >= stepBaseY + 0.75D
                ) {
                    phaseTicks = 0;
                    phase = Phase.WAIT_GROUND;
                    return;
                }

                if (phaseTicks > 50) {
                    finish(DAI_ActionResult.FAILURE, "player did not settle onto the new scaffold block");
                }
            }

            default -> finish(
                    DAI_ActionResult.FAILURE,
                    "invalid ascent phase " + phase
            );
        }
    }

    private static boolean tryPlaceNextBlock(
            Minecraft minecraft
    ) {

        if (
                towerFeetOrigin == null
                        || minecraft.player == null
                        || minecraft.level == null
                        || minecraft.gameMode == null
        ) {
            return false;
        }

        int destinationY =
                towerFeetOrigin.getY()
                        + placedBlocks.size();

        BlockPos destination =
                new BlockPos(
                        towerFeetOrigin.getX(),
                        destinationY,
                        towerFeetOrigin.getZ()
                );

        BlockPos support = destination.below();

        if (destination.equals(target)) {
            return false;
        }

        BlockState destinationState =
                minecraft.level.getBlockState(destination);

        if (
                !destinationState.canBeReplaced()
                        || !destinationState.getFluidState().isEmpty()
        ) {
            return false;
        }

        if (
                minecraft.player.getBoundingBox()
                        .intersects(new AABB(destination))
        ) {
            return false;
        }

        BlockState supportState =
                minecraft.level.getBlockState(support);

        if (
                supportState.isAir()
                        || supportState.canBeReplaced()
                        || !supportState.getFluidState().isEmpty()
        ) {
            return false;
        }

        Identifier material = selectAvailableMaterial();

        if (material == null) {
            return false;
        }

        Vec3 hitLocation =
                Vec3.atCenterOf(support)
                        .add(0.0D, 0.5D, 0.0D);

        if (
                minecraft.player.getEyePosition()
                        .distanceTo(hitLocation)
                        > 5.25D
        ) {
            return false;
        }

        BlockHitResult hitResult =
                new BlockHitResult(
                        hitLocation,
                        Direction.UP,
                        support,
                        false
                );

        InteractionResult result =
                minecraft.gameMode.useItemOn(
                        minecraft.player,
                        InteractionHand.MAIN_HAND,
                        hitResult
                );

        if (!result.consumesAction()) {
            return false;
        }

        minecraft.player.swing(InteractionHand.MAIN_HAND);

        placedBlocks.add(destination.immutable());

        DAI_RuntimeTelemetry.scaffoldEvent(
                "scaffold_place",
                destination,
                placedBlocks.size(),
                material.toString()
        );

        DAI_Core.debug(
                "<DAI>: Placed vertical scaffold block '{}' at {} ({}/{}).",
                material,
                destination,
                placedBlocks.size(),
                maxBlocks
        );

        return true;
    }

    private static void tickDescent(
            Minecraft minecraft
    ) {

        if (placedBlocks.isEmpty()) {
            finish(DAI_ActionResult.SUCCESS, "scaffold cleanup completed");
            return;
        }

        BlockPos block = lastPlacedBlock();

        if (block == null) {
            finish(DAI_ActionResult.SUCCESS, "scaffold cleanup completed");
            return;
        }

        double horizontal = Math.sqrt(
                square(minecraft.player.getX() - (block.getX() + 0.5D))
                        + square(minecraft.player.getZ() - (block.getZ() + 0.5D))
        );

        if (horizontal > 1.65D) {
            finish(DAI_ActionResult.FAILURE, "player moved away from the recorded scaffold column");
            return;
        }

        if (minecraft.level.getBlockState(block).isAir()) {
            placedBlocks.removeLast();
            phase = Phase.FALLING;
            phaseTicks = 0;
            return;
        }

        switch (phase) {

            case WAIT_GROUND, FACE_BREAK -> {

                if (!minecraft.player.onGround()) {
                    phase = Phase.FALLING;
                    return;
                }

                DAI_ApproachController.faceBlock(block);

                if (!DAI_ApproachController.isLookingAtBlock(block)) {
                    phase = Phase.FACE_BREAK;
                    return;
                }

                if (!DAI_BreakController.isActive()) {
                    DAI_BreakController.breakOnce(block);
                }

                phase = Phase.BREAKING;
            }

            case BREAKING -> {

                if (DAI_BreakController.isActive()) {
                    return;
                }

                if (!minecraft.level.getBlockState(block).isAir()) {
                    phase = Phase.FACE_BREAK;
                    return;
                }

                placedBlocks.removeLast();
                phase = Phase.FALLING;
                phaseTicks = 0;

                DAI_RuntimeTelemetry.scaffoldEvent(
                        "scaffold_remove",
                        block,
                        placedBlocks.size(),
                        ""
                );
            }

            case FALLING -> {

                phaseTicks++;

                if (minecraft.player.onGround()) {
                    phase = Phase.WAIT_GROUND;
                    phaseTicks = 0;
                    return;
                }

                if (phaseTicks > 50) {
                    finish(DAI_ActionResult.FAILURE, "player did not settle during scaffold descent");
                }
            }

            default -> finish(
                    DAI_ActionResult.FAILURE,
                    "invalid descent phase " + phase
            );
        }
    }

    public static void reset() {

        if (active) {
            rememberResult(generation, DAI_ActionResult.CANCELLED);
        }

        active = false;
        mode = Mode.NONE;
        phase = Phase.IDLE;
        ticksRemaining = 0;
        phaseTicks = 0;
        placeAttempts = 0;
        target = null;
        towerFeetOrigin = null;
        dimensionId = "";
        placedBlocks.clear();
        stopHorizontalInput();
    }

    public static boolean isActive() {
        return active;
    }

    public static int generation() {
        return generation;
    }

    public static int usedCount() {
        return placedBlocks.size();
    }

    public static DAI_ActionResult resultForGeneration(
            int requestedGeneration
    ) {

        DAI_ActionResult result =
                resultHistory.get(requestedGeneration);

        return result == null
                ? DAI_ActionResult.FAILURE
                : result;
    }

    public static int availableMaterialCount() {

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            return 0;
        }

        Inventory inventory =
                minecraft.player.getInventory();

        int count = 0;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {

            ItemStack stack = inventory.getItem(slot);

            if (stack.isEmpty()) {
                continue;
            }

            Identifier id =
                    BuiltInRegistries.ITEM.getKey(
                            stack.getItem()
                    );

            if (containsMaterial(id)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    private static Identifier selectAvailableMaterial() {

        for (Identifier material : MATERIALS) {
            if (DAI_HotbarLogic.selectItem(material)) {
                return material;
            }
        }

        return null;
    }

    private static boolean containsMaterial(
            Identifier id
    ) {

        if (id == null) {
            return false;
        }

        for (Identifier material : MATERIALS) {
            if (material.equals(id)) {
                return true;
            }
        }

        return false;
    }

    private static BlockPos lastPlacedBlock() {

        return placedBlocks.isEmpty()
                ? null
                : placedBlocks.getLast();
    }

    private static void finish(
            DAI_ActionResult result,
            String reason
    ) {

        int finishedGeneration = generation;
        Mode finishedMode = mode;
        BlockPos finishedTarget = target;
        int remainingBlocks = placedBlocks.size();

        rememberResult(finishedGeneration, result);

        active = false;
        mode = Mode.NONE;
        phase = Phase.IDLE;
        ticksRemaining = 0;
        phaseTicks = 0;
        placeAttempts = 0;
        target = null;
        DAI_InputState.movement().setJump(false);
        stopHorizontalInput();

        if (
                finishedMode == Mode.DESCEND
                        && result == DAI_ActionResult.SUCCESS
        ) {
            towerFeetOrigin = null;
            dimensionId = "";
        }

        DAI_ActionStatus.set(result);

        DAI_RuntimeTelemetry.scaffoldEvent(
                finishedMode == Mode.DESCEND
                        ? "scaffold_descent_finish"
                        : "scaffold_finish",
                finishedTarget,
                remainingBlocks,
                "result=" + result + ";reason=" + reason
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Scaffold operation mode={} generation={} finished result={} remainingBlocks={}: {}.",
                finishedMode,
                finishedGeneration,
                result,
                remainingBlocks,
                reason
        );
    }

    private static void failNewGeneration(
            String reason
    ) {

        generation = nextGeneration(generation);
        rememberResult(generation, DAI_ActionResult.FAILURE);
        failStart(reason);
    }

    private static void failStart(
            String reason
    ) {

        DAI_ActionStatus.set(DAI_ActionResult.FAILURE);

        DAI_Core.debug(
                "<DAI>: Scaffold operation could not start: {}.",
                reason
        );
    }

    private static void rememberResult(
            int resultGeneration,
            DAI_ActionResult result
    ) {

        if (resultGeneration <= 0 || result == null) {
            return;
        }

        resultHistory.put(resultGeneration, result);

        while (resultHistory.size() > MAX_RESULT_HISTORY) {
            Integer first = resultHistory.keySet().iterator().next();
            resultHistory.remove(first);
        }
    }

    private static void stopHorizontalInput() {

        DAI_InputState.movement().setMovement(0.0F, 0.0F);
        DAI_InputState.movement().setSprint(false);
        DAI_InputState.movement().setSneak(false);
    }

    private static int nextGeneration(
            int current
    ) {
        return current == Integer.MAX_VALUE
                ? 1
                : current + 1;
    }

    private static double square(
            double value
    ) {
        return value * value;
    }

    private static Identifier[] ids(
            String... values
    ) {

        Identifier[] result = new Identifier[values.length];

        for (int index = 0; index < values.length; index++) {

            Identifier identifier = Identifier.tryParse(values[index]);

            if (identifier == null) {
                throw new IllegalArgumentException(
                        "Invalid scaffold material identifier: " + values[index]
                );
            }

            result[index] = identifier;
        }

        return result;
    }
}
