package io.github.j12h36h.dai.client.learning;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Safe data-only hotpatch importer for Sapphire knowledge JSON files. */
public final class DAI_KnowledgeImport {
    private DAI_KnowledgeImport() {}

    public static Path inbox(String configuredRelativeFolder) {
        Path game = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        String configured = configuredRelativeFolder == null || configuredRelativeFolder.isBlank()
                ? "dai_learning/knowledge_inbox"
                : configuredRelativeFolder.trim().replace('\\', '/');
        while (configured.startsWith("/")) configured = configured.substring(1);
        Path candidate = game.resolve(configured).normalize();
        if (!candidate.startsWith(game)) candidate = game.resolve("dai_learning/knowledge_inbox").normalize();
        try { Files.createDirectories(candidate); } catch (Exception ignored) {}
        return candidate;
    }

    public static ImportResult inspect(String configuredRelativeFolder, String selection) {
        List<Path> files = selectFiles(configuredRelativeFolder, selection);
        if (files.isEmpty()) return new ImportResult(0, 0, 0, 0, 0, 0, "No matching JSON knowledge files were found in " + inbox(configuredRelativeFolder));

        int concepts = 0;
        int definitions = 0;
        int aliases = 0;
        int relations = 0;
        int groundings = 0;
        int replies = 0;
        List<String> names = new ArrayList<>();
        for (Path path : files) {
            try {
                JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                Counts count = count(root);
                concepts += count.concepts();
                definitions += count.definitions();
                aliases += count.aliases();
                relations += count.relations();
                groundings += count.groundings();
                replies += count.replies();
                names.add(path.getFileName().toString());
            } catch (Exception exception) {
                names.add(path.getFileName() + " (invalid)");
            }
        }
        String message = "Read " + files.size() + " file(s): " + String.join(", ", names)
                + " // concepts=" + concepts
                + ", definitions=" + definitions
                + ", aliases=" + aliases
                + ", relations=" + relations
                + ", groundings=" + groundings
                + ", replies=" + replies;
        return new ImportResult(files.size(), concepts, definitions, aliases, relations, groundings + replies, message);
    }

    public static ImportResult learn(
            DAI_LearningMemory memory,
            String configuredRelativeFolder,
            String selection,
            boolean allowGroundings
    ) {
        List<Path> files = selectFiles(configuredRelativeFolder, selection);
        if (files.isEmpty()) return new ImportResult(0, 0, 0, 0, 0, 0, "No matching JSON knowledge files were found in " + inbox(configuredRelativeFolder));

        int concepts = 0;
        int definitions = 0;
        int aliases = 0;
        int relations = 0;
        int extras = 0;
        List<String> errors = new ArrayList<>();

        for (Path path : files) {
            try {
                JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                String source = "knowledge_file:" + path.getFileName();
                ApplyCounts count = apply(memory, root, source, allowGroundings);
                concepts += count.concepts();
                definitions += count.definitions();
                aliases += count.aliases();
                relations += count.relations();
                extras += count.groundings() + count.replies();
            } catch (Exception exception) {
                errors.add(path.getFileName().toString());
                DAI_Core.LOGGER.warn("<DAI>: Could not import Sapphire knowledge file '{}'.", path, exception);
            }
        }

        memory.save();
        String message = "Learned from " + (files.size() - errors.size()) + "/" + files.size() + " file(s)"
                + " // concepts=" + concepts
                + ", definitions=" + definitions
                + ", aliases=" + aliases
                + ", relations=" + relations
                + ", groundings/replies=" + extras;
        if (!errors.isEmpty()) message += " // invalid: " + String.join(", ", errors);
        return new ImportResult(files.size(), concepts, definitions, aliases, relations, extras, message);
    }

    private static List<Path> selectFiles(String configuredRelativeFolder, String selection) {
        Path root = inbox(configuredRelativeFolder);
        String requested = selection == null ? "here" : selection.trim();
        if (requested.isBlank() || requested.equalsIgnoreCase("here") || requested.equalsIgnoreCase("all")) {
            try (var stream = Files.list(root)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .toList();
            } catch (Exception ignored) {
                return List.of();
            }
        }

        requested = requested.replace('\\', '/');
        if (!requested.toLowerCase(Locale.ROOT).endsWith(".json")) requested += ".json";
        Path candidate = root.resolve(requested).normalize();
        if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) return List.of();
        return List.of(candidate);
    }

    private static ApplyCounts apply(DAI_LearningMemory memory, JsonObject root, String source, boolean allowGroundings) {
        int concepts = 0;
        int definitions = 0;
        int aliases = 0;
        int relations = 0;
        int groundings = 0;
        int replies = 0;

        JsonObject definitionObject = object(root, "definitions");
        if (definitionObject != null) {
            for (Map.Entry<String, JsonElement> entry : definitionObject.entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) continue;
                if (memory.learnDefinition(entry.getKey(), entry.getValue().getAsString(), 1.0D, source)) definitions++;
            }
        }

        JsonObject aliasObject = object(root, "aliases");
        if (aliasObject != null) {
            for (Map.Entry<String, JsonElement> entry : aliasObject.entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) continue;
                if (memory.learnAlias(entry.getKey(), entry.getValue().getAsString())) aliases++;
            }
        }

        JsonArray conceptArray = array(root, "concepts");
        if (conceptArray != null) {
            for (JsonElement element : conceptArray) {
                if (!element.isJsonObject()) continue;
                JsonObject concept = element.getAsJsonObject();
                String id = firstString(concept, "id", "name", "concept");
                if (id.isBlank()) continue;
                if (memory.ensureConcept(id)) concepts++;
                String definition = string(concept, "definition", "");
                if (!definition.isBlank() && memory.learnDefinition(id, definition, number(concept, "confidence", 1.0D), source)) definitions++;

                JsonArray conceptAliases = array(concept, "aliases");
                if (conceptAliases != null) {
                    for (JsonElement alias : conceptAliases) {
                        if (alias.isJsonPrimitive() && memory.learnAlias(alias.getAsString(), id)) aliases++;
                    }
                }

                JsonArray conceptRelations = array(concept, "relations");
                if (conceptRelations != null) {
                    for (JsonElement relElement : conceptRelations) {
                        if (!relElement.isJsonObject()) continue;
                        JsonObject rel = relElement.getAsJsonObject();
                        String predicate = firstString(rel, "predicate", "relation", "type");
                        String object = firstString(rel, "object", "target", "value");
                        if (memory.learnRelation(id, predicate, object, number(rel, "confidence", 1.0D), source)) relations++;
                    }
                }
            }
        }

        relations += applyRelations(memory, array(root, "relations"), source);
        relations += applyRelations(memory, array(root, "facts"), source);

        if (allowGroundings) {
            JsonArray groundingArray = array(root, "groundings");
            if (groundingArray != null) {
                for (JsonElement element : groundingArray) {
                    if (!element.isJsonObject()) continue;
                    JsonObject grounding = element.getAsJsonObject();
                    String concept = string(grounding, "concept", "");
                    String referent = string(grounding, "referent", "");
                    double weight = number(grounding, "weight", number(grounding, "confidence", 1.0D));
                    if (memory.groundAtLeast(referent, concept, weight)) groundings++;
                }
            }
        }

        JsonObject taught = object(root, "taught_replies");
        if (taught != null) {
            for (Map.Entry<String, JsonElement> entry : taught.entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) continue;
                memory.teach(normalizePhrase(entry.getKey()), entry.getValue().getAsString());
                replies++;
            }
        }

        return new ApplyCounts(concepts, definitions, aliases, relations, groundings, replies);
    }

    private static int applyRelations(DAI_LearningMemory memory, JsonArray array, String source) {
        if (array == null) return 0;
        int count = 0;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject rel = element.getAsJsonObject();
            String subject = firstString(rel, "subject", "from");
            String predicate = firstString(rel, "predicate", "relation", "type");
            String object = firstString(rel, "object", "target", "to", "value");
            if (memory.learnRelation(subject, predicate, object, number(rel, "confidence", 1.0D), source)) count++;
        }
        return count;
    }

    private static Counts count(JsonObject root) {
        int definitions = size(object(root, "definitions"));
        int aliases = size(object(root, "aliases"));
        int concepts = size(array(root, "concepts"));
        int relations = size(array(root, "relations")) + size(array(root, "facts"));
        JsonArray conceptArray = array(root, "concepts");
        if (conceptArray != null) {
            for (JsonElement element : conceptArray) {
                if (!element.isJsonObject()) continue;
                JsonObject concept = element.getAsJsonObject();
                if (concept.has("definition")) definitions++;
                aliases += size(array(concept, "aliases"));
                relations += size(array(concept, "relations"));
            }
        }
        int groundings = size(array(root, "groundings"));
        int replies = size(object(root, "taught_replies"));
        return new Counts(concepts, definitions, aliases, relations, groundings, replies);
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement element = root == null ? null : root.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject root, String key) {
        JsonElement element = root == null ? null : root.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static int size(JsonObject object) { return object == null ? 0 : object.size(); }
    private static int size(JsonArray array) { return array == null ? 0 : array.size(); }

    private static String firstString(JsonObject root, String... keys) {
        for (String key : keys) {
            String value = string(root, key, "");
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static String string(JsonObject root, String key, String fallback) {
        try {
            return root != null && root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsString() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double number(JsonObject root, String key, double fallback) {
        try {
            return root != null && root.has(key) ? root.get(key).getAsDouble() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String normalizePhrase(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    public record ImportResult(int files, int concepts, int definitions, int aliases, int relations, int extras, String message) {}
    private record Counts(int concepts, int definitions, int aliases, int relations, int groundings, int replies) {}
    private record ApplyCounts(int concepts, int definitions, int aliases, int relations, int groundings, int replies) {}
}
