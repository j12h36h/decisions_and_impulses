package io.github.j12h36h.dai.comiclife.model;

public final class ComicLifePanel {
    public String eventType = "event";
    public String caption = "";
    public String narration = "";
    public int x;
    public int y;
    public int z;
    public float health;
    public float maxHealth = 20.0F;
    public String biome = "unknown";
    public String dimension = "unknown";
    public String weather = "clear";
    public String timeOfDay = "day";
    public String mainHand = "minecraft:air";
    public int importance;

    public void normalize() {
        if (eventType == null || eventType.isBlank()) eventType = "event";
        if (caption == null) caption = "";
        if (narration == null) narration = "";
        if (biome == null || biome.isBlank()) biome = "unknown";
        if (dimension == null || dimension.isBlank()) dimension = "unknown";
        if (weather == null || weather.isBlank()) weather = "clear";
        if (timeOfDay == null || timeOfDay.isBlank()) timeOfDay = "day";
        if (mainHand == null || mainHand.isBlank()) mainHand = "minecraft:air";
        importance = Math.max(0, Math.min(100, importance));
        maxHealth = Math.max(1.0F, maxHealth);
    }
}
