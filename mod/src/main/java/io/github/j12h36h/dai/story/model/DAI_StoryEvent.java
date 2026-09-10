package io.github.j12h36h.dai.story.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Serialized event fact record. Text remains a template until compilation. */
public final class DAI_StoryEvent {
    public long sequence;
    public long epochMs;
    public long tick;
    public String id = "event";
    public int importance;
    public String captionTemplate = "";
    public String narrationTemplate = "";
    public String scene = "";
    public Map<String, String> fields = new LinkedHashMap<>();

    public void normalize() {
        if (id == null || id.isBlank()) id = "event";
        if (captionTemplate == null) captionTemplate = "";
        if (narrationTemplate == null) narrationTemplate = "";
        if (scene == null) scene = "";
        importance = Math.max(0, Math.min(100, importance));
        if (fields == null) fields = new LinkedHashMap<>();
    }
}
