package io.github.j12h36h.dai.system;

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

    public DAI_SystemButton {
        id = normalize(id);
        text = normalize(text);
        action = normalize(action);

        if (slot < 0) {
            throw new IllegalArgumentException(
                    "Menu button slot cannot be negative."
            );
        }

        if (id.isEmpty() || text.isEmpty() || action.isEmpty()) {
            throw new IllegalArgumentException(
                    "Menu button id, text, and action cannot be blank."
            );
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
