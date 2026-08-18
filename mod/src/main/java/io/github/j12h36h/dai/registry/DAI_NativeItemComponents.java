package io.github.j12h36h.dai.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;

import java.util.Map;

/**
 * Open JSON bridge for default components on registry-backed DAI items.
 *
 * Component values are intentionally decoded with each component type's own
 * Mojang codec. DAI therefore does not need a Java branch for every component
 * Minecraft adds. 26.1+ initializes item components after datapack registries
 * are available, so delayedComponent also permits values that reference
 * datapack registry objects such as instruments, damage types or songs.
 */
public final class DAI_NativeItemComponents {

    private DAI_NativeItemComponents() {}

    public static void apply(Item.Properties properties, Map<String, String> rawComponents) {
        if (properties == null || rawComponents == null || rawComponents.isEmpty()) return;

        for (Map.Entry<String, String> entry : rawComponents.entrySet()) {
            Identifier id = Identifier.tryParse(entry.getKey());
            if (id == null) {
                DAI_Core.LOGGER.warn("<DAI>: Invalid native item component id '{}'.", entry.getKey());
                continue;
            }

            DataComponentType<?> componentType = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
            if (componentType == null) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: Native item component '{}' is not registered by this Minecraft runtime; skipped.",
                        id
                );
                continue;
            }

            String json = entry.getValue();
            applyOne(properties, componentType, id, json == null ? "null" : json);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> void applyOne(
            Item.Properties properties,
            DataComponentType<T> componentType,
            Identifier componentId,
            String rawJson
    ) {
        Codec<T> codec = componentType.codec();
        if (codec == null) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Native item component '{}' has no persistent codec and cannot be authored from JSON.",
                    componentId
            );
            return;
        }

        JsonElement json;
        try {
            json = JsonParser.parseString(rawJson);
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Native item component '{}' contains malformed JSON; skipped.",
                    componentId,
                    exception
            );
            return;
        }

        properties.delayedComponent(
                componentType,
                provider -> decode(componentId, codec, provider, json)
        );
    }

    private static <T> T decode(
            Identifier componentId,
            Codec<T> codec,
            HolderLookup.Provider provider,
            JsonElement json
    ) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, provider);
        DataResult<T> result = codec.parse(ops, json);
        return result.getOrThrow(error -> new IllegalArgumentException(
                "DAI component '" + componentId + "' could not decode: " + error
        ));
    }
}
