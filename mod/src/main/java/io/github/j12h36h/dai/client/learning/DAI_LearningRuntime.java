package io.github.j12h36h.dai.client.learning;

import io.github.j12h36h.dai.client.logics.DAI_AutomationLogic;
import io.github.j12h36h.dai.client.logics.DAI_CombatLogic;
import io.github.j12h36h.dai.client.logics.DAI_LookLogic;
import io.github.j12h36h.dai.client.logics.DAI_TargetLogic;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionResolver;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.client.logics.condition.DAI_ConditionEvaluator;
import io.github.j12h36h.dai.client.logics.core.DAI_HumanTrace;
import io.github.j12h36h.dai.client.menus.system.DAI_TargetState;
import io.github.j12h36h.dai.learning.DAI_LearningAgentDefinition;
import io.github.j12h36h.dai.learning.DAI_LearningLibrary;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client-side learner shared by autonomous action selection, human imitation,
 * and the custom companion chat.
 */
public final class DAI_LearningRuntime {
    private static final Random RANDOM = new Random();
    private static final int MEMORY_FLUSH_TICKS = 100;
    private static final int MAX_HISTORY = 80;
    private static final double DIALOGUE_REPEAT_PENALTY = 0.85D;
    private static final double DIALOGUE_UNKNOWN_THRESHOLD = 0.35D;
    private static final int KNOWLEDGE_SEARCH_RADIUS = 32;
    private static final int KNOWLEDGE_APPROACH_TIMEOUT = 500;
    private static final double KNOWLEDGE_STOP_DISTANCE = 2.75D;

    private static final Pattern[] GROUNDING_PATTERNS = new Pattern[] {
            Pattern.compile("^(?:what|the thing) i(?: am|'m) looking at is (?:a |an |the )?(.+)$"),
            Pattern.compile("^there is (?:a |an |the )?(.+?) where i(?: am|'m) looking(?: .*)?$"),
            Pattern.compile("^(?:this|that) is (?:a |an |the )?(.+)$")
    };

    private static final Pattern KNOWLEDGE_READ_PATTERN = Pattern.compile("^(?:read|inspect|preview) from (.+)$");
    private static final Pattern KNOWLEDGE_LEARN_PATTERN = Pattern.compile("^(?:learn|import|load knowledge) from (.+)$");
    private static final Pattern KNOWLEDGE_DEFINITION_PATTERN = Pattern.compile("^(.+?) (?:means|is defined as) (.+)$");
    private static final Pattern KNOWLEDGE_IS_A_PATTERN = Pattern.compile("^(.+?) (?:is|are) (?:a |an )(.+)$");
    private static final Pattern KNOWLEDGE_PLURAL_IS_A_PATTERN = Pattern.compile("^(.+?) are (.+)$");
    private static final Pattern KNOWLEDGE_PART_OF_PATTERN = Pattern.compile("^(.+?) is part of (.+)$");
    private static final Pattern KNOWLEDGE_USED_FOR_PATTERN = Pattern.compile("^(.+?) is used for (.+)$");
    private static final Pattern KNOWLEDGE_HAS_PATTERN = Pattern.compile("^(.+?) (?:has|have) (.+)$");
    private static final Pattern KNOWLEDGE_CAN_PATTERN = Pattern.compile("^(.+?) can (.+)$");
    private static final Pattern KNOWLEDGE_IS_PATTERN = Pattern.compile("^(.+?) is (.+)$");
    private static final Pattern KNOWLEDGE_IS_QUERY_PATTERN = Pattern.compile("^is (.+?) (?:a |an )(.+)$");
    private static final Pattern KNOWLEDGE_WHAT_DOES_PATTERN = Pattern.compile("^what does (.+?) ([a-z]+)$");
    private static final Pattern KNOWLEDGE_GENERIC_RELATION_PATTERN = Pattern.compile(
            "^(.+?) (orbits|contains|uses|needs|causes|produces|creates|eats|likes|dislikes|supports|opposes|follows|precedes|owns) (.+)$");

    private static Identifier activeId;
    private static DAI_LearningAgentDefinition activeAgent;
    private static DAI_LearningMemory memory;
    private static boolean autonomy;
    private static boolean autonomyInitialized;
    private static int decisionTicks;
    private static int flushTicks;
    private static int pendingOutcomeTicks;
    private static String lastState;
    private static String lastChoice;
    private static DAI_HumanTrace.InputFrame lastHumanInput;
    private static final Map<String, Boolean> rewardEdges = new HashMap<>();

    private static String lastDialogueInput = "";
    private static Set<String> lastDialogueTokens = Set.of();
    private static String lastDialogueResponseId = "";
    private static String spriteMood = "listening";
    private static String lastKnowledgeVerb = "";
    private static String lastKnowledgeConcept = "";
    private static KnowledgeCommand pendingKnowledgeCommand;
    private static final List<String> HISTORY = new ArrayList<>();

    private DAI_LearningRuntime() {}

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;

        ensureAgent();
        if (activeAgent == null || memory == null) return;

        executePendingKnowledgeIfReady(minecraft);
        observeHuman(minecraft);
        evaluateRewardEdges();
        finishPendingOutcome();

        if (++flushTicks >= MEMORY_FLUSH_TICKS) {
            flushTicks = 0;
            memory.save();
        }

        if (!autonomy || minecraft.gui.screen() != null || DAI_AutomationLogic.isActive()) return;
        if (++decisionTicks < activeAgent.decisionInterval()) return;
        decisionTicks = 0;
        if (!DAI_ActionQueue.isEmpty() || pendingOutcomeTicks > 0) return;

        chooseAndRun();
    }

    public static void resetSession() {
        if (memory != null) memory.save();
        decisionTicks = 0;
        flushTicks = 0;
        pendingOutcomeTicks = 0;
        lastState = null;
        lastChoice = null;
        lastHumanInput = null;
        rewardEdges.clear();
        lastDialogueInput = "";
        lastDialogueTokens = Set.of();
        lastDialogueResponseId = "";
        spriteMood = "listening";
        lastKnowledgeVerb = "";
        lastKnowledgeConcept = "";
        pendingKnowledgeCommand = null;
        DAI_EnglishLanguage.reset();
        HISTORY.clear();
        autonomy = false;
        autonomyInitialized = false;
        activeId = null;
        activeAgent = null;
        memory = null;
    }

    public static boolean available() {
        ensureAgent();
        return activeAgent != null;
    }

    public static String agentName() {
        ensureAgent();
        return activeAgent == null ? "DAI Companion" : activeAgent.name();
    }

    public static boolean autonomy() { return autonomy; }

    public static int spriteFrame() {
        long tick = System.currentTimeMillis() / 260L;
        return switch (spriteMood) {
            case "happy" -> tick % 2L == 0L ? 1 : 8;
            case "curious" -> tick % 2L == 0L ? 2 : 11;
            case "thinking" -> tick % 2L == 0L ? 3 : 2;
            case "excited" -> tick % 2L == 0L ? 4 : 11;
            case "confused" -> tick % 2L == 0L ? 5 : 2;
            case "worried" -> tick % 2L == 0L ? 6 : 11;
            case "sad" -> tick % 2L == 0L ? 7 : 10;
            case "determined" -> tick % 2L == 0L ? 9 : 11;
            case "sleepy" -> 10;
            default -> tick % 2L == 0L ? 0 : 11;
        };
    }

    public static void setAutonomy(boolean enabled) {
        ensureAgent();
        autonomy = enabled;
        autonomyInitialized = true;
        spriteMood = enabled ? "determined" : "listening";
        addSystem("Autonomy " + (enabled ? "enabled" : "disabled") + ".");
    }

    public static List<String> history() { return List.copyOf(HISTORY); }

    public static String summary() {
        ensureAgent();
        if (activeAgent == null || memory == null) return "No enabled learning agent is loaded.";
        return activeId + " // autonomy=" + autonomy + " // " + memory.summary();
    }

    public static String sendDialogue(String rawMessage) {
        ensureAgent();
        if (activeAgent == null || memory == null) return "No enabled learning agent is loaded.";
        if (!activeAgent.dialogue().enabled()) return "Dialogue is disabled for this agent.";

        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isEmpty()) return "";

        if (message.startsWith("/")) return command(message);

        if (!lastDialogueResponseId.isBlank() && !lastDialogueInput.isBlank()) {
            reinforceLastDialogue(activeAgent.dialogue().continuationReward());
        }

        addHistory("You: " + message);
        String normalized = normalizePhrase(message);
        Set<String> dialogueTokens = dialogueTokens(message);

        String importResponse = handleKnowledgeFileLanguage(normalized);
        if (importResponse != null) {
            finishDirectChatResponse(message, dialogueTokens, importResponse);
            return importResponse;
        }

        String groundedResponse = handleGroundedLanguage(message, normalized);
        if (groundedResponse != null) {
            finishDirectChatResponse(message, dialogueTokens, groundedResponse);
            return groundedResponse;
        }

        if (activeAgent.knowledge().enabled()) {
            int englishDepth = activeAgent.knowledge().inferenceEnabled()
                    ? activeAgent.knowledge().maxInferenceDepth()
                    : 1;
            DAI_EnglishLanguage.Result english = DAI_EnglishLanguage.process(
                    message,
                    memory,
                    agentName(),
                    englishDepth,
                    activeAgent.knowledge().learnFromChat()
            );
            if (english != null) {
                spriteMood = english.mood();
                if (english.learned()) memory.save();
                finishDirectChatResponse(message, dialogueTokens, english.response());
                return english.response();
            }
        }

        String knowledgeResponse = handleGeneralKnowledgeLanguage(message, normalized);
        if (knowledgeResponse != null) {
            finishDirectChatResponse(message, dialogueTokens, knowledgeResponse);
            return knowledgeResponse;
        }

        String taught = memory.taughtReply(normalized);
        String response;

        if (taught != null) {
            response = taught;
            lastDialogueResponseId = "";
            spriteMood = "happy";
        } else {
            DAI_LearningAgentDefinition.Response selected = selectResponse(dialogueTokens);
            if (selected == null) {
                response = "I'm not sure what that means yet. Try rephrasing it, keep talking to me, or teach me directly.";
                lastDialogueResponseId = "";
                spriteMood = "confused";
            } else {
                response = selected.text();
                lastDialogueResponseId = selected.id();
                spriteMood = inferMood(message, dialogueTokens, selected);
            }
        }

        lastDialogueInput = message;
        lastDialogueTokens = Set.copyOf(dialogueTokens);
        memory.countConversation();
        addHistory(agentName() + ": " + response);
        return response;
    }

    public static void rewardDialogue(double reward) {
        ensureAgent();
        if (activeAgent == null || memory == null || lastDialogueResponseId.isBlank()) {
            addSystem("There is no learned response to score yet.");
            return;
        }
        reinforceLastDialogue(reward);
        addSystem(String.format(Locale.ROOT, "Dialogue feedback: %+.2f", reward));
        memory.save();
    }

    public static void rewardLastAction(double reward) {
        ensureAgent();
        if (activeAgent == null || memory == null || lastState == null || lastChoice == null) {
            addSystem("There is no autonomous action to score yet.");
            return;
        }
        String next = stateKey(activeAgent);
        memory.reinforce(lastState, lastChoice, reward, next, activeAgent.learningRate(), activeAgent.discount());
        addSystem(String.format(Locale.ROOT, "Action feedback: %+.2f (%s)", reward, lastChoice));
        memory.save();
    }

    public static String greeting() {
        ensureAgent();
        if (activeAgent == null || !activeAgent.dialogue().enabled()) return "No conversational learning agent is loaded.";
        spriteMood = "happy";
        if (HISTORY.isEmpty()) addHistory(agentName() + ": " + activeAgent.dialogue().greeting());
        return activeAgent.dialogue().greeting();
    }

    private static void ensureAgent() {
        Map.Entry<Identifier, DAI_LearningAgentDefinition> first = DAI_LearningLibrary.firstEnabled();
        if (first == null) {
            if (memory != null) memory.save();
            activeId = null;
            activeAgent = null;
            memory = null;
            return;
        }
        if (first.getKey().equals(activeId) && activeAgent == first.getValue() && memory != null) return;

        if (memory != null) memory.save();
        activeId = first.getKey();
        activeAgent = first.getValue();
        memory = DAI_LearningMemory.load(activeId);
        int sanitized = memory.sanitizeMalformedEnglishArtifacts();
        if (sanitized > 0) {
            DAI_Core.LOGGER.info("<DAI>: Removed {} malformed legacy English relation(s) from learning memory.", sanitized);
            memory.save();
        }
        if (activeAgent.knowledge().enabled()) {
            DAI_KnowledgeImport.inbox(activeAgent.knowledge().importFolder());
        }
        rewardEdges.clear();
        lastHumanInput = null;
        lastState = null;
        lastChoice = null;
        pendingOutcomeTicks = 0;
        if (!autonomyInitialized) autonomy = activeAgent.autonomyDefault();
        autonomyInitialized = true;
        DAI_Core.LOGGER.info("<DAI>: Learning agent '{}' is active (name='{}').", activeId, activeAgent.name());
    }

    private static void chooseAndRun() {
        String state = stateKey(activeAgent);
        List<DAI_LearningAgentDefinition.Choice> choices = activeAgent.choices();
        if (choices.isEmpty()) return;

        DAI_LearningAgentDefinition.Choice selected;
        if (RANDOM.nextDouble() < activeAgent.exploration()) {
            selected = choices.get(RANDOM.nextInt(choices.size()));
        } else {
            selected = choices.getFirst();
            double best = memory.q(state, selected.id());
            for (int i = 1; i < choices.size(); i++) {
                DAI_LearningAgentDefinition.Choice candidate = choices.get(i);
                double value = memory.q(state, candidate.id());
                if (value > best || (Double.compare(value, best) == 0 && RANDOM.nextBoolean())) {
                    selected = candidate;
                    best = value;
                }
            }
        }

        List<DAI_ActionDefinition> resolved = DAI_ActionResolver.resolve(selected.action());
        if (resolved.isEmpty()) return;

        lastState = state;
        lastChoice = selected.id();
        pendingOutcomeTicks = Math.max(5, activeAgent.decisionInterval());
        memory.countAutonomousChoice();
        DAI_ActionQueue.enqueueAll(resolved);
        DAI_Core.debug("<DAI>: Learning agent '{}' chose '{}' in state '{}'.", activeId, lastChoice, state);
    }

    private static void finishPendingOutcome() {
        if (pendingOutcomeTicks <= 0 || lastState == null || lastChoice == null) return;
        pendingOutcomeTicks--;
        if (pendingOutcomeTicks > 0 || !DAI_ActionQueue.isEmpty()) return;

        DAI_ActionResult result = DAI_ActionStatus.get();
        if (result == DAI_ActionResult.RUNNING) {
            pendingOutcomeTicks = 2;
            return;
        }

        double reward = result == DAI_ActionResult.SUCCESS
                ? activeAgent.successReward()
                : activeAgent.failureReward();
        String nextState = stateKey(activeAgent);
        memory.reinforce(lastState, lastChoice, reward, nextState,
                activeAgent.learningRate(), activeAgent.discount());
        pendingOutcomeTicks = 0;
    }

    private static void evaluateRewardEdges() {
        if (lastState == null || lastChoice == null) {
            for (DAI_LearningAgentDefinition.Reward reward : activeAgent.rewards()) {
                rewardEdges.put(reward.id(), DAI_ConditionEvaluator.evaluate(reward.condition()));
            }
            return;
        }

        for (DAI_LearningAgentDefinition.Reward reward : activeAgent.rewards()) {
            boolean current = DAI_ConditionEvaluator.evaluate(reward.condition());
            Boolean previous = rewardEdges.put(reward.id(), current);
            if (previous != null && !previous && current) {
                String nextState = stateKey(activeAgent);
                memory.reinforce(lastState, lastChoice, reward.value(), nextState,
                        activeAgent.learningRate(), activeAgent.discount());
            }
        }
    }

    private static void observeHuman(Minecraft minecraft) {
        DAI_HumanTrace.InputFrame current = DAI_HumanTrace.input(minecraft);
        if (lastHumanInput == null) {
            lastHumanInput = current;
            return;
        }

        if (activeAgent.imitation().enabled()
                && !autonomy
                && minecraft.gui.screen() == null
                && !DAI_AutomationLogic.isActive()) {
            String state = stateKey(activeAgent);
            for (DAI_LearningAgentDefinition.Choice choice : activeAgent.choices()) {
                for (String input : choice.demonstrations()) {
                    if (rising(input, current, lastHumanInput)) {
                        memory.imitate(state, choice.id(), activeAgent.imitation().weight());
                        DAI_Core.debug("<DAI>: Human demonstration '{}' -> '{}' in state '{}'.", input, choice.id(), state);
                        break;
                    }
                }
            }
        }
        lastHumanInput = current;
    }

    private static boolean rising(String input, DAI_HumanTrace.InputFrame current, DAI_HumanTrace.InputFrame previous) {
        return switch (input) {
            case "forward" -> current.forward() && !previous.forward();
            case "backward" -> current.backward() && !previous.backward();
            case "left" -> current.left() && !previous.left();
            case "right" -> current.right() && !previous.right();
            case "jump" -> current.jump() && !previous.jump();
            case "sneak" -> current.sneak() && !previous.sneak();
            case "sprint" -> current.sprint() && !previous.sprint();
            case "attack" -> current.attack() && !previous.attack();
            case "use" -> current.use() && !previous.use();
            case "pick" -> current.pick() && !previous.pick();
            case "drop" -> current.drop() && !previous.drop();
            case "swap" -> current.swap() && !previous.swap();
            case "inventory" -> current.inventory() && !previous.inventory();
            default -> false;
        };
    }

    private static String stateKey(DAI_LearningAgentDefinition agent) {
        if (agent.observations().isEmpty()) return "default";
        StringBuilder key = new StringBuilder();
        for (DAI_LearningAgentDefinition.Observation observation : agent.observations()) {
            if (!key.isEmpty()) key.append('|');
            key.append(observation.id()).append('=')
                    .append(DAI_ConditionEvaluator.evaluate(observation.condition()) ? '1' : '0');
        }
        return key.toString();
    }

    private static void finishDirectChatResponse(String message, Set<String> dialogueTokens, String response) {
        lastDialogueInput = message;
        lastDialogueTokens = Set.copyOf(dialogueTokens);
        lastDialogueResponseId = "";
        memory.countConversation();
        memory.save();
        addHistory(agentName() + ": " + response);
    }

    private static String handleKnowledgeFileLanguage(String normalized) {
        if (activeAgent == null || !activeAgent.knowledge().enabled()) return null;
        String phrase = stripSentencePunctuation(normalized);
        if (phrase.equals("where is the knowledge folder")
                || phrase.equals("where is your knowledge folder")
                || phrase.equals("knowledge folder")) {
            spriteMood = "thinking";
            return "My knowledge inbox is: " + DAI_KnowledgeImport.inbox(activeAgent.knowledge().importFolder());
        }

        Matcher read = KNOWLEDGE_READ_PATTERN.matcher(phrase);
        if (read.matches()) {
            String selection = cleanFileSelection(read.group(1));
            DAI_KnowledgeImport.ImportResult result = DAI_KnowledgeImport.inspect(
                    activeAgent.knowledge().importFolder(), selection);
            spriteMood = result.files() > 0 ? "thinking" : "confused";
            return result.message();
        }

        Matcher learn = KNOWLEDGE_LEARN_PATTERN.matcher(phrase);
        if (learn.matches()) {
            String selection = cleanFileSelection(learn.group(1));
            DAI_KnowledgeImport.ImportResult result = DAI_KnowledgeImport.learn(
                    memory,
                    activeAgent.knowledge().importFolder(),
                    selection,
                    activeAgent.knowledge().allowGroundingImports());
            spriteMood = result.files() > 0 ? "excited" : "confused";
            return result.message();
        }
        return null;
    }

    private static String handleGeneralKnowledgeLanguage(String message, String normalized) {
        if (activeAgent == null || !activeAgent.knowledge().enabled()) return null;
        String phrase = stripSentencePunctuation(normalized);
        int depth = activeAgent.knowledge().inferenceEnabled()
                ? activeAgent.knowledge().maxInferenceDepth()
                : 1;

        if (phrase.startsWith("what do you know about ")) {
            String concept = cleanConcept(phrase.substring("what do you know about ".length()));
            spriteMood = "thinking";
            return describeGeneralConcept(concept, depth, true);
        }
        if (phrase.startsWith("define ")) {
            String concept = cleanConcept(phrase.substring("define ".length()));
            spriteMood = "thinking";
            return describeGeneralConcept(concept, depth, true);
        }
        if (phrase.startsWith("what does ") && phrase.endsWith(" mean")) {
            String concept = cleanConcept(phrase.substring("what does ".length(), phrase.length() - " mean".length()));
            spriteMood = "thinking";
            return describeGeneralConcept(concept, depth, true);
        }
        if (phrase.startsWith("what is ")) {
            String concept = cleanConcept(phrase.substring("what is ".length()));
            if (memory.knowsConcept(concept)) {
                spriteMood = "thinking";
                return describeGeneralConcept(concept, depth, false);
            }
        }

        Matcher isQuery = KNOWLEDGE_IS_QUERY_PATTERN.matcher(phrase);
        if (isQuery.matches()) {
            String subject = cleanKnowledgeSubject(isQuery.group(1));
            String object = cleanConcept(isQuery.group(2));
            if (!memory.knowsConcept(subject) && !memory.knowsConcept(object)) return null;
            boolean known = memory.knowsRelation(subject, "is_a", object, depth);
            spriteMood = known ? "happy" : "thinking";
            return known
                    ? "Yes. Based on what I know, " + subject + " is a " + object + "."
                    : "I don't currently know that " + subject + " is a " + object + ".";
        }

        Matcher whatDoes = KNOWLEDGE_WHAT_DOES_PATTERN.matcher(phrase);
        if (whatDoes.matches()) {
            String subject = cleanKnowledgeSubject(whatDoes.group(1));
            String predicate = DAI_KnowledgeGraph.normalizePredicate(whatDoes.group(2));
            if (memory.knowsConcept(subject)) {
                List<String> objects = memory.knowledgeObjects(subject, predicate);
                if (!objects.isEmpty()) {
                    spriteMood = "happy";
                    return subject + " " + readablePredicate(predicate) + " " + joinNatural(objects) + ".";
                }
            }
        }

        if (!activeAgent.knowledge().learnFromChat()) return null;

        Matcher definition = KNOWLEDGE_DEFINITION_PATTERN.matcher(phrase);
        if (definition.matches()) {
            String subject = cleanKnowledgeSubject(definition.group(1));
            String meaning = cleanDefinitionText(definition.group(2));
            if (!subject.isBlank() && !meaning.isBlank()) {
                memory.learnDefinition(subject, meaning, 0.95D, "player_chat");
                spriteMood = "excited";
                return "I learned the definition of " + subject + ".";
            }
        }

        Matcher partOf = KNOWLEDGE_PART_OF_PATTERN.matcher(phrase);
        if (partOf.matches()) return learnChatRelation(partOf.group(1), "part_of", partOf.group(2));

        Matcher usedFor = KNOWLEDGE_USED_FOR_PATTERN.matcher(phrase);
        if (usedFor.matches()) return learnChatRelation(usedFor.group(1), "used_for", usedFor.group(2));

        Matcher isA = KNOWLEDGE_IS_A_PATTERN.matcher(phrase);
        if (isA.matches()) return learnChatRelation(isA.group(1), "is_a", isA.group(2));

        Matcher pluralIsA = KNOWLEDGE_PLURAL_IS_A_PATTERN.matcher(phrase);
        if (pluralIsA.matches() && !phrase.startsWith("you are ")) {
            return learnChatRelation(pluralIsA.group(1), "is_a", pluralIsA.group(2));
        }

        Matcher has = KNOWLEDGE_HAS_PATTERN.matcher(phrase);
        if (has.matches()) return learnChatRelation(has.group(1), "has", has.group(2));

        Matcher can = KNOWLEDGE_CAN_PATTERN.matcher(phrase);
        if (can.matches()) return learnChatRelation(can.group(1), "can", can.group(2));

        Matcher generic = KNOWLEDGE_GENERIC_RELATION_PATTERN.matcher(phrase);
        if (generic.matches()) {
            return learnChatRelation(generic.group(1), generic.group(2), generic.group(3));
        }

        if (phrase.startsWith("i am ") && phrase.length() > 5) {
            String name = cleanConcept(phrase.substring(5));
            memory.learnRelation("player", "has_name", name, 0.95D, "player_chat");
            spriteMood = "happy";
            return "I learned that the player speaking to me uses the name " + name + ".";
        }
        if (phrase.startsWith("you are ") && phrase.length() > 8) {
            String identity = cleanConcept(phrase.substring(8));
            memory.learnRelation("sapphire", "has_name", identity, 0.95D, "player_chat");
            spriteMood = "happy";
            return "I learned that you are referring to me as " + identity + ".";
        }

        if (!phrase.startsWith("what ") && !phrase.startsWith("who ")
                && !phrase.startsWith("where ") && !phrase.startsWith("why ")
                && !phrase.startsWith("how ")) {
            Matcher plainIs = KNOWLEDGE_IS_PATTERN.matcher(phrase);
            if (plainIs.matches()) {
                String subject = cleanKnowledgeSubject(plainIs.group(1));
                String object = cleanDefinitionText(plainIs.group(2));
                if (!subject.isBlank() && !object.isBlank()) {
                    if (object.split("\\s+").length >= 4) {
                        memory.learnDefinition(subject, object, 0.85D, "player_chat");
                        spriteMood = "excited";
                        return "I learned that as a definition of " + subject + ".";
                    }
                    return learnChatRelation(subject, "is", object);
                }
            }
        }
        return null;
    }

    private static String learnChatRelation(String rawSubject, String predicate, String rawObject) {
        String subject = cleanKnowledgeSubject(rawSubject);
        String object = cleanConcept(rawObject);
        if (subject.isBlank() || object.isBlank()) return null;
        String normalizedPredicate = DAI_KnowledgeGraph.normalizePredicate(predicate);
        memory.learnRelation(subject, normalizedPredicate, object, 0.90D, "player_chat");
        spriteMood = "excited";
        return "I learned: " + subject + " " + readablePredicate(normalizedPredicate) + " " + object + ".";
    }

    private static String describeGeneralConcept(String rawConcept, int depth, boolean explicitUnknown) {
        String concept = memory.canonicalConcept(rawConcept);
        if (!memory.knowsConcept(concept)) {
            spriteMood = "confused";
            return explicitUnknown ? "I don't know enough about " + rawConcept + " yet." : null;
        }

        List<String> parts = new ArrayList<>();
        String definition = memory.knowledgeDefinition(concept);
        if (definition != null && !definition.isBlank()) parts.add(memory.knowledgeLabel(concept) + " means " + definition);

        List<DAI_KnowledgeGraph.KnowledgeRelation> direct = memory.knowledgeRelations(concept);
        int added = 0;
        for (DAI_KnowledgeGraph.KnowledgeRelation relation : direct) {
            if (added >= 5) break;
            if (relation.predicate().equals("is_a") || relation.predicate().equals("instance_of")) {
                parts.add(memory.knowledgeLabel(concept) + " is a " + relation.object());
            } else {
                parts.add(memory.knowledgeLabel(concept) + " " + readablePredicate(relation.predicate()) + " " + relation.object());
            }
            added++;
        }

        if (activeAgent.knowledge().inferenceEnabled()) {
            List<String> ancestors = memory.knowledgeAncestors(concept, depth);
            for (String ancestor : ancestors) {
                boolean already = direct.stream().anyMatch(rel ->
                        (rel.predicate().equals("is_a") || rel.predicate().equals("instance_of"))
                                && rel.object().equals(ancestor));
                if (!already && parts.size() < 7) {
                    parts.add("I can infer that " + memory.knowledgeLabel(concept) + " is a " + ancestor);
                }
            }
        }

        if (parts.isEmpty()) return "I know the concept " + concept + ", but I don't have any facts attached to it yet.";
        return String.join(". ", parts) + ".";
    }

    private static String cleanKnowledgeSubject(String raw) {
        String subject = cleanConcept(raw);
        return switch (subject) {
            case "i", "me", "myself" -> "player";
            case "you", "yourself" -> "sapphire";
            default -> subject;
        };
    }

    private static String cleanDefinitionText(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("[?!.,]+$", "").replaceAll("\\s+", " ");
    }

    private static String readablePredicate(String predicate) {
        return switch (DAI_KnowledgeGraph.normalizePredicate(predicate)) {
            case "is_a", "instance_of" -> "is a";
            case "part_of" -> "is part of";
            case "used_for" -> "is used for";
            case "has" -> "has";
            case "can" -> "can";
            case "orbit" -> "orbits";
            case "contain" -> "contains";
            case "use" -> "uses";
            case "need" -> "needs";
            case "cause" -> "causes";
            case "produce" -> "produces";
            case "create" -> "creates";
            case "eat" -> "eats";
            case "like" -> "likes";
            case "dislike" -> "dislikes";
            case "support" -> "supports";
            case "oppose" -> "opposes";
            case "follow" -> "follows";
            case "precede" -> "precedes";
            case "own" -> "owns";
            case "has_name" -> "has the name";
            default -> predicate.replace('_', ' ');
        };
    }

    private static String joinNatural(List<String> values) {
        if (values == null || values.isEmpty()) return "nothing I know";
        if (values.size() == 1) return values.getFirst();
        if (values.size() == 2) return values.get(0) + " and " + values.get(1);
        return String.join(", ", values.subList(0, values.size() - 1)) + ", and " + values.getLast();
    }

    private static String stripSentencePunctuation(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).trim().replaceAll("[?!.,;:]+$", "").replaceAll("\\s+", " ");
    }

    private static String cleanFileSelection(String value) {
        if (value == null) return "here";
        String cleaned = value.trim();
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
                || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return cleaned.isBlank() ? "here" : cleaned;
    }

    private static String handleGroundedLanguage(String message, String normalized) {
        String phrase = normalized == null ? "" : normalized
                .toLowerCase(Locale.ROOT)
                .replaceAll("[?!.,]+$", "")
                .trim();

        if (activeAgent.grounding().enabled() && isReferentQuestion(phrase)) {
            WorldReferent referent = currentRayReferent();
            spriteMood = "curious";
            if (referent == null) {
                return "Your crosshair raytrace is not hitting a block or entity right now.";
            }

            DAI_LearningMemory.ConceptMatch match = memory.bestConcept(referent.key());
            if (match != null && match.confidence() >= activeAgent.grounding().confidence()) {
                spriteMood = "happy";
                return "You are looking at what you've taught me to call a " + match.concept() + ".";
            }

            return "Your crosshair raytrace is hitting " + referent.nativeId()
                    + ", but I don't know what you call it yet.";
        }

        String groundedConcept = activeAgent.grounding().enabled() ? extractGroundingConcept(phrase) : null;
        if (groundedConcept != null) {
            WorldReferent referent = currentRayReferent();
            if (referent == null) {
                spriteMood = "confused";
                return "I understand that you're naming what you're looking at, but your raytrace is not hitting a block or entity.";
            }

            memory.ground(referent.key(), groundedConcept, activeAgent.grounding().weight());
            spriteMood = "excited";
            return "I learned that " + referent.nativeId() + " can be called a " + groundedConcept
                    + ". I can now use that concept to find or act on matching targets.";
        }

        if (isRepeatKnowledgeRequest(phrase)) {
            if (lastKnowledgeVerb.isBlank() || lastKnowledgeConcept.isBlank()) {
                spriteMood = "confused";
                return "I don't have a previous learned-world action to repeat yet.";
            }
            pendingKnowledgeCommand = new KnowledgeCommand(lastKnowledgeVerb, lastKnowledgeConcept);
            spriteMood = "determined";
            return "Okay. I'll do the next " + lastKnowledgeConcept + " the same way when you close chat.";
        }

        KnowledgeCommand command = parseKnowledgeCommand(phrase);
        if (command != null) {
            String concept = resolveKnownConcept(command.concept());
            if (concept == null) {
                spriteMood = "confused";
                return "I don't know what \"" + command.concept()
                        + "\" refers to in the world yet. Point at one and tell me what it is first.";
            }

            pendingKnowledgeCommand = new KnowledgeCommand(command.verb(), concept);
            lastKnowledgeVerb = command.verb();
            lastKnowledgeConcept = concept;
            spriteMood = "determined";
            return "I know what you mean by " + concept + ". I'll " + describeVerb(command.verb())
                    + " the closest matching one when you close chat.";
        }

        return null;
    }

    private static boolean isReferentQuestion(String phrase) {
        return phrase.equals("what am i looking at")
                || phrase.equals("what'm i looking at")
                || phrase.equals("what is this")
                || phrase.equals("what's this")
                || phrase.equals("what is that")
                || phrase.equals("what's that")
                || phrase.equals("what is under my crosshair")
                || phrase.equals("what is my crosshair on");
    }

    private static String extractGroundingConcept(String phrase) {
        for (Pattern pattern : GROUNDING_PATTERNS) {
            Matcher matcher = pattern.matcher(phrase);
            if (!matcher.matches()) continue;
            String concept = cleanConcept(matcher.group(1));
            if (!concept.isBlank()) return concept;
        }
        return null;
    }

    private static String cleanConcept(String raw) {
        if (raw == null) return "";
        String concept = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[?!.,]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        while (concept.startsWith("a ") || concept.startsWith("an ") || concept.startsWith("the ")) {
            if (concept.startsWith("a ")) concept = concept.substring(2).trim();
            else if (concept.startsWith("an ")) concept = concept.substring(3).trim();
            else concept = concept.substring(4).trim();
        }
        return concept;
    }

    private static KnowledgeCommand parseKnowledgeCommand(String phrase) {
        String text = phrase == null ? "" : phrase.trim();
        String[] politePrefixes = {
                "please ", "can you ", "could you ", "would you ", "will you ",
                "i want you to ", "i need you to "
        };
        boolean stripped;
        do {
            stripped = false;
            for (String prefix : politePrefixes) {
                if (text.startsWith(prefix)) {
                    text = text.substring(prefix.length()).trim();
                    stripped = true;
                    break;
                }
            }
        } while (stripped);

        String[][] verbs = {
                {"interact with ", "interact"},
                {"cut down ", "cut"},
                {"chop down ", "cut"},
                {"walk to ", "go"},
                {"go to ", "go"},
                {"look at ", "look"},
                {"attack ", "attack"},
                {"kill ", "attack"},
                {"fight ", "attack"},
                {"approach ", "go"},
                {"locate ", "find"},
                {"find ", "find"},
                {"chop ", "cut"},
                {"mine ", "cut"},
                {"break ", "cut"},
                {"cut ", "cut"},
                {"use ", "interact"}
        };

        for (String[] mapping : verbs) {
            if (!text.startsWith(mapping[0])) continue;
            String concept = text.substring(mapping[0].length()).trim();
            concept = concept.replaceFirst("^(?:the |a |an )", "");
            concept = concept.replaceFirst("^(?:nearest |closest |next )", "");
            concept = cleanConcept(concept);
            if (concept.equals("one") && !lastKnowledgeConcept.isBlank()) concept = lastKnowledgeConcept;
            if (concept.isBlank()) return null;
            return new KnowledgeCommand(mapping[1], concept);
        }
        return null;
    }

    private static boolean isRepeatKnowledgeRequest(String phrase) {
        return phrase.equals("do the next one")
                || phrase.equals("do another one")
                || phrase.equals("do that again")
                || phrase.equals("do it again")
                || phrase.equals("next one")
                || phrase.equals("another one")
                || phrase.equals("do the next one closest");
    }

    private static String resolveKnownConcept(String requested) {
        String concept = cleanConcept(requested);
        if (memory.knowsConcept(concept)) return memory.canonicalConcept(concept);
        if (concept.endsWith("s") && concept.length() > 1) {
            String singular = concept.substring(0, concept.length() - 1);
            if (memory.knowsConcept(singular)) return memory.canonicalConcept(singular);
        }
        return null;
    }

    private static String describeVerb(String verb) {
        return switch (verb) {
            case "cut" -> "cut/mine";
            case "go" -> "go to";
            case "look" -> "look at";
            case "find" -> "find";
            case "attack" -> "attack";
            case "interact" -> "interact with";
            default -> verb;
        };
    }

    private static WorldReferent currentRayReferent() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return null;
        HitResult hit = minecraft.hitResult;
        if (hit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            Identifier id = BuiltInRegistries.BLOCK.getKey(minecraft.level.getBlockState(pos).getBlock());
            if (id == null) return null;
            return new WorldReferent("block:" + id, id.toString(), pos, null);
        }
        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (id == null) return null;
            return new WorldReferent("entity:" + id, id.toString(), null, entity);
        }
        return null;
    }

    private static void executePendingKnowledgeIfReady(Minecraft minecraft) {
        if (pendingKnowledgeCommand == null
                || minecraft.gui.screen() != null
                || DAI_AutomationLogic.isActive()
                || !DAI_ActionQueue.isEmpty()) {
            return;
        }

        KnowledgeCommand command = pendingKnowledgeCommand;
        pendingKnowledgeCommand = null;
        decisionTicks = 0;
        String result = executeKnowledgeAction(command);
        addSystem(result);
    }

    private static String executeKnowledgeAction(KnowledgeCommand command) {
        String requiredKind = switch (command.verb()) {
            case "cut", "go" -> "block";
            case "attack" -> "entity";
            default -> "any";
        };

        ResolvedTarget target = resolveClosestKnownTarget(command.concept(), requiredKind);
        if (target == null) {
            spriteMood = "confused";
            return "Sapphire couldn't find a nearby world target matching learned concept '"
                    + command.concept() + "'.";
        }

        switch (command.verb()) {
            case "find" -> {
                spriteMood = "happy";
                return "Sapphire found the closest learned " + command.concept() + " at " + target.description() + ".";
            }
            case "look" -> {
                if (target.entity() != null) DAI_LookLogic.lookAt(target.entity());
                else DAI_LookLogic.lookAt(Vec3.atCenterOf(target.block()));
                spriteMood = "curious";
                return "Sapphire is looking at the closest learned " + command.concept() + ".";
            }
            case "go" -> {
                if (target.block() == null) return "Sapphire currently knows how to approach learned block concepts, not moving entity concepts.";
                queueBlockApproach(false);
                spriteMood = "determined";
                return "Sapphire queued an approach to the closest learned " + command.concept() + ".";
            }
            case "cut" -> {
                if (target.block() == null || !target.referent().startsWith("block:")) {
                    return "Sapphire learned '" + command.concept() + "', but it is not currently grounded to a mineable block target.";
                }
                String blockId = target.referent().substring("block:".length());
                DAI_ActionQueue.enqueue(newAction("mine_nearest_block", blockId, KNOWLEDGE_APPROACH_TIMEOUT, KNOWLEDGE_SEARCH_RADIUS));
                spriteMood = "determined";
                return "Sapphire queued DAI mining for the closest learned " + command.concept() + " (" + blockId + ").";
            }
            case "attack" -> {
                if (!(target.entity() instanceof LivingEntity living) || !living.isAlive()) {
                    return "Sapphire couldn't find a living learned " + command.concept() + " to attack.";
                }
                DAI_TargetLogic.select(living);
                DAI_LookLogic.lookAt(living);
                DAI_CombatLogic.attackTarget(newAction("attack_target", "", 0, 0.0D));
                spriteMood = "determined";
                return "Sapphire engaged the closest learned " + command.concept() + ".";
            }
            case "interact" -> {
                if (target.block() != null) {
                    queueBlockApproach(true);
                    spriteMood = "curious";
                    return "Sapphire queued an approach and interaction with the closest learned " + command.concept() + ".";
                }
                if (target.entity() != null) {
                    DAI_TargetLogic.select(target.entity());
                    DAI_LookLogic.lookAt(target.entity());
                    DAI_ActionQueue.enqueueAll(List.of(
                            newAction("delay", "", 2, 0.0D),
                            newAction("interact", "", 0, 0.0D)
                    ));
                    spriteMood = "curious";
                    return "Sapphire queued an interaction with the closest learned " + command.concept() + ".";
                }
            }
            default -> { }
        }
        return "Sapphire understood the concept but doesn't have an executable mapping for that verb yet.";
    }

    private static void queueBlockApproach(boolean interactAfter) {
        List<DAI_ActionDefinition> actions = new ArrayList<>();
        actions.add(newAction("approach_target_block", "", KNOWLEDGE_APPROACH_TIMEOUT, KNOWLEDGE_STOP_DISTANCE));
        actions.add(newAction("wait_for_approach", "", KNOWLEDGE_APPROACH_TIMEOUT, 0.0D));
        if (interactAfter) {
            actions.add(newAction("wait_for_target_block", "", 20, 0.0D));
            actions.add(newAction("interact", "", 0, 0.0D));
        }
        DAI_ActionQueue.enqueueAll(actions);
    }

    private static DAI_ActionDefinition newAction(String type, String action, int ticks, double value) {
        return new DAI_ActionDefinition(
                type, action, List.of(), List.of(), "", "",
                0.0F, 0.0F, "", ticks, 0, false, value
        );
    }

    private static ResolvedTarget resolveClosestKnownTarget(String concept, String requiredKind) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return null;

        List<DAI_LearningMemory.ReferentMatch> known = memory.referentsForConcept(concept);
        if (known.isEmpty()) return null;

        ResolvedTarget best = null;

        if (!"entity".equals(requiredKind)) {
            for (DAI_LearningMemory.ReferentMatch match : known) {
                String referent = match.referent();
                if (!referent.startsWith("block:")) continue;
                String blockId = referent.substring("block:".length());
                if (!DAI_TargetLogic.findAndSelectBlock(blockId, KNOWLEDGE_SEARCH_RADIUS)) continue;
                BlockPos pos = DAI_TargetState.selectedBlock();
                if (pos == null) continue;
                double distance = minecraft.player.position().distanceToSqr(Vec3.atCenterOf(pos));
                ResolvedTarget candidate = new ResolvedTarget(referent, pos, null, distance);
                if (best == null || candidate.distanceSqr() < best.distanceSqr()) best = candidate;
            }
        }

        if (!"block".equals(requiredKind)) {
            Set<String> entityIds = new LinkedHashSet<>();
            for (DAI_LearningMemory.ReferentMatch match : known) {
                if (match.referent().startsWith("entity:")) {
                    entityIds.add(match.referent().substring("entity:".length()));
                }
            }
            if (!entityIds.isEmpty()) {
                AABB box = minecraft.player.getBoundingBox().inflate(KNOWLEDGE_SEARCH_RADIUS);
                for (Entity entity : minecraft.level.getEntitiesOfClass(
                        Entity.class,
                        box,
                        candidate -> candidate != minecraft.player && !candidate.isRemoved()
                )) {
                    Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                    if (id == null || !entityIds.contains(id.toString())) continue;
                    double distance = entity.distanceToSqr(minecraft.player);
                    ResolvedTarget candidate = new ResolvedTarget("entity:" + id, null, entity, distance);
                    if (best == null || candidate.distanceSqr() < best.distanceSqr()) best = candidate;
                }
            }
        }

        if (best != null) {
            if (best.block() != null) DAI_TargetState.selectBlock(best.block());
            else if (best.entity() != null) DAI_TargetLogic.select(best.entity());
        }
        return best;
    }

    private record KnowledgeCommand(String verb, String concept) {}

    private record WorldReferent(String key, String nativeId, BlockPos block, Entity entity) {}

    private record ResolvedTarget(String referent, BlockPos block, Entity entity, double distanceSqr) {
        String description() {
            if (block != null) return block.toString();
            if (entity != null) return entity.getName().getString();
            return referent;
        }
    }

    private static DAI_LearningAgentDefinition.Response selectResponse(Set<String> tokens) {
        List<DAI_LearningAgentDefinition.Response> responses = activeAgent.dialogue().responses();
        if (responses.isEmpty()) return null;
        if (RANDOM.nextDouble() < activeAgent.dialogue().exploration()) {
            DAI_LearningAgentDefinition.Response random = responses.get(RANDOM.nextInt(responses.size()));
            if ("fallback".equals(random.id())) return fallbackResponse(responses);
            return random;
        }

        DAI_LearningAgentDefinition.Response selected = responses.getFirst();
        double best = responseScore(selected, tokens);
        int bestHits = keywordHits(selected, tokens);
        for (int i = 1; i < responses.size(); i++) {
            DAI_LearningAgentDefinition.Response candidate = responses.get(i);
            double score = responseScore(candidate, tokens);
            int hits = keywordHits(candidate, tokens);
            if (score > best || (Double.compare(score, best) == 0 && hits > bestHits)
                    || (Double.compare(score, best) == 0 && hits == bestHits && RANDOM.nextBoolean())) {
                selected = candidate;
                best = score;
                bestHits = hits;
            }
        }

        int languageTokenCount = languageTokenCount(tokens);
        boolean weakLongMatch = languageTokenCount >= 5 && bestHits <= 1 && best < 2.75D;
        if (best <= DIALOGUE_UNKNOWN_THRESHOLD || weakLongMatch) {
            return fallbackResponse(responses);
        }
        return selected;
    }

    /**
     * Dialogue state tokens are useful context for response scoring, but they are not
     * words the player typed. Do not let them make a short utterance such as
     * "hello" look like a long low-confidence sentence.
     */
    private static int languageTokenCount(Set<String> tokens) {
        int count = 0;
        for (String token : tokens) {
            if (!token.startsWith("state_")) count++;
        }
        return count;
    }

    private static double responseScore(DAI_LearningAgentDefinition.Response response, Set<String> tokens) {
        double score = 0.0D;
        for (String token : tokens) {
            score += memory.dialogueWeight(token, response.id());
            if (response.keywords().contains(token)) score += response.seedWeight();
        }
        if (!lastDialogueResponseId.isBlank() && lastDialogueResponseId.equals(response.id())) {
            score -= DIALOGUE_REPEAT_PENALTY;
        }
        return score;
    }

    private static int keywordHits(DAI_LearningAgentDefinition.Response response, Set<String> tokens) {
        int hits = 0;
        for (String token : tokens) {
            if (response.keywords().contains(token)) hits++;
        }
        return hits;
    }

    private static DAI_LearningAgentDefinition.Response fallbackResponse(List<DAI_LearningAgentDefinition.Response> responses) {
        for (DAI_LearningAgentDefinition.Response response : responses) {
            if ("fallback".equals(response.id())) return response;
        }
        return responses.isEmpty() ? null : responses.getLast();
    }

    private static String inferMood(String message, Set<String> tokens, DAI_LearningAgentDefinition.Response response) {
        if (response == null) return "listening";
        String id = response.id();
        if ("greeting".equals(id) || "thanks".equals(id) || "identity".equals(id) || "apology".equals(id)) return "happy";
        if ("danger".equals(id)) return "worried";
        if ("fallback".equals(id)) return "confused";
        if ("status".equals(id) || "teaching".equals(id) || "activity".equals(id)) return tokens.contains("?") || message.contains("?") ? "thinking" : "listening";
        if (tokens.contains("sorry") || tokens.contains("sad")) return "sad";
        if (tokens.contains("danger") || tokens.contains("enemy") || tokens.contains("fight")) return "worried";
        if (message.contains("?")) return "curious";
        return autonomy ? "determined" : "listening";
    }

    private static void reinforceLastDialogue(double reward) {
        if (lastDialogueResponseId.isBlank() || lastDialogueInput.isBlank()) return;
        memory.reinforceDialogue(lastDialogueTokens, lastDialogueResponseId,
                reward, activeAgent.dialogue().learningRate());
    }

    private static Set<String> tokens(String text) {
        LinkedHashSet<String> output = new LinkedHashSet<>();
        if (text == null) return output;
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9_']+")) {
            if (!token.isBlank()) output.add(token);
        }
        return output;
    }

    /** Add current JSON observation bits to language input so speech can become grounded in gameplay. */
    private static Set<String> dialogueTokens(String text) {
        LinkedHashSet<String> output = new LinkedHashSet<>(tokens(text));
        if (activeAgent != null) {
            for (DAI_LearningAgentDefinition.Observation observation : activeAgent.observations()) {
                if (DAI_ConditionEvaluator.evaluate(observation.condition())) {
                    output.add("state_" + observation.id());
                }
            }
        }

        WorldReferent ray = currentRayReferent();
        if (ray != null) {
            output.add(ray.key().startsWith("block:") ? "ray_kind_block" : "ray_kind_entity");
            output.add("ray_" + ray.nativeId().replace(':', '_').replace('/', '_'));
            DAI_LearningMemory.ConceptMatch learned = memory == null ? null : memory.bestConcept(ray.key());
            if (learned != null && learned.confidence() > 0.0D) {
                output.add("ray_concept_" + learned.concept().replace(' ', '_'));
            }
        }
        return output;
    }

    private static String command(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/knowledge ")) {
            String payload = message.substring("/knowledge ".length()).trim();
            String payloadLower = payload.toLowerCase(Locale.ROOT);
            if (payloadLower.equals("folder")) {
                String result = "Knowledge inbox: " + DAI_KnowledgeImport.inbox(activeAgent.knowledge().importFolder());
                addSystem(result);
                return result;
            }
            if (payloadLower.startsWith("read ")) {
                DAI_KnowledgeImport.ImportResult imported = DAI_KnowledgeImport.inspect(
                        activeAgent.knowledge().importFolder(), cleanFileSelection(payload.substring(5)));
                addSystem(imported.message());
                return imported.message();
            }
            if (payloadLower.startsWith("learn ")) {
                DAI_KnowledgeImport.ImportResult imported = DAI_KnowledgeImport.learn(
                        memory, activeAgent.knowledge().importFolder(), cleanFileSelection(payload.substring(6)),
                        activeAgent.knowledge().allowGroundingImports());
                addSystem(imported.message());
                return imported.message();
            }
            String help = "/knowledge folder | /knowledge read here|file.json | /knowledge learn here|file.json";
            addSystem(help);
            return help;
        }
        if (lower.startsWith("/teach ")) {
            String payload = message.substring(7).trim();
            int split = payload.indexOf("=>");
            if (split <= 0 || split >= payload.length() - 2) {
                String help = "Use /teach question => answer";
                addSystem(help);
                return help;
            }
            String input = payload.substring(0, split).trim();
            String reply = payload.substring(split + 2).trim();
            memory.teach(normalizePhrase(input), reply);
            memory.save();
            spriteMood = "happy";
            String result = "Learned exact reply for: " + input;
            addSystem(result);
            return result;
        }
        if (lower.startsWith("/reward")) {
            spriteMood = "happy";
            double value = parseCommandNumber(message, 1.0D);
            rewardDialogue(Math.abs(value));
            return "Rewarded last response.";
        }
        if (lower.startsWith("/punish")) {
            spriteMood = "sad";
            double value = parseCommandNumber(message, -1.0D);
            rewardDialogue(-Math.abs(value));
            return "Penalized last response.";
        }
        if (lower.startsWith("/auto ")) {
            boolean enabled = lower.endsWith(" on") || lower.endsWith(" true") || lower.endsWith(" 1");
            setAutonomy(enabled);
            return "Autonomy " + (enabled ? "on" : "off") + ".";
        }
        if (lower.equals("/status")) {
            spriteMood = "thinking";
            String result = summary();
            addSystem(result);
            return result;
        }
        String help = "/teach question => answer | /knowledge folder|read|learn | /reward [n] | /punish [n] | /auto on|off | /status";
        addSystem(help);
        return help;
    }

    private static double parseCommandNumber(String text, double fallback) {
        String[] split = text.trim().split("\\s+");
        if (split.length < 2) return fallback;
        try { return Double.parseDouble(split[1]); } catch (NumberFormatException ignored) { return fallback; }
    }

    private static String normalizePhrase(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private static void addHistory(String line) {
        if (line == null || line.isBlank()) return;
        HISTORY.add(line);
        while (HISTORY.size() > MAX_HISTORY) HISTORY.removeFirst();
    }

    private static void addSystem(String line) { addHistory("[DAI] " + line); }
}
