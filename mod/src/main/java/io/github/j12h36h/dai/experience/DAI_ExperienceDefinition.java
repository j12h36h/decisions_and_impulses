package io.github.j12h36h.dai.experience;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
        branding = branding == null ? Branding.DEFAULT : branding;
    }

    public static DAI_ExperienceDefinition parse(String id, JsonObject root) {
        JsonObject ui = object(root, "ui");
        JsonObject controls = object(root, "controls");
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
                        string(ui, "grave_menu_open", "")
                ),
                new Controls(
                        bool(controls, "automation", true),
                        bool(controls, "automation_movement", true),
                        bool(controls, "automation_combat", true),
                        bool(controls, "automation_world_editing", true),
                        integer(controls, "max_actions_per_second", 0),
                        integer(controls, "max_action_queue_size", 0)
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
            String graveMenuOpen
    ) {
        public static final Ui DEFAULT = new Ui(true, true, false, "", "", "", "", "");

        public Ui {
            graveOpenAction = normalize(graveOpenAction);
            graveCloseAction = normalize(graveCloseAction);
            graveAnchorOverlay = normalize(graveAnchorOverlay);
            graveMenu = normalize(graveMenu);
            graveMenuOpen = normalize(graveMenuOpen);
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
