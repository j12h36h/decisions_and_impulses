package io.github.j12h36h.dai.logics.core;

import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * DAI player/creator framework preferences.
 *
 * Game rules remain data-driven. These settings control framework behavior,
 * performance ceilings, presentation convenience, and optional feature
 * modules. Optional modules default to enabled for backwards compatibility.
 */
public final class DAI_Config {

    public static final int CURRENT_CONFIG_VERSION = 5;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<Integer> CONFIG_VERSION =
            BUILDER.comment("DO NOT EDIT - Configuration file version")
                    .define("configVersion", CURRENT_CONFIG_VERSION);

    /* Existing keys keep their original paths. */
    public static final ModConfigSpec.BooleanValue TOGGLE_KEYBINDS =
            BUILDER.comment("Disable vanilla keybinds while DAI owns input")
                    .define("toggleKeybinds", false);

    public static final ModConfigSpec.BooleanValue DEBUGGING =
            BUILDER.comment(
                            "Enable DAI debugging and diagnostic telemetry. "
                                    + "When disabled, DAI debug messages, human trace output, "
                                    + "and logs/DAI_Log telemetry files are not produced."
                    )
                    .define("debugging", false);

    public static final ModConfigSpec.EnumValue<DAI_Position> SYSTEM_MENU_POSITION =
            BUILDER.comment("System menu position")
                    .defineEnum("systemMenuPosition", DAI_Position.BOT_LEFT);

    public static final ModConfigSpec.EnumValue<DAI_Position> ACTION_MENU_POSITION =
            BUILDER.comment("Action menu position")
                    .defineEnum("actionMenuPosition", DAI_Position.BOT_RIGHT);

    public static final ModConfigSpec.BooleanValue AUTOMATION_ENABLED =
            BUILDER.comment(
                            "Allow DAI autonomous gameplay modes. Turning this off does not disable normal datapack-authored game logic."
                    )
                    .define("automationEnabled", true);

    public static final ModConfigSpec.BooleanValue AUTOMATION_MOVEMENT =
            BUILDER.comment("Allow active DAI automation to move, look, pathfind, jump, swim, or fly for the player")
                    .define("automationMovement", true);

    public static final ModConfigSpec.BooleanValue AUTOMATION_COMBAT =
            BUILDER.comment("Allow active DAI automation to perform player combat actions")
                    .define("automationCombat", true);

    public static final ModConfigSpec.BooleanValue AUTOMATION_WORLD_EDITING =
            BUILDER.comment("Allow active DAI automation to mine, place, scaffold, or otherwise edit world blocks")
                    .define("automationWorldEditing", true);

    public static final ModConfigSpec.IntValue MAX_ACTIONS_PER_SECOND =
            BUILDER.comment(
                            "Maximum number of NEW semantic DAI actions started per second. Controllers already in progress still tick smoothly. "
                                    + "DAI may throttle below this limit when heap pressure is high."
                    )
                    .defineInRange("maxActionsPerSecond", 10, 1, 20);

    public static final ModConfigSpec.IntValue MAX_ACTION_QUEUE_SIZE =
            BUILDER.comment(
                            "Maximum queued DAI actions. Lower values bound memory/churn; higher values permit larger authored sequences."
                    )
                    .defineInRange("maxActionQueueSize", 128, 16, 2048);

    public static final ModConfigSpec.BooleanValue AUTO_ENABLE_ADDONS =
            BUILDER.comment(
                            "Automatically include every discovered DAI ADDON datapack in DAI experiences and ordinary worlds. "
                                    + "Existing world datapack selections are preserved; disabling this stops automatic addon layering."
                    )
                    .define("autoEnableAddons", true);

    public static final ModConfigSpec.BooleanValue AUTO_ENABLE_MANAGED_RESOURCE_PACKS =
            BUILDER.comment(
                            "Automatically register, enable, and persist resource packs installed through DAI's managed pack system."
                    )
                    .define("autoEnableManagedResourcePacks", true);

    public static final ModConfigSpec.BooleanValue CUSTOM_TITLE_SCREENS =
            BUILDER.comment("Allow datapacks/experiences to replace Minecraft's title screen with a DAI-authored title screen")
                    .define("customTitleScreens", true);

    public static final ModConfigSpec.DoubleValue OVERLAY_OPACITY =
            BUILDER.comment(
                            "Global opacity multiplier for DAI HUD/overlay sprites. A floor is kept so creator-authored interfaces remain usable."
                    )
                    .defineInRange("overlayOpacity", 1.0D, 0.25D, 1.0D);

    /*
     * Optional feature modules.
     *
     * Core config/action/network plumbing is intentionally not toggleable: it
     * is the minimal spine needed to load DAI data safely. Everything below
     * can be disabled when a project does not use it. Runtime modules stop
     * ticking immediately after config reload. Registry/startup modules are
     * read from the on-disk common config before normal NeoForge config load,
     * allowing them to avoid bootstrap allocation entirely; re-enabling those
     * modules requires a restart.
     */
    public static final ModConfigSpec.BooleanValue MODULE_AUTOMATION;
    public static final ModConfigSpec.BooleanValue MODULE_NAVIGATION;
    public static final ModConfigSpec.BooleanValue MODULE_COMBAT;
    public static final ModConfigSpec.BooleanValue MODULE_WORLD_EDITING;
    public static final ModConfigSpec.BooleanValue MODULE_INTERACTION;
    public static final ModConfigSpec.BooleanValue MODULE_INVENTORY;
    public static final ModConfigSpec.BooleanValue MODULE_CREATIVE;
    public static final ModConfigSpec.BooleanValue MODULE_OVERLAYS;
    public static final ModConfigSpec.BooleanValue MODULE_DATA_SCREENS;
    public static final ModConfigSpec.BooleanValue MODULE_ANIMATIONS;
    public static final ModConfigSpec.BooleanValue MODULE_CINEMATICS;
    public static final ModConfigSpec.BooleanValue MODULE_EXPERIENCE;
    public static final ModConfigSpec.BooleanValue MODULE_LEARNING;
    public static final ModConfigSpec.BooleanValue MODULE_CUSTOMIZATION;
    public static final ModConfigSpec.BooleanValue MODULE_PHYSICS;
    public static final ModConfigSpec.BooleanValue MODULE_CONTENT;
    public static final ModConfigSpec.BooleanValue MODULE_ENTITIES;
    public static final ModConfigSpec.BooleanValue MODULE_BLOCKS;
    public static final ModConfigSpec.BooleanValue MODULE_ITEMS;
    public static final ModConfigSpec.BooleanValue MODULE_VEHICLES;
    public static final ModConfigSpec.BooleanValue MODULE_PROJECTILES;
    public static final ModConfigSpec.BooleanValue MODULE_EFFECTS;
    public static final ModConfigSpec.BooleanValue MODULE_AUDIO;
    public static final ModConfigSpec.BooleanValue MODULE_FLUIDS;
    public static final ModConfigSpec.BooleanValue MODULE_INTERACTIVE;
    public static final ModConfigSpec.BooleanValue MODULE_PORTALS;
    public static final ModConfigSpec.BooleanValue MODULE_WORLDGEN;
    public static final ModConfigSpec.BooleanValue MODULE_REACTIONS;
    public static final ModConfigSpec.BooleanValue MODULE_STATE;
    public static final ModConfigSpec.BooleanValue MODULE_CREATOR;
    public static final ModConfigSpec.BooleanValue MODULE_MANAGED_PACKS;
    public static final ModConfigSpec.BooleanValue MODULE_TITLE_BRANDING;
    public static final ModConfigSpec.BooleanValue MODULE_PARTICLES;
    public static final ModConfigSpec.BooleanValue MODULE_SCREEN_OVERRIDES;
    public static final ModConfigSpec.BooleanValue MODULE_SCENE_ENVIRONMENTS;
    public static final ModConfigSpec.BooleanValue MODULE_STORY_ARCHIVES;
    public static final ModConfigSpec.BooleanValue MODULE_STORY_VIEWER;
    public static final ModConfigSpec.BooleanValue MODULE_INPUT_PROFILES;

    static {
        BUILDER.push("modules");
        MODULE_AUTOMATION = module("automation", "High-level autonomous gameplay modes and watchdogs");
        MODULE_NAVIGATION = module("navigation", "Pathfinding, exploration, approach, movement and navigation controllers");
        MODULE_COMBAT = module("combat", "DAI combat controllers, directional combat and damage presentation");
        MODULE_WORLD_EDITING = module("world_editing", "Mining, building, placement, scaffolding and world-editing controllers");
        MODULE_INTERACTION = module("interaction", "Use/interact controllers and interactive-content runtime");
        MODULE_INVENTORY = module("inventory", "Inventory, item-selection, equipment, container and crafting helpers");
        MODULE_CREATIVE = module("creative", "Creative inventory, creative flight and blueprint-building helpers");
        MODULE_OVERLAYS = module("overlays", "DAI HUD overlays and overlay rendering");
        MODULE_DATA_SCREENS = module("data_screens", "JSON-authored DAI screen definitions and data-screen runtime");
        MODULE_ANIMATIONS = module("animations", "DAI animation runtime");
        MODULE_CINEMATICS = module("cinematics", "ERAS/JSON cinematic playback, camera and cinematic HUD runtime");
        MODULE_EXPERIENCE = module("experience", "DAI experience discovery/launch/runtime integration");
        MODULE_LEARNING = module("learning", "Persistent learning/companion systems");
        MODULE_CUSTOMIZATION = module("customization", "Game customization runtime and presentation overrides");
        MODULE_PHYSICS = module("physics", "DAI client/server physics and physics rendering");
        MODULE_CONTENT = module("content", "Generic DAI native-content components and client content runtime");
        MODULE_ENTITIES = module("entities", "DAI custom entity registration, server runtime and client rendering");
        MODULE_BLOCKS = module("blocks", "DAI block runtime");
        MODULE_ITEMS = module("items", "DAI item runtime");
        MODULE_VEHICLES = module("vehicles", "DAI vehicle runtime and vehicle input bridge");
        MODULE_PROJECTILES = module("projectiles", "DAI projectile runtime");
        MODULE_EFFECTS = module("effects", "DAI effects and potion-like runtime");
        MODULE_AUDIO = module("audio", "DAI audio runtime");
        MODULE_FLUIDS = module("fluids", "DAI fluid runtime");
        MODULE_INTERACTIVE = module("interactive", "DAI interactive-object runtime");
        MODULE_PORTALS = module("portals", "DAI portal runtime");
        MODULE_WORLDGEN = module("worldgen", "DAI generated-world datapacks, worldgen and natural generation runtime");
        MODULE_REACTIONS = module("reactions", "DAI reaction-event registry and input reaction bridge");
        MODULE_STATE = module("state", "Server-authoritative persistent DAI scoped state runtime");
        MODULE_CREATOR = module("creator", "In-game DAI/Automation creator server runtime");
        MODULE_MANAGED_PACKS = module("managed_packs", "Global datapack/resource-pack management helpers");
        MODULE_TITLE_BRANDING = module("title_branding", "DAI title-screen controller and client branding runtime");
        MODULE_PARTICLES = module("particles", "DAI custom particle client bootstrap and particle presentation");
        MODULE_SCREEN_OVERRIDES = module("screen_overrides", "Pack-defined replacement and skinning of arbitrary screens");
        MODULE_SCENE_ENVIRONMENTS = module("scene_environments", "Pack/resource-defined animated scene backgrounds and scene-view widgets");
        MODULE_STORY_ARCHIVES = module("story_archives", "Pack-defined session/event archives, significance rules and compilation");
        MODULE_STORY_VIEWER = module("story_viewer", "Generic pack-styled archive/library/page viewer");
        MODULE_INPUT_PROFILES = module("input_profiles", "Pack-defined physical input gestures, action bindings and first-person poses");
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static final Map<String, Boolean> EARLY_MODULES = loadEarlyModuleSettings();

    public static boolean isDebuggingEnabled() { return bool(DEBUGGING, false); }
    public static boolean automationEnabled() { return bool(AUTOMATION_ENABLED, true); }
    public static boolean automationMovement() { return bool(AUTOMATION_MOVEMENT, true); }
    public static boolean automationCombat() { return bool(AUTOMATION_COMBAT, true); }
    public static boolean automationWorldEditing() { return bool(AUTOMATION_WORLD_EDITING, true); }
    public static int maxActionsPerSecond() { return integer(MAX_ACTIONS_PER_SECOND, 10); }
    public static int maxActionQueueSize() { return integer(MAX_ACTION_QUEUE_SIZE, 128); }
    public static boolean autoEnableAddons() { return bool(AUTO_ENABLE_ADDONS, true); }
    public static boolean autoEnableManagedResourcePacks() { return bool(AUTO_ENABLE_MANAGED_RESOURCE_PACKS, true); }
    public static boolean customTitleScreens() { return bool(CUSTOM_TITLE_SCREENS, true); }

    public static double overlayOpacity() {
        try { return OVERLAY_OPACITY.get(); }
        catch (IllegalStateException exception) { return 1.0D; }
    }

    public static boolean featureModuleEnabled(String moduleId) {
        String id = normalizeModule(moduleId);
        return switch (id) {
            case "automation" -> module(MODULE_AUTOMATION, id);
            case "navigation" -> module(MODULE_NAVIGATION, id);
            case "combat" -> module(MODULE_COMBAT, id);
            case "world_editing" -> module(MODULE_WORLD_EDITING, id);
            case "interaction" -> module(MODULE_INTERACTION, id);
            case "inventory" -> module(MODULE_INVENTORY, id);
            case "creative" -> module(MODULE_CREATIVE, id);
            case "overlays" -> module(MODULE_OVERLAYS, id);
            case "data_screens" -> module(MODULE_DATA_SCREENS, id);
            case "animations" -> module(MODULE_ANIMATIONS, id);
            case "cinematics" -> module(MODULE_CINEMATICS, id);
            case "experience" -> module(MODULE_EXPERIENCE, id);
            case "learning" -> module(MODULE_LEARNING, id);
            case "customization" -> module(MODULE_CUSTOMIZATION, id);
            case "physics" -> module(MODULE_PHYSICS, id);
            case "content" -> module(MODULE_CONTENT, id);
            case "entities" -> module(MODULE_ENTITIES, id);
            case "blocks" -> module(MODULE_BLOCKS, id);
            case "items" -> module(MODULE_ITEMS, id);
            case "vehicles" -> module(MODULE_VEHICLES, id);
            case "projectiles" -> module(MODULE_PROJECTILES, id);
            case "effects" -> module(MODULE_EFFECTS, id);
            case "audio" -> module(MODULE_AUDIO, id);
            case "fluids" -> module(MODULE_FLUIDS, id);
            case "interactive" -> module(MODULE_INTERACTIVE, id);
            case "portals" -> module(MODULE_PORTALS, id);
            case "worldgen" -> module(MODULE_WORLDGEN, id);
            case "reactions" -> module(MODULE_REACTIONS, id);
            case "state" -> module(MODULE_STATE, id);
            case "creator" -> module(MODULE_CREATOR, id);
            case "managed_packs" -> module(MODULE_MANAGED_PACKS, id);
            case "title_branding" -> module(MODULE_TITLE_BRANDING, id);
            case "particles" -> module(MODULE_PARTICLES, id);
            case "screen_overrides" -> module(MODULE_SCREEN_OVERRIDES, id);
            case "scene_environments" -> module(MODULE_SCENE_ENVIRONMENTS, id);
            case "story_archives" -> module(MODULE_STORY_ARCHIVES, id);
            case "story_viewer" -> module(MODULE_STORY_VIEWER, id);
            case "input_profiles" -> module(MODULE_INPUT_PROFILES, id);
            default -> true;
        };
    }

    public static Map<String, Boolean> featureModuleSnapshot() {
        LinkedHashMap<String, Boolean> out = new LinkedHashMap<>();
        for (String id : new String[]{
                "automation", "navigation", "combat", "world_editing", "interaction", "inventory", "creative",
                "overlays", "data_screens", "animations", "cinematics", "experience", "learning", "customization",
                "physics", "content", "entities", "blocks", "items", "vehicles", "projectiles", "effects", "audio",
                "fluids", "interactive", "portals", "worldgen", "reactions", "state", "creator", "managed_packs",
                "title_branding", "particles", "screen_overrides", "scene_environments", "story_archives", "story_viewer", "input_profiles"
        }) out.put(id, featureModuleEnabled(id));
        return Map.copyOf(out);
    }

    private static ModConfigSpec.BooleanValue module(String key, String description) {
        return BUILDER.comment(description + ". Disable when unused to skip its runtime work and reduce memory/tick overhead.")
                .define(key, true);
    }

    private static boolean module(ModConfigSpec.BooleanValue value, String key) {
        try { return value.get(); }
        catch (IllegalStateException exception) { return EARLY_MODULES.getOrDefault(normalizeModule(key), true); }
    }

    private static boolean bool(ModConfigSpec.BooleanValue value, boolean fallback) {
        try { return value.get(); }
        catch (IllegalStateException exception) { return fallback; }
    }

    private static int integer(ModConfigSpec.IntValue value, int fallback) {
        try { return value.get(); }
        catch (IllegalStateException exception) { return fallback; }
    }

    private static Map<String, Boolean> loadEarlyModuleSettings() {
        LinkedHashMap<String, Boolean> output = new LinkedHashMap<>();
        try {
            Path file = FMLPaths.CONFIGDIR.get().resolve("decisions_and_impulses-common.toml");
            if (!Files.isRegularFile(file)) return Map.of();
            boolean modulesSection = false;
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw == null ? "" : raw.trim();
                if (line.isBlank() || line.startsWith("#")) continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    modulesSection = "modules".equalsIgnoreCase(line.substring(1, line.length() - 1).trim());
                    continue;
                }
                String candidate = line;
                if (!modulesSection) {
                    if (!candidate.toLowerCase(Locale.ROOT).startsWith("modules.")) continue;
                    candidate = candidate.substring("modules.".length());
                }
                int equals = candidate.indexOf('=');
                if (equals <= 0) continue;
                String key = normalizeModule(candidate.substring(0, equals));
                String value = candidate.substring(equals + 1);
                int comment = value.indexOf('#');
                if (comment >= 0) value = value.substring(0, comment);
                value = value.trim().toLowerCase(Locale.ROOT);
                if (value.equals("true") || value.equals("false")) output.put(key, Boolean.parseBoolean(value));
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug("<DAI>: Could not read early feature-module settings; using enabled defaults.", exception);
        }
        return Map.copyOf(output);
    }

    private static String normalizeModule(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace('.', '_').replace(' ', '_');
    }

    private DAI_Config() {}
}
