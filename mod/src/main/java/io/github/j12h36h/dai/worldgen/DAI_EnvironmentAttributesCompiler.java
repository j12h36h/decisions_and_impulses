package io.github.j12h36h.dai.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.experience.DAI_EarlyJsonRepository;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Shared 26.2 environment-attribute compiler used by DAI biomes, dimension
 * types, render profiles and timelines.
 *
 * Minecraft 26.2 moved sky/cloud/fog/environment state into data-driven
 * EnvironmentAttributes. DAI deliberately compiles its friendly JSON onto
 * those native attributes instead of maintaining a parallel renderer.
 */
public final class DAI_EnvironmentAttributesCompiler {

    private static final Map<String, String> FRIENDLY_ATTRIBUTE_IDS = friendlyIds();

    private DAI_EnvironmentAttributesCompiler() {}

    /**
     * Resolves render_profile/environment templates and then overlays any
     * attributes authored directly in {@code source}. Later layers win.
     */
    public static JsonObject attributesFor(JsonObject source) {
        JsonObject result = new JsonObject();
        if (source == null) return result;

        mergeTemplate(result, source, "render_profile", "dai_render_profiles", "render_profiles");
        mergeTemplate(result, source, "environment", "dai_environments", "environments");

        // Friendly grouped sections.
        mergeFriendlySection(result, object(source, "sky"));
        mergeFriendlySection(result, object(source, "clouds"));
        mergeFriendlySection(result, object(source, "fog"));
        mergeFriendlySection(result, object(source, "lighting"));
        mergeFriendlySection(result, object(source, "visual"));

        // Friendly values can also live directly on the definition.
        mergeFriendlySection(result, source);

        // Explicit native attributes always have final say.
        mergeObject(result, object(source, "attributes"));

        // Convenience toggles are intentionally applied after authored values.
        // They are equivalent to setting the corresponding native attribute.
        JsonObject flags = object(source, "flags");
        if (isExplicitFalse(flags, "clouds") || isExplicitFalse(object(source, "clouds"), "enabled")) {
            result.addProperty("minecraft:visual/cloud_color", "#00000000");
        }
        if (isExplicitFalse(flags, "stars") || isExplicitFalse(object(source, "sky"), "stars")) {
            result.addProperty("minecraft:visual/star_brightness", 0.0D);
        }
        return result;
    }

    /** Returns a resolved environment template by id, or an empty object. */
    public static JsonObject resolveTemplate(String rawId, String primaryFolder, String legacyFolder) {
        String id = normalizeId(rawId);
        if (id.isBlank()) return new JsonObject();
        Map<String, JsonObject> values = DAI_EarlyJsonRepository.scan(primaryFolder, legacyFolder);
        JsonObject source = values.get(id);
        return source == null ? new JsonObject() : attributesForTemplate(source);
    }

    /**
     * Maps a DAI-friendly track key such as cloud_height or sky_color to the
     * actual Minecraft 26.2 environment attribute id. Unknown namespaced ids
     * are preserved so packs can use Mojang or third-party attributes too.
     */
    public static String attributeId(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.indexOf(':') >= 0) return normalized;
        return FRIENDLY_ATTRIBUTE_IDS.getOrDefault(normalized, normalized);
    }

    /** Copies a timeline tracks object while translating friendly track ids. */
    public static JsonObject translateTracks(JsonObject rawTracks) {
        JsonObject tracks = new JsonObject();
        if (rawTracks == null) return tracks;
        for (Map.Entry<String, JsonElement> entry : rawTracks.entrySet()) {
            String id = attributeId(entry.getKey());
            if (id.isBlank()) continue;
            tracks.add(id, entry.getValue().deepCopy());
        }
        return tracks;
    }

    /**
     * Adds/overrides attributes in a target object while preserving nested
     * modifier objects exactly as authored.
     */
    public static void mergeObject(JsonObject target, JsonObject source) {
        if (target == null || source == null) return;
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            String id = attributeId(entry.getKey());
            if (id.isBlank()) continue;
            target.add(id, entry.getValue().deepCopy());
        }
    }

    private static JsonObject attributesForTemplate(JsonObject source) {
        JsonObject result = new JsonObject();
        if (source == null) return result;

        // Templates may compose other templates too. Keep recursion shallow
        // and deterministic; circular references simply stop contributing.
        mergeFriendlySection(result, object(source, "sky"));
        mergeFriendlySection(result, object(source, "clouds"));
        mergeFriendlySection(result, object(source, "fog"));
        mergeFriendlySection(result, object(source, "lighting"));
        mergeFriendlySection(result, object(source, "visual"));
        mergeFriendlySection(result, source);
        mergeObject(result, object(source, "attributes"));

        JsonObject flags = object(source, "flags");
        if (isExplicitFalse(flags, "clouds") || isExplicitFalse(object(source, "clouds"), "enabled")) {
            result.addProperty("minecraft:visual/cloud_color", "#00000000");
        }
        if (isExplicitFalse(flags, "stars") || isExplicitFalse(object(source, "sky"), "stars")) {
            result.addProperty("minecraft:visual/star_brightness", 0.0D);
        }
        return result;
    }

    private static void mergeTemplate(
            JsonObject target,
            JsonObject source,
            String field,
            String primaryFolder,
            String legacyFolder
    ) {
        String id = string(source, field, "");
        if (id.isBlank()) return;
        mergeObject(target, resolveTemplate(id, primaryFolder, legacyFolder));
    }

    private static void mergeFriendlySection(JsonObject target, JsonObject section) {
        if (target == null || section == null) return;
        for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
            String id = FRIENDLY_ATTRIBUTE_IDS.get(entry.getKey().trim().toLowerCase(Locale.ROOT));
            if (id == null) continue;
            target.add(id, entry.getValue().deepCopy());
        }
    }

    private static Map<String, String> friendlyIds() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();

        // Sky / celestial presentation.
        map.put("sky_color", "minecraft:visual/sky_color");
        map.put("sky_light_color", "minecraft:visual/sky_light_color");
        map.put("sky_light_factor", "minecraft:visual/sky_light_factor");
        map.put("star_brightness", "minecraft:visual/star_brightness");
        map.put("sunrise_sunset_color", "minecraft:visual/sunrise_sunset_color");
        map.put("sun_angle", "minecraft:visual/sun_angle");
        map.put("moon_angle", "minecraft:visual/moon_angle");
        map.put("star_angle", "minecraft:visual/star_angle");
        map.put("moon_phase", "minecraft:visual/moon_phase");

        // Clouds.
        map.put("cloud_color", "minecraft:visual/cloud_color");
        map.put("cloud_height", "minecraft:visual/cloud_height");
        map.put("cloud_fog_end_distance", "minecraft:visual/cloud_fog_end_distance");

        // Fog / visibility.
        map.put("fog_color", "minecraft:visual/fog_color");
        map.put("fog_start_distance", "minecraft:visual/fog_start_distance");
        map.put("fog_end_distance", "minecraft:visual/fog_end_distance");
        map.put("sky_fog_end_distance", "minecraft:visual/sky_fog_end_distance");
        map.put("water_fog_color", "minecraft:visual/water_fog_color");
        map.put("water_fog_start_distance", "minecraft:visual/water_fog_start_distance");
        map.put("water_fog_end_distance", "minecraft:visual/water_fog_end_distance");

        // General lighting.
        map.put("ambient_light_color", "minecraft:visual/ambient_light_color");
        map.put("block_light_tint", "minecraft:visual/block_light_tint");
        map.put("night_vision_color", "minecraft:visual/night_vision_color");

        // Ambient presentation / audio. Explicit native attribute maps remain
        // available for anything not covered by these friendly aliases.
        map.put("ambient_particles", "minecraft:visual/ambient_particles");
        map.put("background_music", "minecraft:audio/background_music");
        map.put("music_volume", "minecraft:audio/music_volume");
        map.put("ambient_sounds", "minecraft:audio/ambient_sounds");

        // Gameplay/environment values that are useful for custom dimensions.
        map.put("sky_light_level", "minecraft:gameplay/sky_light_level");
        map.put("water_evaporates", "minecraft:gameplay/water_evaporates");
        map.put("fast_lava", "minecraft:gameplay/fast_lava");
        map.put("piglins_zombify", "minecraft:gameplay/piglins_zombify");
        map.put("can_start_raid", "minecraft:gameplay/can_start_raid");
        map.put("respawn_anchor_works", "minecraft:gameplay/respawn_anchor_works");
        map.put("nether_portal_spawns_piglin", "minecraft:gameplay/nether_portal_spawns_piglin");
        map.put("snow_golem_melts", "minecraft:gameplay/snow_golem_melts");

        return Map.copyOf(map);
    }


    private static boolean isExplicitFalse(JsonObject root, String key) {
        if (root == null || !root.has(key)) return false;
        try { return !root.get(key).getAsBoolean(); }
        catch (Exception ignored) { return false; }
    }

    private static JsonObject object(JsonObject root, String key) {
        if (root == null || !root.has(key)) return null;
        JsonElement element = root.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String string(JsonObject root, String key, String fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsString().trim(); }
        catch (Exception ignored) { return fallback; }
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
