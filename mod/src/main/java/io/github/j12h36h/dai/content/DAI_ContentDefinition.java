package io.github.j12h36h.dai.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.j12h36h.dai.entity.DAI_EntitySettings;

import java.util.List;
import java.util.Map;

/**
 * Reloadable, datapack-defined DAI content metadata.
 *
 * By default a definition remains a hot-reloadable DAI identity backed by a
 * vanilla/modded carrier. Setting registry_backed=true opts the identity into
 * DAI's native-registry flow. Definitions already installed on disk are
 * discovered during early mod bootstrap and registered in the same Minecraft
 * launch. Runtime-only fields still hot-reload normally. Adding/removing a
 * native id, or editing fields baked into its native registry shell, is staged
 * for the next launch because Minecraft's static registry has already frozen.
 */
public record DAI_ContentDefinition(
        String carrier,
        String displayName,
        String description,
        String model,
        String slot,
        List<String> capabilities,
        List<String> tags,
        Map<String, Double> attributes,
        Map<String, Double> nativeAttributes,
        Map<String, String> events,
        boolean registryBacked,
        String nativeRegistry,
        DAI_ContentStats stats,
        DAI_BlockSettings block,
        DAI_ProjectileSettings projectile,
        DAI_ParticleSettings particle,
        DAI_PotionSettings potion,
        DAI_EffectSettings effect,
        DAI_EntitySettings entity,
        DAI_NativeComponentMap nativeComponents
) {

    private static final Codec<Map<String, Double>> DOUBLE_MAP =
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE);

    private static final Codec<Map<String, String>> STRING_MAP =
            Codec.unboundedMap(Codec.STRING, Codec.STRING);

    private record IdentityPart(
            String carrier,
            String displayName,
            String description,
            String model,
            String slot,
            List<String> capabilities,
            List<String> tags,
            Map<String, Double> attributes,
            Map<String, Double> nativeAttributes,
            Map<String, String> events,
            boolean registryBacked,
            String nativeRegistry
    ) {}

    private record SpecializationPart(
            DAI_ContentStats stats,
            DAI_BlockSettings block,
            DAI_ProjectileSettings projectile,
            DAI_ParticleSettings particle,
            DAI_PotionSettings potion,
            DAI_EffectSettings effect,
            DAI_EntitySettings entity,
            DAI_NativeComponentMap nativeComponents
    ) {}

    private static final MapCodec<IdentityPart> IDENTITY_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("carrier", "").forGetter(IdentityPart::carrier),
                    Codec.STRING.optionalFieldOf("display_name", "").forGetter(IdentityPart::displayName),
                    Codec.STRING.optionalFieldOf("description", "").forGetter(IdentityPart::description),
                    Codec.STRING.optionalFieldOf("model", "").forGetter(IdentityPart::model),
                    Codec.STRING.optionalFieldOf("slot", "").forGetter(IdentityPart::slot),
                    Codec.STRING.listOf().optionalFieldOf("capabilities", List.of()).forGetter(IdentityPart::capabilities),
                    Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(IdentityPart::tags),
                    DOUBLE_MAP.optionalFieldOf("attributes", Map.of()).forGetter(IdentityPart::attributes),
                    DOUBLE_MAP.optionalFieldOf("native_attributes", Map.of()).forGetter(IdentityPart::nativeAttributes),
                    STRING_MAP.optionalFieldOf("events", Map.of()).forGetter(IdentityPart::events),
                    Codec.BOOL.optionalFieldOf("registry_backed", false).forGetter(IdentityPart::registryBacked),
                    Codec.STRING.optionalFieldOf("native_registry", "").forGetter(IdentityPart::nativeRegistry)
            ).apply(instance, IdentityPart::new));

    private static final MapCodec<SpecializationPart> SPECIALIZATION_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DAI_ContentStats.CODEC.optionalFieldOf("stats", DAI_ContentStats.EMPTY).forGetter(SpecializationPart::stats),
                    DAI_BlockSettings.CODEC.optionalFieldOf("block", DAI_BlockSettings.DEFAULT).forGetter(SpecializationPart::block),
                    DAI_ProjectileSettings.CODEC.optionalFieldOf("projectile", DAI_ProjectileSettings.DEFAULT).forGetter(SpecializationPart::projectile),
                    DAI_ParticleSettings.CODEC.optionalFieldOf("particle", DAI_ParticleSettings.DEFAULT).forGetter(SpecializationPart::particle),
                    DAI_PotionSettings.CODEC.optionalFieldOf("potion", DAI_PotionSettings.DEFAULT).forGetter(SpecializationPart::potion),
                    DAI_EffectSettings.CODEC.optionalFieldOf("effect", DAI_EffectSettings.DEFAULT).forGetter(SpecializationPart::effect),
                    DAI_EntitySettings.CODEC.optionalFieldOf("entity", DAI_EntitySettings.DEFAULT).forGetter(SpecializationPart::entity),
                    DAI_NativeComponentMap.CODEC.optionalFieldOf("components", DAI_NativeComponentMap.EMPTY).forGetter(SpecializationPart::nativeComponents)
            ).apply(instance, SpecializationPart::new));

    public static final Codec<DAI_ContentDefinition> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    RecordCodecBuilder.of(DAI_ContentDefinition::identityPart, IDENTITY_CODEC),
                    RecordCodecBuilder.of(DAI_ContentDefinition::specializationPart, SPECIALIZATION_CODEC)
            ).apply(instance, DAI_ContentDefinition::fromParts));

    private IdentityPart identityPart() {
        return new IdentityPart(
                carrier, displayName, description, model, slot, capabilities, tags,
                attributes, nativeAttributes, events, registryBacked, nativeRegistry
        );
    }

    private SpecializationPart specializationPart() {
        return new SpecializationPart(stats, block, projectile, particle, potion, effect, entity, nativeComponents);
    }

    private static DAI_ContentDefinition fromParts(IdentityPart identity, SpecializationPart specialization) {
        return new DAI_ContentDefinition(
                identity.carrier(), identity.displayName(), identity.description(), identity.model(), identity.slot(),
                identity.capabilities(), identity.tags(), identity.attributes(), identity.nativeAttributes(), identity.events(),
                identity.registryBacked(), identity.nativeRegistry(),
                specialization.stats(), specialization.block(), specialization.projectile(), specialization.particle(),
                specialization.potion(), specialization.effect(), specialization.entity(), specialization.nativeComponents()
        );
    }

    public DAI_ContentDefinition {
        carrier = normalize(carrier);
        displayName = displayName == null ? "" : displayName.trim();
        description = description == null ? "" : description.trim();
        model = normalize(model);
        slot = normalize(slot);
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        tags = tags == null ? List.of() : List.copyOf(tags);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        nativeAttributes = nativeAttributes == null ? Map.of() : Map.copyOf(nativeAttributes);
        events = events == null ? Map.of() : Map.copyOf(events);
        nativeRegistry = normalize(nativeRegistry);
        stats = stats == null ? DAI_ContentStats.EMPTY : stats;
        block = block == null ? DAI_BlockSettings.DEFAULT : block;
        projectile = projectile == null ? DAI_ProjectileSettings.DEFAULT : projectile;
        particle = particle == null ? DAI_ParticleSettings.DEFAULT : particle;
        potion = potion == null ? DAI_PotionSettings.DEFAULT : potion;
        effect = effect == null ? DAI_EffectSettings.DEFAULT : effect;
        entity = entity == null ? DAI_EntitySettings.DEFAULT : entity;
        nativeComponents = nativeComponents == null ? DAI_NativeComponentMap.EMPTY : nativeComponents;
    }

    public String event(String name) {
        if (name == null) return "";
        return events.getOrDefault(name.trim().toLowerCase(), "").trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
