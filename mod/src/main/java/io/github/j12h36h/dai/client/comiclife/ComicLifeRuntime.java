package io.github.j12h36h.dai.client.comiclife;

import io.github.j12h36h.dai.client.comiclife.GameProbe.Snapshot;
import io.github.j12h36h.dai.client.comiclife.screen.ComicLifeLibraryScreen;
import io.github.j12h36h.dai.comiclife.model.ComicLifeArchive;
import io.github.j12h36h.dai.comiclife.model.ComicLifeEvent;
import io.github.j12h36h.dai.comiclife.model.ComicLifeLife;
import io.github.j12h36h.dai.comiclife.storage.ComicLifeStorage;
import io.github.j12h36h.dai.comiclife.story.ComicCompiler;
import io.github.j12h36h.dai.comiclife.util.Reflect;
import io.github.j12h36h.dai.client.menus.DAI_ScreenManager;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionRegistry;
import io.github.j12h36h.dai.logics.action.DAI_ActionLibrary;
import io.github.j12h36h.dai.logics.action.DAI_ActionArguments;
import io.github.j12h36h.dai.logics.core.DAI_Config;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ComicLifeRuntime {
    private static final Map<String, Milestone> MILESTONES = milestones();
    private static ComicLifeArchive archive = new ComicLifeArchive();
    private static ComicLifeLife currentLife;
    private static Snapshot previous;
    private static String loadedUuid = "";
    private static String currentContext = "";
    private static long ticks;
    private static long lastSaveTick;
    private static boolean dirty;
    private static boolean shortcutDown;
    private static boolean firstInstructionShown;
    private static boolean active;

    private ComicLifeRuntime() {}

    public static void tick() {
        if (!available()) { if (active) shutdown(); return; }
        active = true;
        ticks++;
        if (ticks % 100L == 1L) ensureDaiActions();
        Minecraft minecraft = Minecraft.getInstance();
        pollShortcut(minecraft);
        if (ticks % 5L != 0L) return;

        Snapshot snapshot = GameProbe.capture(minecraft);
        if (snapshot == null) {
            flush();
            previous = null;
            currentLife = null;
            currentContext = "";
            return;
        }

        ensureArchive(snapshot.uuid());
        if (!snapshot.context().equals(currentContext)) {
            flush();
            currentContext = snapshot.context();
            currentLife = archive.activeByContext.get(currentContext);
            previous = null;
        }

        if (snapshot.dead()) {
            if (currentLife != null && !currentLife.completed) finalizeLife(snapshot);
            previous = snapshot;
            return;
        }

        if (currentLife == null || currentLife.completed) startLife(snapshot);
        observe(snapshot);
        previous = snapshot;

        if (dirty && ticks - lastSaveTick >= 200L) flush();
    }

    public static boolean available() {
        if (!DAI_Config.featureModuleEnabled("comic_life")) return false;
        return DAI_ActionLibrary.contains(Identifier.fromNamespaceAndPath("comiclife", "open"));
    }

    public static void ensureDaiActions() {
        if (!available()) return;
        if (!DAI_ActionRegistry.contains("comiclife_open")) {
            DAI_ActionRegistry.register("comiclife_open", ignored -> openLibrary());
        }
        if (!DAI_ActionRegistry.contains("comiclife_event")) {
            DAI_ActionRegistry.register("comiclife_event", action -> {
                DAI_ActionArguments args = action.arguments();
                String event = args.string("event", "custom_event");
                String caption = args.string("caption", ComicCompiler.friendly(event));
                String narration = args.string("narration", "");
                int importance = Math.max(0, Math.min(100, args.integer("importance", 50)));
                recordCustom(event, caption, narration, importance);
            });
        }
    }

    public static void shutdown() {
        if (!active && loadedUuid.isBlank() && currentLife == null && !dirty) return;
        flush();
        previous = null;
        currentLife = null;
        currentContext = "";
        shortcutDown = false;
        archive = new ComicLifeArchive();
        loadedUuid = "";
        dirty = false;
        active = false;
    }

    public static void openLibrary() {
        if (!available()) return;
        Snapshot snapshot = GameProbe.capture(Minecraft.getInstance());
        if (snapshot != null) ensureArchive(snapshot.uuid());
        DAI_ScreenManager.open(new ComicLifeLibraryScreen());
    }

    public static void recordCustom(String type, String caption, String narration, int importance) {
        if (!available()) return;
        Snapshot snapshot = GameProbe.capture(Minecraft.getInstance());
        if (snapshot == null || snapshot.dead()) return;
        ensureArchive(snapshot.uuid());
        if (!snapshot.context().equals(currentContext)) {
            currentContext = snapshot.context();
            currentLife = archive.activeByContext.get(currentContext);
            previous = null;
        }
        if (currentLife == null || currentLife.completed) startLife(snapshot);
        record(snapshot, normalizeType(type), caption, narration, importance, snapshot.mainHand());
        flush();
    }

    public static ComicLifeArchive archive() {
        archive.normalize();
        return archive;
    }

    public static ComicLifeLife currentLife() { return currentLife; }
    public static int completedCount() { return archive == null || archive.completed == null ? 0 : archive.completed.size(); }

    private static void ensureArchive(String uuid) {
        if (uuid == null || uuid.isBlank() || uuid.equals(loadedUuid)) return;
        flush();
        loadedUuid = uuid;
        archive = ComicLifeStorage.load(uuid);
        archive.normalize();
        currentLife = null;
        currentContext = "";
        previous = null;
    }

    private static void startLife(Snapshot snapshot) {
        ComicLifeLife existing = archive.activeByContext.get(snapshot.context());
        if (existing != null && !existing.completed) {
            existing.normalize();
            currentLife = existing;
            currentContext = snapshot.context();
            return;
        }

        ComicLifeLife life = new ComicLifeLife();
        life.number = archive.nextLifeNumber();
        life.context = snapshot.context();
        life.playerName = snapshot.playerName();
        life.startedAtEpochMs = System.currentTimeMillis();
        life.startX = round(snapshot.x());
        life.startY = round(snapshot.y());
        life.startZ = round(snapshot.z());
        life.startBiome = snapshot.biome();
        life.lastBiome = snapshot.biome();
        life.lastDimension = snapshot.dimension();
        life.seenBiomes.add(snapshot.biome());
        life.seenDimensions.add(snapshot.dimension());
        archive.activeByContext.put(snapshot.context(), life);
        currentLife = life;
        currentContext = snapshot.context();
        record(snapshot, "life_start", "Woke up in " + ComicCompiler.friendly(snapshot.biome()), "", 100, snapshot.mainHand());
        dirty = true;
        flush();
        if (!firstInstructionShown) {
            firstInstructionShown = true;
            message("[ComicLife] Life " + ComicCompiler.roman(life.number) + " started. Ctrl+L opens your Life Library.");
        }
    }

    private static void observe(Snapshot snapshot) {
        if (currentLife == null) return;
        currentLife.observedTicks += 5L;
        currentLife.lastBiome = snapshot.biome();
        currentLife.lastDimension = snapshot.dimension();

        if (!currentLife.seenBiomes.contains(snapshot.biome())) {
            currentLife.seenBiomes.add(snapshot.biome());
            record(snapshot, "biome_discovery", "Entered " + ComicCompiler.friendly(snapshot.biome()), "", 32, snapshot.mainHand());
        }

        if (!currentLife.seenDimensions.contains(snapshot.dimension())) {
            currentLife.seenDimensions.add(snapshot.dimension());
            record(snapshot, "dimension_travel", "Crossed into " + ComicCompiler.friendly(snapshot.dimension()), "", 72, snapshot.mainHand());
        }

        if (previous != null && previous.context().equals(snapshot.context())) {
            float damage = previous.health() - snapshot.health();
            if (damage >= 6.0F) {
                record(snapshot, "major_damage", "Took " + trim(damage) + " health points of sudden damage", "", 46, snapshot.mainHand());
            }
        }

        if (snapshot.health() <= 4.0F && !currentLife.nearDeathOpen) {
            currentLife.nearDeathOpen = true;
            record(snapshot, "near_death", "Dropped to " + trim(snapshot.health()) + " health", "", 82, snapshot.mainHand());
        } else if (currentLife.nearDeathOpen && snapshot.health() >= Math.min(12.0F, snapshot.maxHealth() * 0.6F)) {
            currentLife.nearDeathOpen = false;
            record(snapshot, "recovery", "Recovered from the brink", "", 38, snapshot.mainHand());
        }

        int dx = round(snapshot.x()) - currentLife.startX;
        int dz = round(snapshot.z()) - currentLife.startZ;
        int distance = (int)Math.round(Math.sqrt((double)dx * dx + (double)dz * dz));
        currentLife.farthestDistance = Math.max(currentLife.farthestDistance, distance);
        int band = distanceBand(distance);
        if (band > currentLife.distanceBand) {
            currentLife.distanceBand = band;
            record(snapshot, "long_journey", "Reached " + band + " blocks from the first panel", "", Math.min(75, 28 + band / 250), snapshot.mainHand());
        }

        if (snapshot.y() <= -32.0D && !currentLife.deepEventRecorded) {
            currentLife.deepEventRecorded = true;
            record(snapshot, "deep_descent", "Descended beneath Y -32", "", 48, snapshot.mainHand());
        }
        if (snapshot.y() >= 180.0D && !currentLife.heightEventRecorded) {
            currentLife.heightEventRecorded = true;
            record(snapshot, "high_climb", "Climbed above Y 180", "", 43, snapshot.mainHand());
        }
        if (snapshot.weather().equals("thunder") && !currentLife.thunderEventRecorded) {
            currentLife.thunderEventRecorded = true;
            record(snapshot, "thunder", "A thunderstorm overtook the journey", "", 29, snapshot.mainHand());
        }

        Set<String> inventory = snapshot.inventoryIds();
        for (Map.Entry<String, Milestone> entry : MILESTONES.entrySet()) {
            if (inventory.contains(entry.getKey()) && currentLife.milestones.add(entry.getKey())) {
                Milestone milestone = entry.getValue();
                record(snapshot, "milestone", milestone.caption, milestone.narration, milestone.importance, entry.getKey());
            }
        }
        dirty = true;
    }

    private static void finalizeLife(Snapshot snapshot) {
        if (currentLife == null || currentLife.completed) return;
        currentLife.deathMessage = snapshot.deathMessage() == null || snapshot.deathMessage().isBlank()
                ? currentLife.playerName + " died"
                : snapshot.deathMessage();
        record(snapshot, "death", currentLife.deathMessage, "", 100, snapshot.mainHand());
        currentLife.completed = true;
        currentLife.endedAtEpochMs = System.currentTimeMillis();
        ComicCompiler.compile(currentLife);
        archive.activeByContext.remove(currentContext);
        archive.completed.add(currentLife);
        while (archive.completed.size() > 120) archive.completed.removeFirst();
        dirty = true;
        flush();
        message("[ComicLife] LIFE " + ComicCompiler.roman(currentLife.number) + " HAS BEEN PUBLISHED — “" + currentLife.title + "”");
        currentLife = null;
    }

    private static void record(Snapshot snapshot, String type, String caption, String narration, int importance, String mainHand) {
        if (currentLife == null || snapshot == null) return;
        ComicLifeEvent event = new ComicLifeEvent();
        event.sequence = currentLife.events.isEmpty() ? 1L : currentLife.events.getLast().sequence + 1L;
        event.epochMs = System.currentTimeMillis();
        event.type = normalizeType(type);
        event.caption = caption == null ? "" : caption;
        event.narration = narration == null ? "" : narration;
        event.importance = Math.max(0, Math.min(100, importance));
        event.x = round(snapshot.x());
        event.y = round(snapshot.y());
        event.z = round(snapshot.z());
        event.health = snapshot.health();
        event.maxHealth = snapshot.maxHealth();
        event.biome = snapshot.biome();
        event.dimension = snapshot.dimension();
        event.weather = snapshot.weather();
        event.timeOfDay = snapshot.timeOfDay();
        event.mainHand = mainHand == null || mainHand.isBlank() ? "minecraft:air" : mainHand;
        event.normalize();
        currentLife.events.add(event);
        dirty = true;
    }

    private static void flush() {
        if (!dirty || loadedUuid == null || loadedUuid.isBlank()) return;
        ComicLifeStorage.save(loadedUuid, archive);
        dirty = false;
        lastSaveTick = ticks;
    }

    private static void pollShortcut(Minecraft minecraft) {
        if (minecraft == null) return;
        Object window = Reflect.invoke(Reflect.invoke(minecraft, "getWindow"), "getWindow");
        long handle = window instanceof Number n ? n.longValue() : 0L;
        if (handle == 0L) return;
        try {
            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
            boolean ctrl = Reflect.number(Reflect.invoke(glfw, "glfwGetKey", handle, 341), 0) == 1
                    || Reflect.number(Reflect.invoke(glfw, "glfwGetKey", handle, 345), 0) == 1;
            boolean l = Reflect.number(Reflect.invoke(glfw, "glfwGetKey", handle, 76), 0) == 1;
            boolean down = ctrl && l;
            Object gui = Reflect.field(minecraft, "gui");
            Object screen = Reflect.invoke(gui, "screen");
            if (down && !shortcutDown && screen == null) openLibrary();
            shortcutDown = down;
        } catch (Throwable ignored) {
            shortcutDown = false;
        }
    }

    private static void message(String text) {
        Object player = Reflect.field(Minecraft.getInstance(), "player");
        if (player == null) return;
        Object component = Component.literal(text == null ? "" : text);
        Reflect.invoke(player, "displayClientMessage", component, false);
    }

    private static int distanceBand(int distance) {
        if (distance >= 10000) return 10000;
        if (distance >= 5000) return 5000;
        if (distance >= 2500) return 2500;
        if (distance >= 1000) return 1000;
        if (distance >= 500) return 500;
        return 0;
    }

    private static int round(double value) { return (int)Math.round(value); }
    private static String trim(float value) {
        if (Math.rint(value) == value) return Integer.toString((int)value);
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String normalizeType(String type) {
        String value = type == null ? "custom_event" : type.trim().toLowerCase(Locale.ROOT);
        value = value.replaceAll("[^a-z0-9_./:-]+", "_");
        return value.isBlank() ? "custom_event" : value;
    }

    private static Map<String, Milestone> milestones() {
        Map<String, Milestone> map = new LinkedHashMap<>();
        map.put("minecraft:diamond", new Milestone("Found diamonds", "The inventory gained something worth changing plans for.", 62));
        map.put("minecraft:diamond_block", new Milestone("Held a block of diamond", "What began as a rare discovery had become a statement.", 74));
        map.put("minecraft:ancient_debris", new Milestone("Found ancient debris", "Deep in the Nether, the journey uncovered something older than the road to it.", 68));
        map.put("minecraft:netherite_ingot", new Milestone("Forged netherite", "The gear—and the stakes—grew heavier.", 76));
        map.put("minecraft:netherite_block", new Milestone("Assembled a block of netherite", "A fortune of the Nether fit into a single block.", 90));
        map.put("minecraft:elytra", new Milestone("Obtained an elytra", "From this point on, distance stopped meaning quite the same thing.", 88));
        map.put("minecraft:totem_of_undying", new Milestone("Obtained a Totem of Undying", "For once, the inventory contained an argument against the final page.", 82));
        map.put("minecraft:enchanted_golden_apple", new Milestone("Found an enchanted golden apple", "A nearly mythical prize entered the story.", 78));
        map.put("minecraft:nether_star", new Milestone("Claimed a Nether Star", "A boss fight left behind a star small enough to carry.", 94));
        map.put("minecraft:dragon_egg", new Milestone("Claimed the Dragon Egg", "The End left behind proof that it had been conquered.", 100));
        return map;
    }

    private record Milestone(String caption, String narration, int importance) {}
}
