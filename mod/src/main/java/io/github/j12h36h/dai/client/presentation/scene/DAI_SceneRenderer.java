package io.github.j12h36h.dai.client.presentation.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.presentation.scene.DAI_SceneDefinition;
import io.github.j12h36h.dai.presentation.scene.DAI_SceneRegistry;
import io.github.j12h36h.dai.util.DAI_TemplateEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generic data-driven scene graph renderer for screens and story panels.
 *
 * Scene elements are positioned in a simple 3-D coordinate system and projected
 * through pack-authored camera keyframes. Resource packs own textures/models;
 * datapacks/resource packs own scene composition. No themed environment is
 * implemented here.
 */
public final class DAI_SceneRenderer {
    private DAI_SceneRenderer() {}

    public static boolean render(
            GuiGraphicsExtractor graphics,
            String sceneId,
            int x,
            int y,
            int width,
            int height,
            float partialTick,
            Map<String, ?> variables
    ) {
        if (graphics == null || width <= 0 || height <= 0 || sceneId == null || sceneId.isBlank()) return false;
        DAI_SceneDefinition definition = DAI_SceneRegistry.get(sceneId);
        if (definition == null || !definition.enabled()) return false;
        renderDefinition(graphics, definition.json(), x, y, width, height, partialTick, variables == null ? Map.of() : variables);
        return true;
    }

    public static void renderDefinition(
            GuiGraphicsExtractor graphics,
            JsonObject root,
            int x,
            int y,
            int width,
            int height,
            float partialTick,
            Map<String, ?> variables
    ) {
        if (graphics == null || root == null || width <= 0 || height <= 0) return;
        renderBackground(graphics, DAI_SceneDefinition.object(root, "background"), x, y, width, height, variables);

        Camera camera = camera(root, partialTick);
        JsonArray array = array(root, "elements");
        List<Projected> projected = new ArrayList<>();
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            if (!DAI_SceneDefinition.bool(object, "enabled", true)) continue;
            Projected p = project(object, camera, x, y, width, height, variables);
            if (p != null) projected.add(p);
        }
        projected.sort(Comparator.comparingDouble(Projected::depth).reversed());
        for (Projected p : projected) renderElement(graphics, p, variables);

        JsonObject overlay = DAI_SceneDefinition.object(root, "overlay");
        if (overlay.size() > 0) renderBackground(graphics, overlay, x, y, width, height, variables);
    }

    private static void renderBackground(
            GuiGraphicsExtractor graphics,
            JsonObject background,
            int x,
            int y,
            int width,
            int height,
            Map<String, ?> variables
    ) {
        if (background == null || background.size() == 0) return;
        String type = DAI_SceneDefinition.string(background, "type", "gradient").trim().toLowerCase(Locale.ROOT);
        int top = DAI_SceneDefinition.color(background, "top", 0xFF000000);
        int bottom = DAI_SceneDefinition.color(background, "bottom", top);
        int tint = DAI_SceneDefinition.color(background, "tint", 0xFFFFFFFF);
        switch (type) {
            case "color", "solid" -> graphics.fill(x, y, x + width, y + height,
                    DAI_SceneDefinition.color(background, "color", top));
            case "texture", "image" -> {
                String raw = DAI_TemplateEngine.resolve(DAI_SceneDefinition.string(background, "texture", ""), variables);
                Identifier texture = Identifier.tryParse(raw);
                if (texture != null) {
                    int tw = Math.max(1, DAI_SceneDefinition.integer(background, "texture_width", width));
                    int th = Math.max(1, DAI_SceneDefinition.integer(background, "texture_height", height));
                    graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F,
                            width, height, tw, th, tint);
                } else {
                    graphics.fillGradient(x, y, x + width, y + height, top, bottom);
                }
            }
            default -> graphics.fillGradient(x, y, x + width, y + height, top, bottom);
        }
    }

    private static Camera camera(JsonObject root, float partialTick) {
        JsonObject camera = DAI_SceneDefinition.object(root, "camera");
        JsonArray frames = array(camera, "keyframes");
        long now = System.nanoTime();
        double ticks = now / 50_000_000.0D + partialTick;
        double duration = Math.max(1.0D, DAI_SceneDefinition.number(camera, "duration_ticks", 1200.0D));
        boolean loop = DAI_SceneDefinition.bool(camera, "loop", true);
        double t = loop ? positiveMod(ticks, duration) : Math.min(duration, ticks);

        CameraFrame a = new CameraFrame(0.0D, vec(camera, "position", 0, 0, 10), vec(camera, "look_at", 0, 0, 0),
                DAI_SceneDefinition.number(camera, "fov", 60.0D), DAI_SceneDefinition.number(camera, "roll", 0.0D));
        CameraFrame b = a;
        if (frames.size() > 0) {
            List<CameraFrame> parsed = new ArrayList<>();
            for (JsonElement element : frames) {
                if (element != null && element.isJsonObject()) {
                    JsonObject f = element.getAsJsonObject();
                    parsed.add(new CameraFrame(
                            DAI_SceneDefinition.number(f, "tick", 0.0D),
                            vec(f, "position", 0, 0, 10),
                            vec(f, "look_at", 0, 0, 0),
                            DAI_SceneDefinition.number(f, "fov", 60.0D),
                            DAI_SceneDefinition.number(f, "roll", 0.0D)
                    ));
                }
            }
            parsed.sort(Comparator.comparingDouble(CameraFrame::tick));
            if (!parsed.isEmpty()) {
                a = parsed.getFirst();
                b = parsed.getLast();
                for (int i = 0; i < parsed.size(); i++) {
                    CameraFrame current = parsed.get(i);
                    CameraFrame next = i + 1 < parsed.size() ? parsed.get(i + 1) : current;
                    if (t >= current.tick() && (t <= next.tick() || i + 1 == parsed.size())) {
                        a = current;
                        b = next;
                        break;
                    }
                }
            }
        }

        double span = Math.max(0.000001D, b.tick() - a.tick());
        double local = a == b ? 0.0D : clamp01((t - a.tick()) / span);
        String interpolation = DAI_SceneDefinition.string(camera, "interpolation", "smooth").toLowerCase(Locale.ROOT);
        if (interpolation.equals("smooth") || interpolation.equals("smoothstep")) local = local * local * (3.0D - 2.0D * local);
        if (interpolation.equals("step")) local = 0.0D;

        Vec3 position = lerp(a.position(), b.position(), local);
        Vec3 target = lerp(a.target(), b.target(), local);
        double fov = lerp(a.fov(), b.fov(), local);
        double roll = lerp(a.roll(), b.roll(), local);

        JsonObject motion = DAI_SceneDefinition.object(camera, "motion");
        double seconds = now / 1_000_000_000.0D;
        double drift = DAI_SceneDefinition.number(motion, "drift", 0.0D);
        double driftSpeed = DAI_SceneDefinition.number(motion, "drift_speed", 0.1D);
        double shake = DAI_SceneDefinition.number(motion, "shake", 0.0D);
        double shakeSpeed = DAI_SceneDefinition.number(motion, "shake_speed", 1.0D);
        if (drift != 0.0D) {
            position = new Vec3(position.x + Math.sin(seconds * driftSpeed) * drift,
                    position.y + Math.cos(seconds * driftSpeed * 0.73D) * drift * 0.5D,
                    position.z);
        }
        if (shake != 0.0D) {
            target = new Vec3(target.x + Math.sin(seconds * shakeSpeed * 7.13D) * shake,
                    target.y + Math.cos(seconds * shakeSpeed * 5.37D) * shake,
                    target.z + Math.sin(seconds * shakeSpeed * 3.91D) * shake * 0.5D);
        }
        return Camera.from(position, target, clamp(fov, 10.0D, 150.0D), roll);
    }

    private static Projected project(
            JsonObject element,
            Camera camera,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            Map<String, ?> variables
    ) {
        boolean screenSpace = DAI_SceneDefinition.bool(element, "screen_space", false);
        double worldWidth = Math.max(0.0001D, DAI_SceneDefinition.number(element, "width", 1.0D));
        double worldHeight = Math.max(0.0001D, DAI_SceneDefinition.number(element, "height", worldWidth));
        double sx;
        double sy;
        double drawWidth;
        double drawHeight;
        double depth;

        if (screenSpace) {
            sx = viewportX + DAI_SceneDefinition.number(element, "x", viewportWidth * 0.5D);
            sy = viewportY + DAI_SceneDefinition.number(element, "y", viewportHeight * 0.5D);
            drawWidth = DAI_SceneDefinition.number(element, "pixel_width", worldWidth);
            drawHeight = DAI_SceneDefinition.number(element, "pixel_height", worldHeight);
            depth = DAI_SceneDefinition.number(element, "z", 0.0D);
        } else {
            Vec3 point = vec(element, "position",
                    DAI_SceneDefinition.number(element, "x", 0.0D),
                    DAI_SceneDefinition.number(element, "y", 0.0D),
                    DAI_SceneDefinition.number(element, "z", 0.0D));
            Vec3 delta = point.subtract(camera.position);
            depth = delta.dot(camera.forward);
            double near = DAI_SceneDefinition.number(element, "near", 0.05D);
            double far = DAI_SceneDefinition.number(element, "far", 10000.0D);
            if (depth <= near || depth > far) return null;
            double focal = viewportHeight / (2.0D * Math.tan(Math.toRadians(camera.fov * 0.5D)));
            double horizontal = delta.dot(camera.right);
            double vertical = delta.dot(camera.up);
            sx = viewportX + viewportWidth * 0.5D + horizontal * focal / depth;
            sy = viewportY + viewportHeight * 0.5D - vertical * focal / depth;
            drawWidth = worldWidth * focal / depth;
            drawHeight = worldHeight * focal / depth;
            if (camera.roll != 0.0D) {
                double cx = viewportX + viewportWidth * 0.5D;
                double cy = viewportY + viewportHeight * 0.5D;
                double radians = Math.toRadians(camera.roll);
                double dx = sx - cx;
                double dy = sy - cy;
                sx = cx + dx * Math.cos(radians) - dy * Math.sin(radians);
                sy = cy + dx * Math.sin(radians) + dy * Math.cos(radians);
            }
        }

        double minScale = DAI_SceneDefinition.number(element, "min_pixels", 1.0D);
        double maxScale = DAI_SceneDefinition.number(element, "max_pixels", 4096.0D);
        drawWidth = clamp(drawWidth, minScale, maxScale);
        drawHeight = clamp(drawHeight, minScale, maxScale);
        return new Projected(element, sx, sy, drawWidth, drawHeight, depth);
    }

    private static void renderElement(GuiGraphicsExtractor graphics, Projected p, Map<String, ?> variables) {
        JsonObject element = p.json();
        String type = DAI_SceneDefinition.string(element, "type", "sprite").trim().toLowerCase(Locale.ROOT);
        int width = Math.max(1, (int)Math.round(p.width()));
        int height = Math.max(1, (int)Math.round(p.height()));
        int x = (int)Math.round(p.x() - width * 0.5D);
        int y = (int)Math.round(p.y() - height * 0.5D);
        int color = DAI_SceneDefinition.color(element, "color", 0xFFFFFFFF);
        switch (type) {
            case "rect", "panel", "color" -> graphics.fill(x, y, x + width, y + height, color);
            case "text", "label" -> {
                String text = DAI_TemplateEngine.resolve(DAI_SceneDefinition.string(element, "text", ""), variables);
                graphics.centeredText(Minecraft.getInstance().font, Component.literal(text), (int)Math.round(p.x()), y, color);
            }
            case "item", "block" -> {
                String raw = DAI_TemplateEngine.resolve(DAI_SceneDefinition.string(element, "item", "minecraft:air"), variables);
                Identifier id = Identifier.tryParse(raw);
                if (id != null) {
                    var item = BuiltInRegistries.ITEM.getValue(id);
                    if (item != null) graphics.item(new ItemStack(item), (int)Math.round(p.x()) - 8, (int)Math.round(p.y()) - 8);
                }
            }
            default -> {
                String raw = DAI_TemplateEngine.resolve(DAI_SceneDefinition.string(element, "texture", ""), variables);
                Identifier texture = Identifier.tryParse(raw);
                if (texture == null) return;
                int tw = Math.max(1, DAI_SceneDefinition.integer(element, "texture_width", 16));
                int th = Math.max(1, DAI_SceneDefinition.integer(element, "texture_height", 16));
                int frameWidth = Math.max(1, DAI_SceneDefinition.integer(element, "frame_width", tw));
                int frameHeight = Math.max(1, DAI_SceneDefinition.integer(element, "frame_height", th));
                int frames = Math.max(1, DAI_SceneDefinition.integer(element, "frames", 1));
                int columns = Math.max(1, DAI_SceneDefinition.integer(element, "columns", 1));
                int frameTicks = Math.max(1, DAI_SceneDefinition.integer(element, "frame_ticks", 1));
                long tick = System.nanoTime() / 50_000_000L;
                int frame = frames == 1 ? 0 : (int)((tick / frameTicks) % frames);
                int u = (frame % columns) * frameWidth;
                int v = (frame / columns) * frameHeight;
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, (float)u, (float)v,
                        width, height, frameWidth, frameHeight, tw, th, color);
            }
        }
    }

    private static JsonArray array(JsonObject root, String key) {
        if (root == null || key == null || !root.has(key)) return new JsonArray();
        JsonElement value = root.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static Vec3 vec(JsonObject root, String key, double x, double y, double z) {
        if (root != null && key != null && root.has(key) && root.get(key).isJsonArray()) {
            JsonArray array = root.getAsJsonArray(key);
            if (array.size() >= 3) {
                try { return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()); }
                catch (RuntimeException ignored) {}
            }
        }
        return new Vec3(x, y, z);
    }

    private static double positiveMod(double value, double modulus) {
        double out = value % modulus;
        return out < 0 ? out + modulus : out;
    }
    private static double clamp01(double value) { return clamp(value, 0.0D, 1.0D); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static Vec3 lerp(Vec3 a, Vec3 b, double t) { return new Vec3(lerp(a.x,b.x,t), lerp(a.y,b.y,t), lerp(a.z,b.z,t)); }

    private record CameraFrame(double tick, Vec3 position, Vec3 target, double fov, double roll) {}
    private record Projected(JsonObject json, double x, double y, double width, double height, double depth) {}
    private record Vec3(double x, double y, double z) {
        Vec3 subtract(Vec3 other) { return new Vec3(x-other.x, y-other.y, z-other.z); }
        double dot(Vec3 other) { return x*other.x + y*other.y + z*other.z; }
        Vec3 cross(Vec3 other) { return new Vec3(y*other.z-z*other.y, z*other.x-x*other.z, x*other.y-y*other.x); }
        double length() { return Math.sqrt(dot(this)); }
        Vec3 normalize() { double length = length(); return length < 1.0E-9D ? new Vec3(0,0,0) : new Vec3(x/length,y/length,z/length); }
    }
    private record Camera(Vec3 position, Vec3 forward, Vec3 right, Vec3 up, double fov, double roll) {
        static Camera from(Vec3 position, Vec3 target, double fov, double roll) {
            Vec3 forward = target.subtract(position).normalize();
            if (forward.length() < 1.0E-9D) forward = new Vec3(0,0,-1);
            Vec3 worldUp = Math.abs(forward.y) > 0.995D ? new Vec3(0,0,1) : new Vec3(0,1,0);
            Vec3 right = forward.cross(worldUp).normalize();
            Vec3 up = right.cross(forward).normalize();
            return new Camera(position, forward, right, up, fov, roll);
        }
    }
}
