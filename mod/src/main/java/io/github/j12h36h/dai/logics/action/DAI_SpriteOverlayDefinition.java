package io.github.j12h36h.dai.logics.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DAI_SpriteOverlayDefinition(
        String id,
        String texture,
        String anchor,
        int x,
        int y,
        int width,
        int height,
        double alpha,
        String color,
        int z,
        int ticks,
        boolean interactable,
        String clickAction,
        boolean consumeClick
) {

    public static final DAI_SpriteOverlayDefinition EMPTY =
            new DAI_SpriteOverlayDefinition(
                    "", "", "center",
                    0, 0, 0, 0,
                    1.0D, "#FFFFFF",
                    0, 0,
                    false, "", false
            );

    public static final Codec<DAI_SpriteOverlayDefinition> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.STRING.optionalFieldOf("id", "").forGetter(DAI_SpriteOverlayDefinition::id),
                            Codec.STRING.optionalFieldOf("texture", "").forGetter(DAI_SpriteOverlayDefinition::texture),
                            Codec.STRING.optionalFieldOf("anchor", "center").forGetter(DAI_SpriteOverlayDefinition::anchor),
                            Codec.INT.optionalFieldOf("x", 0).forGetter(DAI_SpriteOverlayDefinition::x),
                            Codec.INT.optionalFieldOf("y", 0).forGetter(DAI_SpriteOverlayDefinition::y),
                            Codec.INT.optionalFieldOf("width", 0).forGetter(DAI_SpriteOverlayDefinition::width),
                            Codec.INT.optionalFieldOf("height", 0).forGetter(DAI_SpriteOverlayDefinition::height),
                            Codec.DOUBLE.optionalFieldOf("alpha", 1.0D).forGetter(DAI_SpriteOverlayDefinition::alpha),
                            Codec.STRING.optionalFieldOf("color", "#FFFFFF").forGetter(DAI_SpriteOverlayDefinition::color),
                            Codec.INT.optionalFieldOf("z", 0).forGetter(DAI_SpriteOverlayDefinition::z),
                            Codec.INT.optionalFieldOf("ticks", 0).forGetter(DAI_SpriteOverlayDefinition::ticks),
                            Codec.BOOL.optionalFieldOf("interactable", false).forGetter(DAI_SpriteOverlayDefinition::interactable),
                            Codec.STRING.optionalFieldOf("click_action", "").forGetter(DAI_SpriteOverlayDefinition::clickAction),
                            Codec.BOOL.optionalFieldOf("consume_click", false).forGetter(DAI_SpriteOverlayDefinition::consumeClick)
                    ).apply(instance, DAI_SpriteOverlayDefinition::new)
            );

    public DAI_SpriteOverlayDefinition {
        id = normalize(id);
        texture = normalize(texture);
        anchor = normalize(anchor).toLowerCase();
        color = normalize(color);
        clickAction = normalize(clickAction);

    }

    public boolean isEmpty() {
        return id.isEmpty() && texture.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
