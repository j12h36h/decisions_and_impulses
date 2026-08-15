package io.github.j12h36h.dai.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Static + reloadable entity-specific metadata embedded in dai_entities JSON. */
public record DAI_EntitySettings(
        String category,
        float width,
        float height,
        int trackingRange,
        int updateInterval,
        boolean fireImmune,
        boolean summonable,
        boolean saveable,
        String texture,
        String behaviorSequence,
        int behaviorInterval,
        boolean vanillaAi,
        DAI_EntitySpawnSettings spawning
) {

    public static final DAI_EntitySettings DEFAULT =
            new DAI_EntitySettings(
                    "creature", 0.6F, 1.0F, 8, 3,
                    false, true, true, "", "", 10, true,
                    DAI_EntitySpawnSettings.DISABLED
            );

    public static final Codec<DAI_EntitySettings> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("category", "creature").forGetter(DAI_EntitySettings::category),
                    Codec.FLOAT.optionalFieldOf("width", 0.6F).forGetter(DAI_EntitySettings::width),
                    Codec.FLOAT.optionalFieldOf("height", 1.0F).forGetter(DAI_EntitySettings::height),
                    Codec.INT.optionalFieldOf("tracking_range", 8).forGetter(DAI_EntitySettings::trackingRange),
                    Codec.INT.optionalFieldOf("update_interval", 3).forGetter(DAI_EntitySettings::updateInterval),
                    Codec.BOOL.optionalFieldOf("fire_immune", false).forGetter(DAI_EntitySettings::fireImmune),
                    Codec.BOOL.optionalFieldOf("summonable", true).forGetter(DAI_EntitySettings::summonable),
                    Codec.BOOL.optionalFieldOf("saveable", true).forGetter(DAI_EntitySettings::saveable),
                    Codec.STRING.optionalFieldOf("texture", "").forGetter(DAI_EntitySettings::texture),
                    Codec.STRING.optionalFieldOf("behavior_sequence", "").forGetter(DAI_EntitySettings::behaviorSequence),
                    Codec.INT.optionalFieldOf("behavior_interval", 10).forGetter(DAI_EntitySettings::behaviorInterval),
                    Codec.BOOL.optionalFieldOf("vanilla_ai", true).forGetter(DAI_EntitySettings::vanillaAi),
                    DAI_EntitySpawnSettings.CODEC.optionalFieldOf("spawning", DAI_EntitySpawnSettings.DISABLED).forGetter(DAI_EntitySettings::spawning)
            ).apply(instance, DAI_EntitySettings::new));

    public DAI_EntitySettings {
        category = normalize(category, "creature");
        width = clamp(width, 0.05F, 32.0F);
        height = clamp(height, 0.05F, 32.0F);
        trackingRange = Math.max(1, Math.min(64, trackingRange));
        updateInterval = Math.max(1, Math.min(1200, updateInterval));
        texture = normalize(texture, "");
        behaviorSequence = normalize(behaviorSequence, "");
        behaviorInterval = Math.max(1, behaviorInterval);
        spawning = spawning == null ? DAI_EntitySpawnSettings.DISABLED : spawning;
    }

    private static String normalize(String value, String fallback) {
        return value == null ? fallback : value.trim().toLowerCase();
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
