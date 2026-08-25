package io.github.j12h36h.dai.client.logics.input;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Datapack-authored named/remappable client control. */
public record DAI_KeybindDefinition(
        String displayName,
        String category,
        String defaultKey
) {
    public static final Codec<DAI_KeybindDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("display_name", "").forGetter(DAI_KeybindDefinition::displayName),
            Codec.STRING.optionalFieldOf("category", "general").forGetter(DAI_KeybindDefinition::category),
            Codec.STRING.optionalFieldOf("default_key", "key.keyboard.unknown").forGetter(DAI_KeybindDefinition::defaultKey)
    ).apply(instance, DAI_KeybindDefinition::new));

    public DAI_KeybindDefinition {
        displayName = displayName == null ? "" : displayName.trim();
        category = category == null || category.isBlank() ? "general" : category.trim().toLowerCase(Locale.ROOT);
        defaultKey = defaultKey == null || defaultKey.isBlank() ? "key.keyboard.unknown" : defaultKey.trim().toLowerCase(Locale.ROOT);
    }

    public String rawKeyId() {
        String value = defaultKey;
        if (value.startsWith("key.keyboard.")) return value.substring("key.keyboard.".length());
        return value;
    }

    public int keyCode() {
        String id = rawKeyId();
        if (id.length() == 1) {
            char c = id.charAt(0);
            if (c >= 'a' && c <= 'z') return GLFW.GLFW_KEY_A + c - 'a';
            if (c >= '0' && c <= '9') return GLFW.GLFW_KEY_0 + c - '0';
        }
        if (id.startsWith("f")) {
            try {
                int n = Integer.parseInt(id.substring(1));
                if (n >= 1 && n <= 25) return GLFW.GLFW_KEY_F1 + n - 1;
            } catch (RuntimeException ignored) {}
        }
        return switch (id) {
            case "space" -> GLFW.GLFW_KEY_SPACE;
            case "enter", "return" -> GLFW.GLFW_KEY_ENTER;
            case "tab" -> GLFW.GLFW_KEY_TAB;
            case "escape", "esc" -> GLFW.GLFW_KEY_ESCAPE;
            case "backspace" -> GLFW.GLFW_KEY_BACKSPACE;
            case "left_shift" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "right_shift" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "left_control", "left_ctrl" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "right_control", "right_ctrl" -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "left_alt" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "right_alt" -> GLFW.GLFW_KEY_RIGHT_ALT;
            case "up", "up_arrow" -> GLFW.GLFW_KEY_UP;
            case "down", "down_arrow" -> GLFW.GLFW_KEY_DOWN;
            case "left", "left_arrow" -> GLFW.GLFW_KEY_LEFT;
            case "right", "right_arrow" -> GLFW.GLFW_KEY_RIGHT;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
    }
}
