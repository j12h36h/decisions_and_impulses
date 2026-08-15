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
        int backgroundTop,
        int backgroundBottom,
        int titleColor,
        int subtitleColor,
        List<ButtonDefinition> buttons
) {

    public DAI_TitleScreenDefinition {
        id = safe(id, "decisions_and_impulses:default");
        title = safe(title, "DECISIONS & IMPULSES");
        subtitle = safe(subtitle, "The interface between humanity and automation.");
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
                color(background, "top", 0xFF071018),
                color(background, "bottom", 0xFF101E29),
                color(root, "title_color", 0xFFFFFFFF),
                color(root, "subtitle_color", 0xFF9EB6C7),
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
                0xFF071018,
                0xFF101E29,
                0xFFFFFFFF,
                0xFF9EB6C7,
                List.of()
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
