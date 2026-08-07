package io.github.j12h36h.dai.logic;

import io.github.j12h36h.dai.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.action.DAI_ActionResult;
import io.github.j12h36h.dai.action.DAI_ActionStatus;
import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.input.DAI_InputTargeting;
import io.github.j12h36h.dai.system.DAI_FailedTargetMemory;
import io.github.j12h36h.dai.system.DAI_TargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

public final class DAI_TargetLogic {

    private static final int DEFAULT_BLOCK_SEARCH_RADIUS =
            16;

    private static final int MAXIMUM_BLOCK_SEARCH_RADIUS =
            48;

    /*
     * Normal overworld acquisition should prefer the terrain layer the
     * player is actually occupying. Blocks exposed only inside unseen caves
     * must not beat ordinary nearby surface targets merely because their raw
     * 3D distance is slightly smaller.
     */
    private static final int PREFERRED_VERTICAL_RANGE =
            4;

    private static final int SURFACE_DEEP_TARGET_LIMIT =
            6;

    private static final double VERTICAL_DISTANCE_WEIGHT =
            4.0D;

    private DAI_TargetLogic() {
        // Utility class.
    }

    /**
     * Finds the nearest living entity, stores it as the selected
     * entity target, and rotates toward it.
     */
    public static void execute(
            DAI_ActionDefinition action
    ) {

        LivingEntity target =
                acquireNearestLiving();

        if (target == null) {
            return;
        }

        DAI_LookLogic.lookAt(
                target
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );
    }

    /**
     * Finds and selects the nearest living entity.
     *
     * @return the selected target, or null when no target was found
     */
    public static LivingEntity acquireNearestLiving() {

        LivingEntity target =
                DAI_InputTargeting.nearestLivingEntity();

        if (target == null) {

            DAI_TargetState.clearEntity();

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: No living target was found."
            );

            return null;
        }

        DAI_TargetState.select(
                target
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Selected living target '{}'.",
                target.getName().getString()
        );

        return target;
    }

    /**
     * Selects a supplied entity without performing a scan.
     */
    public static boolean select(
            Entity target
    ) {

        if (
                target == null
                        || target.isRemoved()
        ) {

            DAI_TargetState.clearEntity();

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            return false;
        }

        DAI_TargetState.select(
                target
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Selected supplied entity target '{}'.",
                target.getName().getString()
        );

        return true;
    }

    /**
     * Runs the recognition-definition system against the block under
     * the player's crosshair.
     */
    public static void recognizeTarget(
            DAI_ActionDefinition action
    ) {

        List<?> matches =
                DAI_RecognitionLogic.recognizeTarget();

        DAI_ActionStatus.set(
                matches.isEmpty()
                        ? DAI_ActionResult.FAILURE
                        : DAI_ActionResult.SUCCESS
        );
    }

    /**
     * Finds the nearest exposed block matching an identifier or block
     * tag and stores it in DAI_TargetState.
     */
    public static void recognizeBlock(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_TargetState.clearBlock();

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot recognize a block without an active player and level."
            );

            return;
        }

        if (
                action == null
                        || !action.hasAction()
        ) {

            DAI_TargetState.clearBlock();

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: recognize_block requires a block id or block tag in 'action'."
            );

            return;
        }

        String requestedTarget =
                action.action()
                        .trim();

        if (
                createBlockMatcher(
                        requestedTarget
                ) == null
        ) {

            DAI_TargetState.clearBlock();

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Invalid recognize_block target '{}'.",
                    requestedTarget
            );

            return;
        }

        int searchRadius =
                action.value() > 0.0D
                        ? Mth.clamp(
                        (int) Math.round(
                                action.value()
                        ),
                        1,
                        MAXIMUM_BLOCK_SEARCH_RADIUS
                )
                        : DEFAULT_BLOCK_SEARCH_RADIUS;

        boolean found =
                findAndSelectBlock(
                        requestedTarget,
                        searchRadius
                );

        DAI_ActionStatus.set(
                found
                        ? DAI_ActionResult.SUCCESS
                        : DAI_ActionResult.FAILURE
        );
    }

    /**
     * Searches for and selects the nearest exposed matching block.
     *
     * This method does not modify DAI_ActionStatus so it can safely
     * be reused by persistent controllers such as exploration.
     *
     * @return true when a matching block was selected
     */
    public static boolean findAndSelectBlock(
            String requestedTarget,
            int requestedRadius
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_TargetState.clearBlock();

            return false;
        }

        BlockMatcher matcher =
                createBlockMatcher(
                        requestedTarget
                );

        if (matcher == null) {

            DAI_TargetState.clearBlock();

            return false;
        }

        int searchRadius =
                Mth.clamp(
                        requestedRadius > 0
                                ? requestedRadius
                                : DEFAULT_BLOCK_SEARCH_RADIUS,
                        1,
                        MAXIMUM_BLOCK_SEARCH_RADIUS
                );

        BlockPos playerPosition =
                minecraft.player
                        .blockPosition();

        BlockPos nearestPosition =
                null;

        double bestScore =
                Double.MAX_VALUE;

        boolean playerNearSurface =
                isNearSurface(
                        minecraft,
                        playerPosition
                );

        BlockPos minimum =
                playerPosition.offset(
                        -searchRadius,
                        -searchRadius,
                        -searchRadius
                );

        BlockPos maximum =
                playerPosition.offset(
                        searchRadius,
                        searchRadius,
                        searchRadius
                );

        for (
                BlockPos candidate
                : BlockPos.betweenClosed(
                minimum,
                maximum
        )
        ) {

            BlockState state =
                    minecraft.level.getBlockState(
                            candidate
                    );

            if (
                    state.isAir()
                            || !matcher.matches(
                            state
                    )
                            || !isExposed(
                            minecraft,
                            candidate
                    )
                            || DAI_FailedTargetMemory.contains(
                            candidate
                    )
            ) {
                continue;
            }

            int verticalDifference =
                    candidate.getY()
                            - playerPosition.getY();

            /*
             * While the player is on or near the surface, reject blocks that
             * are substantially below the current terrain layer and have no
             * sky access. Such blocks are usually cave-wall exposure that the
             * player cannot actually see or reasonably know about yet.
             *
             * Once the player is genuinely underground, cave targets remain
             * legal so mining/cave gameplay is not disabled.
             */
            if (
                    playerNearSurface
                            && verticalDifference
                            < -SURFACE_DEEP_TARGET_LIMIT
                            && !hasSurfaceExposure(
                            minecraft,
                            candidate
                    )
            ) {
                continue;
            }

            double deltaX =
                    candidate.getX()
                            - playerPosition.getX();

            double deltaZ =
                    candidate.getZ()
                            - playerPosition.getZ();

            double horizontalDistanceSquared =
                    deltaX * deltaX
                            + deltaZ * deltaZ;

            double verticalPenalty =
                    Math.abs(
                            verticalDifference
                    )
                            <= PREFERRED_VERTICAL_RANGE
                            ? 0.0D
                            : (
                            Math.abs(
                                    verticalDifference
                            )
                                    - PREFERRED_VERTICAL_RANGE
                    )
                            * (
                            Math.abs(
                                    verticalDifference
                            )
                                    - PREFERRED_VERTICAL_RANGE
                    )
                            * VERTICAL_DISTANCE_WEIGHT;

            double score =
                    horizontalDistanceSquared
                            + verticalPenalty;

            if (score >= bestScore) {
                continue;
            }

            bestScore =
                    score;

            nearestPosition =
                    candidate.immutable();
        }

        if (nearestPosition == null) {

            DAI_TargetState.clearBlock();

            DAI_Core.LOGGER.debug(
                    "<DAI>: No exposed block matching '{}' was found within {} block(s).",
                    requestedTarget,
                    searchRadius
            );

            return false;
        }

        DAI_TargetState.selectBlock(
                nearestPosition
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Selected nearest exposed block matching '{}' at {} (distance={}).",
                requestedTarget,
                nearestPosition,
                String.format(
                        Locale.ROOT,
                        "%.2f",
                        Math.sqrt(
                                nearestPosition.distSqr(
                                        playerPosition
                                )
                        )
                )
        );

        return true;
    }

    /**
     * Clears both entity and block targets.
     */
    public static void clear() {

        DAI_TargetState.clear();

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Cleared all selected targets."
        );
    }

    public static void clearEntity() {

        DAI_TargetState.clearEntity();

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Cleared selected entity target."
        );
    }

    public static void clearBlock() {

        DAI_TargetState.clearBlock();

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Cleared selected block target."
        );
    }

    /**
     * Returns true when the player is close enough to open sky that normal
     * target acquisition should behave as surface gameplay.
     *
     * Checking several blocks above the player prevents a single leaf,
     * overhang, or small roof from immediately classifying the player as
     * underground.
     */
    private static boolean isNearSurface(
            Minecraft minecraft,
            BlockPos playerPosition
    ) {

        if (
                minecraft.level == null
                        || playerPosition == null
        ) {
            return false;
        }

        for (
                int offset = 0;
                offset <= 8;
                offset++
        ) {

            if (
                    minecraft.level.canSeeSky(
                            playerPosition.above(
                                    offset
                            )
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    /**
     * True when the candidate's exposed area connects directly to open sky.
     *
     * Cave walls can be "exposed" because they border cave air, but they do
     * not have surface exposure. This distinction prevents unseen caves from
     * becoming ordinary surface-gathering targets.
     */
    private static boolean hasSurfaceExposure(
            Minecraft minecraft,
            BlockPos position
    ) {

        if (
                minecraft.level == null
                        || position == null
        ) {
            return false;
        }

        if (
                minecraft.level.canSeeSky(
                        position.above()
                )
        ) {
            return true;
        }

        for (
                Direction direction
                : Direction.values()
        ) {

            BlockPos adjacent =
                    position.relative(
                            direction
                    );

            if (
                    minecraft.level.canSeeSky(
                            adjacent
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private static boolean isExposed(
            Minecraft minecraft,
            BlockPos position
    ) {

        if (minecraft.level == null) {
            return false;
        }

        for (
                Direction direction
                : Direction.values()
        ) {

            BlockPos adjacent =
                    position.relative(
                            direction
                    );

            BlockState adjacentState =
                    minecraft.level.getBlockState(
                            adjacent
                    );

            if (
                    adjacentState.isAir()
                            || adjacentState.canBeReplaced()
            ) {
                return true;
            }
        }

        return false;
    }

    private static BlockMatcher createBlockMatcher(
            String requestedTarget
    ) {

        if (
                requestedTarget == null
                        || requestedTarget.isBlank()
        ) {
            return null;
        }

        String normalized =
                requestedTarget.trim();

        if (normalized.startsWith("#")) {

            Identifier tagId =
                    parseBlockIdentifier(
                            normalized.substring(
                                    1
                            )
                    );

            if (tagId == null) {
                return null;
            }

            TagKey<Block> blockTag =
                    TagKey.create(
                            Registries.BLOCK,
                            tagId
                    );

            return state ->
                    state.is(
                            blockTag
                    );
        }

        Identifier blockId =
                parseBlockIdentifier(
                        normalized
                );

        if (blockId == null) {
            return null;
        }

        Block block =
                BuiltInRegistries.BLOCK.getValue(
                        blockId
                );

        if (
                block == null
                        || block == Blocks.AIR
        ) {
            return null;
        }

        return state ->
                state.is(
                        block
                );
    }

    private static Identifier parseBlockIdentifier(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {
            return null;
        }

        String normalized =
                value.trim();

        if (!normalized.contains(":")) {

            normalized =
                    "minecraft:"
                            + normalized;
        }

        return Identifier.tryParse(
                normalized
        );
    }

    @FunctionalInterface
    private interface BlockMatcher {

        boolean matches(
                BlockState state
        );
    }
}