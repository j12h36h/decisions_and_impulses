package io.github.j12h36h.dai.client.learning;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persistent learned values for one agent id. */
public final class DAI_LearningMemory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Identifier id;
    private final Map<String, Map<String, Double>> q = new HashMap<>();
    private final Map<String, Map<String, Double>> dialogue = new HashMap<>();
    private final Map<String, Map<String, Double>> grounding = new HashMap<>();
    private final DAI_KnowledgeGraph knowledge = new DAI_KnowledgeGraph();
    private final Map<String, String> taughtReplies = new LinkedHashMap<>();
    private long demonstrations;
    private long autonomousChoices;
    private long conversations;
    private double lifetimeReward;
    private boolean dirty;

    private DAI_LearningMemory(Identifier id) {
        this.id = id;
    }

    public static DAI_LearningMemory load(Identifier id) {
        DAI_LearningMemory memory = new DAI_LearningMemory(id);
        Path path = memory.path();
        if (!Files.isRegularFile(path)) return memory;

        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            readDoubleTable(root.getAsJsonObject("q"), memory.q);
            readDoubleTable(root.getAsJsonObject("dialogue"), memory.dialogue);
            readDoubleTable(root.getAsJsonObject("grounding"), memory.grounding);
            memory.knowledge.read(root.getAsJsonObject("knowledge"));
            JsonObject taught = root.getAsJsonObject("taught_replies");
            if (taught != null) taught.entrySet().forEach(e -> {
                if (e.getValue().isJsonPrimitive()) memory.taughtReplies.put(e.getKey(), e.getValue().getAsString());
            });
            memory.demonstrations = getLong(root, "demonstrations");
            memory.autonomousChoices = getLong(root, "autonomous_choices");
            memory.conversations = getLong(root, "conversations");
            memory.lifetimeReward = getDouble(root, "lifetime_reward");
            DAI_Core.LOGGER.info("<DAI>: Loaded learning memory '{}' from '{}'.", id, path);
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not load learning memory '{}' from '{}'. Starting fresh.", id, path, exception);
        }
        return memory;
    }

    public double q(String state, String choice) {
        return q.getOrDefault(state, Map.of()).getOrDefault(choice, 0.0D);
    }

    public double maxQ(String state) {
        double max = 0.0D;
        boolean any = false;
        for (double value : q.getOrDefault(state, Map.of()).values()) {
            if (!any || value > max) max = value;
            any = true;
        }
        return any ? max : 0.0D;
    }

    public void reinforce(String state, String choice, double reward, String nextState,
                          double learningRate, double discount) {
        if (state == null || choice == null) return;
        double old = q(state, choice);
        double target = reward + discount * maxQ(nextState == null ? state : nextState);
        setQ(state, choice, old + learningRate * (target - old));
        lifetimeReward += reward;
        dirty = true;
    }

    public void imitate(String state, String choice, double amount) {
        if (state == null || choice == null || !Double.isFinite(amount)) return;
        setQ(state, choice, q(state, choice) + amount);
        demonstrations++;
        dirty = true;
    }

    public double dialogueWeight(String token, String responseId) {
        return dialogue.getOrDefault(token, Map.of()).getOrDefault(responseId, 0.0D);
    }

    public void reinforceDialogue(Iterable<String> tokens, String responseId, double reward, double rate) {
        if (responseId == null || tokens == null) return;
        for (String token : tokens) {
            if (token == null || token.isBlank()) continue;
            Map<String, Double> byResponse = dialogue.computeIfAbsent(token, ignored -> new HashMap<>());
            double old = byResponse.getOrDefault(responseId, 0.0D);
            byResponse.put(responseId, old + rate * reward);
        }
        lifetimeReward += reward;
        dirty = true;
    }

    public void ground(String referent, String concept, double amount) {
        if (referent == null || referent.isBlank() || concept == null || concept.isBlank()
                || !Double.isFinite(amount) || amount <= 0.0D) return;
        String canonical = knowledge.canonical(concept);
        if (canonical.isBlank()) canonical = DAI_KnowledgeGraph.normalizeConcept(concept);
        knowledge.ensureConcept(canonical);
        Map<String, Double> concepts = grounding.computeIfAbsent(referent, ignored -> new HashMap<>());
        concepts.put(canonical, concepts.getOrDefault(canonical, 0.0D) + amount);
        dirty = true;
    }

    /** Idempotent grounding merge used by JSON hotpatch imports. */
    public boolean groundAtLeast(String referent, String concept, double amount) {
        if (referent == null || referent.isBlank() || concept == null || concept.isBlank()
                || !Double.isFinite(amount) || amount <= 0.0D) return false;
        if (!referent.startsWith("block:") && !referent.startsWith("entity:")) return false;
        String canonical = knowledge.canonical(concept);
        if (canonical.isBlank()) canonical = DAI_KnowledgeGraph.normalizeConcept(concept);
        knowledge.ensureConcept(canonical);
        Map<String, Double> concepts = grounding.computeIfAbsent(referent, ignored -> new HashMap<>());
        double old = concepts.getOrDefault(canonical, 0.0D);
        if (old >= amount) return false;
        concepts.put(canonical, amount);
        dirty = true;
        return true;
    }

    public ConceptMatch bestConcept(String referent) {
        if (referent == null || referent.isBlank()) return null;
        Map<String, Double> concepts = grounding.get(referent);
        if (concepts == null || concepts.isEmpty()) return null;
        String bestConcept = null;
        double best = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, Double> entry : concepts.entrySet()) {
            double value = entry.getValue();
            if (bestConcept == null || value > best) {
                bestConcept = entry.getKey();
                best = value;
            }
        }
        return bestConcept == null ? null : new ConceptMatch(bestConcept, best);
    }

    public double groundingWeight(String referent, String concept) {
        return grounding.getOrDefault(referent, Map.of()).getOrDefault(concept, 0.0D);
    }

    /**
     * Returns world referents learned for a concept, strongest association first.
     * Referents use stable keys such as block:minecraft:oak_log or
     * entity:minecraft:zombie so the language model can hand knowledge back
     * to DAI's normal targeting/action systems.
     */
    public List<ReferentMatch> referentsForConcept(String concept) {
        if (concept == null || concept.isBlank()) return List.of();
        String canonical = canonicalConcept(concept);
        java.util.Set<String> accepted = knowledge.descendantsIncluding(canonical, 8);
        if (accepted.isEmpty()) accepted = java.util.Set.of(canonical);

        List<ReferentMatch> matches = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> entry : grounding.entrySet()) {
            double weight = 0.0D;
            for (Map.Entry<String, Double> learned : entry.getValue().entrySet()) {
                String learnedConcept = canonicalConcept(learned.getKey());
                if (accepted.contains(learnedConcept)) weight = Math.max(weight, learned.getValue());
            }
            if (weight > 0.0D) matches.add(new ReferentMatch(entry.getKey(), weight));
        }
        matches.sort(Comparator.comparingDouble(ReferentMatch::confidence).reversed());
        return List.copyOf(matches);
    }

    public boolean knowsConcept(String concept) {
        String canonical = canonicalConcept(concept);
        return knowledge.knows(canonical) || !referentsForConcept(canonical).isEmpty();
    }

    public String canonicalConcept(String concept) {
        String canonical = knowledge.canonical(concept);
        return canonical.isBlank() ? DAI_KnowledgeGraph.normalizeConcept(concept) : canonical;
    }

    public boolean ensureConcept(String concept) {
        boolean changed = knowledge.ensureConcept(concept);
        if (changed) dirty = true;
        return changed;
    }

    public boolean learnDefinition(String concept, String definition, double confidence, String source) {
        boolean changed = knowledge.define(concept, definition, confidence, source);
        if (changed) dirty = true;
        return changed;
    }

    public boolean learnAlias(String alias, String canonical) {
        boolean changed = knowledge.alias(alias, canonical);
        if (changed) dirty = true;
        return changed;
    }

    public boolean learnRelation(String subject, String predicate, String object, double confidence, String source) {
        boolean changed = knowledge.relate(subject, predicate, object, confidence, source);
        if (changed) dirty = true;
        return changed;
    }

    public boolean knowsRelation(String subject, String predicate, String object, int maxDepth) {
        return knowledge.relationKnown(subject, predicate, object, maxDepth);
    }

    public List<String> knowledgeObjects(String subject, String predicate) {
        return knowledge.objects(subject, predicate);
    }

    public List<DAI_KnowledgeGraph.KnowledgeRelation> knowledgeRelations(String concept) {
        return knowledge.relationsFrom(concept);
    }

    public List<String> knowledgeAncestors(String concept, int maxDepth) {
        return knowledge.ancestors(concept, maxDepth);
    }

    public String knowledgeDefinition(String concept) { return knowledge.definition(concept); }
    public String knowledgeLabel(String concept) { return knowledge.label(concept); }
    public int knowledgeConceptCount() { return knowledge.conceptCount(); }
    public int knowledgeRelationCount() { return knowledge.relationCount(); }
    public int knowledgeAliasCount() { return knowledge.aliasCount(); }

    public int sanitizeMalformedEnglishArtifacts() {
        int removed = knowledge.sanitizeMalformedQuestionRelations();
        if (removed > 0) dirty = true;
        return removed;
    }

    public record ConceptMatch(String concept, double confidence) {}
    public record ReferentMatch(String referent, double confidence) {}

    public String taughtReply(String normalizedInput) {
        return taughtReplies.get(normalizedInput);
    }

    public void teach(String normalizedInput, String reply) {
        if (normalizedInput == null || normalizedInput.isBlank() || reply == null || reply.isBlank()) return;
        taughtReplies.put(normalizedInput, reply.trim());
        dirty = true;
    }

    public void countAutonomousChoice() { autonomousChoices++; dirty = true; }
    public void countConversation() { conversations++; dirty = true; }

    public boolean dirty() { return dirty; }

    public String summary() {
        int states = q.size();
        int dialogueTokens = dialogue.size();
        int groundedReferents = grounding.size();
        int concepts = knowledge.conceptCount();
        int relations = knowledge.relationCount();
        return "states=" + states
                + ", demos=" + demonstrations
                + ", autonomous=" + autonomousChoices
                + ", chats=" + conversations
                + ", language_tokens=" + dialogueTokens
                + ", grounded=" + groundedReferents
                + ", concepts=" + concepts
                + ", relations=" + relations
                + ", taught=" + taughtReplies.size()
                + ", reward=" + String.format(java.util.Locale.ROOT, "%.2f", lifetimeReward);
    }

    public void save() {
        if (!dirty) return;
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("agent", id.toString());
            root.add("q", writeDoubleTable(q));
            root.add("dialogue", writeDoubleTable(dialogue));
            root.add("grounding", writeDoubleTable(grounding));
            root.add("knowledge", knowledge.write());
            JsonObject taught = new JsonObject();
            taughtReplies.forEach(taught::addProperty);
            root.add("taught_replies", taught);
            root.addProperty("demonstrations", demonstrations);
            root.addProperty("autonomous_choices", autonomousChoices);
            root.addProperty("conversations", conversations);
            root.addProperty("lifetime_reward", lifetimeReward);

            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not save learning memory '{}' to '{}'.", id, path, exception);
        }
    }

    private void setQ(String state, String choice, double value) {
        if (!Double.isFinite(value)) return;
        q.computeIfAbsent(state, ignored -> new HashMap<>()).put(choice, value);
    }

    private Path path() {
        String safePath = id.getPath().replace('/', '_').replace('\\', '_');
        return FMLPaths.GAMEDIR.get()
                .resolve("dai_learning")
                .resolve(id.getNamespace())
                .resolve(safePath + ".json")
                .toAbsolutePath().normalize();
    }

    private static JsonObject writeDoubleTable(Map<String, Map<String, Double>> table) {
        JsonObject root = new JsonObject();
        table.forEach((outer, values) -> {
            JsonObject child = new JsonObject();
            values.forEach((key, value) -> child.addProperty(key, value));
            root.add(outer, child);
        });
        return root;
    }

    private static void readDoubleTable(JsonObject root, Map<String, Map<String, Double>> destination) {
        if (root == null) return;
        for (Map.Entry<String, JsonElement> outer : root.entrySet()) {
            if (!outer.getValue().isJsonObject()) continue;
            Map<String, Double> child = destination.computeIfAbsent(outer.getKey(), ignored -> new HashMap<>());
            for (Map.Entry<String, JsonElement> inner : outer.getValue().getAsJsonObject().entrySet()) {
                try { child.put(inner.getKey(), inner.getValue().getAsDouble()); } catch (RuntimeException ignored) {}
            }
        }
    }

    private static long getLong(JsonObject root, String key) {
        try { return root.has(key) ? root.get(key).getAsLong() : 0L; } catch (RuntimeException ignored) { return 0L; }
    }

    private static double getDouble(JsonObject root, String key) {
        try { return root.has(key) ? root.get(key).getAsDouble() : 0.0D; } catch (RuntimeException ignored) { return 0.0D; }
    }
}
