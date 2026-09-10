package io.github.j12h36h.dai.story;

import io.github.j12h36h.dai.story.model.DAI_StoryEvent;
import io.github.j12h36h.dai.story.model.DAI_StorySession;

import java.util.LinkedHashMap;
import java.util.Map;

/** Flattens generic story/session/event facts for pack-authored templates and scene bindings. */
public final class DAI_StoryVariables {
    private DAI_StoryVariables() {}

    public static Map<String, Object> session(DAI_StorySession session) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        if (session == null) return out;
        out.put("session.number", session.number);
        out.put("session.context", session.context);
        out.put("session.completed", session.completed);
        out.put("session.observed_ticks", session.observedTicks);
        out.put("session.event_count", session.events == null ? 0 : session.events.size());
        out.put("session.duration_ms", session.durationMs());
        out.put("session.title", session.title);
        out.put("session.subtitle", session.subtitle);
        if (session.startFields != null) session.startFields.forEach((k,v) -> out.put("start." + k, v));
        if (session.endFields != null) session.endFields.forEach((k,v) -> out.put("end." + k, v));
        if (session.memory != null) session.memory.forEach((k,v) -> out.put("memory." + k, v));
        if (session.events != null) {
            LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
            for (DAI_StoryEvent event : session.events) if (event != null) counts.merge(event.id, 1, Integer::sum);
            counts.forEach((k,v) -> out.put("event_count." + k, v));
        }
        return out;
    }

    public static Map<String, Object> panel(DAI_StorySession session, DAI_StoryEvent event) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>(session(session));
        if (event == null) return out;
        out.put("event.id", event.id);
        out.put("event.sequence", event.sequence);
        out.put("event.tick", event.tick);
        out.put("event.importance", event.importance);
        if (event.fields != null) event.fields.forEach((k,v) -> {
            out.put(k, v);
            out.put("event." + k, v);
        });
        return out;
    }
}
