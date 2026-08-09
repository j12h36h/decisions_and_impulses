package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.menus.system.DAI_SpatialState;
import io.github.j12h36h.dai.menus.system.DAI_TargetState;
import io.github.j12h36h.dai.menus.system.DAI_WaypointMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Locale;

/** Shared parsing/source resolution for datapack spatial primitives. */
public final class DAI_SpatialResolver {

    private DAI_SpatialResolver() {
        // Utility class.
    }

    public static BlockPos parseOffset(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {
            return null;
        }

        String normalized =
                value.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        BlockPos named =
                switch (normalized) {
                    case "up" -> new BlockPos(0, 1, 0);
                    case "down" -> new BlockPos(0, -1, 0);
                    case "north" -> new BlockPos(0, 0, -1);
                    case "south" -> new BlockPos(0, 0, 1);
                    case "east" -> new BlockPos(1, 0, 0);
                    case "west" -> new BlockPos(-1, 0, 0);
                    case "north_east", "northeast" -> new BlockPos(1, 0, -1);
                    case "north_west", "northwest" -> new BlockPos(-1, 0, -1);
                    case "south_east", "southeast" -> new BlockPos(1, 0, 1);
                    case "south_west", "southwest" -> new BlockPos(-1, 0, 1);
                    default -> null;
                };

        if (named != null) {
            return named;
        }

        String[] parts =
                normalized.split(",");

        if (parts.length != 3) {
            return null;
        }

        try {

            return new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            );

        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static BlockPos resolveOrigin(
            Minecraft minecraft,
            String source,
            boolean allowLastScan
    ) {

        if (
                minecraft == null
                        || minecraft.level == null
                        || minecraft.player == null
        ) {
            return null;
        }

        String normalized =
                source == null
                        ? ""
                        : source.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (
                allowLastScan
                        && (
                        normalized.isEmpty()
                                || "last_scan".equals(normalized)
                                || "scan".equals(normalized)
                )
                        && DAI_SpatialState.availableIn(
                        minecraft.level.dimension()
                )
        ) {
            return DAI_SpatialState.origin();
        }

        if (
                normalized.isEmpty()
                        || "selected_target".equals(normalized)
                        || "target".equals(normalized)
        ) {
            return DAI_TargetState.selectedBlock();
        }

        if ("player".equals(normalized)) {
            return minecraft.player.blockPosition();
        }

        DAI_WaypointMemory.DAI_Waypoint waypoint =
                DAI_WaypointMemory.getInDimension(
                        normalized,
                        minecraft.level.dimension()
                );

        return waypoint == null
                ? null
                : waypoint.position();
    }

    public static String normalizeMode(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {
            return DAI_SpatialLogic.MODE_SURROUNDING_26;
        }

        String normalized =
                value.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return switch (normalized) {
            case DAI_SpatialLogic.MODE_FACES_6 -> DAI_SpatialLogic.MODE_FACES_6;
            case DAI_SpatialLogic.MODE_HORIZONTAL_8 -> DAI_SpatialLogic.MODE_HORIZONTAL_8;
            case DAI_SpatialLogic.MODE_SURROUNDING_26 -> DAI_SpatialLogic.MODE_SURROUNDING_26;
            default -> DAI_SpatialLogic.MODE_SURROUNDING_26;
        };
    }
}
