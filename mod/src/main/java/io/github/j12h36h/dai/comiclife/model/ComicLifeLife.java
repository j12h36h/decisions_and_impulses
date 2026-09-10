package io.github.j12h36h.dai.comiclife.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ComicLifeLife {
    public int number;
    public String context = "";
    public String playerName = "Player";
    public long startedAtEpochMs;
    public long endedAtEpochMs;
    public long observedTicks;
    public int startX;
    public int startY;
    public int startZ;
    public int farthestDistance;
    public String startBiome = "unknown";
    public String lastBiome = "unknown";
    public String lastDimension = "unknown";
    public String deathMessage = "";
    public String title = "";
    public boolean completed;
    public boolean nearDeathOpen;
    public boolean deepEventRecorded;
    public boolean heightEventRecorded;
    public boolean thunderEventRecorded;
    public int distanceBand;
    public Set<String> seenBiomes = new LinkedHashSet<>();
    public Set<String> seenDimensions = new LinkedHashSet<>();
    public Set<String> milestones = new LinkedHashSet<>();
    public List<ComicLifeEvent> events = new ArrayList<>();
    public List<ComicLifePage> pages = new ArrayList<>();

    public void normalize() {
        if (context == null) context = "";
        if (playerName == null || playerName.isBlank()) playerName = "Player";
        if (startBiome == null || startBiome.isBlank()) startBiome = "unknown";
        if (lastBiome == null || lastBiome.isBlank()) lastBiome = "unknown";
        if (lastDimension == null || lastDimension.isBlank()) lastDimension = "unknown";
        if (deathMessage == null) deathMessage = "";
        if (title == null) title = "";
        if (seenBiomes == null) seenBiomes = new LinkedHashSet<>();
        if (seenDimensions == null) seenDimensions = new LinkedHashSet<>();
        if (milestones == null) milestones = new LinkedHashSet<>();
        if (events == null) events = new ArrayList<>();
        if (pages == null) pages = new ArrayList<>();
        events.removeIf(v -> v == null);
        pages.removeIf(v -> v == null);
        for (ComicLifeEvent event : events) event.normalize();
        for (ComicLifePage page : pages) page.normalize();
    }

    public long durationMs() {
        long end = completed && endedAtEpochMs > 0 ? endedAtEpochMs : System.currentTimeMillis();
        return Math.max(0L, end - startedAtEpochMs);
    }
}
