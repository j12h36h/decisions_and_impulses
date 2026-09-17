package io.github.j12h36h.dai.client.packs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Transport model shared by ERAS/DAI Worlds and the bundled fallback.
 * Schema 1/2 remain accepted; schema 3 adds explicit public pack classification (Experience Pack or Addon).
 */
public record DAI_OfficialPackCatalog(
        int schema,
        List<PackEntry> packs
) {
    public DAI_OfficialPackCatalog {
        packs = packs == null ? List.of() : List.copyOf(packs);
    }

    public static DAI_OfficialPackCatalog parse(JsonObject root) {
        if (root == null) return new DAI_OfficialPackCatalog(1, List.of());

        int schema = integer(root, "schema", 1);
        JsonArray array = array(root, "packs");
        if (array == null) array = array(root, "worlds");
        List<PackEntry> packs = new ArrayList<>();

        if (array != null) {
            for (JsonElement element : array) {
                if (element == null || !element.isJsonObject()) continue;
                PackEntry pack = parsePack(element.getAsJsonObject());
                if (pack != null) packs.add(pack);
            }
        }

        return new DAI_OfficialPackCatalog(schema, packs);
    }

    private static PackEntry parsePack(JsonObject root) {
        String id = string(root, "id", "");
        if (id.isBlank()) return null;

        JsonArray componentArray = array(root, "components");
        List<ComponentEntry> components = new ArrayList<>();
        if (componentArray != null) {
            for (JsonElement element : componentArray) {
                if (element == null || !element.isJsonObject()) continue;
                ComponentEntry component = parseComponent(element.getAsJsonObject());
                if (component != null) components.add(component);
            }
        }

        JsonObject engine = object(root, "engine");
        String minEngine = engine == null
                ? string(root, "minimum_engine", "")
                : string(engine, "minimum", string(engine, "min", ""));
        String maxEngine = engine == null
                ? string(root, "maximum_engine", "")
                : string(engine, "maximum", string(engine, "max", ""));

        String experienceId = string(root, "experience", string(root, "experience_id", ""));
        PublicKind publicKind = PublicKind.parse(
                string(root, "public_type", string(root, "kind", string(root, "catalog_type", string(root, "role", "")))),
                string(root, "category", ""),
                string(root, "type", ""),
                experienceId
        );

        return new PackEntry(
                id,
                string(root, "name", id),
                string(root, "summary", ""),
                string(root, "version", "1.0"),
                string(root, "type", components.size() > 1 ? "combo" : "datapack"),
                string(root, "info_url", ""),
                string(root, "icon_item", "minecraft:chest"),
                bool(root, "featured", false),
                publicKind,
                experienceId,
                string(root, "creator", ""),
                string(root, "category", "World"),
                strings(root, "tags"),
                minEngine,
                maxEngine,
                string(root, "presentation", string(root, "presentation_id", "")),
                string(root, "updated", string(root, "updated_at", "")),
                components
        );
    }

    private static ComponentEntry parseComponent(JsonObject root) {
        String type = string(root, "type", "").toLowerCase(Locale.ROOT);
        if (!"datapack".equals(type) && !"resource_pack".equals(type)) return null;

        int fileId = integer(root, "curseforge_file_id", 0);
        String fileName = string(root, "file_name", "");
        String directUrl = string(root, "download_url", "");

        if (fileId <= 0 && directUrl.isBlank()) return null;
        if (fileId > 0 && fileName.isBlank()) return null;

        return new ComponentEntry(
                string(root, "id", type),
                type,
                fileId,
                fileName,
                directUrl,
                string(root, "sha256", "")
        );
    }

    /** Public ERAS catalog has exactly two user-facing pack classes. */
    public enum PublicKind {
        EXPERIENCE_PACK("Experience Pack"),
        ADDON("Addon");

        private final String label;

        PublicKind(String label) { this.label = label; }

        public String label() { return label; }

        private static PublicKind parse(
                String explicit,
                String legacyCategory,
                String legacyType,
                String experienceId
        ) {
            String value = safe(explicit, "").trim().toLowerCase(Locale.ROOT);
            if (value.isBlank()) value = safe(legacyCategory, "").trim().toLowerCase(Locale.ROOT);
            if (value.isBlank()) value = safe(legacyType, "").trim().toLowerCase(Locale.ROOT);

            if (value.equals("addon") || value.equals("add-on") || value.equals("add_on")
                    || value.equals("extension") || value.equals("module")) {
                return ADDON;
            }
            if (value.equals("experience") || value.equals("experience_pack")
                    || value.equals("experience-pack") || value.equals("world")
                    || value.equals("game")) {
                return EXPERIENCE_PACK;
            }

            // Schema 1/2 compatibility: anything with a launchable experience
            // is an Experience Pack; other public content is an Addon.
            return safe(experienceId, "").isBlank() ? ADDON : EXPERIENCE_PACK;
        }
    }

    public record PackEntry(
            String id,
            String name,
            String summary,
            String version,
            String type,
            String infoUrl,
            String iconItem,
            boolean featured,
            PublicKind publicKind,
            String experienceId,
            String creator,
            String category,
            List<String> tags,
            String minimumEngine,
            String maximumEngine,
            String presentationId,
            String updatedAt,
            List<ComponentEntry> components
    ) {
        public PackEntry {
            id = safe(id, "pack");
            name = safe(name, id);
            summary = safe(summary, "");
            version = safe(version, "1.0");
            type = safe(type, "datapack").toLowerCase(Locale.ROOT);
            infoUrl = safe(infoUrl, "");
            iconItem = safe(iconItem, "minecraft:chest");
            publicKind = publicKind == null ? (safe(experienceId, "").isBlank() ? PublicKind.ADDON : PublicKind.EXPERIENCE_PACK) : publicKind;
            experienceId = safe(experienceId, "");
            creator = safe(creator, "");
            category = safe(category, "World");
            tags = tags == null ? List.of() : List.copyOf(tags);
            minimumEngine = safe(minimumEngine, "");
            maximumEngine = safe(maximumEngine, "");
            presentationId = safe(presentationId, "");
            updatedAt = safe(updatedAt, "");
            components = components == null ? List.of() : List.copyOf(components);
        }

        public boolean needsWorld() { return false; }
        public boolean installable() { return !components.isEmpty(); }
        public boolean isAddon() { return publicKind == PublicKind.ADDON; }
        public boolean isExperiencePack() { return publicKind == PublicKind.EXPERIENCE_PACK; }
        public boolean playable() { return isExperiencePack() && !experienceId.isBlank(); }

        public boolean compatible() {
            return DAI_Versioning.atLeast(DAI_Core.FEATURE_LEVEL, minimumEngine)
                    && DAI_Versioning.atMost(DAI_Core.FEATURE_LEVEL, maximumEngine);
        }

        public String compatibilityLabel() {
            if (compatible()) return "Compatible with DAI " + DAI_Core.FEATURE_LEVEL;
            if (!minimumEngine.isBlank() && !DAI_Versioning.atLeast(DAI_Core.FEATURE_LEVEL, minimumEngine)) {
                return "Requires DAI " + minimumEngine + "+";
            }
            if (!maximumEngine.isBlank()) return "Requires DAI <= " + maximumEngine;
            return "Incompatible with DAI " + DAI_Core.FEATURE_LEVEL;
        }
    }

    public record ComponentEntry(
            String id,
            String type,
            int curseForgeFileId,
            String fileName,
            String downloadUrl,
            String sha256
    ) {
        public ComponentEntry {
            id = safe(id, type);
            type = safe(type, "datapack").toLowerCase(Locale.ROOT);
            fileName = safe(fileName, "");
            downloadUrl = safe(downloadUrl, "");
            sha256 = safe(sha256, "");
        }
    }

    private static JsonArray array(JsonObject root, String key) {
        JsonElement element = root == null ? null : root.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement element = root == null ? null : root.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static List<String> strings(JsonObject root, String key) {
        JsonArray array = array(root, key);
        if (array == null) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            try {
                String value = element.getAsString();
                if (!value.isBlank()) values.add(value);
            } catch (Exception ignored) { }
        }
        return List.copyOf(values);
    }

    private static String string(JsonObject root, String key, String fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsString(); }
        catch (Exception ignored) { return fallback; }
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsBoolean(); }
        catch (Exception ignored) { return fallback; }
    }

    private static int integer(JsonObject root, String key, int fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsInt(); }
        catch (Exception ignored) { return fallback; }
    }

    private static String safe(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
