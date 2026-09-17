package io.github.j12h36h.dai.packs;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Persistent, version-independent ADDON selection for one world save. */
public final class DAI_WorldAddonSelection {

    private static final String MARKER = "dai/addons.json";

    private DAI_WorldAddonSelection() {}

    public static Optional<Selection> read(Path worldRoot) {
        if (worldRoot == null) return Optional.empty();
        Path marker = worldRoot.resolve(MARKER);
        if (!Files.isRegularFile(marker)) return Optional.empty();

        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(marker, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return Optional.empty();
            JsonObject json = parsed.getAsJsonObject();
            LinkedHashSet<String> selected = new LinkedHashSet<>();
            if (json.has("selected") && json.get("selected").isJsonArray()) {
                for (JsonElement value : json.getAsJsonArray("selected")) {
                    if (!value.isJsonPrimitive()) continue;
                    String id = normalize(value.getAsString());
                    if (!id.isBlank()) selected.add(id);
                }
            }
            return Optional.of(new Selection(Set.copyOf(selected)));
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not read world ADDON selection '{}'.", marker, exception);
            return Optional.empty();
        }
    }

    public static boolean write(Path worldRoot, Collection<String> stableIds) {
        if (worldRoot == null) return false;
        Path marker = worldRoot.resolve(MARKER);
        try {
            LinkedHashSet<String> selected = new LinkedHashSet<>();
            if (stableIds != null) {
                for (String value : stableIds) {
                    String id = normalize(value);
                    if (!id.isBlank()) selected.add(id);
                }
            }

            JsonObject json = new JsonObject();
            json.addProperty("managed", true);
            json.addProperty("selection_version", 1);
            JsonArray array = new JsonArray();
            selected.stream().sorted().forEach(array::add);
            json.add("selected", array);

            Files.createDirectories(marker.getParent());
            Files.writeString(
                    marker,
                    new GsonBuilder().setPrettyPrinting().create().toJson(json),
                    StandardCharsets.UTF_8
            );
            return true;
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not write world ADDON selection '{}'.", marker, exception);
            return false;
        }
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('\\', '/').replaceAll("\\s+", "_");
    }

    public record Selection(Set<String> stableIds) {
        public Selection {
            stableIds = stableIds == null ? Set.of() : Set.copyOf(stableIds);
        }

        public boolean includes(Path addon) {
            return addon != null && includes(DAI_DatapackMetadata.stableId(addon));
        }

        public boolean includes(String stableId) {
            String id = normalize(stableId);
            return !id.isBlank() && stableIds.contains(id);
        }
    }
}
