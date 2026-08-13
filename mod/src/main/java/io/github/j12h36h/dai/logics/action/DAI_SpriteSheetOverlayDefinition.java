package io.github.j12h36h.dai.logics.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DAI_SpriteSheetOverlayDefinition(
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
        boolean consumeClick,
        DAI_SpriteSheetAnimationDefinition animation
) {

    public static final DAI_SpriteSheetOverlayDefinition EMPTY =
            new DAI_SpriteSheetOverlayDefinition(
                    "", "", "center",
                    0, 0, 0, 0,
                    1.0D, "#FFFFFF",
                    0, 0,
                    false, "", false,
                    DAI_SpriteSheetAnimationDefinition.EMPTY
            );

    public static final Codec<DAI_SpriteSheetOverlayDefinition> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.STRING.optionalFieldOf("id", "").forGetter(DAI_SpriteSheetOverlayDefinition::id),
                            Codec.STRING.optionalFieldOf("texture", "").forGetter(DAI_SpriteSheetOverlayDefinition::texture),
                            Codec.STRING.optionalFieldOf("anchor", "center").forGetter(DAI_SpriteSheetOverlayDefinition::anchor),
                            Codec.INT.optionalFieldOf("x", 0).forGetter(DAI_SpriteSheetOverlayDefinition::x),
                            Codec.INT.optionalFieldOf("y", 0).forGetter(DAI_SpriteSheetOverlayDefinition::y),
                            Codec.INT.optionalFieldOf("width", 0).forGetter(DAI_SpriteSheetOverlayDefinition::width),
                            Codec.INT.optionalFieldOf("height", 0).forGetter(DAI_SpriteSheetOverlayDefinition::height),
                            Codec.DOUBLE.optionalFieldOf("alpha", 1.0D).forGetter(DAI_SpriteSheetOverlayDefinition::alpha),
                            Codec.STRING.optionalFieldOf("color", "#FFFFFF").forGetter(DAI_SpriteSheetOverlayDefinition::color),
                            Codec.INT.optionalFieldOf("z", 0).forGetter(DAI_SpriteSheetOverlayDefinition::z),
                            Codec.INT.optionalFieldOf("ticks", 0).forGetter(DAI_SpriteSheetOverlayDefinition::ticks),
                            Codec.BOOL.optionalFieldOf("interactable", false).forGetter(DAI_SpriteSheetOverlayDefinition::interactable),
                            Codec.STRING.optionalFieldOf("click_action", "").forGetter(DAI_SpriteSheetOverlayDefinition::clickAction),
                            Codec.BOOL.optionalFieldOf("consume_click", false).forGetter(DAI_SpriteSheetOverlayDefinition::consumeClick),
                            DAI_SpriteSheetAnimationDefinition.CODEC.optionalFieldOf("animation", DAI_SpriteSheetAnimationDefinition.EMPTY).forGetter(DAI_SpriteSheetOverlayDefinition::animation)
                    ).apply(instance, DAI_SpriteSheetOverlayDefinition::new)
            );

    public DAI_SpriteSheetOverlayDefinition {
        id = normalize(id);
        texture = normalize(texture);
        anchor = normalize(anchor).toLowerCase();
        color = normalize(color);
        clickAction = normalize(clickAction);
        animation = animation == null ? DAI_SpriteSheetAnimationDefinition.EMPTY : animation;

    }

    public boolean isEmpty() {
        return id.isEmpty() && texture.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
