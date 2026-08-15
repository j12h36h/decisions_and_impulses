package io.github.j12h36h.dai.client.menus.system;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DAI_ButtonStyle(
        String background,
        String hover,
        String selected,
        String text,
        String border
) {

    public static final DAI_ButtonStyle EMPTY =
            new DAI_ButtonStyle("", "", "", "", "");

    public static final Codec<DAI_ButtonStyle> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.STRING.optionalFieldOf("background", "").forGetter(DAI_ButtonStyle::background),
                            Codec.STRING.optionalFieldOf("hover", "").forGetter(DAI_ButtonStyle::hover),
                            Codec.STRING.optionalFieldOf("selected", "").forGetter(DAI_ButtonStyle::selected),
                            Codec.STRING.optionalFieldOf("text", "").forGetter(DAI_ButtonStyle::text),
                            Codec.STRING.optionalFieldOf("border", "").forGetter(DAI_ButtonStyle::border)
                    ).apply(instance, DAI_ButtonStyle::new)
            );

    public DAI_ButtonStyle {
        background = normalize(background);
        hover = normalize(hover);
        selected = normalize(selected);
        text = normalize(text);
        border = normalize(border);
    }

    public boolean isEmpty() {
        return background.isEmpty()
                && hover.isEmpty()
                && selected.isEmpty()
                && text.isEmpty()
                && border.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
