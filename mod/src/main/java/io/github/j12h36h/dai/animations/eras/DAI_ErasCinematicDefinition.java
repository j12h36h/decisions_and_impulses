package io.github.j12h36h.dai.animations.eras;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parsed ERAS / Simple Animation Designer project.
 *
 * The original project JSON is intentionally preserved instead of being
 * translated into DAI's entity-animation schema. ERAS projects have camera,
 * post, object hierarchy, clip, particle, and timing concepts that do not map
 * one-to-one to a skeletal/entity animation track.
 */
public final class DAI_ErasCinematicDefinition {

    public enum Mode { TWO_D, THREE_D }

    private final Identifier id;
    private final JsonObject root;
    private final int version;
    private final String title;
    private final Mode mode;
    private final double durationSeconds;
    private final boolean loop;
    private final int canvasWidth;
    private final int canvasHeight;
    private final String background;

    private DAI_ErasCinematicDefinition(
            Identifier id,
            JsonObject root,
            int version,
            String title,
            Mode mode,
            double durationSeconds,
            boolean loop,
            int canvasWidth,
            int canvasHeight,
            String background
    ) {
        this.id = id;
        this.root = root.deepCopy();
        this.version = version;
        this.title = title;
        this.mode = mode;
        this.durationSeconds = durationSeconds;
        this.loop = loop;
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.background = background;
    }

    public static DAI_ErasCinematicDefinition parse(Identifier id, JsonObject input) {
        if (id == null) throw new IllegalArgumentException("ERAS cinematic id is required.");
        if (input == null) throw new IllegalArgumentException("ERAS cinematic JSON must be an object.");

        JsonObject root = input.deepCopy();
        JsonObject meta = object(root, "meta");

        String rawMode = string(root, "mode", string(meta, "mode", ""));
        String normalizedMode = rawMode.trim().toUpperCase(Locale.ROOT);
        Mode mode = switch (normalizedMode) {
            case "2D" -> Mode.TWO_D;
            case "3D" -> Mode.THREE_D;
            default -> throw new IllegalArgumentException("ERAS mode must be '2D' or '3D'.");
        };

        double duration = number(root, "duration", number(meta, "duration", Double.NaN));
        if (!Double.isFinite(duration) || duration <= 0.0D) {
            throw new IllegalArgumentException("ERAS duration must be greater than 0 seconds.");
        }
        duration = clamp(duration, 0.05D, 86_400.0D);

        int version = Math.max(1, (int)Math.floor(number(root, "version", 1.0D)));
        String title = string(root, "title", string(meta, "title", string(meta, "name", id.toString())));
        boolean loop = bool(root, "loop", bool(meta, "loop", true));

        JsonObject canvas = object(root, "canvas");
        int width = clampInt((int)Math.round(number(canvas, "width", 960.0D)), 64, 4096);
        int height = clampInt((int)Math.round(number(canvas, "height", 540.0D)), 64, 4096);
        String background = string(canvas, "background", string(meta, "background", "#02090b"));

        JsonArray objects = array(root, "objects");
        if (objects.size() > 2000) throw new IllegalArgumentException("ERAS projects may contain at most 2000 objects.");
        JsonArray sounds = array(root, "sounds");
        if (sounds.size() > 256) throw new IllegalArgumentException("ERAS projects may contain at most 256 sound clips.");

        // Preserve the browser designer's normal defaults inside the retained JSON.
        root.addProperty("version", version);
        root.addProperty("title", title);
        root.addProperty("mode", mode == Mode.TWO_D ? "2D" : "3D");
        root.addProperty("duration", duration);
        root.addProperty("loop", loop);
        canvas.addProperty("width", width);
        canvas.addProperty("height", height);
        canvas.addProperty("background", background);
        root.add("canvas", canvas);
        if (!root.has("assets") || !root.get("assets").isJsonObject()) root.add("assets", new JsonObject());
        if (!root.has("objects") || !root.get("objects").isJsonArray()) root.add("objects", new JsonArray());
        if (!root.has("sounds") || !root.get("sounds").isJsonArray()) root.add("sounds", new JsonArray());
        if (!root.has("clips") || !root.get("clips").isJsonObject()) root.add("clips", new JsonObject());
        if (!root.has("timing") || !root.get("timing").isJsonObject()) root.add("timing", new JsonObject());
        if (!root.has("post") || !root.get("post").isJsonObject()) root.add("post", new JsonObject());
        if (!root.has("lighting") || !root.get("lighting").isJsonObject()) root.add("lighting", new JsonObject());
        if (!root.has("camera") || !root.get("camera").isJsonObject()) root.add("camera", new JsonObject());

        return new DAI_ErasCinematicDefinition(id, root, version, title, mode, duration, loop, width, height, background);
    }

    public Identifier id() { return id; }
    public int version() { return version; }
    public String title() { return title; }
    public Mode mode() { return mode; }
    public boolean is2D() { return mode == Mode.TWO_D; }
    public boolean is3D() { return mode == Mode.THREE_D; }
    public double durationSeconds() { return durationSeconds; }
    public boolean loop() { return loop; }
    public int canvasWidth() { return canvasWidth; }
    public int canvasHeight() { return canvasHeight; }
    public String background() { return background; }

    public JsonObject root() { return root.deepCopy(); }
    public JsonObject camera() { return object(root, "camera"); }
    public JsonObject post() { return object(root, "post"); }
    public JsonObject lighting() { return object(root, "lighting"); }
    public JsonObject assets() { return object(root, "assets"); }
    public JsonObject clips() { return object(root, "clips"); }
    public JsonObject timing() { return object(root, "timing"); }

    public List<JsonObject> objects() {
        return objectList(root.get("objects"));
    }

    public List<JsonObject> sounds() {
        return objectList(root.get("sounds"));
    }

    private static List<JsonObject> objectList(JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        ArrayList<JsonObject> result = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry != null && entry.isJsonObject()) result.add(entry.getAsJsonObject().deepCopy());
        }
        return List.copyOf(result);
    }

    private static JsonObject object(JsonObject parent, String key) {
        if (parent != null && parent.has(key) && parent.get(key).isJsonObject()) {
            return parent.getAsJsonObject(key).deepCopy();
        }
        return new JsonObject();
    }

    private static JsonArray array(JsonObject parent, String key) {
        if (parent != null && parent.has(key) && parent.get(key).isJsonArray()) {
            return parent.getAsJsonArray(key).deepCopy();
        }
        return new JsonArray();
    }

    private static String string(JsonObject object, String key, String fallback) {
        try {
            if (object != null && object.has(key) && object.get(key).isJsonPrimitive()) return object.get(key).getAsString();
        } catch (RuntimeException ignored) {}
        return fallback == null ? "" : fallback;
    }

    private static double number(JsonObject object, String key, double fallback) {
        try {
            if (object != null && object.has(key)) {
                double value = object.get(key).getAsDouble();
                if (Double.isFinite(value)) return value;
            }
        } catch (RuntimeException ignored) {}
        return fallback;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        try {
            if (object != null && object.has(key)) return object.get(key).getAsBoolean();
        } catch (RuntimeException ignored) {}
        return fallback;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
