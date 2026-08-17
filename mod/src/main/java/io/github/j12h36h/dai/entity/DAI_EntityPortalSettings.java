package io.github.j12h36h.dai.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;

/**
 * Optional portal behavior attached to a living JSON-defined DAI entity.
 * The entity remains a normal registry-backed LivingEntity/Mob; this profile
 * simply turns its proximity volume into a configurable dimension gateway.
 */
public record DAI_EntityPortalSettings(
        boolean enabled,
        String destination,
        String targetMode,
        double x,
        double y,
        double z,
        double triggerRadius,
        int cooldownTicks,
        boolean preserveVelocity,
        boolean preserveRotation,
        float yaw,
        float pitch,
        String enterCommand,
        String exitCommand
) {
    public static final DAI_EntityPortalSettings DISABLED =
            new DAI_EntityPortalSettings(
                    false, "", "same_coordinates",
                    0.0D, 64.0D, 0.0D,
                    1.0D, 40,
                    true, true,
                    0.0F, 0.0F,
                    "", ""
            );

    public static final Codec<DAI_EntityPortalSettings> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("enabled", false).forGetter(DAI_EntityPortalSettings::enabled),
                    Codec.STRING.optionalFieldOf("destination", "").forGetter(DAI_EntityPortalSettings::destination),
                    Codec.STRING.optionalFieldOf("target_mode", "same_coordinates").forGetter(DAI_EntityPortalSettings::targetMode),
                    Codec.DOUBLE.optionalFieldOf("x", 0.0D).forGetter(DAI_EntityPortalSettings::x),
                    Codec.DOUBLE.optionalFieldOf("y", 64.0D).forGetter(DAI_EntityPortalSettings::y),
                    Codec.DOUBLE.optionalFieldOf("z", 0.0D).forGetter(DAI_EntityPortalSettings::z),
                    Codec.DOUBLE.optionalFieldOf("trigger_radius", 1.0D).forGetter(DAI_EntityPortalSettings::triggerRadius),
                    Codec.INT.optionalFieldOf("cooldown_ticks", 40).forGetter(DAI_EntityPortalSettings::cooldownTicks),
                    Codec.BOOL.optionalFieldOf("preserve_velocity", true).forGetter(DAI_EntityPortalSettings::preserveVelocity),
                    Codec.BOOL.optionalFieldOf("preserve_rotation", true).forGetter(DAI_EntityPortalSettings::preserveRotation),
                    Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(DAI_EntityPortalSettings::yaw),
                    Codec.FLOAT.optionalFieldOf("pitch", 0.0F).forGetter(DAI_EntityPortalSettings::pitch),
                    Codec.STRING.optionalFieldOf("enter_command", "").forGetter(DAI_EntityPortalSettings::enterCommand),
                    Codec.STRING.optionalFieldOf("exit_command", "").forGetter(DAI_EntityPortalSettings::exitCommand)
            ).apply(instance, DAI_EntityPortalSettings::new));

    public DAI_EntityPortalSettings {
        destination = normalizeId(destination);
        targetMode = normalizeMode(targetMode);
        x = finite(x, 0.0D);
        y = finite(y, 64.0D);
        z = finite(z, 0.0D);
        triggerRadius = Math.max(0.1D, Math.min(32.0D, finite(triggerRadius, 1.0D)));
        cooldownTicks = Math.max(1, Math.min(12000, cooldownTicks));
        yaw = Float.isFinite(yaw) ? yaw : 0.0F;
        pitch = Float.isFinite(pitch) ? pitch : 0.0F;
        enterCommand = enterCommand == null ? "" : enterCommand.trim();
        exitCommand = exitCommand == null ? "" : exitCommand.trim();
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeMode(String value) {
        if (value == null || value.isBlank()) return "same_coordinates";
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
