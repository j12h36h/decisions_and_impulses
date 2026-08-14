package io.github.j12h36h.dai.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Optional common numeric properties shared by virtual DAI content kinds. */
public record DAI_ContentStats(
        int stackSize,
        int durability,
        int durationTicks,
        int tickInterval,
        double attackDamage,
        double attackSpeed,
        double attackRange,
        double armor,
        double armorToughness,
        double projectileSpeed,
        double gravity
) {
    public static final DAI_ContentStats EMPTY = new DAI_ContentStats(
            1, 0, 0, 0,
            0.0D, 0.0D, 0.0D,
            0.0D, 0.0D, 0.0D, 0.0D
    );

    public static final Codec<DAI_ContentStats> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("stack_size", 1).forGetter(DAI_ContentStats::stackSize),
                    Codec.INT.optionalFieldOf("durability", 0).forGetter(DAI_ContentStats::durability),
                    Codec.INT.optionalFieldOf("duration_ticks", 0).forGetter(DAI_ContentStats::durationTicks),
                    Codec.INT.optionalFieldOf("tick_interval", 0).forGetter(DAI_ContentStats::tickInterval),
                    Codec.DOUBLE.optionalFieldOf("attack_damage", 0.0D).forGetter(DAI_ContentStats::attackDamage),
                    Codec.DOUBLE.optionalFieldOf("attack_speed", 0.0D).forGetter(DAI_ContentStats::attackSpeed),
                    Codec.DOUBLE.optionalFieldOf("attack_range", 0.0D).forGetter(DAI_ContentStats::attackRange),
                    Codec.DOUBLE.optionalFieldOf("armor", 0.0D).forGetter(DAI_ContentStats::armor),
                    Codec.DOUBLE.optionalFieldOf("armor_toughness", 0.0D).forGetter(DAI_ContentStats::armorToughness),
                    Codec.DOUBLE.optionalFieldOf("projectile_speed", 0.0D).forGetter(DAI_ContentStats::projectileSpeed),
                    Codec.DOUBLE.optionalFieldOf("gravity", 0.0D).forGetter(DAI_ContentStats::gravity)
            ).apply(instance, DAI_ContentStats::new));

    public DAI_ContentStats {
        stackSize = Math.max(1, stackSize);
        durability = Math.max(0, durability);
        durationTicks = Math.max(0, durationTicks);
        tickInterval = Math.max(0, tickInterval);
    }
}
