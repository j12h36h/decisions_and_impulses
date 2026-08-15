package io.github.j12h36h.dai.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Optional higher-level gameplay metadata for JSON-defined DAI entities. */
public record DAI_EntityGameplaySettings(
        String faction,
        String dialogue,
        String loot,
        List<String> equipment,
        Map<String, String> events
) {
    private static final Codec<Map<String, String>> STRING_MAP =
            Codec.unboundedMap(Codec.STRING, Codec.STRING);

    public static final DAI_EntityGameplaySettings DEFAULT =
            new DAI_EntityGameplaySettings("", "", "", List.of(), Map.of());

    public static final Codec<DAI_EntityGameplaySettings> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("faction", "").forGetter(DAI_EntityGameplaySettings::faction),
                    Codec.STRING.optionalFieldOf("dialogue", "").forGetter(DAI_EntityGameplaySettings::dialogue),
                    Codec.STRING.optionalFieldOf("loot", "").forGetter(DAI_EntityGameplaySettings::loot),
                    Codec.STRING.listOf().optionalFieldOf("equipment", List.of()).forGetter(DAI_EntityGameplaySettings::equipment),
                    STRING_MAP.optionalFieldOf("events", Map.of()).forGetter(DAI_EntityGameplaySettings::events)
            ).apply(instance, DAI_EntityGameplaySettings::new));

    public DAI_EntityGameplaySettings {
        faction = normalize(faction);
        dialogue = normalize(dialogue);
        loot = normalize(loot);
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        events = events == null ? Map.of() : Map.copyOf(events);
    }

    public String event(String name) {
        if (name == null) return "";
        String key = name.trim().toLowerCase(Locale.ROOT);
        String direct = events.get(key);
        if (direct != null) return direct.trim();
        for (var entry : events.entrySet()) {
            if (entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(key)) {
                return entry.getValue() == null ? "" : entry.getValue().trim();
            }
        }
        return "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
