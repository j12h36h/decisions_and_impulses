package io.github.j12h36h.dai.experience;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** JSON-backed launch profile for a complete DAI-authored game experience. */
public record DAI_ExperienceDefinition(
        String id,
        boolean enabled,
        int priority,
        String saveId,
        String saveName,
        boolean createIfMissing,
        boolean loadIfExisting,
        boolean autoCreate,
        String worldgen,
        String onFirstJoin,
        String onJoin,
        Ui ui,
        Controls controls,
        AddonPolicy addons,
        PlayerPresentation playerPresentation,
        Branding branding
) {
    public DAI_ExperienceDefinition {
        id = normalize(id);
        saveId = safe(saveId, path(id).replace('_', ' '));
        saveName = safe(saveName, saveId);
        worldgen = normalize(worldgen);
        onFirstJoin = normalize(onFirstJoin);
        onJoin = normalize(onJoin);
        ui = ui == null ? Ui.DEFAULT : ui;
        controls = controls == null ? Controls.DEFAULT : controls;
        addons = addons == null ? AddonPolicy.DEFAULT : addons;
        playerPresentation = playerPresentation == null ? PlayerPresentation.DEFAULT : playerPresentation;
        branding = branding == null ? Branding.DEFAULT : branding;
    }

    public static DAI_ExperienceDefinition parse(String id, JsonObject root) {
        JsonObject ui = object(root, "ui");
        JsonObject controls = object(root, "controls");
        JsonObject addons = object(root, "addons");
        if (addons == null) addons = object(root, "addon_policy");
        JsonObject playerPresentation = object(root, "player_presentation");
        JsonObject branding = object(root, "branding");
        JsonObject earlyLoading = object(branding, "early_loading");
        JsonObject worldLoading = object(branding, "world_loading");
        return new DAI_ExperienceDefinition(
                id,
                bool(root, "enabled", true),
                integer(root, "priority", 0),
                string(root, "save_id", path(id).replace('_', ' ')),
                string(root, "save_name", ""),
                bool(root, "create_if_missing", true),
                bool(root, "load_if_existing", true),
                bool(root, "auto_create", true),
                string(root, "worldgen", ""),
                string(root, "on_first_join", ""),
                string(root, "on_join", ""),
                new Ui(
                        bool(ui, "auto_enable", true),
                        bool(ui, "grave_cursor_toggle", true),
                        bool(ui, "open_dai_menu_on_grave", false),
                        string(ui, "grave_open_action", ""),
                        string(ui, "grave_close_action", ""),
                        string(ui, "grave_anchor_overlay", ""),
                        string(ui, "grave_menu", ""),
                        string(ui, "grave_menu_open", ""),
                        string(ui, "pause_screen", ""),
                        string(ui, "inventory_screen", ""),
                        string(ui, "death_screen", ""),
                        string(ui, "hud_profile", "")
                ),
                new Controls(
                        bool(controls, "automation", true),
                        bool(controls, "automation_movement", true),
                        bool(controls, "automation_combat", true),
                        bool(controls, "automation_world_editing", true),
                        integer(controls, "max_actions_per_second", 0),
                        integer(controls, "max_action_queue_size", 0)
                ),
                new AddonPolicy(
                        bool(addons, "enabled", true),
                        stringSet(addons, "whitelist")
                ),
                new PlayerPresentation(
                        bool(playerPresentation, "enabled", playerPresentation != null),
                        string(playerPresentation, "default", string(playerPresentation, "profile", "")),
                        string(playerPresentation, "local", string(playerPresentation, "local_profile", "")),
                        bool(playerPresentation, "allow_player_selection", false),
                        stringMap(object(playerPresentation, "players")),
                        stringMap(object(playerPresentation, "teams"))
                ),
                new Branding(
                        string(branding, "window_title", ""),
                        string(branding, "loading_title", ""),
                        string(branding, "loading_subtitle", ""),
                        string(branding, "loading_background_texture", ""),
                        string(branding, "loading_logo", ""),
                        string(branding, "companion_id", ""),
                        color(branding, "loading_background", 0xFF101318),
                        color(branding, "loading_foreground", 0xFFFFFFFF),
                        color(branding, "loading_accent", 0xFF5EE1FF),
                        integer(branding, "loading_logo_size", 72),
                        integer(branding, "loading_progress_width", 280),
                        integer(branding, "loading_progress_height", 4),
                        bool(branding, "custom_loading_screen", branding != null),
                        bool(branding, "show_loading_progress", true),
                        bool(branding, "use_resource_pack_icon", branding != null),
                        new EarlyLoading(
                                bool(earlyLoading, "enabled", bool(branding, "custom_loading_screen", branding != null)),
                                string(earlyLoading, "background_texture", string(branding, "loading_background_texture", "")),
                                string(earlyLoading, "logo", string(branding, "loading_logo", "")),
                                bool(earlyLoading, "hide_mojang_logo", true),
                                bool(earlyLoading, "show_progress", bool(branding, "show_loading_progress", true)),
                                bool(earlyLoading, "show_startup_log", false),
                                bool(earlyLoading, "show_performance", false)
                        ),
                        new WorldLoading(
                                bool(worldLoading, "enabled", bool(branding, "custom_loading_screen", branding != null)),
                                string(worldLoading, "title", string(branding, "loading_title", "")),
                                string(worldLoading, "subtitle", string(branding, "loading_subtitle", "")),
                                string(worldLoading, "background_texture", string(branding, "loading_background_texture", "")),
                                string(worldLoading, "logo", string(branding, "loading_logo", "")),
                                color(worldLoading, "background", color(branding, "loading_background", 0xFF101318)),
                                color(worldLoading, "foreground", color(branding, "loading_foreground", 0xFFFFFFFF)),
                                color(worldLoading, "accent", color(branding, "loading_accent", 0xFF5EE1FF)),
                                integer(worldLoading, "logo_size", integer(branding, "loading_logo_size", 72)),
                                integer(worldLoading, "progress_width", integer(branding, "loading_progress_width", 280)),
                                integer(worldLoading, "progress_height", integer(branding, "loading_progress_height", 4)),
                                bool(worldLoading, "show_progress", bool(branding, "show_loading_progress", true)),
                                bool(worldLoading, "show_status_text", true),
                                bool(worldLoading, "include_transitions", true)
                        )
                )
        );
    }

    /**
     * Client presentation policy for a launched experience.
     *
     * grave_open_action / grave_close_action allow an experience to replace
     * DAI's normal grave-key menu with its own datapack-authored UI. The
     * optional grave_anchor_overlay identifies one persistent overlay that is
     * present while that UI is open, allowing DAI to remain synchronized even
     * when the experience closes itself through an overlay button.
     *
     * grave_menu / grave_menu_open provide the lighter-weight alternative used
     * by experiences that want the grave key to open a specific DAI-authored
     * menu directly instead of showing DAI's default root menu first.
     */
    public record Ui(
            boolean autoEnable,
            boolean graveCursorToggle,
            boolean openDaiMenuOnGrave,
            String graveOpenAction,
            String graveCloseAction,
            String graveAnchorOverlay,
            String graveMenu,
            String graveMenuOpen,
            String pauseScreen,
            String inventoryScreen,
            String deathScreen,
            String hudProfile
    ) {
        public static final Ui DEFAULT = new Ui(true, true, false, "", "", "", "", "", "", "", "", "");

        public Ui {
            graveOpenAction = normalize(graveOpenAction);
            graveCloseAction = normalize(graveCloseAction);
            graveAnchorOverlay = normalize(graveAnchorOverlay);
            graveMenu = normalize(graveMenu);
            graveMenuOpen = normalize(graveMenuOpen);
            pauseScreen = normalize(pauseScreen);
            inventoryScreen = normalize(inventoryScreen);
            deathScreen = normalize(deathScreen);
            hudProfile = normalize(hudProfile);
        }
    }

    /**
     * Creator-authored ceiling for DAI's optional autonomous player control.
     *
     * The player's config can always be stricter. An experience can only
     * reduce permissions/limits, never silently grant control the player has
     * disabled. Zero numeric limits mean "do not add an experience cap".
     *
     * Older experiences omit this object and receive the permissive defaults,
     * preserving their exact pre-1.9 behavior.
     */
    public record Controls(
            boolean automation,
            boolean automationMovement,
            boolean automationCombat,
            boolean automationWorldEditing,
            int maxActionsPerSecond,
            int maxActionQueueSize
    ) {
        public static final Controls DEFAULT = new Controls(true, true, true, true, 0, 0);

        public Controls {
            maxActionsPerSecond = Math.max(0, Math.min(20, maxActionsPerSecond));
            maxActionQueueSize = Math.max(0, Math.min(2048, maxActionQueueSize));
        }
    }


    /**
     * Experience-owned ADDON layering policy.
     *
     * enabled=false disables every DAI ADDON for this experience. When the
     * whitelist is empty, enabled=true permits all globally installed ADDONs
     * (legacy behavior). A non-empty whitelist permits only matching stable
     * addon ids. Stable ids deliberately exclude versions, so an experience
     * can allow "echo_time" once and continue accepting later releases.
     */
    public record AddonPolicy(
            boolean enabled,
            java.util.Set<String> whitelist
    ) {
        public static final AddonPolicy DEFAULT = new AddonPolicy(true, java.util.Set.of());

        public AddonPolicy {
            if (whitelist == null || whitelist.isEmpty()) {
                whitelist = java.util.Set.of();
            } else {
                java.util.LinkedHashSet<String> normalized = new java.util.LinkedHashSet<>();
                for (String value : whitelist) {
                    String key = normalizeAddonId(value);
                    if (!key.isBlank()) normalized.add(key);
                }
                whitelist = java.util.Set.copyOf(normalized);
            }
        }

        public boolean allowsAll() {
            return enabled && whitelist.isEmpty();
        }

        public boolean allows(String addonId) {
            if (!enabled) return false;
            if (whitelist.isEmpty()) return true;
            String normalized = normalizeAddonId(addonId);
            return !normalized.isBlank() && whitelist.contains(normalized);
        }
    }



    /**
     * Experience-owned player visual policy. Profiles themselves live in the
     * resource pack under assets/<namespace>/dai/player_presentations/*.json.
     * UUID and scoreboard-team mappings make individual/team operator models
     * possible without replacing the underlying Minecraft Player entity.
     */
    public record PlayerPresentation(
            boolean enabled,
            String defaultProfile,
            String localProfile,
            boolean allowPlayerSelection,
            Map<String, String> players,
            Map<String, String> teams
    ) {
        public static final PlayerPresentation DEFAULT = new PlayerPresentation(
                false, "", "", false, Map.of(), Map.of()
        );

        public PlayerPresentation {
            defaultProfile = normalize(defaultProfile);
            localProfile = normalize(localProfile);
            players = normalizeMap(players);
            teams = normalizeMap(teams);
        }

        private static Map<String, String> normalizeMap(Map<String, String> input) {
            if (input == null || input.isEmpty()) return Map.of();
            LinkedHashMap<String, String> out = new LinkedHashMap<>();
            input.forEach((key, value) -> {
                if (key == null || key.isBlank() || value == null || value.isBlank()) return;
                out.put(key.trim().toLowerCase(Locale.ROOT), normalize(value));
            });
            return Map.copyOf(out);
        }
    }

    /**
     * Optional application/startup branding for a MAIN experience.
     *
     * The resource-pack icon path intentionally defaults to the companion
     * pack's root pack.png. That keeps branding owned by the game's resource
     * pack and avoids copying application assets into the DAI mod jar.
     */
    public record Branding(
            String windowTitle,
            String loadingTitle,
            String loadingSubtitle,
            String loadingBackgroundTexture,
            String loadingLogo,
            String companionId,
            int loadingBackground,
            int loadingForeground,
            int loadingAccent,
            int loadingLogoSize,
            int loadingProgressWidth,
            int loadingProgressHeight,
            boolean customLoadingScreen,
            boolean showLoadingProgress,
            boolean useResourcePackIcon,
            EarlyLoading earlyLoading,
            WorldLoading worldLoading
    ) {
        public static final Branding DEFAULT = new Branding(
                "", "", "", "", "", "",
                0xFF101318, 0xFFFFFFFF, 0xFF5EE1FF,
                72, 280, 4,
                false, true, false,
                EarlyLoading.DEFAULT,
                WorldLoading.DEFAULT
        );

        public Branding {
            windowTitle = windowTitle == null ? "" : windowTitle.trim();
            loadingTitle = loadingTitle == null ? "" : loadingTitle.trim();
            loadingSubtitle = loadingSubtitle == null ? "" : loadingSubtitle.trim();
            loadingBackgroundTexture = normalize(loadingBackgroundTexture);
            loadingLogo = normalize(loadingLogo);
            companionId = normalize(companionId);
            loadingLogoSize = Math.max(0, Math.min(512, loadingLogoSize));
            loadingProgressWidth = Math.max(32, Math.min(2048, loadingProgressWidth));
            loadingProgressHeight = Math.max(1, Math.min(64, loadingProgressHeight));
            earlyLoading = earlyLoading == null ? EarlyLoading.DEFAULT : earlyLoading;
            worldLoading = worldLoading == null ? WorldLoading.DEFAULT : worldLoading;
        }
    }

    /** Branding copied into FancyModLoader's official config/fml theme for the next JVM launch. */
    public record EarlyLoading(
            boolean enabled,
            String backgroundTexture,
            String logo,
            boolean hideMojangLogo,
            boolean showProgress,
            boolean showStartupLog,
            boolean showPerformance
    ) {
        public static final EarlyLoading DEFAULT = new EarlyLoading(false, "", "", true, true, false, false);

        public EarlyLoading {
            backgroundTexture = normalize(backgroundTexture);
            logo = normalize(logo);
        }
    }

    /** Presentation policy for world generation, terrain loading, and optional level transitions. */
    public record WorldLoading(
            boolean enabled,
            String title,
            String subtitle,
            String backgroundTexture,
            String logo,
            int background,
            int foreground,
            int accent,
            int logoSize,
            int progressWidth,
            int progressHeight,
            boolean showProgress,
            boolean showStatusText,
            boolean includeTransitions
    ) {
        public static final WorldLoading DEFAULT = new WorldLoading(
                false, "", "", "", "",
                0xFF101318, 0xFFFFFFFF, 0xFF5EE1FF,
                72, 280, 4,
                true, true, true
        );

        public WorldLoading {
            title = title == null ? "" : title.trim();
            subtitle = subtitle == null ? "" : subtitle.trim();
            backgroundTexture = normalize(backgroundTexture);
            logo = normalize(logo);
            logoSize = Math.max(0, Math.min(512, logoSize));
            progressWidth = Math.max(32, Math.min(2048, progressWidth));
            progressHeight = Math.max(1, Math.min(64, progressHeight));
        }
    }

    private static JsonObject object(JsonObject root, String key) {
        if (root == null) return null;
        JsonElement value = root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }


    private static java.util.Set<String> stringSet(JsonObject root, String key) {
        if (root == null || !root.has(key) || !root.get(key).isJsonArray()) return java.util.Set.of();
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (JsonElement element : root.getAsJsonArray(key)) {
            try {
                String value = normalizeAddonId(element.getAsString());
                if (!value.isBlank()) result.add(value);
            } catch (Exception ignored) { }
        }
        return java.util.Set.copyOf(result);
    }

    private static String normalizeAddonId(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
        if (normalized.startsWith("explicit:")) normalized = normalized.substring("explicit:".length());
        if (normalized.startsWith("namespace:")) normalized = normalized.substring("namespace:".length());
        return normalized.replaceAll("\\s+", "_");
    }

    private static Map<String, String> stringMap(JsonObject root) {
        if (root == null || root.entrySet().isEmpty()) return Map.of();
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (var entry : root.entrySet()) {
            try {
                String value = entry.getValue().getAsString();
                if (value != null && !value.isBlank()) result.put(entry.getKey(), value);
            } catch (RuntimeException ignored) { }
        }
        return result;
    }

    private static String string(JsonObject root, String key, String fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsString(); } catch (Exception ignored) { return fallback; }
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsBoolean(); } catch (Exception ignored) { return fallback; }
    }

    private static int integer(JsonObject root, String key, int fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsInt(); } catch (Exception ignored) { return fallback; }
    }

    private static int color(JsonObject root, String key, int fallback) {
        if (root == null || !root.has(key)) return fallback;
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        try {
            if (element.getAsJsonPrimitive().isNumber()) return element.getAsInt();
            String raw = element.getAsString().trim();
            if (raw.startsWith("#")) raw = raw.substring(1);
            else if (raw.startsWith("0x") || raw.startsWith("0X")) raw = raw.substring(2);
            if (raw.length() == 6) return (int) (0xFF000000L | Long.parseLong(raw, 16));
            if (raw.length() == 8) return (int) Long.parseLong(raw, 16);
        } catch (Exception ignored) {
            // Fall through to authored/default fallback.
        }
        return fallback;
    }

    private static String path(String id) {
        String normalized = normalize(id);
        int colon = normalized.indexOf(':');
        return colon >= 0 ? normalized.substring(colon + 1) : normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
