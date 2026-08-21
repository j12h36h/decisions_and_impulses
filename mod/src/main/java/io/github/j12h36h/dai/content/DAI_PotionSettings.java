package io.github.j12h36h.dai.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** Effects applied by a DAI potion identity; entries are "id duration amplifier". */
public record DAI_PotionSettings(List<String> effects) {
    public static final DAI_PotionSettings DEFAULT = new DAI_PotionSettings(List.of());
    public static final Codec<DAI_PotionSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("effects", List.of()).forGetter(DAI_PotionSettings::effects)
    ).apply(i, DAI_PotionSettings::new));
    public DAI_PotionSettings { effects = effects == null ? List.of() : List.copyOf(effects); }
}
