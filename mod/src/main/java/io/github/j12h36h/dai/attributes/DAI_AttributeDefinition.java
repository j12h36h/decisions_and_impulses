package io.github.j12h36h.dai.attributes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DAI_AttributeDefinition(
        double defaultValue,
        double minimum,
        double maximum,
        String nativeBinding
) {

    public static final Codec<DAI_AttributeDefinition> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.DOUBLE.optionalFieldOf("default", 0.0D)
                            .forGetter(DAI_AttributeDefinition::defaultValue),
                    Codec.DOUBLE.optionalFieldOf("minimum", -Double.MAX_VALUE)
                            .forGetter(DAI_AttributeDefinition::minimum),
                    Codec.DOUBLE.optionalFieldOf("maximum", Double.MAX_VALUE)
                            .forGetter(DAI_AttributeDefinition::maximum),
                    Codec.STRING.optionalFieldOf("native_binding", "")
                            .forGetter(DAI_AttributeDefinition::nativeBinding)
            ).apply(instance, DAI_AttributeDefinition::new));

    public DAI_AttributeDefinition {
        if (!Double.isFinite(defaultValue)
                || !Double.isFinite(minimum)
                || !Double.isFinite(maximum)) {
            throw new IllegalArgumentException("DAI attribute values must be finite.");
        }
        if (minimum > maximum) {
            throw new IllegalArgumentException("DAI attribute minimum cannot exceed maximum.");
        }
        nativeBinding = nativeBinding == null ? "" : nativeBinding.trim().toLowerCase();
        defaultValue = clamp(defaultValue, minimum, maximum);
    }

    public double clamp(double value) {
        return clamp(value, minimum, maximum);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
