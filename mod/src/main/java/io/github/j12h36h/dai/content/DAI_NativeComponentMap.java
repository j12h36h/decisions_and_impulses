package io.github.j12h36h.dai.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Open JSON map for native Minecraft item data components.
 *
 * Values are retained as compact JSON strings because DAI applies them later
 * through each registered DataComponentType's own Mojang codec. Using
 * Codec.PASSTHROUGH here keeps this layer generic instead of hardcoding every
 * vanilla/modded component schema into DAI.
 */
public record DAI_NativeComponentMap(Map<String, String> values) {

    public static final DAI_NativeComponentMap EMPTY =
            new DAI_NativeComponentMap(Map.of());

    private static final Codec<Map<String, Dynamic<?>>> RAW_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.PASSTHROUGH);

    public static final Codec<DAI_NativeComponentMap> CODEC =
            RAW_CODEC.xmap(
                    DAI_NativeComponentMap::decode,
                    DAI_NativeComponentMap::encode
            );

    public DAI_NativeComponentMap {
        if (values == null || values.isEmpty()) {
            values = Map.of();
        } else {
            LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
            for (var entry : values.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) continue;
                if (entry.getValue() == null || entry.getValue().isBlank()) continue;
                normalized.put(
                        entry.getKey().trim().toLowerCase(Locale.ROOT),
                        entry.getValue().trim()
                );
            }
            values = Map.copyOf(normalized);
        }
    }

    private static DAI_NativeComponentMap decode(Map<String, Dynamic<?>> raw) {
        if (raw == null || raw.isEmpty()) return EMPTY;
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
            Dynamic<?> dynamic = entry.getValue();
            if (entry.getKey() == null || dynamic == null) continue;
            try {
                Object value = dynamic.convert(JsonOps.INSTANCE).getValue();
                if (value instanceof JsonElement json) {
                    values.put(entry.getKey(), json.toString());
                } else if (value != null) {
                    values.put(entry.getKey(), value.toString());
                }
            } catch (RuntimeException ignored) {
                // Normal component validation reports malformed values later.
            }
        }
        return new DAI_NativeComponentMap(values);
    }

    private static Map<String, Dynamic<?>> encode(DAI_NativeComponentMap map) {
        if (map == null || map.values().isEmpty()) return Map.of();
        LinkedHashMap<String, Dynamic<?>> raw = new LinkedHashMap<>();
        for (var entry : map.values().entrySet()) {
            try {
                JsonElement json = JsonParser.parseString(entry.getValue());
                raw.put(entry.getKey(), new Dynamic<>(JsonOps.INSTANCE, json));
            } catch (RuntimeException ignored) {
                // Skip malformed cached values rather than making the whole
                // definition unencodable.
            }
        }
        return raw;
    }
}
