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
        Ui ui
) {
    public DAI_ExperienceDefinition {
        id = normalize(id);
        saveId = safe(saveId, path(id).replace('_', ' '));
        saveName = safe(saveName, saveId);
        worldgen = normalize(worldgen);
        onFirstJoin = normalize(onFirstJoin);
        onJoin = normalize(onJoin);
        ui = ui == null ? Ui.DEFAULT : ui;
    }

    public static DAI_ExperienceDefinition parse(String id, JsonObject root) {
        JsonObject ui = object(root, "ui");
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
