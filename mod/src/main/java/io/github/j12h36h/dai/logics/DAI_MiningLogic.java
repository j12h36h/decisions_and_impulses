package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.condition.DAI_ConditionDefinition;
import io.github.j12h36h.dai.logics.controller.DAI_ApproachController;
import io.github.j12h36h.dai.logics.controller.DAI_BreakController;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.menus.system.DAI_TargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public final class DAI_MiningLogic {

    private static final double DEFAULT_SEARCH_RADIUS =
            16.0D;

    private static final double DEFAULT_STOP_DISTANCE =
            3.25D;

    private static final int DEFAULT_APPROACH_TIMEOUT =
            200;

    private static final int DEFAULT_ALIGNMENT_RETRIES =
            20;

    private static final int TOOL_SWITCH_DELAY =
            2;

    private static final DAI_ConditionDefinition LAST_ACTION_SUCCESS =
            new DAI_ConditionDefinition(
                    "last_action_success"
            );

    private DAI_MiningLogic() {
        // Utility class.
    }

    /*
     * ------------------------------------------------------------
     * AUTONOMOUS MINING
     * ------------------------------------------------------------
     */

    /**
     * Builds the complete autonomous mining sequence:
     *
     * recognize block
     * -> approach block
     * -> wait for approach
     * -> align camera
     * -> mine exact selected block
     * -> collect drops
     */
    public static void mineNearestBlock(
            DAI_ActionDefinition action
    ) {

        if (
                action == null
                        || !action.hasAction()
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: mine_nearest_block requires a block id or block tag in 'action'."
            );

            return;
        }

        double searchRadius =
                action.value() > 0.0D
                        ? action.value()
                        : DEFAULT_SEARCH_RADIUS;

        int timeoutTicks =
                action.ticks() > 0
                        ? action.ticks()
                        : DEFAULT_APPROACH_TIMEOUT;

        /*
         * Many datapack objectives already place collect_nearby_items
         * immediately after mine_nearest_block. Reuse that continuation
         * rather than inserting a second collection pass. If the caller's
         * collector is generic, specialize it to the resource being mined so
         * unrelated drops cannot attract the player.
         */
        boolean callerAlreadyCollects =
                specializeQueuedCollector(
                        action.action()
                );

        List<DAI_ActionDefinition> miningSequence =
                new java.util.ArrayList<>();

        miningSequence.add(
                createAction(
                        "recognize_block",
                        action.action(),
                        0,
                        searchRadius
                )
        );

        miningSequence.add(
                createSuccessAction(
                        "approach_target_block",
                        "",
                        timeoutTicks,
                        DEFAULT_STOP_DISTANCE
                )
        );

        miningSequence.add(
                createSuccessAction(
                        "wait_for_approach",
                        "",
                        timeoutTicks,
                        0.0D
                )
        );

        miningSequence.add(
                createSuccessAction(
                        "wait_for_target_block",
                        "",
                        DEFAULT_ALIGNMENT_RETRIES,
                        0.0D
                )
        );

        miningSequence.add(
                createSuccessAction(
                        "mine_targeted_block",
                        "",
                        DEFAULT_ALIGNMENT_RETRIES,
                        0.0D
                )
        );

        if (!callerAlreadyCollects) {

            miningSequence.add(
                    createSuccessAction(
                            "collect_nearby_items",
                            action.action(),
                            120,
                            6.0D
                    )
            );
        }

        DAI_ActionQueue.enqueueFirstAll(
                miningSequence
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.debug(
                "<DAI>: Queued nearest-block mining for '{}' within radius {}.",
                action.action(),
                searchRadius
        );
    }

    private static boolean specializeQueuedCollector(
            String requestedItem
    ) {

        DAI_ActionDefinition next =
                DAI_ActionQueue.peek();

        if (
                next == null
                        || !"collect_nearby_items".equals(
                        next.type()
                )
        ) {
            return false;
        }

        if (next.hasAction()) {
            return true;
        }

        DAI_ActionDefinition filtered =
                new DAI_ActionDefinition(
                        next.type(),
                        requestedItem,
                        next.conditions(),
                        next.sequence(),
                        next.menu(),
                        next.open(),
                        next.yaw(),
                        next.pitch(),
                        next.direction(),
                        next.ticks(),
                        next.slot(),
                        next.state(),
                        next.value()
                );

        return DAI_ActionQueue.replaceHead(
                filtered
        );
    }

    /*
     * ------------------------------------------------------------
     * TARGETED MINING
     * ------------------------------------------------------------
     */

    /**
     * Prepares the exact interaction target for mining.
     *
     * The target position is encoded into the queued targeted-break action
     * so it cannot be replaced by a different crosshair block during the
     * tool-switch delay.
     */
    public static void mineTargetedBlock(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot mine without an active player and level."
            );

            return;
        }

        BlockPos selectedBlock =
                DAI_ApproachController.interactionTarget();

        if (selectedBlock == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot mine because no block interaction target is available."
            );

            return;
        }

        selectedBlock =
                selectedBlock.immutable();

        BlockState blockState =
                minecraft.level.getBlockState(
                        selectedBlock
                );

        if (blockState.isAir()) {

            DAI_TargetState.clearBlock();

            DAI_ActionStatus.set(
                    DAI_ActionResult.SUCCESS
            );

            DAI_Core.debug(
                    "<DAI>: Mining target {} is already gone.",
                    selectedBlock
            );

            return;
        }

        if (
                !isLookingAtSelectedBlock(
                        minecraft,
                        selectedBlock
                )
        ) {

            retryAlignment(
                    action,
                    selectedBlock
            );

            return;
        }

        String toolType =
                determineToolType(
                        blockState
                );

        if (!toolType.isEmpty()) {

            DAI_Core.debug(
                    "<DAI>: Mining target '{}' at {} with preferred tool type '{}'.",
                    blockState.getBlock()
                            .builtInRegistryHolder()
                            .key()
                            .identifier(),
                    selectedBlock,
                    toolType
            );

            /*
             * Failure to find a preferred tool does not prevent Minecraft
             * from attempting the break using the currently held item.
             */
            equipBestTool(
                    toolType
            );

        } else {

            DAI_Core.debug(
                    "<DAI>: Mining target '{}' at {} with the current item.",
                    blockState.getBlock()
                            .builtInRegistryHolder()
                            .key()
                            .identifier(),
                    selectedBlock
            );
        }

        /*
         * Preserve the exact target through the hotbar synchronization
         * delay.
         *
         * Previously this queued generic break_once, which forgot the
         * selected BlockPos and later rediscovered a target from hitResult.
         */
        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        createAction(
                                "delay",
                                "",
                                TOOL_SWITCH_DELAY,
                                0.0D
                        ),
                        createAction(
                                "break_targeted_once",
                                encodeBlockPos(
                                        selectedBlock
                                ),
                                0,
                                0.0D
                        )
                )
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.debug(
                "<DAI>: Queued exact targeted break for {} after {} tool-sync tick(s).",
                selectedBlock,
                TOOL_SWITCH_DELAY
        );
    }

    /**
     * Executes the exact BlockPos captured by mineTargetedBlock().
     *
     * The encoded position is authoritative. The breaker still requires
     * Minecraft's crosshair to actually be on that same block before it
     * starts destroying it, but it can never silently switch to a different
     * block.
     */
    public static void breakTargetedOnce(
            DAI_ActionDefinition action
    ) {

        if (
                action == null
                        || !action.hasAction()
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: break_targeted_once requires an encoded block position."
            );

            return;
        }

        BlockPos target =
                parseBlockPos(
                        action.action()
                );

        if (target == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Invalid targeted-break block position '{}'.",
                    action.action()
            );

            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot execute targeted block breaking without an active player and level."
            );

            return;
        }

        if (
                minecraft.level
                        .getBlockState(
                                target
                        )
                        .isAir()
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.SUCCESS
            );

            DAI_Core.debug(
                    "<DAI>: Targeted-break block {} is already gone.",
                    target
            );

            return;
        }

        /*
         * Re-align with the exact target after the hotbar delay.
         *
         * This does not change target ownership; it simply restores camera
         * alignment if switching tools or another client update moved the
         * hit result slightly.
         */
        if (
                !isLookingAtSelectedBlock(
                        minecraft,
                        target
                )
        ) {

            DAI_ApproachController.faceBlock(
                    target
            );

            DAI_ActionStatus.set(
                    DAI_ActionResult.RUNNING
            );

            DAI_ActionQueue.enqueueFirstAll(
                    List.of(
                            createAction(
                                    "delay",
                                    "",
                                    1,
                                    0.0D
                            ),
                            createAction(
                                    "break_targeted_once",
                                    encodeBlockPos(
                                            target
                                    ),
                                    0,
                                    0.0D
                            )
                    )
            );

            DAI_Core.debug(
                    "<DAI>: Exact mining target {} moved off the crosshair after tool synchronization; realigning before breaking.",
                    target
            );

            return;
        }

        DAI_BreakController.breakOnce(
                target
        );
    }

    /*
     * ------------------------------------------------------------
     * TOOL SELECTION
     * ------------------------------------------------------------
     */

    /**
     * Selects the preferred tool for whichever block is currently under
     * the player's crosshair.
     */
    public static void equipBestToolForBlock(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot equip a tool without an active player and level."
            );

            return;
        }

        if (
                !(
                        minecraft.hitResult
                                instanceof BlockHitResult blockHitResult
                )
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.debug(
                    "<DAI>: Cannot equip a tool because no block is targeted."
            );

            return;
        }

        BlockState blockState =
                minecraft.level.getBlockState(
                        blockHitResult.getBlockPos()
                );

        if (blockState.isAir()) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.debug(
                    "<DAI>: Cannot equip a tool because the targeted block is air."
            );

            return;
        }

        String toolType =
                determineToolType(
                        blockState
                );

        if (toolType.isEmpty()) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.SUCCESS
            );

            DAI_Core.debug(
                    "<DAI>: Targeted block '{}' does not require a supported tool.",
                    blockState.getBlock()
                            .builtInRegistryHolder()
                            .key()
                            .identifier()
            );

            return;
        }

        DAI_Core.debug(
                "<DAI>: Targeted block '{}' selected tool type '{}'.",
                blockState.getBlock()
                        .builtInRegistryHolder()
                        .key()
                        .identifier(),
                toolType
        );

        boolean equipped =
                equipBestTool(
                        toolType
                );

        DAI_ActionStatus.set(
                equipped
                        ? DAI_ActionResult.SUCCESS
                        : DAI_ActionResult.FAILURE
        );
    }

    /*
     * ------------------------------------------------------------
     * BASIC BREAK ACTIONS
     * ------------------------------------------------------------
     */

    /**
     * Generic crosshair-driven break.
     */
    public static void breakOnce(
            DAI_ActionDefinition action
    ) {

        DAI_BreakController.breakOnce();
    }

    public static void breakStart(
            DAI_ActionDefinition action
    ) {

        DAI_BreakController.start();
    }

    public static void breakStop(
            DAI_ActionDefinition action
    ) {

        DAI_BreakController.stop();
    }

    /*
     * ------------------------------------------------------------
     * ALIGNMENT
     * ------------------------------------------------------------
     */

    private static boolean isLookingAtSelectedBlock(
            Minecraft minecraft,
            BlockPos selectedBlock
    ) {

        return minecraft.hitResult
                instanceof BlockHitResult blockHitResult
                && selectedBlock.equals(
                blockHitResult.getBlockPos()
        );
    }

    private static void retryAlignment(
            DAI_ActionDefinition action,
            BlockPos selectedBlock
    ) {

        int retriesRemaining =
                action != null
                        && action.ticks() > 0
                        ? action.ticks()
                        : DEFAULT_ALIGNMENT_RETRIES;

        if (retriesRemaining <= 1) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.TIMED_OUT
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not align with mining target {}.",
                    selectedBlock
            );

            return;
        }

        DAI_ApproachController.faceBlock(
                selectedBlock
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.debug(
                "<DAI>: Mining target {} is not under the crosshair; retrying with {} check(s) remaining.",
                selectedBlock,
                retriesRemaining - 1
        );

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        createAction(
                                "delay",
                                "",
                                1,
                                0.0D
                        ),
                        createAction(
                                "mine_targeted_block",
                                "",
                                retriesRemaining - 1,
                                0.0D
                        )
                )
        );
    }

    /*
     * ------------------------------------------------------------
     * TOOL TYPE
     * ------------------------------------------------------------
     */

    private static String determineToolType(
            BlockState blockState
    ) {

        if (blockState.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return "pickaxe";
        }

        if (blockState.is(BlockTags.MINEABLE_WITH_AXE)) {
            return "axe";
        }

        if (blockState.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return "shovel";
        }

        if (blockState.is(BlockTags.MINEABLE_WITH_HOE)) {
            return "hoe";
        }

        return "";
    }

    /**
     * Attempts to equip the best supported tool of the requested type.
     */
    private static boolean equipBestTool(
            String toolType
    ) {

        Identifier[] candidates =
                switch (toolType) {

                    case "pickaxe" ->
                            identifiers(
                                    "minecraft:netherite_pickaxe",
                                    "minecraft:diamond_pickaxe",
                                    "minecraft:iron_pickaxe",
                                    "minecraft:stone_pickaxe",
                                    "minecraft:golden_pickaxe",
                                    "minecraft:wooden_pickaxe"
                            );

                    case "axe" ->
                            identifiers(
                                    "minecraft:netherite_axe",
                                    "minecraft:diamond_axe",
                                    "minecraft:iron_axe",
                                    "minecraft:stone_axe",
                                    "minecraft:golden_axe",
                                    "minecraft:wooden_axe"
                            );

                    case "shovel" ->
                            identifiers(
                                    "minecraft:netherite_shovel",
                                    "minecraft:diamond_shovel",
                                    "minecraft:iron_shovel",
                                    "minecraft:stone_shovel",
                                    "minecraft:golden_shovel",
                                    "minecraft:wooden_shovel"
                            );

                    case "hoe" ->
                            identifiers(
                                    "minecraft:netherite_hoe",
                                    "minecraft:diamond_hoe",
                                    "minecraft:iron_hoe",
                                    "minecraft:stone_hoe",
                                    "minecraft:golden_hoe",
                                    "minecraft:wooden_hoe"
                            );

                    default ->
                            null;
                };

        if (candidates == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Unsupported tool type '{}'.",
                    toolType
            );

            return false;
        }

        for (Identifier candidate : candidates) {

            if (
                    DAI_HotbarLogic.selectItem(
                            candidate
                    )
            ) {

                DAI_Core.debug(
                        "<DAI>: Equipped '{}' as the best available {}.",
                        candidate,
                        toolType
                );

                return true;
            }
        }

        DAI_Core.debug(
                "<DAI>: No supported {} was found in the player inventory.",
                toolType
        );

        return false;
    }

    /*
     * ------------------------------------------------------------
     * BLOCK POSITION ENCODING
     * ------------------------------------------------------------
     */

    private static String encodeBlockPos(
            BlockPos position
    ) {

        return position.getX()
                + ","
                + position.getY()
                + ","
                + position.getZ();
    }

    private static BlockPos parseBlockPos(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {
            return null;
        }

        String[] parts =
                value.trim()
                        .split(
                                ","
                        );

        if (parts.length != 3) {
            return null;
        }

        try {

            int x =
                    Integer.parseInt(
                            parts[0].trim()
                    );

            int y =
                    Integer.parseInt(
                            parts[1].trim()
                    );

            int z =
                    Integer.parseInt(
                            parts[2].trim()
                    );

            return new BlockPos(
                    x,
                    y,
                    z
            );

        } catch (NumberFormatException exception) {

            return null;
        }
    }

    /*
     * ------------------------------------------------------------
     * IDENTIFIERS
     * ------------------------------------------------------------
     */

    private static Identifier[] identifiers(
            String... values
    ) {

        Identifier[] identifiers =
                new Identifier[values.length];

        for (
                int index = 0;
                index < values.length;
                index++
        ) {

            Identifier identifier =
                    Identifier.tryParse(
                            values[index]
                    );

            if (identifier == null) {

                throw new IllegalArgumentException(
                        "Invalid built-in identifier: "
                                + values[index]
                );
            }

            identifiers[index] =
                    identifier;
        }

        return identifiers;
    }

    /*
     * ------------------------------------------------------------
     * ACTION FACTORIES
     * ------------------------------------------------------------
     */

    private static DAI_ActionDefinition createAction(
            String type,
            String action,
            int ticks,
            double value
    ) {

        return createAction(
                type,
                action,
                ticks,
                value,
                List.of()
        );
    }

    private static DAI_ActionDefinition createSuccessAction(
            String type,
            String action,
            int ticks,
            double value
    ) {

        return createAction(
                type,
                action,
                ticks,
                value,
                List.of(
                        LAST_ACTION_SUCCESS
                )
        );
    }

    private static DAI_ActionDefinition createAction(
            String type,
            String action,
            int ticks,
            double value,
            List<DAI_ConditionDefinition> conditions
    ) {

        return new DAI_ActionDefinition(
                type,
                action,
                conditions,
                List.of(),
                "",
                "",
                0.0F,
                0.0F,
                "",
                ticks,
                0,
                false,
                value
        );
    }
}