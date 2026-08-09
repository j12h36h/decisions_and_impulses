package io.github.j12h36h.dai.logics.condition;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;

public final class DAI_ConditionsNavigation {

    private static final int DEFAULT_SAFE_DROP = 2;
    private static final int DEFAULT_HEADROOM = 2;

    private DAI_ConditionsNavigation() {
        // Utility class.
    }

    public static void registerAll() {

        /*
         * True when the player can walk straight forward without
         * colliding with a block at body or head height.
         */
        DAI_ConditionRegistry.register(
                "path_forward_clear",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasLevel()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    ForwardPositions positions =
                            positions(context);

                    return DAI_ConditionValue.bool(
                            isPassable(
                                    context,
                                    positions.forwardFeet()
                            )
                                    && isPassable(
                                    context,
                                    positions.forwardHead()
                            )
                    );
                }
        );

        /*
         * True when a one-block step is directly ahead and the
         * two blocks above it are clear.
         */
        DAI_ConditionRegistry.register(
                "path_forward_step_up",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasLevel()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    ForwardPositions positions =
                            positions(context);

                    boolean obstacleAtFeet =
                            isSupporting(
                                    context,
                                    positions.forwardFeet()
                            );

                    boolean bodyClear =
                            isPassable(
                                    context,
                                    positions.forwardHead()
                            );

                    boolean headClear =
                            isPassable(
                                    context,
                                    positions.forwardAboveHead()
                            );

                    return DAI_ConditionValue.bool(
                            obstacleAtFeet
                                    && bodyClear
                                    && headClear
                    );
                }
        );

        /*
         * True when the forward route is blocked and cannot be
         * handled as a normal one-block step.
         */
        DAI_ConditionRegistry.register(
                "path_forward_blocked",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasLevel()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    ForwardPositions positions =
                            positions(context);

                    boolean feetBlocked =
                            !isPassable(
                                    context,
                                    positions.forwardFeet()
                            );

                    boolean headBlocked =
                            !isPassable(
                                    context,
                                    positions.forwardHead()
                            );

                    return DAI_ConditionValue.bool(
                            feetBlocked
                                    || headBlocked
                    );
                }
        );

        /*
         * True when forward movement remains supported or produces
         * a drop no deeper than the configured safe distance.
         *
         * parameter_number controls the maximum safe drop.
         * Default: 2 blocks.
         */
        DAI_ConditionRegistry.register(
                "path_forward_safe_drop",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasLevel()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    int maximumDrop =
                            positiveInt(
                                    condition.parameterNumber(),
                                    DEFAULT_SAFE_DROP
                            );

                    ForwardPositions positions =
                            positions(context);

                    int drop =
                            findDropDepth(
                                    context,
                                    positions.forwardBelow(),
                                    maximumDrop + 1
                            );

                    return DAI_ConditionValue.bool(
                            drop >= 0
                                    && drop <= maximumDrop
                    );
                }
        );

        /*
         * True when no supporting block is found within the configured
         * safe-drop distance.
         *
         * parameter_number controls the maximum safe drop.
         * Default: 2 blocks.
         */
        DAI_ConditionRegistry.register(
                "path_forward_dangerous_drop",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasLevel()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    int maximumDrop =
                            positiveInt(
                                    condition.parameterNumber(),
                                    DEFAULT_SAFE_DROP
                            );

                    ForwardPositions positions =
                            positions(context);

                    int drop =
                            findDropDepth(
                                    context,
                                    positions.forwardBelow(),
                                    maximumDrop + 1
                            );

                    return DAI_ConditionValue.bool(
                            drop < 0
                                    || drop > maximumDrop
                    );
                }
        );

        /*
         * True when lava occupies the forward body space or lies
         * immediately beneath the forward destination.
         */
        DAI_ConditionRegistry.register(
                "path_forward_lava",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasLevel()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    ForwardPositions positions =
                            positions(context);

                    return DAI_ConditionValue.bool(
                            isLava(
                                    context,
                                    positions.forwardFeet()
                            )
                                    || isLava(
                                    context,
                                    positions.forwardBelow()
                            )
                    );
                }
        );

        /*
         * True when the route ahead is clear, supported within the
         * safe-drop limit, and free from lava.
         */
        DAI_ConditionRegistry.register(
                "path_forward_safe",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasLevel()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    int maximumDrop =
                            positiveInt(
                                    condition.parameterNumber(),
                                    DEFAULT_SAFE_DROP
                            );

                    ForwardPositions positions =
                            positions(context);

                    boolean clear =
                            isPassable(
                                    context,
                                    positions.forwardFeet()
                            )
                                    && isPassable(
                                    context,
                                    positions.forwardHead()
                            );

                    int drop =
                            findDropDepth(
                                    context,
                                    positions.forwardBelow(),
                                    maximumDrop + 1
                            );

                    boolean supported =
                            drop >= 0
                                    && drop <= maximumDrop;

                    boolean lava =
                            isLava(
                                    context,
                                    positions.forwardFeet()
                            )
                                    || isLava(
                                    context,
                                    positions.forwardBelow()
                            );

                    return DAI_ConditionValue.bool(
                            clear
                                    && supported
                                    && !lava
                    );
                }
        );

        /*
         * True when the player has enough vertical room to jump.
         *
         * parameter_number controls required clear blocks.
         * Default: 2 blocks.
         */
        DAI_ConditionRegistry.register(
                "player_has_jump_headroom",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasLevel()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    int requiredHeadroom =
                            positiveInt(
                                    condition.parameterNumber(),
                                    DEFAULT_HEADROOM
                            );

                    BlockPos playerPosition =
                            context.player()
                                    .blockPosition();

                    for (
                            int offset = 1;
                            offset <= requiredHeadroom;
                            offset++
                    ) {

                        if (
                                !isPassable(
                                        context,
                                        playerPosition.above(offset)
                                )
                        ) {
                            return DAI_ConditionValue.bool(
                                    false
                            );
                        }
                    }

                    return DAI_ConditionValue.bool(
                            true
                    );
                }
        );

        /*
         * Returns the elevation of the nearest supporting block ahead
         * relative to the block beneath the player.
         *
         *  1 = forward route steps upward
         *  0 = forward route is level
         * -1 = forward route drops one block
         * -2 = forward route drops two blocks
         */
        DAI_ConditionRegistry.register(
                "path_forward_elevation",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasLevel()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    int searchDepth =
                            positiveInt(
                                    condition.parameterNumber(),
                                    4
                            );

                    ForwardPositions positions =
                            positions(context);

                    if (
                            isSupporting(
                                    context,
                                    positions.forwardFeet()
                            )
                                    && isPassable(
                                    context,
                                    positions.forwardHead()
                            )
                                    && isPassable(
                                    context,
                                    positions.forwardAboveHead()
                            )
                    ) {
                        return DAI_ConditionValue.number(
                                1.0D
                        );
                    }

                    int drop =
                            findDropDepth(
                                    context,
                                    positions.forwardBelow(),
                                    searchDepth
                            );

                    if (drop < 0) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            -drop
                    );
                }
        );

        /*
         * True when the route ahead rises by one block and can
         * therefore be climbed with a forward jump.
         */
        DAI_ConditionRegistry.register(
                "path_forward_uphill",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasLevel()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    ForwardPositions positions =
                            positions(context);

                    return DAI_ConditionValue.bool(
                            isSupporting(
                                    context,
                                    positions.forwardFeet()
                            )
                                    && isPassable(
                                    context,
                                    positions.forwardHead()
                            )
                                    && isPassable(
                                    context,
                                    positions.forwardAboveHead()
                            )
                    );
                }
        );

        /*
         * True when the nearest supporting surface ahead is lower
         * than the current surface.
         */
        DAI_ConditionRegistry.register(
                "path_forward_downhill",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasLevel()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    int searchDepth =
                            positiveInt(
                                    condition.parameterNumber(),
                                    4
                            );

                    ForwardPositions positions =
                            positions(context);

                    int drop =
                            findDropDepth(
                                    context,
                                    positions.forwardBelow(),
                                    searchDepth
                            );

                    return DAI_ConditionValue.bool(
                            drop > 0
                    );
                }
        );
    }

    private static ForwardPositions positions(
            DAI_ConditionContext context
    ) {

        BlockPos playerPosition =
                context.player()
                        .blockPosition();

        HorizontalDirection direction =
                horizontalDirection(
                        context.player()
                                .getYRot()
                );

        BlockPos forwardFeet =
                playerPosition.offset(
                        direction.x(),
                        0,
                        direction.z()
                );

        return new ForwardPositions(
                forwardFeet,
                forwardFeet.above(),
                forwardFeet.above(2),
                forwardFeet.below()
        );
    }

    private static HorizontalDirection horizontalDirection(
            float yaw
    ) {

        int quadrant =
                Math.floorMod(
                        Math.round(yaw / 90.0F),
                        4
                );

        return switch (quadrant) {

            case 0 ->
                    new HorizontalDirection(
                            0,
                            1
                    );

            case 1 ->
                    new HorizontalDirection(
                            -1,
                            0
                    );

            case 2 ->
                    new HorizontalDirection(
                            0,
                            -1
                    );

            case 3 ->
                    new HorizontalDirection(
                            1,
                            0
                    );

            default ->
                    throw new IllegalStateException(
                            "Unexpected horizontal direction quadrant."
                    );
        };
    }

    private static int findDropDepth(
            DAI_ConditionContext context,
            BlockPos firstPosition,
            int maximumSearchDepth
    ) {

        for (
                int depth = 0;
                depth <= maximumSearchDepth;
                depth++
        ) {

            BlockPos position =
                    firstPosition.below(depth);

            if (
                    isLava(
                            context,
                            position
                    )
            ) {
                return -1;
            }

            if (
                    isSupporting(
                            context,
                            position
                    )
            ) {
                return depth;
            }
        }

        return -1;
    }

    private static boolean isPassable(
            DAI_ConditionContext context,
            BlockPos position
    ) {

        BlockState state =
                context.level()
                        .getBlockState(position);

        return state.isAir()
                || state.getCollisionShape(
                        context.level(),
                        position
                )
                .isEmpty();
    }

    private static boolean isSupporting(
            DAI_ConditionContext context,
            BlockPos position
    ) {

        BlockState state =
                context.level()
                        .getBlockState(position);

        if (state.isAir()) {
            return false;
        }

        if (
                state.getFluidState()
                        .is(FluidTags.LAVA)
        ) {
            return false;
        }

        return !state.getCollisionShape(
                        context.level(),
                        position
                )
                .isEmpty();
    }

    private static boolean isLava(
            DAI_ConditionContext context,
            BlockPos position
    ) {

        return context.level()
                .getFluidState(position)
                .is(FluidTags.LAVA);
    }

    private static int positiveInt(
            double value,
            int fallback
    ) {

        if (
                !Double.isFinite(value)
                        || value <= 0.0D
        ) {
            return fallback;
        }

        return Math.max(
                1,
                (int) Math.round(value)
        );
    }

    private record HorizontalDirection(
            int x,
            int z
    ) {
    }

    private record ForwardPositions(
            BlockPos forwardFeet,
            BlockPos forwardHead,
            BlockPos forwardAboveHead,
            BlockPos forwardBelow
    ) {
    }
}
