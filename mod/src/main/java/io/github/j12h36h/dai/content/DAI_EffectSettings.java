package io.github.j12h36h.dai.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Static MobEffect registry shell plus display/runtime cadence metadata. */
public record DAI_EffectSettings(
        String category,
        int color,
        int tickInterval
) {
    public static final DAI_EffectSettings DEFAULT = new DAI_EffectSettings("neutral", 0xFFFFFF, 1);
    public static final Codec<DAI_EffectSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("category", "neutral").forGetter(DAI_EffectSettings::category),
            Codec.INT.optionalFieldOf("color", 0xFFFFFF).forGetter(DAI_EffectSettings::color),
            Codec.INT.optionalFieldOf("tick_interval", 1).forGetter(DAI_EffectSettings::tickInterval)
    ).apply(i, DAI_EffectSettings::new));
    public DAI_EffectSettings {
        category = category == null ? "neutral" : category.trim().toLowerCase();
        color &= 0xFFFFFF;
        tickInterval = Math.max(1, tickInterval);
    }
}
