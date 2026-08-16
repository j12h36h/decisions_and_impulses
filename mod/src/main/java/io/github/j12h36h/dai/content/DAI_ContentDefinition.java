package io.github.j12h36h.dai.content;

import com.mojang.serialization.Codec;
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
        DAI_EntitySettings entity
) {

    private static final Codec<Map<String, Double>> DOUBLE_MAP =
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE);

    private static final Codec<Map<String, String>> STRING_MAP =
            Codec.unboundedMap(Codec.STRING, Codec.STRING);

    public static final Codec<DAI_ContentDefinition> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("carrier", "").forGetter(DAI_ContentDefinition::carrier),
                    Codec.STRING.optionalFieldOf("display_name", "").forGetter(DAI_ContentDefinition::displayName),
                    Codec.STRING.optionalFieldOf("description", "").forGetter(DAI_ContentDefinition::description),
                    Codec.STRING.optionalFieldOf("model", "").forGetter(DAI_ContentDefinition::model),
                    Codec.STRING.optionalFieldOf("slot", "").forGetter(DAI_ContentDefinition::slot),
                    Codec.STRING.listOf().optionalFieldOf("capabilities", List.of()).forGetter(DAI_ContentDefinition::capabilities),
                    Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(DAI_ContentDefinition::tags),
                    DOUBLE_MAP.optionalFieldOf("attributes", Map.of()).forGetter(DAI_ContentDefinition::attributes),
                    DOUBLE_MAP.optionalFieldOf("native_attributes", Map.of()).forGetter(DAI_ContentDefinition::nativeAttributes),
                    STRING_MAP.optionalFieldOf("events", Map.of()).forGetter(DAI_ContentDefinition::events),
                    Codec.BOOL.optionalFieldOf("registry_backed", false).forGetter(DAI_ContentDefinition::registryBacked),
                    Codec.STRING.optionalFieldOf("native_registry", "").forGetter(DAI_ContentDefinition::nativeRegistry),
                    DAI_ContentStats.CODEC.optionalFieldOf("stats", DAI_ContentStats.EMPTY).forGetter(DAI_ContentDefinition::stats),
                    DAI_EntitySettings.CODEC.optionalFieldOf("entity", DAI_EntitySettings.DEFAULT).forGetter(DAI_ContentDefinition::entity)
            ).apply(instance, DAI_ContentDefinition::new));

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
        entity = entity == null ? DAI_EntitySettings.DEFAULT : entity;
    }

    public String event(String name) {
        if (name == null) return "";
        return events.getOrDefault(name.trim().toLowerCase(), "").trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
