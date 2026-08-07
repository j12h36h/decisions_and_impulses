package io.github.j12h36h.dai.logic;

import io.github.j12h36h.dai.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.action.DAI_ActionQueue;
import io.github.j12h36h.dai.action.DAI_ActionResult;
import io.github.j12h36h.dai.action.DAI_ActionStatus;
import io.github.j12h36h.dai.condition.DAI_ConditionDefinition;
import io.github.j12h36h.dai.controller.DAI_ApproachController;
import io.github.j12h36h.dai.controller.DAI_BreakController;
import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.system.DAI_TargetState;
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

    /**
     * Builds the complete autonomous mining sequence:
     *
     * recognize block
     * -> approach block
     * -> wait for approach
     * -> align camera
     * -> mine selected block
     *
     * Each stage after recognition only executes when the previous
     * stage completed successfully.
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

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        createAction(
                                "recognize_block",
                                action.action(),
                                0,
                                searchRadius
                        ),
                        createSuccessAction(
                                "approach_target_block",
                                "",
                                timeoutTicks,
                                DEFAULT_STOP_DISTANCE
                        ),
                        createSuccessAction(
                                "wait_for_approach",
                                "",
                                timeoutTicks,
                                0.0D
                        ),
                        createSuccessAction(
                                "wait_for_target_block",
                                "",
                                DEFAULT_ALIGNMENT_RETRIES,
                                0.0D
                        ),
                        createSuccessAction(
                                "mine_targeted_block",
                                "",
                                DEFAULT_ALIGNMENT_RETRIES,
                                0.0D
                        ),
                        createSuccessAction(
                                "collect_nearby_items",
                                "",
                                120,
                                6.0D
                        )
                )
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Queued nearest-block mining for '{}' within radius {}.",
                action.action(),
                searchRadius
        );
    }

    /**
     * Mines the block currently stored in DAI_TargetState.
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
                    "<DAI>: Cannot mine because no block target is selected."
            );

            return;
        }

        BlockState blockState =
                minecraft.level.getBlockState(
                        selectedBlock
                );

        if (blockState.isAir()) {

            DAI_TargetState.clearBlock();

            DAI_ActionStatus.set(
                    DAI_ActionResult.SUCCESS
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Selected block {} is already gone.",
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

            DAI_Core.LOGGER.debug(
                    "<DAI>: Mining selected block '{}' at {} with preferred tool type '{}'.",
                    blockState.getBlock()
                            .builtInRegistryHolder()
                            .key()
                            .identifier(),
                    selectedBlock,
                    toolType
            );

            /*
             * Not finding a preferred tool does not prevent Minecraft
             * from attempting to break the block with the held item.
             */
            equipBestTool(
                    toolType
            );

        } else {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Mining selected block '{}' at {} with the current item.",
                    blockState.getBlock()
                            .builtInRegistryHolder()
                            .key()
                            .identifier(),
                    selectedBlock
            );
        }

        /*
         * Allow the selected hotbar slot to synchronize before sending
         * the one-shot break request.
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
                                "break_once",
                                "",
                                0,
                                0.0D
                        )
                )
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );
    }

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

            DAI_Core.LOGGER.debug(
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

            DAI_Core.LOGGER.debug(
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

            DAI_Core.LOGGER.debug(
                    "<DAI>: Targeted block '{}' does not require a supported tool.",
                    blockState.getBlock()
                            .builtInRegistryHolder()
                            .key()
                            .identifier()
            );

            return;
        }

        DAI_Core.LOGGER.debug(
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
                    "<DAI>: Could not align with selected block {} for mining.",
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

        DAI_Core.LOGGER.debug(
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
     *
     * @return true when a matching tool was selected; otherwise false.
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

                DAI_Core.LOGGER.debug(
                        "<DAI>: Equipped '{}' as the best available {}.",
                        candidate,
                        toolType
                );

                return true;
            }
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: No supported {} was found in the player inventory.",
                toolType
        );

        return false;
    }

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