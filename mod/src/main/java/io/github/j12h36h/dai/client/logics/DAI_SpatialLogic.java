package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.controller.DAI_ApproachController;
import io.github.j12h36h.dai.client.menus.system.DAI_SpatialState;
import io.github.j12h36h.dai.client.menus.system.DAI_TargetState;
import io.github.j12h36h.dai.client.menus.system.DAI_WaypointMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic datapack-facing spatial primitives.
 *
 * Java owns coordinate resolution and world access; JSON owns the meaning of
 * those coordinates (blueprints, portal frames, houses, mines, etc.).
 */
public final class DAI_SpatialLogic {

    public static final String MODE_FACES_6 =
            "faces_6";

    public static final String MODE_HORIZONTAL_8 =
            "horizontal_8";

    public static final String MODE_SURROUNDING_26 =
            "surrounding_26";

    private DAI_SpatialLogic() {
        // Utility class.
    }

    /**
     * Selects waypoint.position + directionOffset as the normal temporary
     * block target.
     *
     * JSON:
     * {
     *   "type": "select_waypoint_offset",
     *   "action": "blueprint_origin",
     *   "direction": "2,3,0"
     * }
     */
    public static void selectWaypointOffset(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.level == null
                        || action == null
                        || !action.hasAction()
        ) {

            fail(
                    "select_waypoint_offset requires an active world and waypoint name in 'action'."
            );

            return;
        }

        BlockPos offset =
                DAI_SpatialResolver.parseOffset(
                        action.direction()
                );

        if (offset == null) {

            fail(
                    "select_waypoint_offset requires direction='x,y,z'; got '{}'.",
                    action.direction()
            );

            return;
        }

        DAI_WaypointMemory.DAI_Waypoint waypoint =
                DAI_WaypointMemory.getInDimension(
                        action.action(),
                        minecraft.level.dimension()
                );

        if (waypoint == null) {

            fail(
                    "Cannot select offset from unknown/out-of-dimension waypoint '{}'.",
                    action.action()
            );

            return;
        }

        BlockPos position =
                waypoint.position()
                        .offset(
                                offset.getX(),
                                offset.getY(),
                                offset.getZ()
                        )
                        .immutable();

        DAI_ApproachController.discardTargetOwnership();

        DAI_TargetState.selectBlock(
                position
        );

        success();

        DAI_Core.debug(
                "<DAI>: Selected waypoint '{}' offset {} as temporary block target {}.",
                waypoint.name(),
                offset,
                position
        );
    }

    /**
     * Remembers a new waypoint at baseWaypoint + offset.
     *
     * action = base waypoint name
     * open = new waypoint name
     * direction = x,y,z
     */
    public static void rememberOffsetWaypoint(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.level == null
                        || action == null
                        || !action.hasAction()
                        || action.open().isBlank()
        ) {

            fail(
                    "remember_offset_waypoint requires base waypoint in 'action' and new waypoint name in 'open'."
            );

            return;
        }

        BlockPos offset =
                DAI_SpatialResolver.parseOffset(
                        action.direction()
                );

        if (offset == null) {

            fail(
                    "remember_offset_waypoint requires direction='x,y,z'; got '{}'.",
                    action.direction()
            );

            return;
        }

        DAI_WaypointMemory.DAI_Waypoint waypoint =
                DAI_WaypointMemory.getInDimension(
                        action.action(),
                        minecraft.level.dimension()
                );

        if (waypoint == null) {

            fail(
                    "Cannot remember offset from unknown/out-of-dimension waypoint '{}'.",
                    action.action()
            );

            return;
        }

        BlockPos position =
                waypoint.position()
                        .offset(
                                offset.getX(),
                                offset.getY(),
                                offset.getZ()
                        )
                        .immutable();

        DAI_WaypointMemory.remember(
                action.open(),
                minecraft.level.dimension(),
                position
        );

        success();
    }

    /**
     * Remembers a new waypoint at baseWaypoint + horizontal offset, but
     * normalizes the resulting Y coordinate to the local walkable terrain.
     *
     * This primitive is intentionally footprint-aware for structures. It
     * samples a 5x5 area beginning at the requested origin and uses the
     * median walkable feet Y. Median selection prevents one tree, trench, or
     * cliff edge from pulling the whole blueprint several blocks vertically.
     *
     * JSON:
     * {
     *   "type": "remember_surface_offset_waypoint",
     *   "action": "home_seed",
     *   "direction": "4,0,4",
     *   "open": "home_origin"
     * }
     */
    public static void rememberSurfaceOffsetWaypoint(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.level == null
                        || action == null
                        || !action.hasAction()
                        || action.open().isBlank()
        ) {

            fail(
                    "remember_surface_offset_waypoint requires an active world, base waypoint in 'action', and new waypoint name in 'open'."
            );

            return;
        }

        BlockPos offset =
                DAI_SpatialResolver.parseOffset(
                        action.direction()
                );

        if (offset == null) {

            fail(
                    "remember_surface_offset_waypoint requires direction='x,y,z'; got '{}'.",
                    action.direction()
            );

            return;
        }

        DAI_WaypointMemory.DAI_Waypoint waypoint =
                DAI_WaypointMemory.getInDimension(
                        action.action(),
                        minecraft.level.dimension()
                );

        if (waypoint == null) {

            fail(
                    "Cannot remember surface offset from unknown/out-of-dimension waypoint '{}'.",
                    action.action()
            );

            return;
        }

        BlockPos requested =
                waypoint.position()
                        .offset(
                                offset.getX(),
                                offset.getY(),
                                offset.getZ()
                        )
                        .immutable();

        List<Integer> surfaceYs =
                new ArrayList<>();

        /*
         * Home blueprints use a 5x5 footprint. Sample every footprint column
         * and resolve a nearby standable feet Y. A +/-12 search is generous
         * enough for normal hills while refusing to anchor a house to a cave
         * floor far below or a mountain shelf far above the seed.
         */
        for (int localZ = 0; localZ < 5; localZ++) {
            for (int localX = 0; localX < 5; localX++) {

                Integer surfaceY =
                        findNearbyWalkableFeetY(
                                minecraft,
                                requested.getX() + localX,
                                requested.getY(),
                                requested.getZ() + localZ,
                                12
                        );

                if (surfaceY != null) {
                    surfaceYs.add(
                            surfaceY
                    );
                }
            }
        }

        int resolvedY =
                requested.getY();

        if (!surfaceYs.isEmpty()) {

            Collections.sort(
                    surfaceYs
            );

            resolvedY =
                    surfaceYs.get(
                            surfaceYs.size() / 2
                    );
        }

        BlockPos resolved =
                new BlockPos(
                        requested.getX(),
                        resolvedY,
                        requested.getZ()
                )
                .immutable();

        DAI_WaypointMemory.remember(
                action.open(),
                minecraft.level.dimension(),
                resolved
        );

        success();

        DAI_Core.debug(
                "<DAI>: Remembered surface-normalized waypoint '{}' from '{}' requested={} resolved={} samples={}.",
                action.open(),
                waypoint.name(),
                requested,
                resolved,
                surfaceYs.size()
        );
    }

    private static Integer findNearbyWalkableFeetY(
            Minecraft minecraft,
            int x,
            int expectedY,
            int z,
            int radius
    ) {

        for (int delta = 0; delta <= radius; delta++) {

            int above =
                    expectedY + delta;

            if (
                    isWalkableFeetPosition(
                            minecraft,
                            x,
                            above,
                            z
                    )
            ) {
                return above;
            }

            if (delta == 0) {
                continue;
            }

            int below =
                    expectedY - delta;

            if (
                    isWalkableFeetPosition(
                            minecraft,
                            x,
                            below,
                            z
                    )
            ) {
                return below;
            }
        }

        return null;
    }

    private static boolean isWalkableFeetPosition(
            Minecraft minecraft,
            int x,
            int feetY,
            int z
    ) {

        BlockPos floorPosition =
                new BlockPos(
                        x,
                        feetY - 1,
                        z
                );

        BlockPos feetPosition =
                new BlockPos(
                        x,
                        feetY,
                        z
                );

        BlockPos headPosition =
                new BlockPos(
                        x,
                        feetY + 1,
                        z
                );

        BlockState floorState =
                minecraft.level.getBlockState(
                        floorPosition
                );

        BlockState feetState =
                minecraft.level.getBlockState(
                        feetPosition
                );

        BlockState headState =
                minecraft.level.getBlockState(
                        headPosition
                );

        /*
         * Do not normalize a structure onto a tree canopy/trunk. The floor
         * must have collision and no fluid; feet and head must be collision
         * free and dry. Empty-collision vegetation (grass/fern/etc.) remains
         * acceptable because the existing house clear sequence can remove it.
         */
        return !floorState.isAir()
                && !floorState.is(
                BlockTags.LEAVES
        )
                && !floorState.is(
                BlockTags.LOGS
        )
                && floorState.getFluidState()
                .isEmpty()
                && !floorState.getCollisionShape(
                minecraft.level,
                floorPosition
        )
                .isEmpty()
                && feetState.getFluidState()
                .isEmpty()
                && feetState.getCollisionShape(
                minecraft.level,
                feetPosition
        )
                .isEmpty()
                && headState.getFluidState()
                .isEmpty()
                && headState.getCollisionShape(
                minecraft.level,
                headPosition
        )
                .isEmpty();
    }

    /**
     * Captures a transient neighborhood snapshot around a source position.
     *
     * action source:
     * - "selected_target" / "target" / empty: current selected block
     * - "player": player block
     * - otherwise: named waypoint
     *
     * direction mode:
     * - faces_6
     * - horizontal_8
     * - surrounding_26 (default)
     */
    public static void scanAdjacentBlocks(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.level == null
                        || minecraft.player == null
        ) {

            fail(
                    "Cannot scan adjacent blocks without an active player/world."
            );

            return;
        }

        String source =
                action == null
                        ? ""
                        : action.action();

        BlockPos origin =
                DAI_SpatialResolver.resolveOrigin(
                        minecraft,
                        source,
                        false
                );

        if (origin == null) {

            fail(
                    "Could not resolve spatial scan source '{}'.",
                    source
            );

            return;
        }

        String mode =
                DAI_SpatialResolver.normalizeMode(
                        action == null
                                ? ""
                                : action.direction()
                );

        Map<BlockPos, BlockState> blocks =
                new LinkedHashMap<>();

        for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
                for (int x = -1; x <= 1; x++) {

                    if (
                            x == 0
                                    && y == 0
                                    && z == 0
                    ) {
                        continue;
                    }

                    if (
                            MODE_FACES_6.equals(mode)
                                    && Math.abs(x)
                                    + Math.abs(y)
                                    + Math.abs(z) != 1
                    ) {
                        continue;
                    }

                    if (
                            MODE_HORIZONTAL_8.equals(mode)
                                    && y != 0
                    ) {
                        continue;
                    }

                    BlockPos offset =
                            new BlockPos(
                                    x,
                                    y,
                                    z
                            );

                    BlockPos position =
                            origin.offset(
                                    x,
                                    y,
                                    z
                            );

                    blocks.put(
                            offset,
                            minecraft.level.getBlockState(
                                    position
                            )
                    );
                }
            }
        }

        DAI_SpatialState.remember(
                minecraft.level.dimension(),
                origin,
                mode,
                blocks
        );

        success();

        DAI_Core.debug(
                "<DAI>: Captured spatial scan mode='{}' origin={} entries={}.",
                mode,
                origin,
                blocks.size()
        );
    }

    /**
     * Selects one block relative to a source position.
     *
     * action source supports the same values as scan_adjacent_blocks plus
     * "last_scan" (or empty when a scan exists).
     * direction accepts x,y,z and common cardinal aliases.
     */
    public static void selectAdjacentBlock(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.level == null
                        || minecraft.player == null
                        || action == null
        ) {

            fail(
                    "select_adjacent_block requires an active player/world and action definition."
            );

            return;
        }

        BlockPos offset =
                DAI_SpatialResolver.parseOffset(
                        action.direction()
                );

        if (offset == null) {

            fail(
                    "select_adjacent_block requires a valid direction/offset; got '{}'.",
                    action.direction()
            );

            return;
        }

        BlockPos origin =
                DAI_SpatialResolver.resolveOrigin(
                        minecraft,
                        action.action(),
                        true
                );

        if (origin == null) {

            fail(
                    "Could not resolve adjacent-block source '{}'.",
                    action.action()
            );

            return;
        }

        BlockPos selected =
                origin.offset(
                        offset.getX(),
                        offset.getY(),
                        offset.getZ()
                )
                .immutable();

        DAI_ApproachController.discardTargetOwnership();

        DAI_TargetState.selectBlock(
                selected
        );

        success();

        DAI_Core.debug(
                "<DAI>: Selected adjacent offset {} from origin {} as target {}.",
                offset,
                origin,
                selected
        );
    }

    public static void clearSpatialState(
            DAI_ActionDefinition action
    ) {

        DAI_SpatialState.clear();
        success();
    }

    private static void success() {

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );
    }

    private static void fail(
            String message,
            Object... arguments
    ) {

        DAI_ActionStatus.set(
                DAI_ActionResult.FAILURE
        );

        DAI_Core.debug(
                "<DAI>: " + message,
                arguments
        );
    }
}
