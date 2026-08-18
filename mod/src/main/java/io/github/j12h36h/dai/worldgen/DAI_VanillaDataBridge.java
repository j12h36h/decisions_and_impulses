package io.github.j12h36h.dai.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.experience.DAI_EarlyJsonRepository;
import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Generic DAI -> Mojang data bridge.
 *
 * Most modern Minecraft customization is already a data registry. DAI should
 * not reimplement those registries in Java. This bridge gives them stable DAI
 * authoring folders while compiling the JSON back into the exact vanilla data
 * location. `dai_registry` and `dai_data` are future-proof escape hatches:
 * specify an explicit `registry`/`output` path and DAI can emit a registry or
 * JSON server-data type Mojang adds later without a new hardcoded loader.
 */
public final class DAI_VanillaDataBridge {

    private static final Map<String, String> KNOWN = knownFolders();
    private static final Set<String> META = Set.of(
            "enabled", "display_name", "description", "registry", "registry_path",
            "output", "output_path", "raw", "vanilla", "value"
    );

    private DAI_VanillaDataBridge() {}

    public static int compile(Path root, BiConsumer<Path, JsonObject> writer) throws IOException {
        int count = 0;

        for (Map.Entry<String, String> mapping : KNOWN.entrySet()) {
            Map<String, JsonObject> definitions =
                    DAI_EarlyJsonRepository.scan(mapping.getKey(), mapping.getKey());
            for (Map.Entry<String, JsonObject> entry : definitions.entrySet()) {
                if (!enabled(entry.getValue())) continue;
                IdParts id = split(entry.getKey());
                if (id == null) continue;
                Path target = target(root, id, mapping.getValue());
                writer.accept(target, payload(entry.getValue()));
                count++;
            }
        }

        count += compileOpen(root, writer, "dai_registry", "registry");
        count += compileOpen(root, writer, "dai_data", "data");
        return count;
    }


    private static int compileOpen(
            Path root,
            BiConsumer<Path, JsonObject> writer,
            String folder,
            String label
    ) {
        int count = 0;
        Map<String, JsonObject> open = DAI_EarlyJsonRepository.scan(folder, label);
        for (Map.Entry<String, JsonObject> entry : open.entrySet()) {
            JsonObject source = entry.getValue();
            if (!enabled(source)) continue;
            IdParts id = split(entry.getKey());
            if (id == null) continue;

            String output = first(source, "registry_path", "registry", "output_path", "output");
            output = normalizeOutput(output);
            if (output.isBlank()) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: Open {} definition '{}' has no registry/output path; skipped.",
                        label,
                        entry.getKey()
                );
                continue;
            }

            writer.accept(target(root, id, output), payload(source));
            count++;
        }
        return count;
    }

    public static Map<String, String> knownFolders() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();

        // Core registries / UI / audio.
        map.put("dai_world_clocks", "world_clock");
        map.put("dai_dialogs", "dialog");
        map.put("dai_enchantments_native", "enchantment");
        map.put("dai_paintings", "painting_variant");
        map.put("dai_jukebox_songs", "jukebox_song");
        map.put("dai_instruments", "instrument");
        map.put("dai_damage_types", "damage_type");
        map.put("dai_chat_types", "chat_type");
        map.put("dai_banner_patterns", "banner_pattern");
        map.put("dai_trim_materials", "trim_material");
        map.put("dai_trim_patterns", "trim_pattern");
        map.put("dai_test_instances", "test_instance");
        map.put("dai_test_environments", "test_environment");

        // Standard server-data resources. These are not all dynamic
        // registries, but their JSON is already fully Mojang-defined, so DAI
        // simply compiles the friendly folder back to the native data path.
        map.put("dai_recipes_native", "recipe");
        map.put("dai_loot_tables_native", "loot_table");
        map.put("dai_advancements_native", "advancement");
        map.put("dai_predicates_native", "predicate");
        map.put("dai_item_modifiers_native", "item_modifier");

        // 26.1 merchant and sound-variant registries.
        map.put("dai_villager_trades", "villager_trade");
        map.put("dai_trade_sets", "trade_set");
        map.put("dai_cat_sound_variants", "cat_sound_variant");
        map.put("dai_pig_sound_variants", "pig_sound_variant");
        map.put("dai_cow_sound_variants", "cow_sound_variant");
        map.put("dai_chicken_sound_variants", "chicken_sound_variant");
        map.put("dai_wolf_sound_variants", "wolf_sound_variant");

        // Data-driven mob variants.
        map.put("dai_cat_variants", "cat_variant");
        map.put("dai_pig_variants", "pig_variant");
        map.put("dai_cow_variants", "cow_variant");
        map.put("dai_chicken_variants", "chicken_variant");
        map.put("dai_wolf_variants", "wolf_variant");
        map.put("dai_frog_variants", "frog_variant");

        // Full vanilla worldgen registry surface. DAI's friendly biome,
        // dimension and timeline compilers still run separately and may use
        // the same output locations when authors prefer high-level JSON.
        map.put("dai_noise", "worldgen/noise");
        map.put("dai_noise_settings", "worldgen/noise_settings");
        map.put("dai_density_functions", "worldgen/density_function");
        map.put("dai_configured_features", "worldgen/configured_feature");
        map.put("dai_placed_features", "worldgen/placed_feature");
        map.put("dai_structure_sets", "worldgen/structure_set");
        map.put("dai_processor_lists", "worldgen/processor_list");
        map.put("dai_template_pools", "worldgen/template_pool");
        map.put("dai_native_structures", "worldgen/structure");
        map.put("dai_world_presets_native", "worldgen/world_preset");
        map.put("dai_flat_presets", "worldgen/flat_level_generator_preset");
        map.put("dai_multi_noise_parameters", "worldgen/multi_noise_biome_source_parameter_list");

        return Map.copyOf(map);
    }

    private static JsonObject payload(JsonObject source) {
        if (source == null) return new JsonObject();
        for (String key : new String[]{"raw", "vanilla", "value"}) {
            JsonElement element = source.get(key);
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject().deepCopy();
            }
        }

        JsonObject copy = source.deepCopy();
        for (String key : META) copy.remove(key);
        return copy;
    }

    private static boolean enabled(JsonObject source) {
        if (source == null || !source.has("enabled")) return true;
        try { return source.get("enabled").getAsBoolean(); }
        catch (Exception ignored) { return true; }
    }

    private static String first(JsonObject root, String... keys) {
        for (String key : keys) {
            if (root == null || !root.has(key)) continue;
            try {
                String value = root.get(key).getAsString().trim();
                if (!value.isBlank()) return value;
            } catch (Exception ignored) {}
        }
        return "";
    }

    private static String normalizeOutput(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
        if (value.startsWith("minecraft:")) value = value.substring("minecraft:".length());
        while (value.startsWith("/")) value = value.substring(1);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.contains("..")) return "";
        return value;
    }

    private static Path target(Path root, IdParts id, String output) {
        Path target = root.resolve("data").resolve(id.namespace());
        for (String part : output.split("/")) {
            if (!part.isBlank()) target = target.resolve(part);
        }
        return target.resolve(id.path() + ".json");
    }

    private static IdParts split(String raw) {
        if (raw == null) return null;
        String id = raw.trim().toLowerCase(Locale.ROOT);
        int colon = id.indexOf(':');
        if (colon <= 0 || colon >= id.length() - 1) return null;
        String namespace = id.substring(0, colon);
        String path = id.substring(colon + 1).replace('\\', '/');
        if (path.contains("..")) return null;
        return new IdParts(namespace, path);
    }

    private record IdParts(String namespace, String path) {}
}
