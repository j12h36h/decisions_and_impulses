package io.github.j12h36h.dai.logics.condition;

import io.github.j12h36h.dai.logics.DAI_SpatialResolver;
import io.github.j12h36h.dai.logics.navigation.DAI_PathFinder;
import io.github.j12h36h.dai.menus.system.DAI_SpatialState;
import io.github.j12h36h.dai.menus.system.DAI_TargetState;
import io.github.j12h36h.dai.menus.system.DAI_WaypointMemory;
import io.github.j12h36h.dai.objectives.recognition.DAI_RecogBlockMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Set;

/** Datapack-facing conditions for waypoint-relative and scanned geometry. */
public final class DAI_ConditionsSpatial {

    /*
     * Exact plank set used by the fail-proof starter-house datapack.
     *
     * Thousands of generated conditions previously fetched the same block
     * state up to eleven times just to ask whether it was one of these plank
     * variants. Keep the accepted set identical while reducing that query to
     * one block-state read and one set lookup.
     */
    private static final Set<String> HOUSE_PLANK_IDS =
            Set.of(
                    "minecraft:oak_planks",
                    "minecraft:spruce_planks",
                    "minecraft:birch_planks",
                    "minecraft:jungle_planks",
                    "minecraft:acacia_planks",
                    "minecraft:dark_oak_planks",
                    "minecraft:mangrove_planks",
                    "minecraft:cherry_planks",
                    "minecraft:pale_oak_planks",
                    "minecraft:crimson_planks",
                    "minecraft:warped_planks"
            );

    private static final Set<String> STRICT_AIR_IDS =
            Set.of(
                    "minecraft:air",
                    "minecraft:cave_air",
                    "minecraft:void_air"
            );

    private DAI_ConditionsSpatial() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "block_at_waypoint_offset",
                (context, condition) -> {

                    WaypointOffset query =
                            parseWaypointOffset(
                                    condition.parameter()
                            );

                    if (
                            query == null
                                    || !context.hasLevel()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    DAI_WaypointMemory.DAI_Waypoint waypoint =
                            DAI_WaypointMemory.getInDimension(
                                    query.waypoint(),
                                    context.level().dimension()
                            );

                    if (waypoint == null) {
                        return DAI_ConditionValue.missing();
                    }

                    BlockPos position =
                            waypoint.position()
                                    .offset(
                                            query.offset().getX(),
                                            query.offset().getY(),
                                            query.offset().getZ()
                                    );

                    return blockId(
                            context.level()
                                    .getBlockState(position)
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "waypoint_offset_air",
                (context, condition) -> {

                    BlockState state =
                            waypointOffsetState(
                                    context,
                                    condition.parameter()
                            );

                    if (state == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            state.isAir()
                                    || state.canBeReplaced()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "waypoint_offset_strict_air",
                (context, condition) -> {

                    BlockState state =
                            waypointOffsetState(
                                    context,
                                    condition.parameter()
                            );

                    if (state == null) {
                        return DAI_ConditionValue.missing();
                    }

                    /*
                     * Exact equivalent of the old generated any-of
                     * air/cave_air/void_air checks, but performs one world
                     * lookup instead of three. Do not broaden this to
                     * state.isAir(); modded air-like blocks were not accepted
                     * by the original datapack.
                     */
                    String blockId =
                            state.getBlock()
                                    .builtInRegistryHolder()
                                    .key()
                                    .identifier()
                                    .toString();

                    return DAI_ConditionValue.bool(
                            STRICT_AIR_IDS.contains(
                                    blockId
                            )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "waypoint_offset_house_plank",
                (context, condition) -> {

                    BlockState state =
                            waypointOffsetState(
                                    context,
                                    condition.parameter()
                            );

                    if (state == null) {
                        return DAI_ConditionValue.missing();
                    }

                    String blockId =
                            state.getBlock()
                                    .builtInRegistryHolder()
                                    .key()
                                    .identifier()
                                    .toString();

                    return DAI_ConditionValue.bool(
                            HOUSE_PLANK_IDS.contains(
                                    blockId
                            )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "waypoint_offset_traversable",
                (context, condition) -> {

                    WaypointOffset query =
                            parseWaypointOffset(
                                    condition.parameter()
                            );

                    if (
                            query == null
                                    || !context.hasLevel()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    DAI_WaypointMemory.DAI_Waypoint waypoint =
                            DAI_WaypointMemory.getInDimension(
                                    query.waypoint(),
                                    context.level().dimension()
                            );

                    if (waypoint == null) {
                        return DAI_ConditionValue.missing();
                    }

                    BlockPos position =
                            waypoint.position()
                                    .offset(
                                            query.offset().getX(),
                                            query.offset().getY(),
                                            query.offset().getZ()
                                    );

                    return DAI_ConditionValue.bool(
                            DAI_PathFinder.isTraversablePosition(
                                    context.level(),
                                    position
                            )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "selected_block",
                (context, condition) -> {

                    if (!context.hasLevel()) {
                        return DAI_ConditionValue.missing();
                    }

                    BlockPos selected =
                            DAI_TargetState.selectedBlock();

                    if (selected == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return blockId(
                            context.level()
                                    .getBlockState(selected)
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "selected_block_air",
                (context, condition) -> {

                    if (!context.hasLevel()) {
                        return DAI_ConditionValue.missing();
                    }

                    BlockPos selected =
                            DAI_TargetState.selectedBlock();

                    if (selected == null) {
                        return DAI_ConditionValue.missing();
                    }

                    BlockState state =
                            context.level()
                                    .getBlockState(selected);

                    return DAI_ConditionValue.bool(
                            state.isAir()
                                    || state.canBeReplaced()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "selected_block_traversable",
                (context, condition) -> {

                    if (!context.hasLevel()) {
                        return DAI_ConditionValue.missing();
                    }

                    BlockPos selected =
                            DAI_TargetState.selectedBlock();

                    if (selected == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            DAI_PathFinder.isTraversablePosition(
                                    context.level(),
                                    selected
                            )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "spatial_scan_available",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                context.hasLevel()
                                        && DAI_SpatialState.availableIn(
                                        context.level().dimension()
                                )
                        )
        );

        DAI_ConditionRegistry.register(
                "spatial_scan_size",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !DAI_SpatialState.availableIn(
                                    context.level().dimension()
                            )
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            DAI_SpatialState.size()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "adjacent_block",
                (context, condition) -> {

                    BlockState state =
                            scannedState(
                                    context,
                                    condition.parameter()
                            );

                    if (state == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return blockId(state);
                }
        );

        DAI_ConditionRegistry.register(
                "adjacent_block_air",
                (context, condition) -> {

                    BlockState state =
                            scannedState(
                                    context,
                                    condition.parameter()
                            );

                    if (state == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            state.isAir()
                                    || state.canBeReplaced()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "adjacent_block_traversable",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !DAI_SpatialState.availableIn(
                                    context.level().dimension()
                            )
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    BlockPos origin =
                            DAI_SpatialState.origin();

                    BlockPos offset =
                            DAI_SpatialResolver.parseOffset(
                                    condition.parameter()
                            );

                    if (
                            origin == null
                                    || offset == null
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    BlockPos position =
                            origin.offset(
                                    offset.getX(),
                                    offset.getY(),
                                    offset.getZ()
                            );

                    return DAI_ConditionValue.bool(
                            DAI_PathFinder.isTraversablePosition(
                                    context.level(),
                                    position
                            )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "adjacent_block_count",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !DAI_SpatialState.availableIn(
                                    context.level().dimension()
                            )
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    String matcher =
                            condition.parameter();

                    if (
                            matcher == null
                                    || matcher.isBlank()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    int count =
                            0;

                    for (
                            Map.Entry<BlockPos, BlockState> entry
                            : DAI_SpatialState.snapshot().entrySet()
                    ) {

                        if (
                                DAI_RecogBlockMatcher.matches(
                                        matcher,
                                        entry.getValue()
                                )
                        ) {
                            count++;
                        }
                    }

                    return DAI_ConditionValue.number(
                            count
                    );
                }
        );
    }

    private static BlockState waypointOffsetState(
            DAI_ConditionContext context,
            String parameter
    ) {

        WaypointOffset query =
                parseWaypointOffset(
                        parameter
                );

        if (
                query == null
                        || !context.hasLevel()
        ) {
            return null;
        }

        DAI_WaypointMemory.DAI_Waypoint waypoint =
                DAI_WaypointMemory.getInDimension(
                        query.waypoint(),
                        context.level().dimension()
                );

        if (waypoint == null) {
            return null;
        }

        BlockPos position =
                waypoint.position()
                        .offset(
                                query.offset().getX(),
                                query.offset().getY(),
                                query.offset().getZ()
                        );

        return context.level()
                .getBlockState(position);
    }

    private static BlockState scannedState(
            DAI_ConditionContext context,
            String offsetValue
    ) {

        if (
                !context.hasLevel()
                        || !DAI_SpatialState.availableIn(
                        context.level().dimension()
                )
        ) {
            return null;
        }

        BlockPos offset =
                DAI_SpatialResolver.parseOffset(
                        offsetValue
                );

        if (offset == null) {
            return null;
        }

        return DAI_SpatialState.blockAtOffset(
                offset
        );
    }

    private static WaypointOffset parseWaypointOffset(
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
                                "\\|",
                                2
                        );

        if (parts.length != 2) {
            return null;
        }

        String waypoint =
                parts[0].trim();

        BlockPos offset =
                DAI_SpatialResolver.parseOffset(
                        parts[1]
                );

        if (
                waypoint.isEmpty()
                        || offset == null
        ) {
            return null;
        }

        return new WaypointOffset(
                waypoint,
                offset
        );
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

    private record WaypointOffset(
            String waypoint,
            BlockPos offset
    ) {
    }
}
