package io.github.j12h36h.dai.menus.system;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transient spatial snapshot used by datapack-driven construction and
 * environment reasoning.
 *
 * This state is intentionally independent from DAI_TargetState and waypoint
 * memory. Scanning neighbors therefore cannot replace the current gameplay
 * objective or erase persistent world memory.
 */
public final class DAI_SpatialState {

    private static ResourceKey<Level> dimension;
    private static BlockPos origin;
    private static String mode = "";

    private static final Map<BlockPos, BlockState> BLOCKS =
            new LinkedHashMap<>();

    private DAI_SpatialState() {
        // Utility class.
    }

    public static void remember(
            ResourceKey<Level> scanDimension,
            BlockPos scanOrigin,
            String scanMode,
            Map<BlockPos, BlockState> blocks
    ) {

        clear();

        if (
                scanDimension == null
                        || scanOrigin == null
                        || blocks == null
        ) {
            return;
        }

        dimension = scanDimension;
        origin = scanOrigin.immutable();
        mode = scanMode == null
                ? ""
                : scanMode.trim().toLowerCase();

        for (
                Map.Entry<BlockPos, BlockState> entry
                : blocks.entrySet()
        ) {

            if (
                    entry.getKey() == null
                            || entry.getValue() == null
            ) {
                continue;
            }

            BLOCKS.put(
                    entry.getKey().immutable(),
                    entry.getValue()
            );
        }
    }

    public static boolean available() {

        return dimension != null
                && origin != null
                && !BLOCKS.isEmpty();
    }

    public static boolean availableIn(
            ResourceKey<Level> requestedDimension
    ) {

        return available()
                && requestedDimension != null
                && requestedDimension.equals(dimension);
    }

    public static @Nullable ResourceKey<Level> dimension() {
        return dimension;
    }

    public static @Nullable BlockPos origin() {
        return origin;
    }

    public static String mode() {
        return mode;
    }

    public static @Nullable BlockState blockAtOffset(
            BlockPos offset
    ) {

        if (offset == null) {
            return null;
        }

        return BLOCKS.get(offset);
    }

    public static Map<BlockPos, BlockState> snapshot() {

        return Collections.unmodifiableMap(
                new LinkedHashMap<>(BLOCKS)
        );
    }

    public static int size() {
        return BLOCKS.size();
    }

    public static void clear() {

        dimension = null;
        origin = null;
        mode = "";
        BLOCKS.clear();
    }
}
