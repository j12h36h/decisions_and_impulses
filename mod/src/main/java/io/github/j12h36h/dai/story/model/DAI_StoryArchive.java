package io.github.j12h36h.dai.story.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DAI_StoryArchive {
    public int format = 1;
    public String profile = "";
    public List<DAI_StorySession> completed = new ArrayList<>();
    public Map<String, DAI_StorySession> activeByContext = new LinkedHashMap<>();

    public void normalize() {
        if (profile == null) profile = "";
        if (completed == null) completed = new ArrayList<>();
        if (activeByContext == null) activeByContext = new LinkedHashMap<>();
        completed.removeIf(v -> v == null);
        completed.forEach(DAI_StorySession::normalize);
        activeByContext.values().removeIf(v -> v == null);
        activeByContext.values().forEach(DAI_StorySession::normalize);
    }

    public int nextSessionNumber() {
        int max = 0;
        for (DAI_StorySession session : completed) max = Math.max(max, session.number);
        for (DAI_StorySession session : activeByContext.values()) max = Math.max(max, session.number);
        return max + 1;
    }
}
