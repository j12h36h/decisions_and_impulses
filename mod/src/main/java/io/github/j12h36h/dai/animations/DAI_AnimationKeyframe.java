package io.github.j12h36h.dai.animations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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
        double scaleZ
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
                    Codec.DOUBLE.optionalFieldOf("scale_z", 1.0D).forGetter(DAI_AnimationKeyframe::scaleZ)
            ).apply(instance, DAI_AnimationKeyframe::new));

    public DAI_AnimationKeyframe {
        if (tick < 0) throw new IllegalArgumentException("Animation keyframe tick cannot be negative.");
    }
}
