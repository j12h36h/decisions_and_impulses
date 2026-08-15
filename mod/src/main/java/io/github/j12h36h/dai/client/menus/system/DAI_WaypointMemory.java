package io.github.j12h36h.dai.client.menus.system;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class DAI_WaypointMemory {

    private static final Map<String, DAI_Waypoint> WAYPOINTS =
            new LinkedHashMap<>();

    private DAI_WaypointMemory() {
        // Utility class.
    }

    public static void remember(
            String name,
            ResourceKey<Level> dimension,
            BlockPos position
    ) {

        String normalizedName =
                normalizeName(
                        name
                );

        if (normalizedName.isEmpty()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot remember a waypoint without a name."
            );

            return;
        }

        if (
                dimension == null
                        || position == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot remember waypoint '{}' without a dimension and position.",
                    normalizedName
            );

            return;
        }

        DAI_Waypoint waypoint =
                new DAI_Waypoint(
                        normalizedName,
                        dimension,
                        position.immutable()
                );

        DAI_Waypoint previous =
                WAYPOINTS.put(
                        normalizedName,
                        waypoint
                );

        if (previous == null) {

            DAI_Core.debug(
                    "<DAI>: Remembered waypoint '{}' at {} in {}.",
                    normalizedName,
                    position,
                    dimension
            );

            return;
        }

        DAI_Core.debug(
                "<DAI>: Updated waypoint '{}' from {} in {} to {} in {}.",
                normalizedName,
                previous.position(),
                previous.dimension(),
                position,
                dimension
        );
    }

    @Nullable
    public static DAI_Waypoint get(
            String name
    ) {

        String normalizedName =
                normalizeName(
                        name
                );

        if (normalizedName.isEmpty()) {
            return null;
        }

        return WAYPOINTS.get(
                normalizedName
        );
    }

    public static boolean contains(
            String name
    ) {

        return get(
                name
        ) != null;
    }

    @Nullable
    public static DAI_Waypoint getInDimension(
            String name,
            ResourceKey<Level> dimension
    ) {

        if (dimension == null) {
            return null;
        }

        DAI_Waypoint waypoint =
                get(
                        name
                );

        if (
                waypoint == null
                        || !waypoint.dimension()
                        .equals(
                                dimension
                        )
        ) {
            return null;
        }

        return waypoint;
    }

    public static boolean containsInDimension(
            String name,
            ResourceKey<Level> dimension
    ) {

        return getInDimension(
                name,
                dimension
        ) != null;
    }

    public static boolean forget(
            String name
    ) {

        String normalizedName =
                normalizeName(
                        name
                );

        if (normalizedName.isEmpty()) {
            return false;
        }

        DAI_Waypoint removed =
                WAYPOINTS.remove(
                        normalizedName
                );

        if (removed == null) {
            return false;
        }

        DAI_Core.debug(
                "<DAI>: Forgot waypoint '{}' at {} in {}.",
                removed.name(),
                removed.position(),
                removed.dimension()
        );

        return true;
    }

    public static void clear() {

        int removed =
                WAYPOINTS.size();

        WAYPOINTS.clear();

        DAI_Core.debug(
                "<DAI>: Cleared waypoint memory (removed {}).",
                removed
        );
    }

    public static int size() {

        return WAYPOINTS.size();
    }

    public static Map<String, DAI_Waypoint> snapshot() {

        return Collections.unmodifiableMap(
                new LinkedHashMap<>(
                        WAYPOINTS
                )
        );
    }

    private static String normalizeName(
            String name
    ) {

        if (name == null) {
            return "";
        }

        return name
                .trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    public record DAI_Waypoint(
            String name,
            ResourceKey<Level> dimension,
            BlockPos position
    ) {

        public DAI_Waypoint {

            if (
                    name == null
                            || name.isBlank()
            ) {

                throw new IllegalArgumentException(
                        "Waypoint name cannot be null or blank."
                );
            }

            if (dimension == null) {

                throw new IllegalArgumentException(
                        "Waypoint dimension cannot be null."
                );
            }

            if (position == null) {

                throw new IllegalArgumentException(
                        "Waypoint position cannot be null."
                );
            }

            position =
                    position.immutable();
        }
    }
}