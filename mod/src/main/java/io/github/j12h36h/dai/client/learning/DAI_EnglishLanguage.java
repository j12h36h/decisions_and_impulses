package io.github.j12h36h.dai.client.learning;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Broad, conservative English semantic layer for Sapphire.
 *
 * This is intentionally parser-first: a message is classified as a question,
 * statement, condition, cause, correction, etc. before it is permitted to
 * mutate learned knowledge. Unknown English remains unknown instead of being
 * forced into a malformed fact.
 *
 * It is not a transformer or a claim of perfect natural-language competence;
 * it is a compositional grammar/semantics bridge for the DAI knowledge graph.
 */
public final class DAI_EnglishLanguage {
    private static final Set<String> WH_WORDS = Set.of("what", "who", "where", "when", "why", "how", "which");
    private static final Set<String> AUXILIARIES = Set.of(
            "am", "is", "are", "was", "were", "do", "does", "did",
            "can", "could", "will", "would", "should", "shall", "may", "might",
            "have", "has", "had"
    );
    private static final Set<String> PRONOUNS = Set.of(
            "i", "me", "myself", "you", "yourself", "he", "him", "she", "her",
            "it", "we", "us", "they", "them", "this", "that", "these", "those"
    );
    private static final Set<String> GREETINGS = Set.of("hello", "hi", "hey", "yo", "hiya", "sup");
    private static final Set<String> GENERIC_VERBS = Set.of(
            "like", "likes", "love", "loves", "hate", "hates", "need", "needs",
            "want", "wants", "use", "uses", "contain", "contains", "cause", "causes",
            "create", "creates", "produce", "produces", "follow", "follows", "precede", "precedes",
            "support", "supports", "oppose", "opposes", "own", "owns", "eat", "eats",
            "know", "knows", "remember", "remembers", "see", "sees", "make", "makes",
            "give", "gives", "take", "takes", "orbit", "orbits"
    );

    private static final Pattern COPULA_CLASS = Pattern.compile("^(.+?) (?:am|is|are|was|were) (?:a |an )(.+)$");
    private static final Pattern COPULA_NEG_CLASS = Pattern.compile("^(.+?) (?:am|is|are|was|were) not (?:a |an )(.+)$");
    private static final Pattern COPULA = Pattern.compile("^(.+?) (?:am|is|are|was|were) (.+)$");
    private static final Pattern COPULA_NEG = Pattern.compile("^(.+?) (?:am|is|are|was|were) not (.+)$");
    private static final Pattern HAS = Pattern.compile("^(.+?) (?:has|have|had|has got|have got) (.+)$");
    private static final Pattern HAS_NEG = Pattern.compile("^(.+?) (?:does not have|do not have|did not have|has no|have no) (.+)$");
    private static final Pattern CAN = Pattern.compile("^(.+?) (?:can|could) (.+)$");
    private static final Pattern CANNOT = Pattern.compile("^(.+?) (?:cannot|can not|can't|could not|couldn't) (.+)$");
    private static final Pattern MODAL = Pattern.compile("^(.+?) (should|must|will|would|may|might) (.+)$");
    private static final Pattern NAMED = Pattern.compile("^(.+?) (?:is called|is named|are called|are named) (.+)$");
    private static final Pattern DEFINITION = Pattern.compile("^(.+?) (?:means|mean|is defined as|are defined as) (.+)$");
    private static final Pattern PREPOSITIONAL = Pattern.compile("^(.+?) (?:am|is|are|was|were) (in|inside|on|at|near|under|below|over|above|behind|beside|next to|outside|within) (.+)$");
    private static final Pattern GENERIC_RELATION = Pattern.compile("^(.+?) ([a-z][a-z']*) (.+)$");

    private static String lastTopic = "";
    private static String lastObject = "";

    private DAI_EnglishLanguage() {}

    public static void reset() {
        lastTopic = "";
        lastObject = "";
    }

    public static Result process(
            String raw,
            DAI_LearningMemory memory,
            String agentName,
            int inferenceDepth,
            boolean learnFromChat
    ) {
        if (raw == null || memory == null) return null;
        String phrase = normalize(raw);
        if (phrase.isBlank() || phrase.startsWith("/")) return null;
        if (GREETINGS.contains(stripFinalPunctuation(phrase))) return null; // Let dialogue personality answer greetings.

        Result social = socialQuestion(phrase, memory, agentName);
        if (social != null) return social;

        if (isQuestion(raw, phrase)) {
            Result question = answerQuestion(phrase, memory, agentName, Math.max(1, inferenceDepth));
            if (question != null) return question;
            return new Result(
                    "I understand that as a question, but I don't know the answer yet.",
                    "curious",
                    false
            );
        }

        if (!learnFromChat) return null;

        Result correction = correction(phrase, memory);
        if (correction != null) return correction;

        Result compound = compoundStatement(phrase, memory);
        if (compound != null) return compound;

        return learnStatement(phrase, memory, 0.90D);
    }

    private static Result socialQuestion(String phrase, DAI_LearningMemory memory, String agentName) {
        String p = stripFinalPunctuation(phrase);
        if (p.matches("^(?:how are you|how are you doing|how do you feel|are you okay|are you ok|are you alright)$")) {
            return new Result("I'm active, listening, and learning. I'm okay.", "happy", false);
        }
        if (p.matches("^(?:who are you|what are you|what is your name|what's your name|who is sapphire|what is sapphire)$")) {
            return new Result("I'm " + agentName + ", the DAI learning companion.", "happy", false);
        }
        if (p.matches("^(?:what do you know|what have you learned|what do you remember|how much do you know)$")) {
            return new Result("I currently have " + memory.knowledgeConceptCount() + " concepts, "
                    + memory.knowledgeRelationCount() + " relations, and "
                    + memory.knowledgeAliasCount() + " aliases in persistent knowledge.", "thinking", false);
        }
        if (p.startsWith("do you know ")) {
            String concept = cleanObject(p.substring("do you know ".length()));
            boolean known = memory.knowsConcept(concept);
            rememberTopic(concept, "");
            return new Result(known ? "Yes. I know the concept " + label(memory, concept) + "."
                    : "Not yet. I don't know " + concept + " well enough yet.", known ? "happy" : "curious", false);
        }
        if (p.startsWith("do you understand ")) {
            String concept = cleanObject(p.substring("do you understand ".length()));
            boolean known = memory.knowsConcept(concept);
            String definition = memory.knowledgeDefinition(concept);
            rememberTopic(concept, "");
            if (known && definition != null && !definition.isBlank()) {
                return new Result("I understand " + label(memory, concept) + " as: " + definition + ".", "thinking", false);
            }
            return new Result(known ? "I know " + label(memory, concept) + ", but my understanding is still incomplete."
                    : "Not yet. I don't have a grounded understanding of " + concept + " yet.", "curious", false);
        }
        return null;
    }

    private static Result answerQuestion(String phrase, DAI_LearningMemory memory, String agentName, int depth) {
        String p = stripFinalPunctuation(phrase);

        // Definitions / identity.
        Matcher m = Pattern.compile("^(?:what does) (.+?) mean$").matcher(p);
        if (m.matches()) return describe(memory, resolveSubject(m.group(1)), depth);

        m = Pattern.compile("^(?:what is|who is) (.+)$").matcher(p);
        if (m.matches()) {
            String subject = resolveSubject(m.group(1));
            if (subject.equals("sapphire")) return new Result("I'm " + agentName + ", the DAI learning companion.", "happy", false);
            return describe(memory, subject, depth);
        }

        // Where questions use common spatial predicates.
        m = Pattern.compile("^where (?:am|is|are|was|were) (.+)$").matcher(p);
        if (m.matches()) {
            String subject = resolveSubject(m.group(1));
            rememberTopic(subject, "");
            for (String predicate : List.of("located_at", "at", "in", "inside", "on", "near", "within")) {
                List<String> objects = memory.knowledgeObjects(subject, predicate);
                if (!objects.isEmpty()) {
                    return new Result(label(memory, subject) + " " + readablePredicate(predicate) + " " + joinNatural(objects) + ".", "happy", false);
                }
            }
            return new Result("I know what " + label(memory, subject) + " refers to, but I don't know its location yet.", "curious", false);
        }

        // Why questions: search explicit cause/reason links on a normalized proposition.
        if (p.startsWith("why ")) {
            String proposition = propositionKey(p.substring(4));
            for (String predicate : List.of("because", "reason", "caused_by", "cause")) {
                List<String> reasons = memory.knowledgeObjects(proposition, predicate);
                if (!reasons.isEmpty()) {
                    return new Result("Because " + joinNatural(reasons) + ".", "thinking", false);
                }
            }
            return new Result("I understand you're asking why, but I don't have a learned cause for that yet.", "thinking", false);
        }

        // When questions: learned temporal links.
        m = Pattern.compile("^when (?:is|does|did|will) (.+)$").matcher(p);
        if (m.matches()) {
            String subject = propositionKey(m.group(1));
            for (String predicate : List.of("time", "during", "before", "after", "when")) {
                List<String> values = memory.knowledgeObjects(subject, predicate);
                if (!values.isEmpty()) return new Result("I associate that with " + joinNatural(values) + ".", "thinking", false);
            }
            return new Result("I understand the time question, but I don't know when yet.", "curious", false);
        }

        // How-to / capability questions.
        m = Pattern.compile("^how (?:does|do|did|can|could) (.+?) ([a-z][a-z']*)(?: (.+))?$").matcher(p);
        if (m.matches()) {
            String subject = resolveSubject(m.group(1));
            String verb = baseVerb(m.group(2));
            String object = cleanObject(m.group(3));
            List<String> methods = memory.knowledgeObjects(subject, "how_" + verb);
            if (methods.isEmpty()) methods = memory.knowledgeObjects(subject, "used_for");
            if (!methods.isEmpty()) return new Result(label(memory, subject) + " " + verb + " by " + joinNatural(methods) + ".", "thinking", false);
            String target = object.isBlank() ? verb : verb + " " + object;
            if (memory.knowsRelation(subject, "can", target, depth)) {
                return new Result("I know that " + label(memory, subject) + " can " + target + ", but I don't know the method in detail yet.", "thinking", false);
            }
            return new Result("I understand what you're asking how to do, but I haven't learned the method yet.", "curious", false);
        }

        // Does/do/did ... have ...?
        m = Pattern.compile("^(?:does|do|did) (.+?) have (.+)$").matcher(p);
        if (m.matches()) return yesNoRelation(memory, m.group(1), "has", m.group(2), depth);

        // Can/could ...?
        m = Pattern.compile("^(?:can|could) (.+?) (.+)$").matcher(p);
        if (m.matches()) return yesNoRelation(memory, m.group(1), "can", m.group(2), depth);

        // Does/do/did subject verb object?
        m = Pattern.compile("^(?:does|do|did) (.+?) ([a-z][a-z']*)(?: (.+))?$").matcher(p);
        if (m.matches()) {
            String subject = resolveSubject(m.group(1));
            String predicate = baseVerb(m.group(2));
            String object = cleanObject(m.group(3));
            if (object.isBlank()) {
                List<String> values = memory.knowledgeObjects(subject, predicate);
                if (!values.isEmpty()) return new Result("Yes. I know that " + label(memory, subject) + " " + readablePredicate(predicate) + " " + joinNatural(values) + ".", "happy", false);
                return new Result("I don't currently know that " + label(memory, subject) + " " + predicate + ".", "thinking", false);
            }
            return yesNoRelation(memory, subject, predicate, object, depth);
        }

        // Is/are/was/were subject a type?
        m = Pattern.compile("^(?:am|is|are|was|were) (.+?) (?:a |an )(.+)$").matcher(p);
        if (m.matches()) return yesNoRelation(memory, m.group(1), "is_a", m.group(2), depth);

        // Is/are subject not ...?
        m = Pattern.compile("^(?:am|is|are|was|were) (.+?) not (?:a |an )?(.+)$").matcher(p);
        if (m.matches()) {
            String subject = resolveSubject(m.group(1));
            String object = cleanObject(m.group(2));
            boolean negativeKnown = memory.knowsRelation(subject, "not_is_a", object, depth)
                    || memory.knowsRelation(subject, "not_is", object, depth);
            boolean positiveKnown = memory.knowsRelation(subject, "is_a", object, depth)
                    || memory.knowsRelation(subject, "is", object, depth);
            if (negativeKnown) return new Result("Yes. I have learned that " + label(memory, subject) + " is not " + object + ".", "happy", false);
            if (positiveKnown) return new Result("No. What I currently know says " + label(memory, subject) + " is " + object + ".", "thinking", false);
            return new Result("I don't know whether " + label(memory, subject) + " is not " + object + ".", "curious", false);
        }

        // General copular yes/no question.
        m = Pattern.compile("^(?:am|is|are|was|were) (.+?) (.+)$").matcher(p);
        if (m.matches()) {
            String subject = resolveSubject(m.group(1));
            String object = cleanObject(m.group(2));
            boolean known = memory.knowsRelation(subject, "is", object, depth)
                    || memory.knowsRelation(subject, "is_a", object, depth);
            rememberTopic(subject, object);
            return new Result(known
                    ? "Yes. Based on what I know, " + label(memory, subject) + " is " + object + "."
                    : "I don't currently know that " + label(memory, subject) + " is " + object + ".",
                    known ? "happy" : "thinking", false);
        }

        // What does subject verb?
        m = Pattern.compile("^what (?:does|do|did) (.+?) ([a-z][a-z']*)$").matcher(p);
        if (m.matches()) {
            String subject = resolveSubject(m.group(1));
            String predicate = baseVerb(m.group(2));
            List<String> objects = memory.knowledgeObjects(subject, predicate);
            if (!objects.isEmpty()) {
                rememberTopic(subject, objects.getFirst());
                return new Result(label(memory, subject) + " " + readablePredicate(predicate) + " " + joinNatural(objects) + ".", "happy", false);
            }
            return new Result("I don't know what " + label(memory, subject) + " " + predicate + " yet.", "curious", false);
        }

        // Which X ...? At least recognize it as a selection question rather than a fact.
        if (p.startsWith("which ")) {
            return new Result("I understand that as a selection question, but I need more learned candidates or world context to choose reliably.", "curious", false);
        }

        return null;
    }

    private static Result correction(String phrase, DAI_LearningMemory memory) {
        String p = stripFinalPunctuation(phrase);
        if (p.equals("no") || p.equals("wrong") || p.equals("that's wrong") || p.equals("that is wrong")) {
            return new Result("Okay. I understand that as a correction, not as a new fact by itself.", "thinking", false);
        }
        if (p.equals("yes") || p.equals("correct") || p.equals("that's correct") || p.equals("that is correct")) {
            return new Result("Okay. I understand that as confirmation.", "happy", false);
        }
        if (p.startsWith("no, ")) {
            String rest = p.substring(4).trim();
            Result learned = learnStatement(rest, memory, 0.98D);
            if (learned != null) return new Result("Thanks for correcting me. " + learned.response(), "thinking", true);
            return new Result("Thanks for correcting me. I'll treat what follows 'no' as a correction when I can parse it.", "thinking", false);
        }
        if (p.startsWith("i meant ")) {
            String rest = p.substring("i meant ".length()).trim();
            Result learned = learnStatement(rest, memory, 0.98D);
            if (learned != null) return new Result("I understand the correction. " + learned.response(), "thinking", true);
            return new Result("I understand that you're correcting the previous meaning, but I couldn't fully parse the replacement yet.", "curious", false);
        }
        return null;
    }

    private static Result compoundStatement(String phrase, DAI_LearningMemory memory) {
        String p = stripFinalPunctuation(phrase);

        Matcher conditional = Pattern.compile("^if (.+?)(?:,? then |, )(.+)$").matcher(p);
        if (conditional.matches()) {
            String condition = propositionKey(conditional.group(1));
            String result = propositionKey(conditional.group(2));
            if (!condition.isBlank() && !result.isBlank()) {
                memory.learnRelation(condition, "implies", result, 0.88D, "player_chat");
                rememberTopic(condition, result);
                return new Result("I learned that if " + conditional.group(1).trim() + ", then " + conditional.group(2).trim() + ".", "excited", true);
            }
        }

        int because = p.indexOf(" because ");
        if (because > 0 && because < p.length() - 9) {
            String eventText = p.substring(0, because).trim();
            String causeText = p.substring(because + 9).trim();
            String event = propositionKey(eventText);
            String cause = propositionKey(causeText);
            memory.learnRelation(event, "because", cause, 0.90D, "player_chat");
            memory.learnRelation(cause, "cause", event, 0.86D, "player_chat");
            rememberTopic(event, cause);
            return new Result("I learned the causal link: " + eventText + " because " + causeText + ".", "excited", true);
        }

        return null;
    }

    private static Result learnStatement(String phrase, DAI_LearningMemory memory, double confidence) {
        String p = stripFinalPunctuation(phrase);
        if (p.isBlank() || isQuestion("", p)) return null;

        Matcher m = Pattern.compile("^my name is (.+)$").matcher(p);
        if (m.matches()) return learnRelation(memory, "player", "has_name", m.group(1), confidence, "I learned your name.");

        m = Pattern.compile("^(?:your name is|you are called|you are named) (.+)$").matcher(p);
        if (m.matches()) return learnRelation(memory, "sapphire", "has_name", m.group(1), confidence, "I learned the name you're using for me.");

        m = Pattern.compile("^i am called (.+)$").matcher(p);
        if (m.matches()) return learnRelation(memory, "player", "has_name", m.group(1), confidence, "I learned your name.");

        m = DEFINITION.matcher(p);
        if (m.matches()) {
            String subject = resolveSubject(m.group(1));
            String definition = cleanObject(m.group(2));
            if (invalidFactParts(subject, definition)) return null;
            memory.learnDefinition(subject, definition, confidence, "player_chat_english");
            rememberTopic(subject, definition);
            return new Result("I learned the definition of " + label(memory, subject) + ".", "excited", true);
        }

        m = NAMED.matcher(p);
        if (m.matches()) return learnRelation(memory, m.group(1), "has_name", m.group(2), confidence, null);

        m = COPULA_NEG_CLASS.matcher(p);
        if (m.matches()) return learnRelation(memory, m.group(1), "not_is_a", m.group(2), confidence, null);

        m = COPULA_CLASS.matcher(p);
        if (m.matches()) return learnRelation(memory, m.group(1), "is_a", m.group(2), confidence, null);

        m = HAS_NEG.matcher(p);
        if (m.matches()) return learnRelation(memory, m.group(1), "not_has", m.group(2), confidence, null);

        m = HAS.matcher(p);
        if (m.matches()) return learnRelation(memory, m.group(1), "has", m.group(2), confidence, null);

        m = CANNOT.matcher(p);
        if (m.matches()) return learnRelation(memory, m.group(1), "not_can", m.group(2), confidence, null);

        m = CAN.matcher(p);
        if (m.matches()) return learnRelation(memory, m.group(1), "can", m.group(2), confidence, null);

        m = MODAL.matcher(p);
        if (m.matches()) return learnRelation(memory, m.group(1), m.group(2), m.group(3), confidence * 0.92D, null);

        m = PREPOSITIONAL.matcher(p);
        if (m.matches()) return learnRelation(memory, m.group(1), normalizePreposition(m.group(2)), m.group(3), confidence, null);

        m = COPULA_NEG.matcher(p);
        if (m.matches()) return learnRelation(memory, m.group(1), "not_is", m.group(2), confidence, null);

        m = COPULA.matcher(p);
        if (m.matches()) {
            String subject = resolveSubject(m.group(1));
            String object = cleanObject(m.group(2));
            if (invalidFactParts(subject, object)) return null;
            if (object.split("\\s+").length >= 6) {
                memory.learnDefinition(subject, object, confidence * 0.90D, "player_chat_english");
                rememberTopic(subject, object);
                return new Result("I learned that as a description of " + label(memory, subject) + ".", "excited", true);
            }
            return learnRelation(memory, subject, "is", object, confidence, null);
        }

        m = GENERIC_RELATION.matcher(p);
        if (m.matches() && GENERIC_VERBS.contains(m.group(2))) {
            return learnRelation(memory, m.group(1), baseVerb(m.group(2)), m.group(3), confidence * 0.94D, null);
        }

        return null;
    }

    private static Result learnRelation(
            DAI_LearningMemory memory,
            String rawSubject,
            String predicate,
            String rawObject,
            double confidence,
            String customResponse
    ) {
        String subject = resolveSubject(rawSubject);
        String object = resolveObject(rawObject);
        if (invalidFactParts(subject, object)) return null;
        String normalizedPredicate = DAI_KnowledgeGraph.normalizePredicate(predicate);
        memory.learnRelation(subject, normalizedPredicate, object, confidence, "player_chat_english");
        rememberTopic(subject, object);
        String response = customResponse != null
                ? customResponse
                : "I learned: " + label(memory, subject) + " " + readablePredicate(normalizedPredicate) + " " + object + ".";
        return new Result(response, "excited", true);
    }

    private static Result yesNoRelation(DAI_LearningMemory memory, String rawSubject, String predicate, String rawObject, int depth) {
        String subject = resolveSubject(rawSubject);
        String object = resolveObject(rawObject);
        String p = DAI_KnowledgeGraph.normalizePredicate(predicate);
        boolean positive = memory.knowsRelation(subject, p, object, depth);
        boolean negative = memory.knowsRelation(subject, "not_" + p, object, depth);
        rememberTopic(subject, object);
        if (positive) return new Result("Yes. Based on what I know, " + label(memory, subject) + " " + readablePredicate(p) + " " + object + ".", "happy", false);
        if (negative) return new Result("No. I have learned the opposite about " + label(memory, subject) + ".", "thinking", false);
        return new Result("I don't currently know whether " + label(memory, subject) + " " + readablePredicate(p) + " " + object + ".", "curious", false);
    }

    private static Result describe(DAI_LearningMemory memory, String rawConcept, int depth) {
        String concept = resolveSubject(rawConcept);
        if (!memory.knowsConcept(concept)) {
            rememberTopic(concept, "");
            return new Result("I don't know enough about " + cleanObject(rawConcept) + " yet.", "curious", false);
        }

        List<String> parts = new ArrayList<>();
        String definition = memory.knowledgeDefinition(concept);
        if (definition != null && !definition.isBlank()) parts.add(label(memory, concept) + " means " + definition);

        for (DAI_KnowledgeGraph.KnowledgeRelation relation : memory.knowledgeRelations(concept)) {
            if (parts.size() >= 6) break;
            parts.add(label(memory, concept) + " " + readablePredicate(relation.predicate()) + " " + relation.object());
        }
        if (parts.size() < 6) {
            for (String ancestor : memory.knowledgeAncestors(concept, depth)) {
                if (parts.size() >= 6) break;
                parts.add("I can infer that " + label(memory, concept) + " is a " + ancestor);
            }
        }
        rememberTopic(concept, "");
        if (parts.isEmpty()) return new Result("I know the concept " + label(memory, concept) + ", but I don't have enough facts attached to explain it yet.", "thinking", false);
        return new Result(String.join(". ", parts) + ".", "thinking", false);
    }

    public static boolean isQuestion(String raw, String normalized) {
        String p = stripFinalPunctuation(normalized == null ? normalize(raw) : normalized);
        if (raw != null && raw.trim().endsWith("?")) return true;
        if (p.isBlank()) return false;
        String first = firstWord(p);
        if (WH_WORDS.contains(first) || AUXILIARIES.contains(first)) return true;
        return p.startsWith("do you know ") || p.startsWith("do you understand ");
    }

    private static boolean invalidFactParts(String subject, String object) {
        if (subject == null || object == null || subject.isBlank() || object.isBlank()) return true;
        String first = firstWord(subject);
        if (WH_WORDS.contains(first)) return true;
        if (AUXILIARIES.contains(first) && subject.split("\\s+").length == 1) return true;
        // Prevent artifacts like "how is-a you" and other interrogative fragments.
        return WH_WORDS.contains(subject) && PRONOUNS.contains(object);
    }

    private static String resolveSubject(String raw) {
        String value = cleanObject(raw);
        if (value.isBlank()) return "";
        return switch (value) {
            case "i", "me", "myself", "my" -> "player";
            case "you", "yourself", "your" -> "sapphire";
            case "it", "this", "that", "this thing", "that thing" -> !lastTopic.isBlank() ? lastTopic : value;
            default -> value;
        };
    }

    private static String resolveObject(String raw) {
        String value = cleanObject(raw);
        if (value.isBlank()) return "";
        return switch (value) {
            case "me", "myself", "i" -> "player";
            case "you", "yourself" -> "sapphire";
            case "it", "this", "that" -> !lastObject.isBlank() ? lastObject : (!lastTopic.isBlank() ? lastTopic : value);
            default -> value;
        };
    }

    private static void rememberTopic(String subject, String object) {
        if (subject != null && !subject.isBlank()) lastTopic = subject;
        if (object != null && !object.isBlank()) lastObject = object;
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        String p = raw.toLowerCase(Locale.ROOT).trim()
                .replace('’', '\'')
                .replaceAll("\\s+", " ");
        String[][] contractions = {
                {"what's", "what is"}, {"who's", "who is"}, {"where's", "where is"}, {"when's", "when is"}, {"why's", "why is"}, {"how's", "how is"},
                {"i'm", "i am"}, {"you're", "you are"}, {"we're", "we are"}, {"they're", "they are"}, {"it's", "it is"}, {"that's", "that is"}, {"there's", "there is"},
                {"i've", "i have"}, {"you've", "you have"}, {"we've", "we have"}, {"they've", "they have"},
                {"i'd", "i would"}, {"you'd", "you would"}, {"we'd", "we would"}, {"they'd", "they would"},
                {"i'll", "i will"}, {"you'll", "you will"}, {"we'll", "we will"}, {"they'll", "they will"},
                {"don't", "do not"}, {"doesn't", "does not"}, {"didn't", "did not"}, {"isn't", "is not"}, {"aren't", "are not"}, {"wasn't", "was not"}, {"weren't", "were not"},
                {"won't", "will not"}, {"wouldn't", "would not"}, {"shouldn't", "should not"}, {"can't", "cannot"}, {"couldn't", "could not"}, {"hasn't", "has not"}, {"haven't", "have not"}, {"hadn't", "had not"}
        };
        for (String[] pair : contractions) p = p.replace(pair[0], pair[1]);
        return p.replaceAll("\\s+", " ").trim();
    }

    private static String stripFinalPunctuation(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("[?!.,;:]+$", "").trim();
    }

    private static String cleanObject(String raw) {
        if (raw == null) return "";
        String value = stripFinalPunctuation(normalize(raw));
        value = value.replaceAll("^(?:the |a |an )", "").trim();
        return value;
    }

    private static String firstWord(String text) {
        if (text == null || text.isBlank()) return "";
        int space = text.indexOf(' ');
        return space < 0 ? text : text.substring(0, space);
    }

    private static String baseVerb(String verb) {
        if (verb == null) return "";
        String v = verb.toLowerCase(Locale.ROOT).trim();
        return switch (v) {
            case "is", "are", "was", "were" -> "is";
            case "has", "had" -> "have";
            case "does", "did" -> "do";
            case "uses" -> "use";
            case "likes" -> "like";
            case "loves" -> "love";
            case "hates" -> "hate";
            case "needs" -> "need";
            case "wants" -> "want";
            case "contains" -> "contain";
            case "causes" -> "cause";
            case "creates" -> "create";
            case "produces" -> "produce";
            case "follows" -> "follow";
            case "precedes" -> "precede";
            case "supports" -> "support";
            case "opposes" -> "oppose";
            case "owns" -> "own";
            case "eats" -> "eat";
            case "knows" -> "know";
            case "remembers" -> "remember";
            case "sees" -> "see";
            case "makes" -> "make";
            case "gives" -> "give";
            case "takes" -> "take";
            case "orbits" -> "orbit";
            default -> v;
        };
    }

    private static String normalizePreposition(String prep) {
        String p = prep == null ? "" : prep.trim().replace(' ', '_');
        return switch (p) {
            case "inside" -> "in";
            case "below" -> "under";
            case "above" -> "over";
            case "next_to" -> "near";
            case "within" -> "in";
            default -> p;
        };
    }

    private static String propositionKey(String text) {
        String p = stripFinalPunctuation(normalize(text));
        p = p.replaceFirst("^(?:is|are|was|were|do|does|did|can|could|will|would|should) ", "");
        return "proposition:" + p;
    }

    private static String readablePredicate(String predicate) {
        String p = DAI_KnowledgeGraph.normalizePredicate(predicate);
        return switch (p) {
            case "is_a", "instance_of" -> "is a";
            case "not_is_a" -> "is not a";
            case "is" -> "is";
            case "not_is" -> "is not";
            case "part_of" -> "is part of";
            case "used_for" -> "is used for";
            case "located_at", "at" -> "is at";
            case "in" -> "is in";
            case "on" -> "is on";
            case "near" -> "is near";
            case "under" -> "is under";
            case "over" -> "is over";
            case "behind" -> "is behind";
            case "beside" -> "is beside";
            case "has" -> "has";
            case "not_has" -> "does not have";
            case "can" -> "can";
            case "not_can" -> "cannot";
            case "has_name" -> "has the name";
            case "implies" -> "implies";
            case "because" -> "is true because";
            default -> p.replace('_', ' ');
        };
    }

    private static String label(DAI_LearningMemory memory, String concept) {
        String label = memory.knowledgeLabel(concept);
        return label == null || label.isBlank() ? concept : label;
    }

    private static String joinNatural(List<String> values) {
        if (values == null || values.isEmpty()) return "nothing";
        LinkedHashSet<String> unique = new LinkedHashSet<>(values);
        List<String> list = new ArrayList<>(unique);
        if (list.size() == 1) return list.getFirst();
        if (list.size() == 2) return list.get(0) + " and " + list.get(1);
        return String.join(", ", list.subList(0, list.size() - 1)) + ", and " + list.getLast();
    }

    public record Result(String response, String mood, boolean learned) {}
}
