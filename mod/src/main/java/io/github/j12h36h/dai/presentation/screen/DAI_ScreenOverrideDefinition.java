package io.github.j12h36h.dai.presentation.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.gui.screens.Screen;

import java.util.Locale;
import java.util.regex.Pattern;

/** Generic JSON rule describing how a screen class/title should be replaced or skinned. */
public final class DAI_ScreenOverrideDefinition {
    public static final Codec<DAI_ScreenOverrideDefinition> CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> {
                Object value = dynamic.convert(JsonOps.INSTANCE).getValue();
                return value instanceof JsonObject object
                        ? new DAI_ScreenOverrideDefinition(object)
                        : new DAI_ScreenOverrideDefinition(new JsonObject());
            },
            definition -> new Dynamic<>(JsonOps.INSTANCE, definition.json())
    );

    private final JsonObject json;

    public DAI_ScreenOverrideDefinition(JsonObject json) {
        this.json = json == null ? new JsonObject() : json.deepCopy();
    }

    public JsonObject json() { return json.deepCopy(); }
    public boolean enabled() { return bool(json, "enabled", true); }
    public int priority() { return integer(json, "priority", 0); }
    public String mode() { return string(json, "mode", "skin").trim().toLowerCase(Locale.ROOT); }
    public String replacementScreen() { return string(json, "screen", string(json, "replacement_screen", "")); }
    public String backgroundScene() { return string(json, "background_scene", ""); }
    public String foregroundScene() { return string(json, "foreground_scene", ""); }

    public boolean matches(Screen screen) {
        if (screen == null || !enabled()) return false;
        JsonObject target = object(json, "target");
        if (target.size() == 0) return false;

        String className = screen.getClass().getName();
        String simpleName = screen.getClass().getSimpleName();
        String title;
        try { title = screen.getTitle() == null ? "" : screen.getTitle().getString(); }
        catch (RuntimeException ignored) { title = ""; }

        String classRule = string(target, "class", "");
        String simpleRule = string(target, "simple_class", "");
        String titleRule = string(target, "title", "");
        String excludeClass = string(target, "exclude_class", "");

        if (!excludeClass.isBlank() && glob(excludeClass, className)) return false;
        if (!classRule.isBlank() && !glob(classRule, className)) return false;
        if (!simpleRule.isBlank() && !glob(simpleRule, simpleName)) return false;
        if (!titleRule.isBlank() && !glob(titleRule, title)) return false;
        return !classRule.isBlank() || !simpleRule.isBlank() || !titleRule.isBlank();
    }

    private static boolean glob(String expression, String value) {
        if (expression == null || expression.isBlank()) return true;
        StringBuilder regex = new StringBuilder("^");
        for (char c : expression.toCharArray()) {
            if (c == '*') regex.append(".*");
            else if (c == '?') regex.append('.');
            else regex.append(Pattern.quote(String.valueOf(c)));
        }
        regex.append('$');
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE).matcher(value == null ? "" : value).matches();
    }

    private static JsonObject object(JsonObject root, String key) {
        if (root == null || key == null) return new JsonObject();
        JsonElement element = root.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }
    private static String string(JsonObject root, String key, String fallback) {
        try { return root != null && root.has(key) ? root.get(key).getAsString() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static boolean bool(JsonObject root, String key, boolean fallback) {
        try { return root != null && root.has(key) ? root.get(key).getAsBoolean() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static int integer(JsonObject root, String key, int fallback) {
        try { return root != null && root.has(key) ? root.get(key).getAsInt() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
}
