package io.github.j12h36h.dai.story.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DAI_StoryPanel {
    public long eventSequence;
    public String eventId = "";
    public String caption = "";
    public String narration = "";
    public String scene = "";
    public int importance;
    public Map<String, String> fields = new LinkedHashMap<>();

    public void normalize() {
        if (eventId == null) eventId = "";
        if (caption == null) caption = "";
        if (narration == null) narration = "";
        if (scene == null) scene = "";
        if (fields == null) fields = new LinkedHashMap<>();
        importance = Math.max(0, Math.min(100, importance));
    }
}
