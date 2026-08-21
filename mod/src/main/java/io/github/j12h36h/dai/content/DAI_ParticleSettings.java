package io.github.j12h36h.dai.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;

/** Authored emitter + client-physics settings for a dai_particles definition. */
public record DAI_ParticleSettings(
        String shape,
        int count,
        double spreadX,
        double spreadY,
        double spreadZ,
        double speed,
        double radius,
        boolean force,
        String texture,
        int lifetime,
        double gravity,
        double friction,
        double scale,
        int color,
        double alpha,
        boolean fullBright,
        boolean collision
) {
    public static final DAI_ParticleSettings DEFAULT = new DAI_ParticleSettings(
            "point", 1, 0, 0, 0, 0, 1, false,
            "minecraft:generic", 20, 0.0D, 0.98D, 1.0D,
            0xFFFFFF, 1.0D, false, false
    );

    private record EmitterPart(
            String shape,
            int count,
            double spreadX,
            double spreadY,
            double spreadZ,
            double speed,
            double radius,
            boolean force
    ) {}

    private record RenderPart(
            String texture,
            int lifetime,
            double gravity,
            double friction,
            double scale,
            int color,
            double alpha,
            boolean fullBright,
            boolean collision
    ) {}

    private static final MapCodec<EmitterPart> EMITTER_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("shape", "point").forGetter(EmitterPart::shape),
            Codec.INT.optionalFieldOf("count", 1).forGetter(EmitterPart::count),
            Codec.DOUBLE.optionalFieldOf("spread_x", 0.0D).forGetter(EmitterPart::spreadX),
            Codec.DOUBLE.optionalFieldOf("spread_y", 0.0D).forGetter(EmitterPart::spreadY),
            Codec.DOUBLE.optionalFieldOf("spread_z", 0.0D).forGetter(EmitterPart::spreadZ),
            Codec.DOUBLE.optionalFieldOf("speed", 0.0D).forGetter(EmitterPart::speed),
            Codec.DOUBLE.optionalFieldOf("radius", 1.0D).forGetter(EmitterPart::radius),
            Codec.BOOL.optionalFieldOf("force", false).forGetter(EmitterPart::force)
    ).apply(i, EmitterPart::new));

    private static final MapCodec<RenderPart> RENDER_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("texture", "minecraft:generic").forGetter(RenderPart::texture),
            Codec.INT.optionalFieldOf("lifetime", 20).forGetter(RenderPart::lifetime),
            Codec.DOUBLE.optionalFieldOf("gravity", 0.0D).forGetter(RenderPart::gravity),
            Codec.DOUBLE.optionalFieldOf("friction", 0.98D).forGetter(RenderPart::friction),
            Codec.DOUBLE.optionalFieldOf("scale", 1.0D).forGetter(RenderPart::scale),
            Codec.INT.optionalFieldOf("color", 0xFFFFFF).forGetter(RenderPart::color),
            Codec.DOUBLE.optionalFieldOf("alpha", 1.0D).forGetter(RenderPart::alpha),
            Codec.BOOL.optionalFieldOf("full_bright", false).forGetter(RenderPart::fullBright),
            Codec.BOOL.optionalFieldOf("collision", false).forGetter(RenderPart::collision)
    ).apply(i, RenderPart::new));

    public static final Codec<DAI_ParticleSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            RecordCodecBuilder.of(DAI_ParticleSettings::emitterPart, EMITTER_CODEC),
            RecordCodecBuilder.of(DAI_ParticleSettings::renderPart, RENDER_CODEC)
    ).apply(i, DAI_ParticleSettings::fromParts));

    private EmitterPart emitterPart() {
        return new EmitterPart(shape, count, spreadX, spreadY, spreadZ, speed, radius, force);
    }

    private RenderPart renderPart() {
        return new RenderPart(texture, lifetime, gravity, friction, scale, color, alpha, fullBright, collision);
    }

    private static DAI_ParticleSettings fromParts(EmitterPart emitter, RenderPart render) {
        return new DAI_ParticleSettings(
                emitter.shape(), emitter.count(), emitter.spreadX(), emitter.spreadY(), emitter.spreadZ(),
                emitter.speed(), emitter.radius(), emitter.force(),
                render.texture(), render.lifetime(), render.gravity(), render.friction(), render.scale(),
                render.color(), render.alpha(), render.fullBright(), render.collision()
        );
    }

    public DAI_ParticleSettings {
        shape = normalize(shape, "point");
        count = Math.max(1, Math.min(4096, count));
        spreadX = finite(spreadX); spreadY = finite(spreadY); spreadZ = finite(spreadZ);
        speed = Math.max(0, finite(speed)); radius = Math.max(0, finite(radius));
        texture = normalize(texture, "minecraft:generic");
        lifetime = Math.max(1, Math.min(72000, lifetime));
        gravity = clamp(finite(gravity), -16.0D, 16.0D);
        friction = clamp(finite(friction), 0.0D, 2.0D);
        scale = clamp(finite(scale), 0.001D, 64.0D);
        color = Math.max(0, Math.min(0xFFFFFF, color));
        alpha = clamp(finite(alpha), 0.0D, 1.0D);
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim().toLowerCase(Locale.ROOT);
    }
    private static double finite(double v) { return Double.isFinite(v) ? v : 0.0D; }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}
