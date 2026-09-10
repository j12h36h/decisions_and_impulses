package io.github.j12h36h.dai.client.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.client.menus.DAI_ScreenManager;
import io.github.j12h36h.dai.client.story.screen.DAI_StoryLibraryScreen;
import io.github.j12h36h.dai.story.DAI_StoryCompiler;
import io.github.j12h36h.dai.story.DAI_StoryConditions;
import io.github.j12h36h.dai.story.DAI_StoryProfileDefinition;
import io.github.j12h36h.dai.story.DAI_StoryProfileRegistry;
import io.github.j12h36h.dai.story.model.DAI_StoryArchive;
import io.github.j12h36h.dai.story.model.DAI_StoryEvent;
import io.github.j12h36h.dai.story.model.DAI_StorySession;
import io.github.j12h36h.dai.story.storage.DAI_StoryStorage;
import io.github.j12h36h.dai.util.DAI_TemplateEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Runtime for any pack-authored story profile. No profile semantics live here. */
public final class DAI_StoryRuntime {
    private static final Map<Identifier, State> STATES = new LinkedHashMap<>();
    private static long ticks;
    private static boolean active;

    private DAI_StoryRuntime() {}

    public static void tick() {
        if (DAI_StoryProfileRegistry.isEmpty()) { shutdown(); return; }
        active = true;
        ticks++;

        boolean anyDue = false;
        for (var entry : DAI_StoryProfileRegistry.ordered()) {
            if (ticks % entry.getValue().pollInterval() == 0L) { anyDue = true; break; }
        }
        if (!anyDue) return;

        DAI_StoryProbe.Snapshot snapshot = DAI_StoryProbe.capture(Minecraft.getInstance());
        if (snapshot == null) { flushAll(); return; }

        for (var entry : DAI_StoryProfileRegistry.ordered()) {
            if (ticks % entry.getValue().pollInterval() != 0L) continue;
            observe(entry.getKey(), entry.getValue(), snapshot);
        }
    }

    public static void shutdown() {
        if (!active && STATES.isEmpty()) return;
        flushAll();
        STATES.clear();
        active = false;
    }

    public static DAI_StoryArchive archive(String profileId) {
        Identifier id = Identifier.tryParse(profileId == null ? "" : profileId.trim());
        if (id == null) return null;
        State state = STATES.get(id);
        if (state != null) return state.archive;
        DAI_StoryProbe.Snapshot snapshot = DAI_StoryProbe.capture(Minecraft.getInstance());
        if (snapshot == null || DAI_StoryProfileRegistry.get(id) == null) return null;
        return state(id, snapshot).archive;
    }

    public static void openLibrary(String profileId) {
        Identifier id = selectProfile(profileId);
        if (id == null) return;
        DAI_StoryArchive archive = archive(id.toString());
        if (archive == null) return;
        DAI_ScreenManager.open(new DAI_StoryLibraryScreen(id, archive));
    }

    public static boolean recordExplicit(String profileId, String eventId, int importance, String caption,
                                         String narration, String scene, Map<String,String> suppliedFields) {
        Identifier id = selectProfile(profileId);
        if (id == null) return false;
        DAI_StoryProfileDefinition profile = DAI_StoryProfileRegistry.get(id);
        DAI_StoryProbe.Snapshot snapshot = DAI_StoryProbe.capture(Minecraft.getInstance());
        if (profile == null || snapshot == null) return false;
        State state = state(id, snapshot);
        switchContext(state, snapshot);
        if (state.current == null) {
            if (!DAI_StoryProfileDefinition.bool(profile.session(), "auto_start_on_explicit", true)) return false;
            startSession(id, profile, state, snapshot);
        }
        if (state.current == null) return false;
        LinkedHashMap<String,String> fields = stringify(snapshot.fields());
        if (suppliedFields != null) fields.putAll(suppliedFields);
        addEvent(state.current, eventId, importance, caption, narration, scene, fields, snapshot);
        state.dirty = true;
        flush(id, state);
        return true;
    }

    public static boolean startExplicit(String profileId) {
        Identifier id = selectProfile(profileId);
        DAI_StoryProfileDefinition profile = id == null ? null : DAI_StoryProfileRegistry.get(id);
        DAI_StoryProbe.Snapshot snapshot = DAI_StoryProbe.capture(Minecraft.getInstance());
        if (id == null || profile == null || snapshot == null) return false;
        State state = state(id, snapshot);
        switchContext(state, snapshot);
        if (state.current == null || state.current.completed) startSession(id, profile, state, snapshot);
        flush(id, state);
        return state.current != null;
    }

    public static boolean endExplicit(String profileId) {
        Identifier id = selectProfile(profileId);
        DAI_StoryProfileDefinition profile = id == null ? null : DAI_StoryProfileRegistry.get(id);
        DAI_StoryProbe.Snapshot snapshot = DAI_StoryProbe.capture(Minecraft.getInstance());
        if (id == null || profile == null || snapshot == null) return false;
        State state = state(id, snapshot);
        switchContext(state, snapshot);
        if (state.current == null || state.current.completed) return false;
        endSession(id, profile, state, snapshot);
        flush(id, state);
        return true;
    }

    private static void observe(Identifier id, DAI_StoryProfileDefinition profile, DAI_StoryProbe.Snapshot snapshot) {
        State state = state(id, snapshot);
        switchContext(state, snapshot);
        Map<String,Object> current = snapshot.fields();
        Map<String,Object> previous = state.previous;
        JsonObject sessionDef = profile.session();

        if (state.current == null || state.current.completed) {
            JsonArray when = DAI_StoryProfileDefinition.array(sessionDef, "start_when");
            boolean auto = DAI_StoryProfileDefinition.bool(sessionDef, "auto_start", when.isEmpty());
            if (auto || DAI_StoryConditions.all(when, current, previous, null)) startSession(id, profile, state, snapshot);
        }

        if (state.current != null && !state.current.completed) {
            state.current.observedTicks += profile.pollInterval();
            evaluateEventRules(profile, state, snapshot);
            JsonArray end = DAI_StoryProfileDefinition.array(sessionDef, "end_when");
            if (!end.isEmpty() && DAI_StoryConditions.all(end, current, previous, state.current)) {
                endSession(id, profile, state, snapshot);
            }
            state.dirty = true;
        }
        state.previous = new LinkedHashMap<>(current);
        if (state.dirty && ticks - state.lastSaveTick >= 200L) flush(id, state);
    }

    private static void evaluateEventRules(DAI_StoryProfileDefinition profile, State state, DAI_StoryProbe.Snapshot snapshot) {
        if (state.current == null) return;
        for (JsonElement element : profile.events()) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            if (!DAI_StoryProfileDefinition.bool(rule, "enabled", true)) continue;
            String id = DAI_StoryProfileDefinition.string(rule, "id", "event").trim();
            if (id.isBlank()) continue;
            if (!DAI_StoryConditions.all(DAI_StoryProfileDefinition.array(rule, "when"), snapshot.fields(), state.previous, state.current)) continue;

            long nowTick = (long) number(snapshot.fields().get("runtime.game_tick"), ticks);
            long cooldown = Math.max(0, DAI_StoryProfileDefinition.integer(rule, "cooldown_ticks", 0));
            long last = state.current.lastEventTick.getOrDefault(id, Long.MIN_VALUE / 4);
            if (cooldown > 0 && nowTick - last < cooldown) continue;

            String onceKey = "once." + id;
            if (DAI_StoryProfileDefinition.bool(rule, "once", false) && "true".equals(state.current.memory.get(onceKey))) continue;
            String oncePerField = DAI_StoryProfileDefinition.string(rule, "once_per_value", "");
            String oncePerKey = "";
            if (!oncePerField.isBlank()) {
                Object value = DAI_StoryConditions.value(oncePerField, snapshot.fields(), state.current);
                oncePerKey = "once_value." + id + "." + String.valueOf(value);
                if ("true".equals(state.current.memory.get(oncePerKey))) continue;
            }

            int importance = score(rule, snapshot.fields(), state.previous, state.current);
            String caption = DAI_StoryProfileDefinition.string(rule, "caption", "");
            String narration = DAI_StoryProfileDefinition.string(rule, "narration", "");
            String scene = DAI_StoryProfileDefinition.string(rule, "scene", "");
            LinkedHashMap<String,String> fields = stringify(snapshot.fields());
            JsonObject authoredFields = DAI_StoryProfileDefinition.object(rule, "fields");
            Map<String,Object> vars = new LinkedHashMap<>(snapshot.fields());
            vars.putAll(io.github.j12h36h.dai.story.DAI_StoryVariables.session(state.current));
            for (var authored : authoredFields.entrySet()) {
                try { fields.put(authored.getKey(), DAI_TemplateEngine.resolve(authored.getValue().getAsString(), vars)); }
                catch (RuntimeException ignored) {}
            }
            addEvent(state.current, id, importance, caption, narration, scene, fields, snapshot);
            state.current.lastEventTick.put(id, nowTick);
            if (DAI_StoryProfileDefinition.bool(rule, "once", false)) state.current.memory.put(onceKey, "true");
            if (!oncePerKey.isBlank()) state.current.memory.put(oncePerKey, "true");
            JsonObject remember = DAI_StoryProfileDefinition.object(rule, "remember");
            for (var memory : remember.entrySet()) {
                try { state.current.memory.put(memory.getKey(), DAI_TemplateEngine.resolve(memory.getValue().getAsString(), vars)); }
                catch (RuntimeException ignored) {}
            }
        }
    }

    private static int score(JsonObject rule, Map<String,Object> current, Map<String,Object> previous, DAI_StorySession session) {
        double score = DAI_StoryProfileDefinition.number(rule, "importance", 50.0D);
        for (JsonElement element : DAI_StoryProfileDefinition.array(rule, "importance_rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject adjustment = element.getAsJsonObject();
            if (!DAI_StoryConditions.all(DAI_StoryProfileDefinition.array(adjustment, "when"), current, previous, session)) continue;
            if (adjustment.has("set")) score = DAI_StoryProfileDefinition.number(adjustment, "set", score);
            score += DAI_StoryProfileDefinition.number(adjustment, "add", 0.0D);
            score *= DAI_StoryProfileDefinition.number(adjustment, "multiply", 1.0D);
        }
        return Math.max(0, Math.min(100, (int)Math.round(score)));
    }

    private static void startSession(Identifier id, DAI_StoryProfileDefinition profile, State state, DAI_StoryProbe.Snapshot snapshot) {
        DAI_StorySession existing = state.archive.activeByContext.get(snapshot.context());
        if (existing != null && !existing.completed) {
            existing.normalize(); state.current = existing; return;
        }
        DAI_StorySession session = new DAI_StorySession();
        session.number = state.archive.nextSessionNumber();
        session.context = snapshot.context();
        session.startedAtEpochMs = System.currentTimeMillis();
        session.startFields = stringify(snapshot.fields());
        state.archive.activeByContext.put(snapshot.context(), session);
        state.current = session;
        JsonObject startEvent = DAI_StoryProfileDefinition.object(profile.session(), "start_event");
        if (startEvent.size() > 0) addConfiguredEvent(session, startEvent, snapshot);
        state.dirty = true;
    }

    private static void endSession(Identifier id, DAI_StoryProfileDefinition profile, State state, DAI_StoryProbe.Snapshot snapshot) {
        DAI_StorySession session = state.current;
        if (session == null || session.completed) return;
        JsonObject endEvent = DAI_StoryProfileDefinition.object(profile.session(), "end_event");
        if (endEvent.size() > 0) addConfiguredEvent(session, endEvent, snapshot);
        session.endFields = stringify(snapshot.fields());
        session.endedAtEpochMs = System.currentTimeMillis();
        session.completed = true;
        DAI_StoryCompiler.compile(session, profile);
        state.archive.activeByContext.remove(snapshot.context());
        state.archive.completed.add(session);
        int maxSessions = Math.max(1, Math.min(10000, DAI_StoryProfileDefinition.integer(profile.compiler(), "max_archived_sessions", 250)));
        while (state.archive.completed.size() > maxSessions) state.archive.completed.removeFirst();
        state.current = null;
        state.dirty = true;
    }

    private static void addConfiguredEvent(DAI_StorySession session, JsonObject event, DAI_StoryProbe.Snapshot snapshot) {
        addEvent(session,
                DAI_StoryProfileDefinition.string(event, "id", "event"),
                Math.max(0, Math.min(100, DAI_StoryProfileDefinition.integer(event, "importance", 50))),
                DAI_StoryProfileDefinition.string(event, "caption", ""),
                DAI_StoryProfileDefinition.string(event, "narration", ""),
                DAI_StoryProfileDefinition.string(event, "scene", ""),
                stringify(snapshot.fields()), snapshot);
    }

    private static void addEvent(DAI_StorySession session, String id, int importance, String caption, String narration,
                                 String scene, Map<String,String> fields, DAI_StoryProbe.Snapshot snapshot) {
        if (session == null) return;
        DAI_StoryEvent event = new DAI_StoryEvent();
        event.sequence = session.events.stream().mapToLong(e -> e == null ? 0L : e.sequence).max().orElse(0L) + 1L;
        event.epochMs = System.currentTimeMillis();
        event.tick = (long)number(snapshot == null ? null : snapshot.fields().get("runtime.game_tick"), ticks);
        event.id = id == null || id.isBlank() ? "event" : id.trim();
        event.importance = Math.max(0, Math.min(100, importance));
        event.captionTemplate = caption == null ? "" : caption;
        event.narrationTemplate = narration == null ? "" : narration;
        event.scene = scene == null ? "" : scene;
        event.fields = fields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fields);
        session.events.add(event);
    }

    private static State state(Identifier id, DAI_StoryProbe.Snapshot snapshot) {
        State state = STATES.computeIfAbsent(id, ignored -> new State());
        if (!snapshot.uuid().equals(state.loadedUuid)) {
            if (!state.loadedUuid.isBlank()) flush(id, state);
            state.loadedUuid = snapshot.uuid();
            state.archive = DAI_StoryStorage.load(id.toString(), snapshot.uuid());
            state.archive.normalize();
            state.context = ""; state.current = null; state.previous = null; state.dirty = false;
        }
        return state;
    }

    private static void switchContext(State state, DAI_StoryProbe.Snapshot snapshot) {
        if (snapshot.context().equals(state.context)) return;
        state.context = snapshot.context();
        state.current = state.archive.activeByContext.get(snapshot.context());
        if (state.current != null) state.current.normalize();
        state.previous = null;
    }

    private static Identifier selectProfile(String requested) {
        Identifier parsed = Identifier.tryParse(requested == null ? "" : requested.trim());
        if (parsed != null && DAI_StoryProfileRegistry.get(parsed) != null) return parsed;
        List<Map.Entry<Identifier, DAI_StoryProfileDefinition>> ordered = DAI_StoryProfileRegistry.ordered();
        return ordered.isEmpty() ? null : ordered.getFirst().getKey();
    }

    private static void flushAll() {
        for (var entry : new ArrayList<>(STATES.entrySet())) flush(entry.getKey(), entry.getValue());
    }
    private static void flush(Identifier id, State state) {
        if (state == null || state.loadedUuid.isBlank() || !state.dirty) return;
        DAI_StoryStorage.save(id.toString(), state.loadedUuid, state.archive);
        state.dirty = false; state.lastSaveTick = ticks;
    }

    private static LinkedHashMap<String,String> stringify(Map<String,Object> input) {
        LinkedHashMap<String,String> out = new LinkedHashMap<>();
        if (input == null) return out;
        input.forEach((key,value) -> {
            if (key == null || value == null) return;
            String text;
            if (value instanceof Collection<?> collection) text = String.join("|", collection.stream().map(String::valueOf).toList());
            else text = String.valueOf(value);
            if (text.length() > 2048) text = text.substring(0, 2048);
            out.put(key, text);
        });
        return out;
    }
    private static double number(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } catch (RuntimeException ignored) { return fallback; }
    }

    private static final class State {
        private String loadedUuid = "";
        private String context = "";
        private DAI_StoryArchive archive = new DAI_StoryArchive();
        private DAI_StorySession current;
        private Map<String,Object> previous;
        private boolean dirty;
        private long lastSaveTick;
    }
}
