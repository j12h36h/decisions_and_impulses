package io.github.j12h36h.dai.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DAI_SystemButton(
        int slot,
        String id,
        String text,
        String action
) {

    public static final Codec<DAI_SystemButton> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.INT.fieldOf("slot").forGetter(DAI_SystemButton::slot),
                            Codec.STRING.fieldOf("id").forGetter(DAI_SystemButton::id),
                            Codec.STRING.fieldOf("text").forGetter(DAI_SystemButton::text),
                            Codec.STRING.fieldOf("action").forGetter(DAI_SystemButton::action)
                    ).apply(instance, DAI_SystemButton::new)
            );
}
