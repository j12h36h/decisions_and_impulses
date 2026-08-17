package io.github.j12h36h.dai.client.title;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Immutable JSON-backed definition for the D.A.I. title-screen renderer.
 *
 * Coordinates are deliberately simple: buttons use an anchor plus x/y offsets,
 * which makes the same definition usable across GUI scales and resolutions.
 */
public record DAI_TitleScreenDefinition(
        String id,
        boolean enabled,
        int priority,
        String title,
        String subtitle,
        String theme,
        int backgroundTop,
        int backgroundBottom,
        int titleColor,
        int subtitleColor,
        SaveBrowserDefinition saveBrowser,
        List<ButtonDefinition> buttons
) {

    public DAI_TitleScreenDefinition {
        id = safe(id, "decisions_and_impulses:default");
        title = safe(title, "DECISIONS & IMPULSES");
        subtitle = safe(subtitle, "The interface between humanity and automation.");
        theme = safe(theme, "gradient").trim().toLowerCase(Locale.ROOT);
        saveBrowser = saveBrowser == null ? SaveBrowserDefinition.DISABLED : saveBrowser;
        buttons = buttons == null ? List.of() : List.copyOf(buttons);
    }

    public static DAI_TitleScreenDefinition parse(
            String id,
            JsonObject root
    ) {
        if (root == null) {
            return fallback(id);
        }

        JsonObject background = object(root, "background");
        JsonObject saveBrowser = object(root, "experience_save_browser");
        JsonArray buttonArray = array(root, "buttons");
        List<ButtonDefinition> buttons = new ArrayList<>();

        if (buttonArray != null) {
            for (JsonElement element : buttonArray) {
                if (element != null && element.isJsonObject()) {
                    ButtonDefinition button = parseButton(element.getAsJsonObject());
                    if (button != null) {
                        buttons.add(button);
                    }
                }
            }
        }

        return new DAI_TitleScreenDefinition(
                id,
                bool(root, "enabled", true),
                integer(root, "priority", 0),
                string(root, "title", "DECISIONS & IMPULSES"),
                string(root, "subtitle", "The interface between humanity and automation."),
                string(root, "theme", "gradient"),
                color(background, "top", 0xFF071018),
                color(background, "bottom", 0xFF101E29),
                color(root, "title_color", 0xFFFFFFFF),
                color(root, "subtitle_color", 0xFF9EB6C7),
                parseSaveBrowser(saveBrowser),
                buttons
        );
    }

    public static DAI_TitleScreenDefinition fallback(String id) {
        return new DAI_TitleScreenDefinition(
                id,
                true,
                Integer.MIN_VALUE,
                "DECISIONS & IMPULSES",
                "The interface between humanity and automation.",
                "gradient",
                0xFF071018,
                0xFF101E29,
                0xFFFFFFFF,
                0xFF9EB6C7,
                SaveBrowserDefinition.DISABLED,
                List.of()
        );
    }

    private static SaveBrowserDefinition parseSaveBrowser(JsonObject object) {
        if (object == null) return SaveBrowserDefinition.DISABLED;

        JsonObject style = object(object, "style");
        return new SaveBrowserDefinition(
                bool(object, "enabled", true),
                string(object, "experience", ""),
                string(object, "title", "YOUR SAVES"),
                string(object, "entry_prefix", "Run"),
                string(object, "empty_title", "No saves yet."),
                string(object, "empty_subtitle", "Start a new experience from the main menu."),
                string(object, "delete_warning", "This permanently deletes this save."),
                string(object, "delete_detail", "Progress and the world cannot be recovered."),
                string(object, "anchor", "right"),
                integer(object, "x", 24),
                integer(object, "y", -70),
                integer(object, "width", 286),
                integer(object, "height", 196),
                integer(object, "rows", 3),
                color(style, "background", 0xD0121110),
                color(style, "border", 0xFF7E6337),
                color(style, "title", 0xFFFFD36A),
                color(style, "entry", 0xC01A1712),
                color(style, "entry_hover", 0xE0352B1C),
                color(style, "entry_border", 0xFF80683F),
                color(style, "text", 0xFFFFFFFF),
                color(style, "muted", 0xFFB7AA8D),
                color(style, "delete", 0xC03A1717),
                color(style, "delete_hover", 0xE06A2222),
                color(style, "delete_border", 0xFFC96565)
        );
    }

    private static ButtonDefinition parseButton(JsonObject object) {
        String id = string(object, "id", "");
        String label = string(object, "label", id);
        String action = string(object, "action", "");

        if (id.isBlank() || action.isBlank()) {
            return null;
        }

        JsonObject icon = object(object, "icon");
        JsonObject style = object(object, "style");
        JsonObject hover = object(object, "hover_animation");

        return new ButtonDefinition(
                id,
                label,
                action,
                string(object, "url", ""),
                string(object, "experience", ""),
                string(object, "anchor", "center"),
                integer(object, "x", 0),
                integer(object, "y", 0),
                integer(object, "width", 210),
                integer(object, "height", 24),
                new IconDefinition(
                        string(icon, "type", "none"),
                        string(icon, "id", ""),
                        decimal(icon, "scale", 1.0F),
                        integer(icon, "offset_x", 8),
                        integer(icon, "offset_y", 0)
                ),
                new StyleDefinition(
                        color(style, "background", 0xB8182734),
                        color(style, "hover", 0xE02C4A5D),
                        color(style, "border", 0xFF4E7389),
                        color(style, "text", 0xFFFFFFFF)
                ),
                new HoverAnimation(
                        string(hover, "type", "none").toLowerCase(Locale.ROOT),
                        decimal(hover, "speed", 1.0F),
                        decimal(hover, "amount", 1.0F)
                )
        );
    }

    public record SaveBrowserDefinition(
            boolean enabled,
            String experience,
            String title,
            String entryPrefix,
            String emptyTitle,
            String emptySubtitle,
            String deleteWarning,
            String deleteDetail,
            String anchor,
            int x,
            int y,
            int width,
            int height,
            int rows,
            int background,
            int border,
            int titleColor,
            int entryBackground,
            int entryHover,
            int entryBorder,
            int textColor,
            int mutedColor,
            int deleteBackground,
            int deleteHover,
            int deleteBorder
    ) {
        static final SaveBrowserDefinition DISABLED = new SaveBrowserDefinition(
                false, "", "YOUR SAVES", "Run",
                "No saves yet.", "Start a new experience from the main menu.",
                "This permanently deletes this save.", "Progress and the world cannot be recovered.",
                "right", 24, -70, 286, 196, 3,
                0xD0121110, 0xFF7E6337, 0xFFFFD36A,
                0xC01A1712, 0xE0352B1C, 0xFF80683F,
                0xFFFFFFFF, 0xFFB7AA8D,
                0xC03A1717, 0xE06A2222, 0xFFC96565
        );

        public SaveBrowserDefinition {
            experience = safe(experience, "");
            title = safe(title, "YOUR SAVES");
            entryPrefix = safe(entryPrefix, "Run");
            emptyTitle = safe(emptyTitle, "No saves yet.");
            emptySubtitle = safe(emptySubtitle, "Start a new experience from the main menu.");
            deleteWarning = safe(deleteWarning, "This permanently deletes this save.");
            deleteDetail = safe(deleteDetail, "Progress and the world cannot be recovered.");
            anchor = safe(anchor, "right").trim().toLowerCase(Locale.ROOT);
            width = Math.max(210, width);
            height = Math.max(116, height);
            rows = Math.max(1, Math.min(6, rows));
        }
    }

    public record ButtonDefinition(
            String id,
            String label,
            String action,
            String url,
            String experience,
            String anchor,
            int x,
            int y,
            int width,
            int height,
            IconDefinition icon,
            StyleDefinition style,
            HoverAnimation hoverAnimation
    ) {
        public ButtonDefinition {
            id = safe(id, "button");
            label = safe(label, id);
            action = safe(action, "");
            url = safe(url, "");
            experience = safe(experience, "");
            anchor = safe(anchor, "center").toLowerCase(Locale.ROOT);
            width = Math.max(40, width);
            height = Math.max(18, height);
            icon = icon == null ? IconDefinition.NONE : icon;
            style = style == null ? StyleDefinition.DEFAULT : style;
            hoverAnimation = hoverAnimation == null ? HoverAnimation.NONE : hoverAnimation;
        }
    }

    public record IconDefinition(
            String type,
            String id,
            float scale,
            int offsetX,
            int offsetY
    ) {
        static final IconDefinition NONE = new IconDefinition("none", "", 1.0F, 0, 0);

        public IconDefinition {
            type = safe(type, "none").toLowerCase(Locale.ROOT);
            id = safe(id, "");
            scale = Math.max(0.25F, Math.min(4.0F, scale));
        }
    }

    public record StyleDefinition(
            int background,
            int hover,
            int border,
            int text
    ) {
        static final StyleDefinition DEFAULT =
                new StyleDefinition(0xB8182734, 0xE02C4A5D, 0xFF4E7389, 0xFFFFFFFF);
    }

    public record HoverAnimation(
            String type,
            float speed,
            float amount
    ) {
        static final HoverAnimation NONE = new HoverAnimation("none", 1.0F, 1.0F);

        public HoverAnimation {
            type = safe(type, "none").toLowerCase(Locale.ROOT);
            speed = Math.max(0.05F, Math.min(20.0F, speed));
            amount = Math.max(0.0F, Math.min(12.0F, amount));
        }
    }

    private static JsonObject object(JsonObject root, String key) {
        if (root == null || key == null) return null;
        JsonElement value = root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject root, String key) {
        if (root == null || key == null) return null;
        JsonElement value = root.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static String string(JsonObject root, String key, String fallback) {
        if (root == null || !root.has(key)) return fallback;
        try {
            return root.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        if (root == null || !root.has(key)) return fallback;
        try {
            return root.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int integer(JsonObject root, String key, int fallback) {
        if (root == null || !root.has(key)) return fallback;
        try {
            return root.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float decimal(JsonObject root, String key, float fallback) {
        if (root == null || !root.has(key)) return fallback;
        try {
            return root.get(key).getAsFloat();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int color(JsonObject root, String key, int fallback) {
        String value = string(root, key, "");
        if (value.isBlank()) return fallback;

        String normalized = value.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }

        try {
            if (normalized.length() == 6) {
                return 0xFF000000 | Integer.parseUnsignedInt(normalized, 16);
            }
            if (normalized.length() == 8) {
                return (int) Long.parseLong(normalized, 16);
            }
        } catch (NumberFormatException ignored) {
            // Keep the requested fallback.
        }

        return fallback;
    }

    private static String safe(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
