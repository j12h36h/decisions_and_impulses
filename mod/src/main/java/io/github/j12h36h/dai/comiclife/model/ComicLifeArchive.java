package io.github.j12h36h.dai.comiclife.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ComicLifeArchive {
    public int schema = 1;
    public Map<String, ComicLifeLife> activeByContext = new LinkedHashMap<>();
    public List<ComicLifeLife> completed = new ArrayList<>();

    public void normalize() {
        if (activeByContext == null) activeByContext = new LinkedHashMap<>();
        if (completed == null) completed = new ArrayList<>();
        activeByContext.values().removeIf(v -> v == null);
        completed.removeIf(v -> v == null);
        for (ComicLifeLife life : activeByContext.values()) life.normalize();
        for (ComicLifeLife life : completed) life.normalize();
    }

    public int nextLifeNumber() {
        int max = 0;
        for (ComicLifeLife life : completed) max = Math.max(max, life.number);
        for (ComicLifeLife life : activeByContext.values()) max = Math.max(max, life.number);
        return max + 1;
    }
}
