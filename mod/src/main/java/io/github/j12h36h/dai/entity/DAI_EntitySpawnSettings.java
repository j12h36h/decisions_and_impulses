package io.github.j12h36h.dai.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** Reloadable natural-spawn policy for JSON-defined DAI entities. */
public record DAI_EntitySpawnSettings(
        boolean natural,
        List<String> biomes,
        List<String> dimensions,
        String placement,
        int weight,
        int minGroup,
        int maxGroup,
        int minLight,
        int maxLight,
        int minY,
        int maxY,
        int minRadius,
        int maxRadius,
        int capPerPlayer,
        int intervalTicks,
        int attemptsPerPlayer
) {

    public static final DAI_EntitySpawnSettings DISABLED =
            new DAI_EntitySpawnSettings(
                    false, List.of(), List.of(), "on_ground", 10, 1, 1,
                    0, 15, -2048, 2048,
                    24, 48, 4, 80, 1
            );

    public static final Codec<DAI_EntitySpawnSettings> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("natural", false).forGetter(DAI_EntitySpawnSettings::natural),
                    Codec.STRING.listOf().optionalFieldOf("biomes", List.of()).forGetter(DAI_EntitySpawnSettings::biomes),
                    Codec.STRING.listOf().optionalFieldOf("dimensions", List.of()).forGetter(DAI_EntitySpawnSettings::dimensions),
                    Codec.STRING.optionalFieldOf("placement", "on_ground").forGetter(DAI_EntitySpawnSettings::placement),
                    Codec.INT.optionalFieldOf("weight", 10).forGetter(DAI_EntitySpawnSettings::weight),
                    Codec.INT.optionalFieldOf("min_group", 1).forGetter(DAI_EntitySpawnSettings::minGroup),
                    Codec.INT.optionalFieldOf("max_group", 1).forGetter(DAI_EntitySpawnSettings::maxGroup),
                    Codec.INT.optionalFieldOf("min_light", 0).forGetter(DAI_EntitySpawnSettings::minLight),
                    Codec.INT.optionalFieldOf("max_light", 15).forGetter(DAI_EntitySpawnSettings::maxLight),
                    Codec.INT.optionalFieldOf("min_y", -2048).forGetter(DAI_EntitySpawnSettings::minY),
                    Codec.INT.optionalFieldOf("max_y", 2048).forGetter(DAI_EntitySpawnSettings::maxY),
                    Codec.INT.optionalFieldOf("min_radius", 24).forGetter(DAI_EntitySpawnSettings::minRadius),
                    Codec.INT.optionalFieldOf("max_radius", 48).forGetter(DAI_EntitySpawnSettings::maxRadius),
                    Codec.INT.optionalFieldOf("cap_per_player", 4).forGetter(DAI_EntitySpawnSettings::capPerPlayer),
                    Codec.INT.optionalFieldOf("interval_ticks", 80).forGetter(DAI_EntitySpawnSettings::intervalTicks),
                    Codec.INT.optionalFieldOf("attempts_per_player", 1).forGetter(DAI_EntitySpawnSettings::attemptsPerPlayer)
            ).apply(instance, DAI_EntitySpawnSettings::new));

    public DAI_EntitySpawnSettings {
        biomes = biomes == null ? List.of() : List.copyOf(biomes);
        dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
        placement = placement == null || placement.isBlank() ? "on_ground" : placement.trim().toLowerCase();
        weight = Math.max(1, Math.min(10000, weight));
        minGroup = Math.max(1, minGroup);
        maxGroup = Math.max(minGroup, maxGroup);
        minLight = Math.max(0, Math.min(15, minLight));
        maxLight = Math.max(minLight, Math.min(15, maxLight));
        if (maxY < minY) maxY = minY;
        minRadius = Math.max(4, minRadius);
        maxRadius = Math.max(minRadius, maxRadius);
        capPerPlayer = Math.max(1, capPerPlayer);
        intervalTicks = Math.max(20, intervalTicks);
        attemptsPerPlayer = Math.max(1, Math.min(16, attemptsPerPlayer));
    }
}
