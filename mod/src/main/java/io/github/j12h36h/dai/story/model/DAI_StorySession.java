package io.github.j12h36h.dai.story.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DAI_StorySession {
    public int number;
    public String context = "";
    public long startedAtEpochMs;
    public long endedAtEpochMs;
    public long observedTicks;
    public boolean completed;
    public String title = "";
    public String subtitle = "";
    public Map<String, String> startFields = new LinkedHashMap<>();
    public Map<String, String> endFields = new LinkedHashMap<>();
    public Map<String, String> memory = new LinkedHashMap<>();
    public Map<String, Long> lastEventTick = new LinkedHashMap<>();
    public List<DAI_StoryEvent> events = new ArrayList<>();
    public List<DAI_StoryPage> pages = new ArrayList<>();

    public void normalize() {
        if (context == null) context = "";
        if (title == null) title = "";
        if (subtitle == null) subtitle = "";
        if (startFields == null) startFields = new LinkedHashMap<>();
        if (endFields == null) endFields = new LinkedHashMap<>();
        if (memory == null) memory = new LinkedHashMap<>();
        if (lastEventTick == null) lastEventTick = new LinkedHashMap<>();
        if (events == null) events = new ArrayList<>();
        if (pages == null) pages = new ArrayList<>();
        events.removeIf(e -> e == null);
        events.forEach(DAI_StoryEvent::normalize);
        pages.removeIf(p -> p == null);
        pages.forEach(DAI_StoryPage::normalize);
    }

    public long durationMs() {
        long end = endedAtEpochMs > 0L ? endedAtEpochMs : System.currentTimeMillis();
        return Math.max(0L, end - startedAtEpochMs);
    }
}
