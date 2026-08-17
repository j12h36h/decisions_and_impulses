package io.github.j12h36h.dai.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;

/** Reloadable autonomous movement profile for JSON-defined DAI entities. */
public record DAI_EntityMovementSettings(
        String type,
        double speed,
        double radius,
        double verticalRange,
        int intervalTicks,
        boolean noGravity,
        boolean noCollision,
        boolean lookAtPlayer,
        double driftX,
        double driftY,
        double driftZ
) {
    public static final DAI_EntityMovementSettings DEFAULT =
            new DAI_EntityMovementSettings(
                    "behavior", 0.08D, 8.0D, 2.0D, 20,
                    false, false, false,
                    0.0D, 0.0D, 0.0D
            );

    public static final Codec<DAI_EntityMovementSettings> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("type", "behavior").forGetter(DAI_EntityMovementSettings::type),
                    Codec.DOUBLE.optionalFieldOf("speed", 0.08D).forGetter(DAI_EntityMovementSettings::speed),
                    Codec.DOUBLE.optionalFieldOf("radius", 8.0D).forGetter(DAI_EntityMovementSettings::radius),
                    Codec.DOUBLE.optionalFieldOf("vertical_range", 2.0D).forGetter(DAI_EntityMovementSettings::verticalRange),
                    Codec.INT.optionalFieldOf("interval_ticks", 20).forGetter(DAI_EntityMovementSettings::intervalTicks),
                    Codec.BOOL.optionalFieldOf("no_gravity", false).forGetter(DAI_EntityMovementSettings::noGravity),
                    Codec.BOOL.optionalFieldOf("no_collision", false).forGetter(DAI_EntityMovementSettings::noCollision),
                    Codec.BOOL.optionalFieldOf("look_at_player", false).forGetter(DAI_EntityMovementSettings::lookAtPlayer),
                    Codec.DOUBLE.optionalFieldOf("drift_x", 0.0D).forGetter(DAI_EntityMovementSettings::driftX),
                    Codec.DOUBLE.optionalFieldOf("drift_y", 0.0D).forGetter(DAI_EntityMovementSettings::driftY),
                    Codec.DOUBLE.optionalFieldOf("drift_z", 0.0D).forGetter(DAI_EntityMovementSettings::driftZ)
            ).apply(instance, DAI_EntityMovementSettings::new));

    public DAI_EntityMovementSettings {
        type = normalize(type);
        speed = finiteClamp(speed, 0.0D, 4.0D, 0.08D);
        radius = finiteClamp(radius, 0.0D, 128.0D, 8.0D);
        verticalRange = finiteClamp(verticalRange, 0.0D, 64.0D, 2.0D);
        intervalTicks = Math.max(1, Math.min(12000, intervalTicks));
        driftX = finiteClamp(driftX, -8.0D, 8.0D, 0.0D);
        driftY = finiteClamp(driftY, -8.0D, 8.0D, 0.0D);
        driftZ = finiteClamp(driftZ, -8.0D, 8.0D, 0.0D);
    }

    public boolean ownsMovement() {
        return !type.equals("behavior") && !type.equals("none");
    }

    public boolean flyingStyle() {
        return noGravity || type.equals("drift") || type.equals("orbit_player") || type.equals("orbit");
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "behavior";
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static double finiteClamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }
}
