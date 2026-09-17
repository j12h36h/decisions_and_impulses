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
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic data-driven scene graph renderer for screens and story panels.
 *
 * Scene elements are positioned in a simple 3-D coordinate system and projected
 * through pack-authored camera keyframes. Resource packs own textures/models;
 * datapacks/resource packs own scene composition. No themed environment is
 * implemented here.
 */
public final class DAI_SceneRenderer {
    private static final Map<Identifier, ItemStack> ITEM_MODEL_CACHE = new ConcurrentHashMap<>();
    private static final Map<Identifier, ItemStack> BLOCK_MODEL_CACHE = new ConcurrentHashMap<>();

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

            String type = DAI_SceneDefinition.string(object, "type", "sprite")
                    .trim().toLowerCase(Locale.ROOT);
            if (type.equals("block_structure") || type.equals("voxel_structure") || type.equals("block_scene")) {
                for (JsonObject block : expandBlockStructure(object, variables)) {
                    Projected p = project(block, camera, x, y, width, height, variables);
                    if (p != null) projected.add(p);
                }
                continue;
            }

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
                Identifier texture = safeTextureId(raw);
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
        double padding = 24.0D;
        if (sx + drawWidth * 0.5D < viewportX - padding
                || sx - drawWidth * 0.5D > viewportX + viewportWidth + padding
                || sy + drawHeight * 0.5D < viewportY - padding
                || sy - drawHeight * 0.5D > viewportY + viewportHeight + padding) {
            return null;
        }
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
            case "item" -> renderItemModel(graphics, p, element, variables, false);
            case "block" -> renderItemModel(graphics, p, element, variables, true);
            default -> {
                String raw = DAI_TemplateEngine.resolve(DAI_SceneDefinition.string(element, "texture", ""), variables);
                Identifier texture = safeTextureId(raw);
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


    /**
     * Renders a scene item or block through Minecraft's normal baked GUI item
     * model. Block items therefore keep their actual 3-D model geometry and
     * resource-pack/mod-provided model, while the scene camera determines the
     * model's projected position and size.
     */
    private static void renderItemModel(
            GuiGraphicsExtractor graphics,
            Projected projected,
            JsonObject element,
            Map<String, ?> variables,
            boolean block
    ) {
        String key = block ? "block" : "item";
        String fallbackKey = block ? "item" : "block";
        String raw = DAI_TemplateEngine.resolve(
                DAI_SceneDefinition.string(element, key,
                        DAI_SceneDefinition.string(element, fallbackKey, "minecraft:air")),
                variables
        );
        Identifier id = Identifier.tryParse(raw);
        if (id == null) return;

        /* ItemStack GUI models are deliberately used here rather than a custom
         * block mesh format. That means the active Minecraft/resource-pack/mod
         * model is what DAI displays. The DAI shell boot binds registry
         * components first; after that readiness boundary these models remain
         * available across title and later world-transition screens. */
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        /*
         * Do not construct ItemStacks during the bootstrap/title reload phase.
         * Minecraft exposes registry holders before their component maps are
         * bound in 26.2, and ItemStack(Item) will throw "Components not bound
         * yet" in that window. DAI's bootstrap-safe universe renderer covers
         * this period; model-backed scenes become available after the first
         * client level has fully attached.
         */
        if (!DAI_SceneRenderSafety.registryModelsReady()) return;

        ItemStack stack;
        if (block) {
            stack = BLOCK_MODEL_CACHE.get(id);
            if (stack == null) {
                var blockValue = BuiltInRegistries.BLOCK.getValue(id);
                if (blockValue == null || blockValue.asItem() == Items.AIR) return;
                stack = new ItemStack(blockValue.asItem());
                BLOCK_MODEL_CACHE.put(id, stack);
            }
        } else {
            stack = ITEM_MODEL_CACHE.get(id);
            if (stack == null) {
                var item = BuiltInRegistries.ITEM.getValue(id);
                if (item == null || item == Items.AIR) return;
                stack = new ItemStack(item);
                ITEM_MODEL_CACHE.put(id, stack);
            }
        }

        /*
         * A baked GUI block model does not consume every pixel of its nominal
         * 16x16 item cell: the isometric corners leave a small transparent
         * margin. If scene blocks are scaled to the mathematical cell size
         * without compensating for that margin, adjacent terrain reads as a
         * collection of slightly shrunken cubes with bright seams between
         * them. block_structure children therefore opt into grid fitting.
         *
         * The correction is intentionally small and author-overridable. It
         * expands only the visual model; world-space block centers remain on
         * the exact authored grid, so structures do not drift or accumulate
         * positional error as they grow.
         */
        boolean gridFit = block && DAI_SceneDefinition.bool(element, "grid_fit", false);
        double fit = Math.max(0.01D, DAI_SceneDefinition.number(
                element, "model_fit", gridFit ? 1.115D : 1.0D));
        double seamOverlapPixels = Math.max(0.0D, DAI_SceneDefinition.number(
                element, "seam_overlap_pixels", gridFit ? 0.65D : 0.0D));
        double targetPixels = Math.min(projected.width(), projected.height()) + seamOverlapPixels;

        float scale = (float)Math.max(0.02D, targetPixels / 16.0D);
        scale *= (float)Math.max(0.01D, DAI_SceneDefinition.number(element, "model_scale", 1.0D));
        scale *= (float)fit;
        float rotation = (float)Math.toRadians(DAI_SceneDefinition.number(element, "screen_rotation", 0.0D));

        // Half-pixel snapping keeps neighboring block icons on the same raster
        // phase while the camera moves, reducing shimmer and one-pixel cracks.
        float centerX = gridFit ? snapHalf(projected.x()) : (float)projected.x();
        float centerY = gridFit ? snapHalf(projected.y()) : (float)projected.y();

        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        if (rotation != 0.0F) graphics.pose().rotate(rotation);
        graphics.pose().scale(scale, scale);
        graphics.item(stack, -8, -8);
        graphics.pose().popMatrix();
    }

    /**
     * Expands a compact JSON voxel/block structure into ordinary projected
     * block scene elements. This intentionally resolves only registry ids at
     * render time, so vanilla blocks, modded blocks and DAI-generated blocks
     * all use the same format.
     *
     * Supported authoring forms:
     *  - explicit blocks: {"blocks":[{"block":"minecraft:stone","pos":[0,0,0]}]}
     *  - layered palette maps: {"palette":{"#":"minecraft:stone"},"layers":[["###","# #","###"]]}
     */
    private static List<JsonObject> expandBlockStructure(
            JsonObject structure,
            Map<String, ?> variables
    ) {
        List<JsonObject> out = new ArrayList<>();
        if (structure == null) return out;

        int limit = Math.max(1, Math.min(16384,
                DAI_SceneDefinition.integer(structure, "max_blocks", 8192)));
        double blockSize = Math.max(0.001D,
                DAI_SceneDefinition.number(structure, "block_size", 1.0D));
        double scaleX = DAI_SceneDefinition.number(structure, "scale_x", 1.0D);
        double scaleY = DAI_SceneDefinition.number(structure, "scale_y", 1.0D);
        double scaleZ = DAI_SceneDefinition.number(structure, "scale_z", 1.0D);
        double yaw = Math.toRadians(DAI_SceneDefinition.number(structure, "yaw", 0.0D));
        boolean centered = DAI_SceneDefinition.bool(structure, "centered", true);
        Vec3 base = vec(structure, "position",
                DAI_SceneDefinition.number(structure, "x", 0.0D),
                DAI_SceneDefinition.number(structure, "y", 0.0D),
                DAI_SceneDefinition.number(structure, "z", 0.0D));

        JsonObject palette = DAI_SceneDefinition.object(structure, "palette");
        JsonArray explicit = array(structure, "blocks");
        for (JsonElement entry : explicit) {
            if (out.size() >= limit) break;
            if (entry == null || !entry.isJsonObject()) continue;
            JsonObject block = entry.getAsJsonObject();
            String rawBlock = DAI_TemplateEngine.resolve(
                    DAI_SceneDefinition.string(block, "block", DAI_SceneDefinition.string(block, "id", "")),
                    variables
            );
            rawBlock = paletteValue(palette, rawBlock, variables);
            if (rawBlock.isBlank() || rawBlock.equals("minecraft:air") || Identifier.tryParse(rawBlock) == null) continue;

            Vec3 local = vec(block, "pos",
                    DAI_SceneDefinition.number(block, "x", 0.0D),
                    DAI_SceneDefinition.number(block, "y", 0.0D),
                    DAI_SceneDefinition.number(block, "z", 0.0D));
            out.add(blockElement(structure, block, rawBlock,
                    transformStructurePoint(base, local, blockSize, scaleX, scaleY, scaleZ, yaw),
                    blockSize));
        }

        JsonArray layers = array(structure, "layers");
        if (layers.size() > 0 && out.size() < limit) {
            int layerCount = layers.size();
            int maxRows = 0;
            int maxColumns = 0;
            for (JsonElement layerElement : layers) {
                JsonArray rows = layerElement != null && layerElement.isJsonArray()
                        ? layerElement.getAsJsonArray() : new JsonArray();
                maxRows = Math.max(maxRows, rows.size());
                for (JsonElement rowElement : rows) {
                    if (rowElement != null && rowElement.isJsonPrimitive()) {
                        maxColumns = Math.max(maxColumns, rowElement.getAsString().length());
                    }
                }
            }

            double centerX = centered ? (maxColumns - 1) * 0.5D : 0.0D;
            double centerY = centered ? (layerCount - 1) * 0.5D : 0.0D;
            double centerZ = centered ? (maxRows - 1) * 0.5D : 0.0D;

            for (int ly = 0; ly < layers.size() && out.size() < limit; ly++) {
                JsonElement layerElement = layers.get(ly);
                if (layerElement == null || !layerElement.isJsonArray()) continue;
                JsonArray rows = layerElement.getAsJsonArray();
                for (int rz = 0; rz < rows.size() && out.size() < limit; rz++) {
                    JsonElement rowElement = rows.get(rz);
                    if (rowElement == null || !rowElement.isJsonPrimitive()) continue;
                    String row = rowElement.getAsString();
                    for (int cx = 0; cx < row.length() && out.size() < limit; cx++) {
                        String token = String.valueOf(row.charAt(cx));
                        if (token.isBlank()) continue;
                        String rawBlock = paletteValue(palette, token, variables);
                        if (rawBlock.isBlank() || rawBlock.equals("minecraft:air") || Identifier.tryParse(rawBlock) == null) continue;

                        Vec3 local = new Vec3(cx - centerX, ly - centerY, rz - centerZ);
                        out.add(blockElement(structure, null, rawBlock,
                                transformStructurePoint(base, local, blockSize, scaleX, scaleY, scaleZ, yaw),
                                blockSize));
                    }
                }
            }
        }
        return out;
    }

    private static JsonObject blockElement(
            JsonObject structure,
            JsonObject override,
            String blockId,
            Vec3 point,
            double blockSize
    ) {
        JsonObject child = new JsonObject();
        child.addProperty("type", "block");
        child.addProperty("block", blockId);
        JsonArray position = new JsonArray();
        position.add(point.x());
        position.add(point.y());
        position.add(point.z());
        child.add("position", position);
        child.addProperty("width", blockSize * DAI_SceneDefinition.number(structure, "model_width", 1.0D));
        child.addProperty("height", blockSize * DAI_SceneDefinition.number(structure, "model_height", 1.0D));
        child.addProperty("grid_fit", DAI_SceneDefinition.bool(structure, "grid_fit", true));
        copyNumber(structure, child, "model_fit");
        copyNumber(structure, child, "seam_overlap_pixels");
        copyNumber(structure, child, "near");
        copyNumber(structure, child, "far");
        copyNumber(structure, child, "min_pixels");
        copyNumber(structure, child, "max_pixels");
        copyNumber(structure, child, "model_scale");
        copyNumber(structure, child, "screen_rotation");
        if (override != null) {
            copyNumber(override, child, "model_scale");
            copyNumber(override, child, "screen_rotation");
        }
        return child;
    }

    private static void copyNumber(JsonObject source, JsonObject target, String key) {
        if (source != null && source.has(key) && source.get(key).isJsonPrimitive()) {
            try { target.addProperty(key, source.get(key).getAsDouble()); }
            catch (RuntimeException ignored) { }
        }
    }

    private static String paletteValue(JsonObject palette, String token, Map<String, ?> variables) {
        String resolved = token == null ? "" : token.trim();
        if (palette != null && palette.has(resolved)) {
            try { resolved = palette.get(resolved).getAsString(); }
            catch (RuntimeException ignored) { return ""; }
        }
        return DAI_TemplateEngine.resolve(resolved, variables).trim();
    }

    private static Vec3 transformStructurePoint(
            Vec3 base,
            Vec3 local,
            double blockSize,
            double scaleX,
            double scaleY,
            double scaleZ,
            double yaw
    ) {
        double x = local.x() * blockSize * scaleX;
        double y = local.y() * blockSize * scaleY;
        double z = local.z() * blockSize * scaleZ;
        if (yaw != 0.0D) {
            double cos = Math.cos(yaw);
            double sin = Math.sin(yaw);
            double rotatedX = x * cos - z * sin;
            double rotatedZ = x * sin + z * cos;
            x = rotatedX;
            z = rotatedZ;
        }
        return new Vec3(base.x() + x, base.y() + y, base.z() + z);
    }

    private static Identifier safeTextureId(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty() || value.endsWith(":")) return null;
        return Identifier.tryParse(value);
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

    private static float snapHalf(double value) {
        return (float)(Math.rint(value * 2.0D) * 0.5D);
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
