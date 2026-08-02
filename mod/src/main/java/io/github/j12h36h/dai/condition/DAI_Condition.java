package io.github.j12h36h.dai.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DAI_Condition(
        String type
) {

    public static final Codec<DAI_Condition> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.STRING
                                    .fieldOf("type")
                                    .forGetter(
                                            DAI_Condition::type
                                    )
                    ).apply(
                            instance,
                            DAI_Condition::new
                    )
            );

    public DAI_Condition {

        if (type == null) {
            throw new IllegalArgumentException(
                    "Condition type cannot be null."
            );
        }

        type = type.trim();

        if (type.isEmpty()) {
            throw new IllegalArgumentException(
                    "Condition type cannot be empty."
            );
        }
    }
}