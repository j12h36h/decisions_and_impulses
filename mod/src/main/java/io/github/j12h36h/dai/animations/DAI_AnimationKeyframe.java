package io.github.j12h36h.dai.animations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;

public record DAI_AnimationKeyframe(
        int tick,
        double x,
        double y,
        double z,
        double pitch,
        double yaw,
        double roll,
        double scaleX,
        double scaleY,
        double scaleZ,
        String interpolation,
        String easing
) {
    public static final Codec<DAI_AnimationKeyframe> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("tick").forGetter(DAI_AnimationKeyframe::tick),
                    Codec.DOUBLE.optionalFieldOf("x", 0.0D).forGetter(DAI_AnimationKeyframe::x),
                    Codec.DOUBLE.optionalFieldOf("y", 0.0D).forGetter(DAI_AnimationKeyframe::y),
                    Codec.DOUBLE.optionalFieldOf("z", 0.0D).forGetter(DAI_AnimationKeyframe::z),
                    Codec.DOUBLE.optionalFieldOf("pitch", 0.0D).forGetter(DAI_AnimationKeyframe::pitch),
                    Codec.DOUBLE.optionalFieldOf("yaw", 0.0D).forGetter(DAI_AnimationKeyframe::yaw),
                    Codec.DOUBLE.optionalFieldOf("roll", 0.0D).forGetter(DAI_AnimationKeyframe::roll),
                    Codec.DOUBLE.optionalFieldOf("scale_x", 1.0D).forGetter(DAI_AnimationKeyframe::scaleX),
                    Codec.DOUBLE.optionalFieldOf("scale_y", 1.0D).forGetter(DAI_AnimationKeyframe::scaleY),
                    Codec.DOUBLE.optionalFieldOf("scale_z", 1.0D).forGetter(DAI_AnimationKeyframe::scaleZ),
                    Codec.STRING.optionalFieldOf("interpolation", "linear").forGetter(DAI_AnimationKeyframe::interpolation),
                    Codec.STRING.optionalFieldOf("easing", "linear").forGetter(DAI_AnimationKeyframe::easing)
            ).apply(instance, DAI_AnimationKeyframe::new));

    public DAI_AnimationKeyframe {
        if (tick < 0) throw new IllegalArgumentException("Animation keyframe tick cannot be negative.");
        interpolation = normalize(interpolation, "linear");
        easing = normalize(easing, "linear");
        if (!interpolation.equals("linear") && !interpolation.equals("step")
                && !interpolation.equals("smooth") && !interpolation.equals("cubic")) {
            throw new IllegalArgumentException("Unsupported animation interpolation '" + interpolation + "'.");
        }
    }

    public DAI_AnimationKeyframe(int tick, double x, double y, double z, double pitch, double yaw, double roll,
                                 double scaleX, double scaleY, double scaleZ) {
        this(tick, x, y, z, pitch, yaw, roll, scaleX, scaleY, scaleZ, "linear", "linear");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toLowerCase(Locale.ROOT);
    }
}
