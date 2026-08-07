package io.github.j12h36h.dai.condition;

import io.github.j12h36h.dai.system.DAI_TargetState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class DAI_ConditionsBlock {

    private DAI_ConditionsBlock() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "block_at_offset",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !context.hasPlayer()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    BlockPos offset =
                            parseOffset(
                                    condition.parameter()
                            );

                    if (offset == null) {
                        return DAI_ConditionValue.missing();
                    }

                    BlockPos position =
                            context.player()
                                    .blockPosition()
                                    .offset(
                                            offset.getX(),
                                            offset.getY(),
                                            offset.getZ()
                                    );

                    BlockState state =
                            context.level()
                                    .getBlockState(position);

                    return blockId(state);
                }
        );

        DAI_ConditionRegistry.register(
                "targeted_block_exists",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                context.hitResult()
                                        instanceof BlockHitResult
                        )
        );

        DAI_ConditionRegistry.register(
                "targeted_block",
                (context, condition) -> {

                    BlockState state =
                            targetedBlockState(context);

                    if (state == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.string(
                            state.getBlock()
                                    .builtInRegistryHolder()
                                    .key()
                                    .identifier()
                                    .toString()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "targeted_block_breakable",
                (context, condition) -> {

                    BlockState state =
                            targetedBlockState(context);

                    if (
                            state == null
                                    || !context.hasLevel()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    BlockPos position =
                            targetedBlockPosition(context);

                    if (position == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            state.getDestroySpeed(
                                    context.level(),
                                    position
                            ) >= 0.0F
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "targeted_block_air",
                (context, condition) -> {

                    BlockState state =
                            targetedBlockState(context);

                    if (state == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            state.isAir()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "block_below",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !context.hasPlayer()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    BlockState state =
                            context.level()
                                    .getBlockState(
                                            context.player()
                                                    .blockPosition()
                                                    .below()
                                    );

                    return blockId(state);
                }
        );

        DAI_ConditionRegistry.register(
                "block_above",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !context.hasPlayer()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    BlockState state =
                            context.level()
                                    .getBlockState(
                                            context.player()
                                                    .blockPosition()
                                                    .above()
                                    );

                    return blockId(state);
                }
        );

        DAI_ConditionRegistry.register(
                "block_at_feet",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !context.hasPlayer()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    BlockState state =
                            context.level()
                                    .getBlockState(
                                            context.player()
                                                    .blockPosition()
                                    );

                    return blockId(state);
                }
        );

        DAI_ConditionRegistry.register(
                "target_block_selected",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                DAI_TargetState.selectedBlock()
                                        != null
                        )
        );
    }

    private static BlockState targetedBlockState(
            DAI_ConditionContext context
    ) {

        if (
                !context.hasLevel()
                        || !(context.hitResult()
                        instanceof BlockHitResult blockHitResult)
        ) {
            return null;
        }

        return context.level()
                .getBlockState(
                        blockHitResult.getBlockPos()
                );
    }

    private static BlockPos targetedBlockPosition(
            DAI_ConditionContext context
    ) {

        if (
                context.hitResult()
                        instanceof BlockHitResult blockHitResult
        ) {
            return blockHitResult.getBlockPos();
        }

        return null;
    }

    private static DAI_ConditionValue blockId(
            BlockState state
    ) {

        if (state == null) {
            return DAI_ConditionValue.missing();
        }

        return DAI_ConditionValue.string(
                state.getBlock()
                        .builtInRegistryHolder()
                        .key()
                        .identifier()
                        .toString()
        );
    }
    private static BlockPos parseOffset(
            String value
    ) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String[] parts =
                value.trim()
                        .split(",");

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
}
