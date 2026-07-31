package io.github.j12h36h.dai.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;


public record DAI_SystemDefinition(
        int priority,
        List<DAI_SystemButton> buttons
) {

    public static final Codec<DAI_SystemDefinition> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.INT.fieldOf("priority")
                                    .forGetter(DAI_SystemDefinition::priority),

                            DAI_SystemButton.CODEC.listOf()
                                    .fieldOf("buttons")
                                    .forGetter(DAI_SystemDefinition::buttons)
                    ).apply(instance, DAI_SystemDefinition::new)
            );
}