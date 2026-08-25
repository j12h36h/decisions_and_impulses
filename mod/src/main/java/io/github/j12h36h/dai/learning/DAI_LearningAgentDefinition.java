package io.github.j12h36h.dai.learning;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.j12h36h.dai.logics.condition.DAI_ConditionDefinition;

import java.util.List;
import java.util.Locale;

/**
 * Datapack-defined persistent learning agent.
 *
 * Definitions describe what the agent may observe/do. Learned values are
 * deliberately stored outside the datapack by the client learning runtime.
 */
public record DAI_LearningAgentDefinition(
        String name,
        boolean enabled,
        boolean autonomyDefault,
        int decisionInterval,
        double learningRate,
        double discount,
        double exploration,
        double successReward,
        double failureReward,
        List<Observation> observations,
        List<Choice> choices,
        List<Reward> rewards,
        Imitation imitation,
        Dialogue dialogue,
        Grounding grounding,
        Knowledge knowledge
) {
    public static final Codec<DAI_LearningAgentDefinition> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("name", "DAI Companion")
                            .forGetter(DAI_LearningAgentDefinition::name),
                    Codec.BOOL.optionalFieldOf("enabled", true)
                            .forGetter(DAI_LearningAgentDefinition::enabled),
                    Codec.BOOL.optionalFieldOf("autonomy_default", false)
                            .forGetter(DAI_LearningAgentDefinition::autonomyDefault),
                    Codec.INT.optionalFieldOf("decision_interval", 10)
                            .forGetter(DAI_LearningAgentDefinition::decisionInterval),
                    Codec.DOUBLE.optionalFieldOf("learning_rate", 0.15D)
                            .forGetter(DAI_LearningAgentDefinition::learningRate),
                    Codec.DOUBLE.optionalFieldOf("discount", 0.90D)
                            .forGetter(DAI_LearningAgentDefinition::discount),
                    Codec.DOUBLE.optionalFieldOf("exploration", 0.10D)
                            .forGetter(DAI_LearningAgentDefinition::exploration),
                    Codec.DOUBLE.optionalFieldOf("success_reward", 0.25D)
                            .forGetter(DAI_LearningAgentDefinition::successReward),
                    Codec.DOUBLE.optionalFieldOf("failure_reward", -0.50D)
                            .forGetter(DAI_LearningAgentDefinition::failureReward),
                    Observation.CODEC.listOf().optionalFieldOf("observations", List.of())
                            .forGetter(DAI_LearningAgentDefinition::observations),
                    Choice.CODEC.listOf().optionalFieldOf("choices", List.of())
                            .forGetter(DAI_LearningAgentDefinition::choices),
                    Reward.CODEC.listOf().optionalFieldOf("rewards", List.of())
                            .forGetter(DAI_LearningAgentDefinition::rewards),
                    Imitation.CODEC.optionalFieldOf("imitation", Imitation.DEFAULT)
                            .forGetter(DAI_LearningAgentDefinition::imitation),
                    Dialogue.CODEC.optionalFieldOf("dialogue", Dialogue.DEFAULT)
                            .forGetter(DAI_LearningAgentDefinition::dialogue),
                    Grounding.CODEC.optionalFieldOf("grounding", Grounding.DEFAULT)
                            .forGetter(DAI_LearningAgentDefinition::grounding),
                    Knowledge.CODEC.optionalFieldOf("knowledge", Knowledge.DEFAULT)
                            .forGetter(DAI_LearningAgentDefinition::knowledge)
            ).apply(instance, DAI_LearningAgentDefinition::new));

    public DAI_LearningAgentDefinition {
        name = clean(name, "DAI Companion");
        decisionInterval = Math.max(1, decisionInterval);
        learningRate = clamp(learningRate, 0.0D, 1.0D);
        discount = clamp(discount, 0.0D, 1.0D);
        exploration = clamp(exploration, 0.0D, 1.0D);
        observations = observations == null ? List.of() : List.copyOf(observations);
        choices = choices == null ? List.of() : List.copyOf(choices);
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
        imitation = imitation == null ? Imitation.DEFAULT : imitation;
        dialogue = dialogue == null ? Dialogue.DEFAULT : dialogue;
        grounding = grounding == null ? Grounding.DEFAULT : grounding;
        knowledge = knowledge == null ? Knowledge.DEFAULT : knowledge;
    }

    public record Observation(String id, DAI_ConditionDefinition condition) {
        public static final Codec<Observation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Observation::id),
                DAI_ConditionDefinition.CODEC.fieldOf("condition").forGetter(Observation::condition)
        ).apply(instance, Observation::new));

        public Observation {
            id = normalizeId(id, "observation");
            if (condition == null) throw new IllegalArgumentException("Learning observation condition cannot be null.");
        }
    }

    public record Choice(String id, String action, List<String> demonstrations) {
        public static final Codec<Choice> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Choice::id),
                Codec.STRING.fieldOf("action").forGetter(Choice::action),
                Codec.STRING.listOf().optionalFieldOf("demonstrations", List.of())
                        .forGetter(Choice::demonstrations)
        ).apply(instance, Choice::new));

        public Choice {
            id = normalizeId(id, "choice");
            action = clean(action, "");
            demonstrations = demonstrations == null ? List.of() : demonstrations.stream()
                    .filter(v -> v != null && !v.isBlank())
                    .map(v -> v.trim().toLowerCase(Locale.ROOT))
                    .toList();
            if (action.isBlank()) throw new IllegalArgumentException("Learning choice action cannot be empty.");
        }
    }

    /** A reward fires on the false -> true edge of its condition. */
    public record Reward(String id, DAI_ConditionDefinition condition, double value) {
        public static final Codec<Reward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Reward::id),
                DAI_ConditionDefinition.CODEC.fieldOf("condition").forGetter(Reward::condition),
                Codec.DOUBLE.fieldOf("value").forGetter(Reward::value)
        ).apply(instance, Reward::new));

        public Reward {
            id = normalizeId(id, "reward");
            if (condition == null) throw new IllegalArgumentException("Learning reward condition cannot be null.");
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Learning reward must be finite.");
        }
    }

    public record Imitation(boolean enabled, double weight) {
        public static final Imitation DEFAULT = new Imitation(true, 0.50D);
        public static final Codec<Imitation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Imitation::enabled),
                Codec.DOUBLE.optionalFieldOf("weight", 0.50D).forGetter(Imitation::weight)
        ).apply(instance, Imitation::new));

        public Imitation {
            if (!Double.isFinite(weight)) weight = 0.50D;
        }
    }

    public record Dialogue(
            boolean enabled,
            String greeting,
            double learningRate,
            double exploration,
            double continuationReward,
            List<Response> responses
    ) {
        public static final Dialogue DEFAULT = new Dialogue(false, "Hello.", 0.20D, 0.08D, 0.05D, List.of());
        public static final Codec<Dialogue> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enabled", false).forGetter(Dialogue::enabled),
                Codec.STRING.optionalFieldOf("greeting", "Hello.").forGetter(Dialogue::greeting),
                Codec.DOUBLE.optionalFieldOf("learning_rate", 0.20D).forGetter(Dialogue::learningRate),
                Codec.DOUBLE.optionalFieldOf("exploration", 0.08D).forGetter(Dialogue::exploration),
                Codec.DOUBLE.optionalFieldOf("continuation_reward", 0.05D).forGetter(Dialogue::continuationReward),
                Response.CODEC.listOf().optionalFieldOf("responses", List.of()).forGetter(Dialogue::responses)
        ).apply(instance, Dialogue::new));

        public Dialogue {
            greeting = clean(greeting, "Hello.");
            learningRate = clamp(learningRate, 0.0D, 1.0D);
            exploration = clamp(exploration, 0.0D, 1.0D);
            responses = responses == null ? List.of() : List.copyOf(responses);
        }
    }

    /**
     * Connects ordinary companion-chat language to the object hit by the
     * player crosshair/raytrace. This is deliberately separate from exact
     * /teach replies: it is persistent world-grounded concept learning.
     */
    public record Grounding(boolean enabled, double weight, double confidence) {
        public static final Grounding DEFAULT = new Grounding(true, 1.0D, 0.75D);
        public static final Codec<Grounding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Grounding::enabled),
                Codec.DOUBLE.optionalFieldOf("weight", 1.0D).forGetter(Grounding::weight),
                Codec.DOUBLE.optionalFieldOf("confidence", 0.75D).forGetter(Grounding::confidence)
        ).apply(instance, Grounding::new));

        public Grounding {
            if (!Double.isFinite(weight)) weight = 1.0D;
            weight = Math.max(0.0D, weight);
            confidence = clamp(confidence, 0.0D, Double.MAX_VALUE);
        }
    }


    /** General concept/relationship learning and safe JSON knowledge hotpatching. */
    public record Knowledge(
            boolean enabled,
            boolean learnFromChat,
            boolean inferenceEnabled,
            int maxInferenceDepth,
            String importFolder,
            boolean allowGroundingImports
    ) {
        public static final Knowledge DEFAULT = new Knowledge(
                true, true, true, 6, "dai_learning/knowledge_inbox", true);

        public static final Codec<Knowledge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Knowledge::enabled),
                Codec.BOOL.optionalFieldOf("learn_from_chat", true).forGetter(Knowledge::learnFromChat),
                Codec.BOOL.optionalFieldOf("inference_enabled", true).forGetter(Knowledge::inferenceEnabled),
                Codec.INT.optionalFieldOf("max_inference_depth", 6).forGetter(Knowledge::maxInferenceDepth),
                Codec.STRING.optionalFieldOf("import_folder", "dai_learning/knowledge_inbox").forGetter(Knowledge::importFolder),
                Codec.BOOL.optionalFieldOf("allow_grounding_imports", true).forGetter(Knowledge::allowGroundingImports)
        ).apply(instance, Knowledge::new));

        public Knowledge {
            maxInferenceDepth = Math.max(1, Math.min(32, maxInferenceDepth));
            importFolder = clean(importFolder, "dai_learning/knowledge_inbox")
                    .replace('\\', '/');
        }
    }

    public record Response(String id, String text, List<String> keywords, double seedWeight) {
        public static final Codec<Response> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Response::id),
                Codec.STRING.fieldOf("text").forGetter(Response::text),
                Codec.STRING.listOf().optionalFieldOf("keywords", List.of()).forGetter(Response::keywords),
                Codec.DOUBLE.optionalFieldOf("seed_weight", 1.0D).forGetter(Response::seedWeight)
        ).apply(instance, Response::new));

        public Response {
            id = normalizeId(id, "response");
            text = clean(text, "...");
            keywords = keywords == null ? List.of() : keywords.stream()
                    .filter(v -> v != null && !v.isBlank())
                    .map(v -> v.trim().toLowerCase(Locale.ROOT))
                    .toList();
            if (!Double.isFinite(seedWeight)) seedWeight = 1.0D;
        }
    }

    private static String normalizeId(String value, String fallback) {
        return clean(value, fallback).toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim();
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
