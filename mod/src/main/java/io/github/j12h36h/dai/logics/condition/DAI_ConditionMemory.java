package io.github.j12h36h.dai.logics.condition;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public final class DAI_ConditionMemory {

    private static final int MAX_POSITION_HISTORY =
            20 * 60 * 10;

    private static final Deque<PositionSample> POSITION_HISTORY =
            new ArrayDeque<>();

    private static final Set<Identifier> KNOWN_BIOMES =
            new HashSet<>();

    private static final Set<Identifier> KNOWN_STRUCTURES =
            new HashSet<>();

    private static long currentTick;

    private DAI_ConditionMemory() {
        // Utility class.
    }

    public static void tick() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {
            clearPositionHistory();
            return;
        }

        currentTick++;

        Vec3 position =
                minecraft.player.position();

        POSITION_HISTORY.addLast(
                new PositionSample(
                        currentTick,
                        position
                )
        );

        while (
                POSITION_HISTORY.size()
                        > MAX_POSITION_HISTORY
        ) {
            POSITION_HISTORY.removeFirst();
        }

        rememberCurrentBiome(
                minecraft
        );
    }

    public static void clear() {

        POSITION_HISTORY.clear();
        KNOWN_BIOMES.clear();
        KNOWN_STRUCTURES.clear();
        currentTick = 0L;
    }

    public static void clearPositionHistory() {
        POSITION_HISTORY.clear();
    }

    public static double distanceMoved(
            int ticks
    ) {

        if (POSITION_HISTORY.size() < 2) {
            return 0.0D;
        }

        int requestedTicks =
                Math.max(
                        1,
                        ticks
                );

        long targetTick =
                currentTick
                        - requestedTicks;

        PositionSample oldest =
                POSITION_HISTORY.getFirst();

        for (
                PositionSample sample
                : POSITION_HISTORY
        ) {

            oldest = sample;

            if (sample.tick() >= targetTick) {
                break;
            }
        }

        PositionSample newest =
                POSITION_HISTORY.getLast();

        return oldest.position()
                .distanceTo(
                        newest.position()
                );
    }

    public static boolean isStuck(
            int ticks,
            double maximumDistance
    ) {

        int requestedTicks =
                Math.max(
                        1,
                        ticks
                );

        if (
                POSITION_HISTORY.isEmpty()
                        || currentTick
                        < requestedTicks
        ) {
            return false;
        }

        return distanceMoved(
                requestedTicks
        ) <= Math.max(
                0.0D,
                maximumDistance
        );
    }

    public static void rememberBiome(
            Identifier biomeId
    ) {

        if (biomeId != null) {
            KNOWN_BIOMES.add(
                    biomeId
            );
        }
    }

    public static boolean knowsBiome(
            Identifier biomeId
    ) {

        return biomeId != null
                && KNOWN_BIOMES.contains(
                biomeId
        );
    }

    public static void rememberStructure(
            Identifier structureId
    ) {

        if (structureId != null) {
            KNOWN_STRUCTURES.add(
                    structureId
            );
        }
    }

    public static boolean knowsStructure(
            Identifier structureId
    ) {

        return structureId != null
                && KNOWN_STRUCTURES.contains(
                structureId
        );
    }

    public static Set<Identifier> knownBiomes() {
        return Set.copyOf(
                KNOWN_BIOMES
        );
    }

    public static Set<Identifier> knownStructures() {
        return Set.copyOf(
                KNOWN_STRUCTURES
        );
    }

    private static void rememberCurrentBiome(
            Minecraft minecraft
    ) {

        BlockPos position =
                minecraft.player
                        .blockPosition();

        ResourceKey<Biome> biomeKey =
                minecraft.level
                        .getBiome(position)
                        .unwrapKey()
                        .orElse(null);

        if (biomeKey == null) {
            return;
        }

        rememberBiome(
                biomeKey.identifier()
        );
    }

    private record PositionSample(
            long tick,
            Vec3 position
    ) {
    }
}
