package io.github.j12h36h.dai.client.learning;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Persistent, general-purpose concept graph used by Sapphire.
 *
 * This graph is deliberately game-agnostic. Minecraft world groundings live in
 * DAI_LearningMemory and point into these concepts. That lets the same concept
 * participate in ordinary facts (tree IS_A plant) and executable world
 * knowledge (block:minecraft:oak_log -> tree).
 */
public final class DAI_KnowledgeGraph {
    private final Map<String, ConceptNode> concepts = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final Map<String, KnowledgeRelation> relations = new LinkedHashMap<>();

    public boolean define(String concept, String definition, double confidence, String source) {
        String id = normalizeConcept(concept);
        String text = cleanText(definition);
        if (id.isBlank() || text.isBlank()) return false;
        ConceptNode node = concepts.computeIfAbsent(id, ignored -> new ConceptNode(id, cleanLabel(concept)));
        double safeConfidence = clampConfidence(confidence);
        if (node.definition == null || safeConfidence >= node.definitionConfidence) {
            boolean changed = !text.equals(node.definition)
                    || Double.compare(safeConfidence, node.definitionConfidence) != 0
                    || !cleanSource(source).equals(node.definitionSource);
            node.definition = text;
            node.definitionConfidence = safeConfidence;
            node.definitionSource = cleanSource(source);
            return changed;
        }
        return false;
    }

    public boolean ensureConcept(String concept) {
        String id = normalizeConcept(concept);
        if (id.isBlank()) return false;
        if (concepts.containsKey(id)) return false;
        concepts.put(id, new ConceptNode(id, cleanLabel(concept)));
        return true;
    }

    public boolean alias(String alias, String canonical) {
        String aliasId = normalizeConcept(alias);
        String canonicalId = canonical(canonical);
        if (canonicalId.isBlank()) canonicalId = normalizeConcept(canonical);
        if (aliasId.isBlank() || canonicalId.isBlank() || aliasId.equals(canonicalId)) return false;
        ensureConcept(canonicalId);
        String previous = aliases.put(aliasId, canonicalId);
        return !canonicalId.equals(previous);
    }

    public boolean relate(String subject, String predicate, String object, double confidence, String source) {
        String s = canonicalOrCreate(subject);
        String p = normalizePredicate(predicate);
        String o = canonicalOrCreate(object);
        if (s.isBlank() || p.isBlank() || o.isBlank()) return false;

        KnowledgeRelation incoming = new KnowledgeRelation(
                s, p, o, clampConfidence(confidence), cleanSource(source));
        String key = relationKey(s, p, o);
        KnowledgeRelation previous = relations.get(key);
        if (previous == null || incoming.confidence() >= previous.confidence()) {
            relations.put(key, incoming);
            return previous == null || !previous.equals(incoming);
        }
        return false;
    }

    public String canonical(String concept) {
        String id = normalizeConcept(concept);
        if (id.isBlank()) return "";
        Set<String> seen = new HashSet<>();
        String current = id;
        while (seen.add(current)) {
            String next = aliases.get(current);
            if (next == null || next.isBlank() || next.equals(current)) break;
            current = next;
        }
        return current;
    }

    public boolean knows(String concept) {
        String id = canonical(concept);
        return !id.isBlank() && concepts.containsKey(id);
    }

    public String definition(String concept) {
        ConceptNode node = concepts.get(canonical(concept));
        return node == null ? null : node.definition;
    }

    public String label(String concept) {
        String canonical = canonical(concept);
        ConceptNode node = concepts.get(canonical);
        return node == null || node.label == null || node.label.isBlank() ? canonical : node.label;
    }

    public List<KnowledgeRelation> relationsFrom(String concept) {
        String id = canonical(concept);
        if (id.isBlank()) return List.of();
        List<KnowledgeRelation> output = new ArrayList<>();
        for (KnowledgeRelation relation : relations.values()) {
            if (relation.subject().equals(id)) output.add(relation);
        }
        output.sort(Comparator.comparing(KnowledgeRelation::predicate).thenComparing(KnowledgeRelation::object));
        return List.copyOf(output);
    }

    public List<String> objects(String subject, String predicate) {
        String s = canonical(subject);
        String p = normalizePredicate(predicate);
        if (s.isBlank() || p.isBlank()) return List.of();
        LinkedHashSet<String> output = new LinkedHashSet<>();
        for (KnowledgeRelation relation : relations.values()) {
            if (relation.subject().equals(s) && relation.predicate().equals(p)) output.add(relation.object());
        }
        return List.copyOf(output);
    }

    public boolean relationKnown(String subject, String predicate, String object, int maxDepth) {
        String p = normalizePredicate(predicate);
        if ("is_a".equals(p) || "instance_of".equals(p)) {
            return isA(subject, object, maxDepth);
        }
        String s = canonical(subject);
        String o = canonical(object);
        return relations.containsKey(relationKey(s, p, o));
    }

    /** Transitive IS_A / INSTANCE_OF reasoning. */
    public boolean isA(String subject, String target, int maxDepth) {
        String start = canonical(subject);
        String wanted = canonical(target);
        if (start.isBlank() || wanted.isBlank()) return false;
        if (start.equals(wanted)) return true;

        int depthLimit = Math.max(1, maxDepth);
        ArrayDeque<Step> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(new Step(start, 0));
        visited.add(start);

        while (!queue.isEmpty()) {
            Step step = queue.removeFirst();
            if (step.depth() >= depthLimit) continue;
            for (KnowledgeRelation relation : relations.values()) {
                if (!relation.subject().equals(step.id())) continue;
                if (!relation.predicate().equals("is_a") && !relation.predicate().equals("instance_of")) continue;
                String next = canonical(relation.object());
                if (next.equals(wanted)) return true;
                if (visited.add(next)) queue.addLast(new Step(next, step.depth() + 1));
            }
        }
        return false;
    }

    /**
     * Returns the queried concept plus concepts that are known subclasses /
     * instances of it. Used to turn abstract knowledge into executable target
     * lookup (e.g. oak_tree IS_A tree and oak_tree is grounded to oak_log).
     */
    public Set<String> descendantsIncluding(String concept, int maxDepth) {
        String root = canonical(concept);
        if (root.isBlank()) return Set.of();
        LinkedHashSet<String> output = new LinkedHashSet<>();
        output.add(root);
        int depthLimit = Math.max(1, maxDepth);

        ArrayDeque<Step> queue = new ArrayDeque<>();
        queue.add(new Step(root, 0));
        while (!queue.isEmpty()) {
            Step step = queue.removeFirst();
            if (step.depth() >= depthLimit) continue;
            for (KnowledgeRelation relation : relations.values()) {
                if (!relation.predicate().equals("is_a") && !relation.predicate().equals("instance_of")) continue;
                String parent = canonical(relation.object());
                if (!parent.equals(step.id())) continue;
                String child = canonical(relation.subject());
                if (output.add(child)) queue.addLast(new Step(child, step.depth() + 1));
            }
        }
        return Set.copyOf(output);
    }

    /** Direct and inferred ancestors for compact answers. */
    public List<String> ancestors(String concept, int maxDepth) {
        String start = canonical(concept);
        if (start.isBlank()) return List.of();
        LinkedHashSet<String> output = new LinkedHashSet<>();
        ArrayDeque<Step> queue = new ArrayDeque<>();
        queue.add(new Step(start, 0));
        int depthLimit = Math.max(1, maxDepth);
        while (!queue.isEmpty()) {
            Step step = queue.removeFirst();
            if (step.depth() >= depthLimit) continue;
            for (KnowledgeRelation relation : relations.values()) {
                if (!relation.subject().equals(step.id())) continue;
                if (!relation.predicate().equals("is_a") && !relation.predicate().equals("instance_of")) continue;
                String parent = canonical(relation.object());
                if (output.add(parent)) queue.addLast(new Step(parent, step.depth() + 1));
            }
        }
        return List.copyOf(output);
    }

    /**
     * Removes a narrow class of malformed facts produced by older chat parsing,
     * e.g. "how are you" accidentally becoming HOW IS_A YOU. This is deliberately
     * conservative so legitimate definitions of question words remain intact.
     */
    public int sanitizeMalformedQuestionRelations() {
        Set<String> questionWords = Set.of("what", "who", "where", "when", "why", "how", "which");
        Set<String> pronouns = Set.of("i", "me", "you", "he", "him", "she", "her", "it", "we", "us", "they", "them");
        int before = relations.size();
        relations.entrySet().removeIf(entry -> {
            KnowledgeRelation relation = entry.getValue();
            if (!questionWords.contains(relation.subject()) || !pronouns.contains(relation.object())) return false;
            return relation.predicate().equals("is") || relation.predicate().equals("is_a") || relation.predicate().equals("instance_of");
        });
        return before - relations.size();
    }

    public int conceptCount() { return concepts.size(); }
    public int relationCount() { return relations.size(); }
    public int aliasCount() { return aliases.size(); }

    public JsonObject write() {
        JsonObject root = new JsonObject();

        JsonObject conceptJson = new JsonObject();
        for (ConceptNode node : concepts.values()) {
            JsonObject value = new JsonObject();
            value.addProperty("label", node.label);
            if (node.definition != null && !node.definition.isBlank()) {
                value.addProperty("definition", node.definition);
                value.addProperty("definition_confidence", node.definitionConfidence);
                value.addProperty("definition_source", node.definitionSource);
            }
            conceptJson.add(node.id, value);
        }
        root.add("concepts", conceptJson);

        JsonObject aliasJson = new JsonObject();
        aliases.forEach(aliasJson::addProperty);
        root.add("aliases", aliasJson);

        JsonArray relationJson = new JsonArray();
        for (KnowledgeRelation relation : relations.values()) {
            JsonObject value = new JsonObject();
            value.addProperty("subject", relation.subject());
            value.addProperty("predicate", relation.predicate());
            value.addProperty("object", relation.object());
            value.addProperty("confidence", relation.confidence());
            value.addProperty("source", relation.source());
            relationJson.add(value);
        }
        root.add("relations", relationJson);
        return root;
    }

    public void read(JsonObject root) {
        if (root == null) return;

        JsonObject conceptJson = asObject(root.get("concepts"));
        if (conceptJson != null) {
            for (Map.Entry<String, JsonElement> entry : conceptJson.entrySet()) {
                String id = normalizeConcept(entry.getKey());
                if (id.isBlank()) continue;
                ConceptNode node = concepts.computeIfAbsent(id, ignored -> new ConceptNode(id, entry.getKey()));
                JsonObject value = asObject(entry.getValue());
                if (value == null) continue;
                node.label = getString(value, "label", entry.getKey());
                String definition = getString(value, "definition", "");
                if (!definition.isBlank()) {
                    node.definition = definition;
                    node.definitionConfidence = getDouble(value, "definition_confidence", 1.0D);
                    node.definitionSource = getString(value, "definition_source", "memory");
                }
            }
        }

        JsonObject aliasJson = asObject(root.get("aliases"));
        if (aliasJson != null) {
            for (Map.Entry<String, JsonElement> entry : aliasJson.entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) continue;
                String alias = normalizeConcept(entry.getKey());
                String canonical = normalizeConcept(entry.getValue().getAsString());
                if (!alias.isBlank() && !canonical.isBlank()) aliases.put(alias, canonical);
            }
        }

        JsonArray relationJson = asArray(root.get("relations"));
        if (relationJson != null) {
            for (JsonElement element : relationJson) {
                JsonObject value = asObject(element);
                if (value == null) continue;
                String subject = getString(value, "subject", "");
                String predicate = getString(value, "predicate", "");
                String object = getString(value, "object", "");
                double confidence = getDouble(value, "confidence", 1.0D);
                String source = getString(value, "source", "memory");
                relate(subject, predicate, object, confidence, source);
            }
        }
    }

    public static String normalizeConcept(String value) {
        if (value == null) return "";
        String output = value.toLowerCase(Locale.ROOT)
                .replaceAll("[?!.,;:]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        return output;
    }

    public static String normalizePredicate(String value) {
        if (value == null) return "";
        String p = value.toLowerCase(Locale.ROOT).trim()
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("[^a-z0-9_]+", "");
        return switch (p) {
            case "isa", "is_an", "is_a", "are_a", "are_an", "type_of", "kind_of" -> "is_a";
            case "instanceof", "instance_of" -> "instance_of";
            case "partof", "part_of", "belongs_to" -> "part_of";
            case "usedfor", "used_for" -> "used_for";
            case "orbits" -> "orbit";
            case "contains" -> "contain";
            case "uses" -> "use";
            case "needs" -> "need";
            case "causes" -> "cause";
            case "produces" -> "produce";
            case "creates" -> "create";
            case "eats" -> "eat";
            case "likes" -> "like";
            case "dislikes" -> "dislike";
            case "supports" -> "support";
            case "opposes" -> "oppose";
            case "follows" -> "follow";
            case "precedes" -> "precede";
            case "owns" -> "own";
            default -> p;
        };
    }

    private String canonicalOrCreate(String raw) {
        String id = canonical(raw);
        if (id.isBlank()) id = normalizeConcept(raw);
        if (!id.isBlank() && !concepts.containsKey(id)) {
            concepts.put(id, new ConceptNode(id, cleanLabel(raw)));
        }
        return id;
    }

    private static String relationKey(String subject, String predicate, String object) {
        return subject + '\u0001' + predicate + '\u0001' + object;
    }

    private static String cleanLabel(String value) {
        if (value == null || value.isBlank()) return "";
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String cleanText(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String cleanSource(String value) {
        return value == null || value.isBlank() ? "player" : value.trim();
    }

    private static double clampConfidence(double value) {
        if (!Double.isFinite(value)) return 1.0D;
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray asArray(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String getString(JsonObject root, String key, String fallback) {
        try {
            return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsString() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double getDouble(JsonObject root, String key, double fallback) {
        try {
            return root.has(key) ? root.get(key).getAsDouble() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private record Step(String id, int depth) {}

    public record KnowledgeRelation(String subject, String predicate, String object, double confidence, String source) {}

    private static final class ConceptNode {
        private final String id;
        private String label;
        private String definition;
        private double definitionConfidence;
        private String definitionSource = "";

        private ConceptNode(String id, String label) {
            this.id = id;
            this.label = label == null || label.isBlank() ? id : label;
        }
    }
}
