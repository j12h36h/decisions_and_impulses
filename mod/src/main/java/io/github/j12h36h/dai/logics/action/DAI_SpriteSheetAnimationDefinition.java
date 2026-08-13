package io.github.j12h36h.dai.logics.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DAI_SpriteSheetAnimationDefinition(
        int frameWidth,
        int frameHeight,
        int frameCount,
        int columns,
        int frameTicks,
        boolean loop
) {

    public static final DAI_SpriteSheetAnimationDefinition EMPTY =
            new DAI_SpriteSheetAnimationDefinition(0, 0, 0, 1, 1, true);

    public static final Codec<DAI_SpriteSheetAnimationDefinition> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.INT.optionalFieldOf("frame_width", 0).forGetter(DAI_SpriteSheetAnimationDefinition::frameWidth),
                            Codec.INT.optionalFieldOf("frame_height", 0).forGetter(DAI_SpriteSheetAnimationDefinition::frameHeight),
                            Codec.INT.optionalFieldOf("frame_count", 0).forGetter(DAI_SpriteSheetAnimationDefinition::frameCount),
                            Codec.INT.optionalFieldOf("columns", 1).forGetter(DAI_SpriteSheetAnimationDefinition::columns),
                            Codec.INT.optionalFieldOf("frame_ticks", 1).forGetter(DAI_SpriteSheetAnimationDefinition::frameTicks),
                            Codec.BOOL.optionalFieldOf("loop", true).forGetter(DAI_SpriteSheetAnimationDefinition::loop)
                    ).apply(instance, DAI_SpriteSheetAnimationDefinition::new)
            );

}
