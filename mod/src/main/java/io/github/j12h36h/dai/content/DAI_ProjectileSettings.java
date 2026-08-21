package io.github.j12h36h.dai.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Reloadable projectile-physics settings layered over stats.projectile_speed/gravity. */
public record DAI_ProjectileSettings(
        double drag,
        int lifetime,
        double hitRadius,
        double damage,
        double knockback,
        int pierce,
        int ricochets,
        double homingRadius,
        double homingStrength,
        boolean returnToOwner,
        int returnAfterTicks,
        boolean hitOwner,
        boolean hitAllies,
        boolean collideBlocks,
        boolean collideEntities
) {
    public static final DAI_ProjectileSettings DEFAULT = new DAI_ProjectileSettings(
            0.0D, 100, 0.35D, 0.0D, 0.0D, 0, 0,
            0.0D, 0.0D, false, 20, false, true, true, true
    );

    public static final Codec<DAI_ProjectileSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("drag", 0.0D).forGetter(DAI_ProjectileSettings::drag),
            Codec.INT.optionalFieldOf("lifetime", 100).forGetter(DAI_ProjectileSettings::lifetime),
            Codec.DOUBLE.optionalFieldOf("hit_radius", 0.35D).forGetter(DAI_ProjectileSettings::hitRadius),
            Codec.DOUBLE.optionalFieldOf("damage", 0.0D).forGetter(DAI_ProjectileSettings::damage),
            Codec.DOUBLE.optionalFieldOf("knockback", 0.0D).forGetter(DAI_ProjectileSettings::knockback),
            Codec.INT.optionalFieldOf("pierce", 0).forGetter(DAI_ProjectileSettings::pierce),
            Codec.INT.optionalFieldOf("ricochets", 0).forGetter(DAI_ProjectileSettings::ricochets),
            Codec.DOUBLE.optionalFieldOf("homing_radius", 0.0D).forGetter(DAI_ProjectileSettings::homingRadius),
            Codec.DOUBLE.optionalFieldOf("homing_strength", 0.0D).forGetter(DAI_ProjectileSettings::homingStrength),
            Codec.BOOL.optionalFieldOf("return_to_owner", false).forGetter(DAI_ProjectileSettings::returnToOwner),
            Codec.INT.optionalFieldOf("return_after_ticks", 20).forGetter(DAI_ProjectileSettings::returnAfterTicks),
            Codec.BOOL.optionalFieldOf("hit_owner", false).forGetter(DAI_ProjectileSettings::hitOwner),
            Codec.BOOL.optionalFieldOf("hit_allies", true).forGetter(DAI_ProjectileSettings::hitAllies),
            Codec.BOOL.optionalFieldOf("collide_blocks", true).forGetter(DAI_ProjectileSettings::collideBlocks),
            Codec.BOOL.optionalFieldOf("collide_entities", true).forGetter(DAI_ProjectileSettings::collideEntities)
    ).apply(instance, DAI_ProjectileSettings::new));

    public DAI_ProjectileSettings {
        drag = clamp(drag, 0.0D, 1.0D);
        lifetime = Math.max(1, Math.min(72000, lifetime));
        hitRadius = clamp(hitRadius, 0.05D, 8.0D);
        damage = Math.max(0.0D, finite(damage));
        knockback = Math.max(0.0D, finite(knockback));
        pierce = Math.max(0, Math.min(128, pierce));
        ricochets = Math.max(0, Math.min(128, ricochets));
        homingRadius = clamp(homingRadius, 0.0D, 128.0D);
        homingStrength = clamp(homingStrength, 0.0D, 1.0D);
        returnAfterTicks = Math.max(0, returnAfterTicks);
    }

    private static double finite(double value) { return Double.isFinite(value) ? value : 0.0D; }
    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
