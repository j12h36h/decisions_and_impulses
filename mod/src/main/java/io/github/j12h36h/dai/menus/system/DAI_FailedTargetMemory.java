package io.github.j12h36h.dai.menus.system;

import net.minecraft.core.BlockPos;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DAI_FailedTargetMemory {

    private static final long FAILURE_DURATION_MILLISECONDS =
            30_000L;

    private static final int MAX_FAILED_TARGETS =
            64;

    private static final Map<BlockPos, Long> FAILED_TARGETS =
            new LinkedHashMap<>();

    private DAI_FailedTargetMemory() {
        // Utility class.
    }

    public static void remember(
            BlockPos position
    ) {

        if (position == null) {
            return;
        }

        cleanupExpired();

        BlockPos immutable =
                position.immutable();

        long expiresAt =
                System.currentTimeMillis()
                        + FAILURE_DURATION_MILLISECONDS;

        FAILED_TARGETS.remove(
                immutable
        );

        FAILED_TARGETS.put(
                immutable,
                expiresAt
        );

        trimToMaximumSize();
    }

    public static boolean contains(
            BlockPos position
    ) {

        if (position == null) {
            return false;
        }

        cleanupExpired();

        return FAILED_TARGETS.containsKey(
                position
        );
    }

    public static void forget(
            BlockPos position
    ) {

        if (position == null) {
            return;
        }

        FAILED_TARGETS.remove(
                position
        );
    }

    public static int size() {

        cleanupExpired();

        return FAILED_TARGETS.size();
    }

    public static void clear() {

        FAILED_TARGETS.clear();
    }

    private static void cleanupExpired() {

        long now =
                System.currentTimeMillis();

        Iterator<Map.Entry<BlockPos, Long>> iterator =
                FAILED_TARGETS
                        .entrySet()
                        .iterator();

        while (iterator.hasNext()) {

            Map.Entry<BlockPos, Long> entry =
                    iterator.next();

            if (
                    entry.getValue()
                            <= now
            ) {
                iterator.remove();
            }
        }
    }

    private static void trimToMaximumSize() {

        while (
                FAILED_TARGETS.size()
                        > MAX_FAILED_TARGETS
        ) {

            Iterator<BlockPos> iterator =
                    FAILED_TARGETS
                            .keySet()
                            .iterator();

            if (!iterator.hasNext()) {
                return;
            }

            iterator.next();
            iterator.remove();
        }
    }
}
