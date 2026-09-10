package io.github.j12h36h.dai.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.story.model.DAI_StoryEvent;
import io.github.j12h36h.dai.story.model.DAI_StoryPage;
import io.github.j12h36h.dai.story.model.DAI_StoryPanel;
import io.github.j12h36h.dai.story.model.DAI_StorySession;
import io.github.j12h36h.dai.util.DAI_TemplateEngine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Generic significance/pagination compiler driven entirely by profile JSON. */
public final class DAI_StoryCompiler {
    private DAI_StoryCompiler() {}

    public static void compile(DAI_StorySession session, DAI_StoryProfileDefinition profile) {
        if (session == null || profile == null) return;
        session.normalize();
        JsonObject compiler = profile.compiler();
        Map<String, Object> variables = DAI_StoryVariables.session(session);
        session.title = resolveRuleTemplate(DAI_StoryProfileDefinition.array(compiler, "title_rules"), "title", compiler,
                "title", variables, session);
        session.subtitle = resolveRuleTemplate(DAI_StoryProfileDefinition.array(compiler, "subtitle_rules"), "subtitle", compiler,
                "subtitle", variables, session);
        variables = DAI_StoryVariables.session(session);
        session.pages = buildPages(session, profile, variables);
    }

    public static List<DAI_StoryPage> preview(DAI_StorySession session, DAI_StoryProfileDefinition profile) {
        if (session == null || profile == null) return List.of();
        DAI_StorySession copy = copyShallow(session);
        compile(copy, profile);
        return List.copyOf(copy.pages);
    }

    private static List<DAI_StoryPage> buildPages(DAI_StorySession session, DAI_StoryProfileDefinition profile, Map<String,Object> variables) {
        JsonObject compiler = profile.compiler();
        JsonObject viewer = profile.viewer();
        List<DAI_StoryEvent> selected = selectEvents(session, compiler);
        ArrayList<DAI_StoryPage> pages = new ArrayList<>();

        JsonObject cover = DAI_StoryProfileDefinition.object(viewer, "cover");
        if (DAI_StoryProfileDefinition.bool(cover, "enabled", cover.size() > 0)) {
            DAI_StoryPage page = new DAI_StoryPage();
            page.kind = "cover";
            page.heading = DAI_TemplateEngine.resolve(DAI_StoryProfileDefinition.string(cover, "heading", ""), variables);
            page.subheading = DAI_TemplateEngine.resolve(DAI_StoryProfileDefinition.string(cover, "subheading", ""), variables);
            pages.add(page);
        }

        int panelsPerPage = Math.max(1, Math.min(12, DAI_StoryProfileDefinition.integer(compiler, "panels_per_page", 2)));
        JsonObject storyPage = DAI_StoryProfileDefinition.object(viewer, "story_page");
        String headingTemplate = DAI_StoryProfileDefinition.string(storyPage, "heading", "");
        String subheadingTemplate = DAI_StoryProfileDefinition.string(storyPage, "subheading", "");
        for (int offset = 0, pageNumber = 1; offset < selected.size(); offset += panelsPerPage, pageNumber++) {
            DAI_StoryPage page = new DAI_StoryPage();
            page.kind = "story";
            LinkedHashMap<String,Object> pageVars = new LinkedHashMap<>(variables);
            pageVars.put("page.number", pageNumber);
            pageVars.put("page.first_event", offset + 1);
            page.heading = DAI_TemplateEngine.resolve(headingTemplate, pageVars);
            page.subheading = DAI_TemplateEngine.resolve(subheadingTemplate, pageVars);
            for (int i = offset; i < Math.min(selected.size(), offset + panelsPerPage); i++) {
                DAI_StoryEvent event = selected.get(i);
                DAI_StoryPanel panel = new DAI_StoryPanel();
                panel.eventSequence = event.sequence;
                panel.eventId = event.id;
                panel.importance = event.importance;
                panel.scene = event.scene;
                Map<String,Object> eventVars = DAI_StoryVariables.panel(session, event);
                panel.caption = DAI_TemplateEngine.resolve(event.captionTemplate, eventVars);
                panel.narration = DAI_TemplateEngine.resolve(event.narrationTemplate, eventVars);
                panel.fields = event.fields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(event.fields);
                page.panels.add(panel);
            }
            pages.add(page);
        }

        JsonObject back = DAI_StoryProfileDefinition.object(viewer, "back");
        if (DAI_StoryProfileDefinition.bool(back, "enabled", back.size() > 0)) {
            DAI_StoryPage page = new DAI_StoryPage();
            page.kind = "back";
            page.heading = DAI_TemplateEngine.resolve(DAI_StoryProfileDefinition.string(back, "heading", ""), variables);
            page.subheading = DAI_TemplateEngine.resolve(DAI_StoryProfileDefinition.string(back, "subheading", ""), variables);
            pages.add(page);
        }
        return pages;
    }

    private static List<DAI_StoryEvent> selectEvents(DAI_StorySession session, JsonObject compiler) {
        List<DAI_StoryEvent> all = new ArrayList<>(session.events == null ? List.of() : session.events);
        all.removeIf(e -> e == null);
        all.sort(Comparator.comparingLong(e -> e.sequence));
        if (all.isEmpty()) return all;
        int min = Math.max(0, Math.min(100, DAI_StoryProfileDefinition.integer(compiler, "minimum_importance", 0)));
        int max = Math.max(1, Math.min(1000, DAI_StoryProfileDefinition.integer(compiler, "max_events", 24)));
        boolean keepFirst = DAI_StoryProfileDefinition.bool(compiler, "preserve_first", true);
        boolean keepLast = DAI_StoryProfileDefinition.bool(compiler, "preserve_last", true);

        Set<Long> keep = new HashSet<>();
        if (keepFirst) keep.add(all.getFirst().sequence);
        if (keepLast) keep.add(all.getLast().sequence);
        List<DAI_StoryEvent> ranked = all.stream().filter(e -> e.importance >= min)
                .sorted(Comparator.comparingInt((DAI_StoryEvent e) -> e.importance).reversed().thenComparingLong(e -> e.sequence))
                .toList();
        for (DAI_StoryEvent event : ranked) {
            if (keep.size() >= max) break;
            keep.add(event.sequence);
        }
        ArrayList<DAI_StoryEvent> selected = new ArrayList<>();
        for (DAI_StoryEvent event : all) if (keep.contains(event.sequence)) selected.add(event);
        if (selected.size() > max) selected = new ArrayList<>(selected.subList(0, max));
        return selected;
    }

    private static String resolveRuleTemplate(JsonArray rules, String templateKey, JsonObject fallbackObject, String fallbackKey,
                                              Map<String,Object> vars, DAI_StorySession session) {
        if (rules != null) {
            for (JsonElement element : rules) {
                if (!element.isJsonObject()) continue;
                JsonObject rule = element.getAsJsonObject();
                if (!DAI_StoryConditions.all(DAI_StoryProfileDefinition.array(rule, "when"), vars, null, session)) continue;
                return DAI_TemplateEngine.resolve(DAI_StoryProfileDefinition.string(rule, templateKey, ""), vars);
            }
        }
        return DAI_TemplateEngine.resolve(DAI_StoryProfileDefinition.string(fallbackObject, fallbackKey, ""), vars);
    }

    private static DAI_StorySession copyShallow(DAI_StorySession source) {
        DAI_StorySession out = new DAI_StorySession();
        out.number = source.number; out.context = source.context; out.startedAtEpochMs = source.startedAtEpochMs;
        out.endedAtEpochMs = source.endedAtEpochMs; out.observedTicks = source.observedTicks; out.completed = source.completed;
        out.title = source.title; out.subtitle = source.subtitle;
        out.startFields = new LinkedHashMap<>(source.startFields); out.endFields = new LinkedHashMap<>(source.endFields);
        out.memory = new LinkedHashMap<>(source.memory); out.lastEventTick = new LinkedHashMap<>(source.lastEventTick);
        out.events = new ArrayList<>(source.events);
        return out;
    }
}
