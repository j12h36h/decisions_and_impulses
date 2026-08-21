package io.github.j12h36h.dai.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
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
        DAI_EntitySpawnSettings spawning,
        DAI_EntityGameplaySettings gameplay,
        DAI_EntityMovementSettings movement,
        DAI_EntityPortalSettings portal,
        DAI_EntityRidingSettings riding
) {

    public static final DAI_EntitySettings DEFAULT =
            new DAI_EntitySettings(
                    "creature", 0.6F, 1.0F, 8, 3,
                    false, true, true, "", "", 10, true,
                    DAI_EntitySpawnSettings.DISABLED,
                    DAI_EntityGameplaySettings.DEFAULT,
                    DAI_EntityMovementSettings.DEFAULT,
                    DAI_EntityPortalSettings.DISABLED,
                    DAI_EntityRidingSettings.DEFAULT
            );

    private record ShellPart(
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
            boolean vanillaAi
    ) {}

    private record RuntimePart(
            DAI_EntitySpawnSettings spawning,
            DAI_EntityGameplaySettings gameplay,
            DAI_EntityMovementSettings movement,
            DAI_EntityPortalSettings portal,
            DAI_EntityRidingSettings riding
    ) {}

    private static final MapCodec<ShellPart> SHELL_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("category", "creature").forGetter(ShellPart::category),
                    Codec.FLOAT.optionalFieldOf("width", 0.6F).forGetter(ShellPart::width),
                    Codec.FLOAT.optionalFieldOf("height", 1.0F).forGetter(ShellPart::height),
                    Codec.INT.optionalFieldOf("tracking_range", 8).forGetter(ShellPart::trackingRange),
                    Codec.INT.optionalFieldOf("update_interval", 3).forGetter(ShellPart::updateInterval),
                    Codec.BOOL.optionalFieldOf("fire_immune", false).forGetter(ShellPart::fireImmune),
                    Codec.BOOL.optionalFieldOf("summonable", true).forGetter(ShellPart::summonable),
                    Codec.BOOL.optionalFieldOf("saveable", true).forGetter(ShellPart::saveable),
                    Codec.STRING.optionalFieldOf("texture", "").forGetter(ShellPart::texture),
                    Codec.STRING.optionalFieldOf("behavior_sequence", "").forGetter(ShellPart::behaviorSequence),
                    Codec.INT.optionalFieldOf("behavior_interval", 10).forGetter(ShellPart::behaviorInterval),
                    Codec.BOOL.optionalFieldOf("vanilla_ai", true).forGetter(ShellPart::vanillaAi)
            ).apply(instance, ShellPart::new));

    private static final MapCodec<RuntimePart> RUNTIME_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DAI_EntitySpawnSettings.CODEC.optionalFieldOf("spawning", DAI_EntitySpawnSettings.DISABLED).forGetter(RuntimePart::spawning),
                    DAI_EntityGameplaySettings.CODEC.optionalFieldOf("gameplay", DAI_EntityGameplaySettings.DEFAULT).forGetter(RuntimePart::gameplay),
                    DAI_EntityMovementSettings.CODEC.optionalFieldOf("movement", DAI_EntityMovementSettings.DEFAULT).forGetter(RuntimePart::movement),
                    DAI_EntityPortalSettings.CODEC.optionalFieldOf("portal", DAI_EntityPortalSettings.DISABLED).forGetter(RuntimePart::portal),
                    DAI_EntityRidingSettings.CODEC.optionalFieldOf("riding", DAI_EntityRidingSettings.DEFAULT).forGetter(RuntimePart::riding)
            ).apply(instance, RuntimePart::new));

    public static final Codec<DAI_EntitySettings> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    RecordCodecBuilder.of(DAI_EntitySettings::shellPart, SHELL_CODEC),
                    RecordCodecBuilder.of(DAI_EntitySettings::runtimePart, RUNTIME_CODEC)
            ).apply(instance, DAI_EntitySettings::fromParts));

    private ShellPart shellPart() {
        return new ShellPart(
                category, width, height, trackingRange, updateInterval, fireImmune,
                summonable, saveable, texture, behaviorSequence, behaviorInterval, vanillaAi
        );
    }

    private RuntimePart runtimePart() {
        return new RuntimePart(spawning, gameplay, movement, portal, riding);
    }

    private static DAI_EntitySettings fromParts(ShellPart shell, RuntimePart runtime) {
        return new DAI_EntitySettings(
                shell.category(), shell.width(), shell.height(), shell.trackingRange(), shell.updateInterval(),
                shell.fireImmune(), shell.summonable(), shell.saveable(), shell.texture(), shell.behaviorSequence(),
                shell.behaviorInterval(), shell.vanillaAi(), runtime.spawning(), runtime.gameplay(), runtime.movement(),
                runtime.portal(), runtime.riding()
        );
    }

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
        gameplay = gameplay == null ? DAI_EntityGameplaySettings.DEFAULT : gameplay;
        movement = movement == null ? DAI_EntityMovementSettings.DEFAULT : movement;
        portal = portal == null ? DAI_EntityPortalSettings.DISABLED : portal;
        riding = riding == null ? DAI_EntityRidingSettings.DEFAULT : riding;
    }

    private static String normalize(String value, String fallback) {
        return value == null ? fallback : value.trim().toLowerCase();
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
