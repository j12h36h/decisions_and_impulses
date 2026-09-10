package io.github.j12h36h.dai.comiclife.story;

import io.github.j12h36h.dai.comiclife.model.ComicLifeEvent;
import io.github.j12h36h.dai.comiclife.model.ComicLifeLife;
import io.github.j12h36h.dai.comiclife.model.ComicLifePage;
import io.github.j12h36h.dai.comiclife.model.ComicLifePanel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ComicCompiler {
    private static final int MAX_STORY_EVENTS = 18;

    private ComicCompiler() {}

    public static void compile(ComicLifeLife life) {
        if (life == null) return;
        life.normalize();
        life.title = titleFor(life);
        life.pages = buildPages(life, false);
    }

    public static List<ComicLifePage> previewPages(ComicLifeLife life) {
        if (life == null) return List.of();
        life.normalize();
        if (life.title == null || life.title.isBlank()) life.title = titleFor(life);
        return buildPages(life, true);
    }

    private static List<ComicLifePage> buildPages(ComicLifeLife life, boolean preview) {
        List<ComicLifeEvent> selected = selectEvents(life);
        List<ComicLifePage> pages = new ArrayList<>();

        ComicLifePage cover = new ComicLifePage();
        cover.kind = "cover";
        cover.heading = "LIFE " + roman(Math.max(1, life.number));
        cover.subheading = life.title;
        pages.add(cover);

        for (int i = 0; i < selected.size(); i += 2) {
            ComicLifePage page = new ComicLifePage();
            page.kind = "story";
            page.heading = storyHeading(selected.get(i), i / 2);
            page.subheading = pageSubtitle(life, i / 2);
            for (int j = i; j < Math.min(i + 2, selected.size()); j++) {
                ComicLifeEvent event = selected.get(j);
                ComicLifePanel panel = new ComicLifePanel();
                panel.eventType = event.type;
                panel.caption = event.caption;
                panel.narration = event.narration == null || event.narration.isBlank()
                        ? narrationFor(event, life, j == 0)
                        : event.narration;
                panel.x = event.x;
                panel.y = event.y;
                panel.z = event.z;
                panel.health = event.health;
                panel.maxHealth = event.maxHealth;
                panel.biome = event.biome;
                panel.dimension = event.dimension;
                panel.weather = event.weather;
                panel.timeOfDay = event.timeOfDay;
                panel.mainHand = event.mainHand;
                panel.importance = event.importance;
                page.panels.add(panel);
            }
            pages.add(page);
        }

        ComicLifePage back = new ComicLifePage();
        back.kind = "back";
        back.heading = preview ? "LIFE IN PROGRESS" : "THE END OF LIFE " + roman(Math.max(1, life.number));
        back.subheading = preview
                ? "The ending has not been written yet."
                : (life.deathMessage == null || life.deathMessage.isBlank() ? "This life came to an end." : life.deathMessage);
        pages.add(back);
        return pages;
    }

    private static List<ComicLifeEvent> selectEvents(ComicLifeLife life) {
        if (life.events == null || life.events.isEmpty()) return List.of();
        List<ComicLifeEvent> all = new ArrayList<>(life.events);
        all.removeIf(e -> e == null);
        all.sort(Comparator.comparingLong(e -> e.sequence));
        if (all.size() <= MAX_STORY_EVENTS) return all;

        Set<Long> keepSeq = new HashSet<>();
        keepSeq.add(all.getFirst().sequence);
        keepSeq.add(all.getLast().sequence);
        List<ComicLifeEvent> ranked = new ArrayList<>(all);
        ranked.sort(Comparator.comparingInt((ComicLifeEvent e) -> e.importance).reversed().thenComparingLong(e -> e.sequence));
        for (ComicLifeEvent event : ranked) {
            if (keepSeq.size() >= MAX_STORY_EVENTS) break;
            if (event.importance >= 20 || keepSeq.size() < 8) keepSeq.add(event.sequence);
        }
        List<ComicLifeEvent> selected = new ArrayList<>();
        for (ComicLifeEvent event : all) if (keepSeq.contains(event.sequence)) selected.add(event);
        return selected;
    }

    private static String narrationFor(ComicLifeEvent event, ComicLifeLife life, boolean first) {
        String place = friendly(event.biome);
        return switch (safe(event.type)) {
            case "life_start" -> first
                    ? "The world opened in " + article(place) + place + ". " + foreshadow(life.deathMessage)
                    : "Another beginning took shape in " + article(place) + place + ".";
            case "biome_discovery" -> "Beyond the familiar ground waited " + article(place) + place + ".";
            case "dimension_travel" -> "The rules of the journey changed upon entering " + friendly(event.dimension) + ".";
            case "near_death" -> "For a few seconds, the margin between a story and an ending became very small.";
            case "recovery" -> "The danger passed, but it left a chapter behind.";
            case "major_damage" -> "A sudden blow changed the pace of the journey.";
            case "milestone" -> "A rare prize entered the inventory: " + friendly(event.mainHand) + ".";
            case "long_journey" -> "Home, if there was one, had become a distant idea.";
            case "deep_descent" -> "The surface was far above now, and every sound carried farther.";
            case "high_climb" -> "From this height, the route behind looked almost simple.";
            case "thunder" -> "Thunder rolled over the chapter and refused to stay in the background.";
            case "death" -> "And then the life reached the panel it could not turn past.";
            default -> event.caption == null || event.caption.isBlank()
                    ? "Something worth remembering happened here."
                    : event.caption;
        };
    }

    private static String titleFor(ComicLifeLife life) {
        String death = safe(life.deathMessage).toLowerCase(Locale.ROOT);
        if (!life.completed) return "A Story Still Being Written";
        if (death.contains("warden")) return "Something Was Listening";
        if (death.contains("creeper") || death.contains("blew up") || death.contains("explosion")) return "One Last Hiss";
        if (death.contains("fall") || death.contains("high place") || death.contains("ground too hard")) return "The Long Way Down";
        if (death.contains("lava") || death.contains("fire") || death.contains("burn")) return "Where the Heat Won";
        if (death.contains("drown") || death.contains("water")) return "No Air Between Panels";
        if (death.contains("void") || death.contains("out of the world")) return "Past the Bottom of the Page";
        if (death.contains("dragon")) return "At the Edge of the End";
        if (death.contains("zombie") || death.contains("skeleton") || death.contains("spider")) return "Night Had Other Plans";
        ComicLifeEvent best = life.events.stream().filter(e -> e != null).max(Comparator.comparingInt(e -> e.importance)).orElse(null);
        if (best != null && !safe(best.biome).equals("unknown")) return "A Life Through " + friendly(best.biome);
        return "The Story of Life " + roman(Math.max(1, life.number));
    }

    private static String foreshadow(String deathMessage) {
        String death = safe(deathMessage).toLowerCase(Locale.ROOT);
        if (death.contains("warden")) return "Far below, something was already listening.";
        if (death.contains("creeper") || death.contains("explosion")) return "Somewhere ahead, one quiet hiss would matter more than the rest.";
        if (death.contains("fall") || death.contains("ground too hard")) return "The ground would eventually have the final word.";
        if (death.contains("lava") || death.contains("burn") || death.contains("fire")) return "The story would end somewhere much hotter.";
        if (death.contains("drown")) return "Before the end, even a breath would become valuable.";
        if (death.contains("void")) return "There was, eventually, a place with no ground at all.";
        return "Nothing on the first page explained how the last one would end.";
    }

    private static String storyHeading(ComicLifeEvent event, int page) {
        return switch (safe(event.type)) {
            case "life_start" -> "A BEGINNING";
            case "death" -> "THE FINAL EVENT";
            case "near_death" -> "TOO CLOSE";
            case "dimension_travel" -> "A DIFFERENT WORLD";
            case "milestone" -> "FOUND";
            default -> "CHAPTER " + (page + 1);
        };
    }

    private static String pageSubtitle(ComicLifeLife life, int page) {
        if (page == 0) return friendly(life.startBiome);
        return formatDuration(life.durationMs()) + " observed";
    }

    public static String formatDuration(long ms) {
        long seconds = Math.max(0L, ms / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + secs + "s";
        return secs + "s";
    }

    public static String friendly(String id) {
        String value = safe(id);
        int colon = value.indexOf(':');
        if (colon >= 0 && colon < value.length() - 1) value = value.substring(colon + 1);
        value = value.replace('/', ' ').replace('_', ' ').replace('-', ' ').trim();
        if (value.isBlank() || value.equals("unknown")) return "an unknown place";
        StringBuilder out = new StringBuilder();
        for (String word : value.split("\\s+")) {
            if (word.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static String article(String friendly) {
        if (friendly == null || friendly.isBlank() || friendly.startsWith("an ")) return "";
        char c = Character.toLowerCase(friendly.charAt(0));
        return "aeiou".indexOf(c) >= 0 ? "an " : "a ";
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

    public static String roman(int number) {
        if (number <= 0) return Integer.toString(number);
        if (number > 3999) return Integer.toString(number);
        int[] values = {1000,900,500,400,100,90,50,40,10,9,5,4,1};
        String[] symbols = {"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};
        StringBuilder out = new StringBuilder();
        int n = number;
        for (int i = 0; i < values.length; i++) {
            while (n >= values[i]) { n -= values[i]; out.append(symbols[i]); }
        }
        return out.toString();
    }
}
