package io.github.j12h36h.dai.logics.navigation;

import net.minecraft.core.BlockPos;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public final class DAI_ExplorationMemory {

    private static final int MAX_RECENT_CHUNKS =
            48;

    private static final Set<Long> RECENT_CHUNKS =
            new LinkedHashSet<>();

    private DAI_ExplorationMemory() {
        // Utility class.
    }

    public static void visit(
            BlockPos position
    ) {

        if (position == null) {
            return;
        }

        long key =
                chunkKey(
                        position
                );

        /*
         * Revisiting a chunk makes it the newest
         * entry in the exploration history.
         */
        RECENT_CHUNKS.remove(
                key
        );

        RECENT_CHUNKS.add(
                key
        );

        while (
                RECENT_CHUNKS.size()
                        > MAX_RECENT_CHUNKS
        ) {

            Iterator<Long> iterator =
                    RECENT_CHUNKS.iterator();

            if (!iterator.hasNext()) {
                break;
            }

            iterator.next();
            iterator.remove();
        }
    }

    public static boolean wasRecentlyVisited(
            BlockPos position
    ) {

        if (position == null) {
            return false;
        }

        return RECENT_CHUNKS.contains(
                chunkKey(
                        position
                )
        );
    }

    public static int size() {

        return RECENT_CHUNKS.size();
    }

    public static void clear() {

        RECENT_CHUNKS.clear();
    }

    private static long chunkKey(
            BlockPos position
    ) {

        int chunkX =
                position.getX() >> 4;

        int chunkZ =
                position.getZ() >> 4;

        return (
                ((long) chunkX) << 32
        )
                ^ (
                chunkZ & 0xFFFFFFFFL
        );
    }
}