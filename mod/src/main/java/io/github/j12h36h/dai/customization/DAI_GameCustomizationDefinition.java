package io.github.j12h36h.dai.customization;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Open-ended data model shared by the 1.9 customization registries.
 *
 * The common fields cover presentation and dispatch while properties/numbers/
 * flags/entries deliberately preserve domain-specific data. This lets DAI add
 * richer native runtimes during the 1.9 -> 2.0 stabilization cycle without
 * invalidating 1.9-authored packs or forcing every category into a giant Java
 * record today.
 */
public record DAI_GameCustomizationDefinition(
        String displayName,
        String description,
        String carrier,
        String model,
        String texture,
        String category,
        String sequence,
        String command,
        String target,
        List<String> tags,
        List<String> entries,
        Map<String, String> properties,
        Map<String, Double> numbers,
        Map<String, Boolean> flags,
        Map<String, String> events
) {

    private static final Codec<Map<String, String>> STRING_MAP =
            Codec.unboundedMap(Codec.STRING, Codec.STRING);
    private static final Codec<Map<String, Double>> DOUBLE_MAP =
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE);
    private static final Codec<Map<String, Boolean>> BOOLEAN_MAP =
            Codec.unboundedMap(Codec.STRING, Codec.BOOL);

    public static final Codec<DAI_GameCustomizationDefinition> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("display_name", "").forGetter(DAI_GameCustomizationDefinition::displayName),
                    Codec.STRING.optionalFieldOf("description", "").forGetter(DAI_GameCustomizationDefinition::description),
                    Codec.STRING.optionalFieldOf("carrier", "").forGetter(DAI_GameCustomizationDefinition::carrier),
                    Codec.STRING.optionalFieldOf("model", "").forGetter(DAI_GameCustomizationDefinition::model),
                    Codec.STRING.optionalFieldOf("texture", "").forGetter(DAI_GameCustomizationDefinition::texture),
                    Codec.STRING.optionalFieldOf("category", "").forGetter(DAI_GameCustomizationDefinition::category),
                    Codec.STRING.optionalFieldOf("sequence", "").forGetter(DAI_GameCustomizationDefinition::sequence),
                    Codec.STRING.optionalFieldOf("command", "").forGetter(DAI_GameCustomizationDefinition::command),
                    Codec.STRING.optionalFieldOf("target", "").forGetter(DAI_GameCustomizationDefinition::target),
                    Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(DAI_GameCustomizationDefinition::tags),
                    Codec.STRING.listOf().optionalFieldOf("entries", List.of()).forGetter(DAI_GameCustomizationDefinition::entries),
                    STRING_MAP.optionalFieldOf("properties", Map.of()).forGetter(DAI_GameCustomizationDefinition::properties),
                    DOUBLE_MAP.optionalFieldOf("numbers", Map.of()).forGetter(DAI_GameCustomizationDefinition::numbers),
                    BOOLEAN_MAP.optionalFieldOf("flags", Map.of()).forGetter(DAI_GameCustomizationDefinition::flags),
                    STRING_MAP.optionalFieldOf("events", Map.of()).forGetter(DAI_GameCustomizationDefinition::events)
            ).apply(instance, DAI_GameCustomizationDefinition::new));

    public DAI_GameCustomizationDefinition {
        displayName = text(displayName);
        description = text(description);
        carrier = id(carrier);
        model = id(model);
        texture = id(texture);
        category = id(category);
        sequence = text(sequence);
        command = text(command);
        target = text(target);
        tags = tags == null ? List.of() : List.copyOf(tags);
        entries = entries == null ? List.of() : List.copyOf(entries);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        numbers = numbers == null ? Map.of() : Map.copyOf(numbers);
        flags = flags == null ? Map.of() : Map.copyOf(flags);
        events = events == null ? Map.of() : Map.copyOf(events);
    }

    public String event(String name) {
        if (name == null) return "";
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        String exact = events.get(normalized);
        if (exact != null) return exact.trim();
        for (var entry : events.entrySet()) {
            if (entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(normalized)) {
                return entry.getValue() == null ? "" : entry.getValue().trim();
            }
        }
        return "";
    }

    public String property(String name) {
        if (name == null) return "";
        String exact = properties.get(name);
        if (exact != null) return exact.trim();
        for (var entry : properties.entrySet()) {
            if (entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(name.trim())) {
                return entry.getValue() == null ? "" : entry.getValue().trim();
            }
        }
        return "";
    }

    public double number(String name, double fallback) {
        if (name == null) return fallback;
        Double exact = numbers.get(name);
        if (exact != null && Double.isFinite(exact)) return exact;
        for (var entry : numbers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(name.trim())) {
                Double value = entry.getValue();
                return value != null && Double.isFinite(value) ? value : fallback;
            }
        }
        return fallback;
    }

    public boolean flag(String name, boolean fallback) {
        if (name == null) return fallback;
        Boolean exact = flags.get(name);
        if (exact != null) return exact;
        for (var entry : flags.entrySet()) {
            if (entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(name.trim())) {
                return entry.getValue() == null ? fallback : entry.getValue();
            }
        }
        return fallback;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String id(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
