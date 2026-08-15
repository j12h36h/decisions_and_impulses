package io.github.j12h36h.dai.client.packs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small transport model shared by the website catalog and bundled fallback. */
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

        return new PackEntry(
                id,
                string(root, "name", id),
                string(root, "summary", ""),
                string(root, "version", "1.0"),
                string(root, "type", components.size() > 1 ? "combo" : "datapack"),
                string(root, "info_url", ""),
                string(root, "icon_item", "minecraft:chest"),
                bool(root, "featured", false),
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

    public record PackEntry(
            String id,
            String name,
            String summary,
            String version,
            String type,
            String infoUrl,
            String iconItem,
            boolean featured,
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
            components = components == null ? List.of() : List.copyOf(components);
        }

        /**
         * DAI 1.8.2 installs datapack components into the global datapack
         * library. Experience launch later hands the declaring pack to the
         * target save, so catalog installation no longer requires choosing a
         * world up front.
         */
        public boolean needsWorld() {
            return false;
        }

        public boolean installable() {
            return !components.isEmpty();
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
        JsonElement element = root.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String string(JsonObject root, String key, String fallback) {
        if (root == null || !root.has(key)) return fallback;
        try {
            return root.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        if (root == null || !root.has(key)) return fallback;
        try {
            return root.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int integer(JsonObject root, String key, int fallback) {
        if (root == null || !root.has(key)) return fallback;
        try {
            return root.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String safe(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
