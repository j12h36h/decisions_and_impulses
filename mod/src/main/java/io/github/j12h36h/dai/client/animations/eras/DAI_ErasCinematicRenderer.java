package io.github.j12h36h.dai.client.animations.eras;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.animations.eras.DAI_ErasCinematicDefinition;
import io.github.j12h36h.dai.animations.eras.DAI_ErasSampling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Software-style ERAS scene renderer targeting Minecraft's GUI extraction API. */
final class DAI_ErasCinematicRenderer {
    private DAI_ErasCinematicRenderer() {}

    static void render(
            GuiGraphicsExtractor graphics,
            DAI_ErasCinematicDefinition definition,
            double time,
            boolean renderScene,
            boolean renderBackground,
            boolean renderPost
    ) {
        if (graphics == null || definition == null) return;
        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        if (guiWidth <= 0 || guiHeight <= 0) return;

        double fit = Math.min(
                guiWidth / (double)Math.max(1, definition.canvasWidth()),
                guiHeight / (double)Math.max(1, definition.canvasHeight())
        );
        double stageWidth = definition.canvasWidth() * fit;
        double stageHeight = definition.canvasHeight() * fit;
        double offsetX = (guiWidth - stageWidth) * 0.5D;
        double offsetY = (guiHeight - stageHeight) * 0.5D;

        if (renderScene || renderBackground) {
            graphics.pose().pushMatrix();
            graphics.pose().translate((float)offsetX, (float)offsetY);
            graphics.pose().scale((float)fit, (float)fit);

            if (renderBackground) {
                graphics.fill(0, 0, definition.canvasWidth(), definition.canvasHeight(), color(definition.background(), 1.0D));
            }
            if (renderScene) {
                if (definition.is2D()) render2D(graphics, definition, time);
                else render3D(graphics, definition, time);
            }
            graphics.pose().popMatrix();
        }

        if (renderPost) applyPost(graphics, definition, time, guiWidth, guiHeight);
    }

    private static void render2D(GuiGraphicsExtractor graphics, DAI_ErasCinematicDefinition definition, double time) {
        DAI_ErasSampling.Camera2D camera = DAI_ErasSampling.camera2D(definition, time);
        for (JsonObject object : DAI_ErasSampling.worldObjects2D(definition, time)) {
            if (!DAI_ErasSampling.visible(object, time, definition.durationSeconds())) continue;
            String type = DAI_ErasSampling.string(object, "type", "").toLowerCase(Locale.ROOT);
            if (type.isBlank() || type.equals("group") || type.equals("bone") || type.equals("ik")
                    || DAI_ErasSampling.bool(object, "maskOnly", false)) continue;

            double dx = DAI_ErasSampling.number(object, "x", 0.0D) - camera.x();
            double dy = DAI_ErasSampling.number(object, "y", 0.0D) - camera.y();
            double radians = Math.toRadians(camera.rotation());
            double c = Math.cos(radians), s = Math.sin(radians);
            double x = definition.canvasWidth() * 0.5D + (dx * c + dy * s) * camera.zoom();
            double y = definition.canvasHeight() * 0.5D + (-dx * s + dy * c) * camera.zoom();
            double rotation = DAI_ErasSampling.number(object, "rotation", 0.0D) - camera.rotation();
            double scaleX = DAI_ErasSampling.number(object, "scaleX", DAI_ErasSampling.number(object, "scale", 1.0D)) * camera.zoom();
            double scaleY = DAI_ErasSampling.number(object, "scaleY", DAI_ErasSampling.number(object, "scale", 1.0D)) * camera.zoom();
            double opacity = DAI_ErasSampling.clamp(DAI_ErasSampling.number(object, "opacity", 1.0D), 0.0D, 1.0D);

            graphics.pose().pushMatrix();
            graphics.pose().translate((float)x, (float)y);
            if (rotation != 0.0D) graphics.pose().rotate((float)Math.toRadians(rotation));
            if (scaleX != 1.0D || scaleY != 1.0D) graphics.pose().scale((float)scaleX, (float)scaleY);
            graphics.pose().translate(
                    (float)-DAI_ErasSampling.number(object, "pivotX", 0.0D),
                    (float)-DAI_ErasSampling.number(object, "pivotY", 0.0D)
            );
            render2DObject(graphics, definition, object, type, opacity, time);
            graphics.pose().popMatrix();
        }
    }

    private static void render2DObject(
            GuiGraphicsExtractor graphics,
            DAI_ErasCinematicDefinition definition,
            JsonObject object,
            String type,
            double opacity,
            double time
    ) {
        int fill = paintColor(object.get("fill"), opacity, 0xFFFFFFFF);
        int stroke = paintColor(object.get("stroke"), opacity, 0xFFFFFFFF);
        int lineWidth = Math.max(1, (int)Math.round(DAI_ErasSampling.number(object, "lineWidth", 1.0D)));

        switch (type) {
            case "rect" -> {
                double width = DAI_ErasSampling.number(object, "width", 100.0D);
                double height = DAI_ErasSampling.number(object, "height", 100.0D);
                double ax = DAI_ErasSampling.number(object, "anchorX", 0.5D);
                double ay = DAI_ErasSampling.number(object, "anchorY", 0.5D);
                int left = (int)Math.round(-width * ax);
                int top = (int)Math.round(-height * ay);
                int w = Math.max(1, (int)Math.round(width));
                int h = Math.max(1, (int)Math.round(height));
                if (hasFill(object)) graphics.fill(left, top, left + w, top + h, fill);
                if (hasStroke(object)) drawRectOutline(graphics, left, top, w, h, lineWidth, stroke);
            }
            case "limb" -> {
                double length = Math.max(0.001D, DAI_ErasSampling.number(object, "length", DAI_ErasSampling.number(object, "height", 80.0D)));
                double widthStart = Math.max(0.001D, DAI_ErasSampling.number(object, "widthStart", DAI_ErasSampling.number(object, "width", 24.0D)));
                double widthEnd = Math.max(0.001D, DAI_ErasSampling.number(object, "widthEnd", widthStart * 0.82D));
                List<Point2> points = List.of(
                        new Point2(-widthStart / 2.0D, 0),
                        new Point2(widthStart / 2.0D, 0),
                        new Point2(widthEnd / 2.0D, length),
                        new Point2(-widthEnd / 2.0D, length)
                );
                if (hasFill(object)) fillPolygon(graphics, points, fill);
                if (hasStroke(object)) strokePolygon(graphics, points, true, lineWidth, stroke);
            }
            case "circle", "ellipse" -> {
                double rx = DAI_ErasSampling.number(object, "radiusX", DAI_ErasSampling.number(object, "radius", 50.0D));
                double ry = DAI_ErasSampling.number(object, "radiusY", DAI_ErasSampling.number(object, "radius", rx));
                if (hasFill(object)) fillEllipse(graphics, 0.0D, 0.0D, rx, ry, fill);
                if (hasStroke(object)) strokeEllipse(graphics, 0.0D, 0.0D, rx, ry, lineWidth, stroke);
            }
            case "polygon", "path" -> {
                List<Point2> points = points(object.has("points") ? object.get("points") : object.get("pathPoints"));
                boolean closed = DAI_ErasSampling.bool(object, "closed", true);
                if (!points.isEmpty() && hasFill(object) && closed) fillPolygon(graphics, points, fill);
                if (!points.isEmpty() && hasStroke(object)) strokePolygon(graphics, points, closed, lineWidth, stroke);
            }
            case "text" -> renderText(graphics, object, opacity);
            case "sprite" -> renderSprite(graphics, definition, object, opacity, time);
            case "emitter" -> renderEmitter(graphics, object, opacity, time);
            case "line" -> {
                double x1 = DAI_ErasSampling.number(object, "x1", 0.0D);
                double y1 = DAI_ErasSampling.number(object, "y1", 0.0D);
                double x2 = DAI_ErasSampling.number(object, "x2", DAI_ErasSampling.number(object, "width", 100.0D));
                double y2 = DAI_ErasSampling.number(object, "y2", 0.0D);
                drawLine(graphics, x1, y1, x2, y2, lineWidth, hasStroke(object) ? stroke : fill);
            }
            default -> {
                // Unknown browser-only objects remain safely invisible rather than corrupting the HUD.
            }
        }
    }

    private static void renderText(GuiGraphicsExtractor graphics, JsonObject object, double opacity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null) return;
        String text = DAI_ErasSampling.string(object, "text", "");
        if (text.isEmpty()) return;
        int color = paintColor(object.get("fill"), opacity, 0xFFFFFFFF);
        double pixelSize = parseFontPixels(DAI_ErasSampling.string(object, "font", "700 32px system-ui"));
        double fontScale = Math.max(0.1D, pixelSize / Math.max(1.0D, minecraft.font.lineHeight));
        String align = DAI_ErasSampling.string(object, "align", "left").toLowerCase(Locale.ROOT);
        int width = minecraft.font.width(text);
        double localX = switch (align) {
            case "center" -> -width * 0.5D;
            case "right", "end" -> -width;
            default -> 0.0D;
        };
        graphics.pose().pushMatrix();
        graphics.pose().scale((float)fontScale, (float)fontScale);
        graphics.text(minecraft.font, Component.literal(text), (int)Math.round(localX), 0, color);
        graphics.pose().popMatrix();
    }

    private static void renderSprite(
            GuiGraphicsExtractor graphics,
            DAI_ErasCinematicDefinition definition,
            JsonObject object,
            double opacity,
            double time
    ) {
        String assetId = DAI_ErasSampling.string(object, "asset", "");
        JsonObject assets = definition.assets();
        JsonObject asset = assets.has(assetId) && assets.get(assetId).isJsonObject()
                ? assets.getAsJsonObject(assetId)
                : new JsonObject();
        Identifier texture = resolveTexture(definition, DAI_ErasSampling.string(asset, "src", ""));
        double width = DAI_ErasSampling.number(object, "width", DAI_ErasSampling.number(asset, "frameWidth", 80.0D));
        double height = DAI_ErasSampling.number(object, "height", DAI_ErasSampling.number(asset, "frameHeight", 80.0D));
        double ax = DAI_ErasSampling.number(object, "anchorX", 0.5D);
        double ay = DAI_ErasSampling.number(object, "anchorY", 0.5D);
        int x = (int)Math.round(-width * ax);
        int y = (int)Math.round(-height * ay);
        int w = Math.max(1, (int)Math.round(width));
        int h = Math.max(1, (int)Math.round(height));

        if (texture == null) {
            drawRectOutline(graphics, x, y, w, h, 1, color("#ff6b92", opacity));
            drawLine(graphics, x, y, x + w, y + h, 1, color("#ff6b92", opacity));
            drawLine(graphics, x + w, y, x, y + h, 1, color("#ff6b92", opacity));
            return;
        }

        int frameWidth = Math.max(1, (int)Math.round(DAI_ErasSampling.number(asset, "frameWidth", width)));
        int frameHeight = Math.max(1, (int)Math.round(DAI_ErasSampling.number(asset, "frameHeight", height)));
        int columns = Math.max(1, (int)Math.round(DAI_ErasSampling.number(asset, "columns", 1.0D)));
        int frames = Math.max(1, (int)Math.round(DAI_ErasSampling.number(asset, "frames", 1.0D)));
        int rows = Math.max(1, (int)Math.ceil(frames / (double)columns));
        int textureWidth = Math.max(frameWidth, (int)Math.round(DAI_ErasSampling.number(asset, "textureWidth", frameWidth * columns)));
        int textureHeight = Math.max(frameHeight, (int)Math.round(DAI_ErasSampling.number(asset, "textureHeight", frameHeight * rows)));
        double fps = Math.max(0.0D, DAI_ErasSampling.number(asset, "fps", 0.0D));
        int frame = object.has("frame")
                ? (int)Math.floor(DAI_ErasSampling.number(object, "frame", 0.0D))
                : fps > 0.0D ? (int)Math.floor(time * fps) : 0;
        frame = Math.floorMod(frame, frames);
        int u = (frame % columns) * frameWidth;
        int v = (frame / columns) * frameHeight;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x,
                y,
                (float)u,
                (float)v,
                w,
                h,
                textureWidth,
                textureHeight,
                color("#ffffff", opacity)
        );
    }

    private static void renderEmitter(GuiGraphicsExtractor graphics, JsonObject object, double opacity, double time) {
        double elapsed = time - DAI_ErasSampling.number(object, "start", 0.0D);
        if (elapsed < 0.0D) return;
        double rate = DAI_ErasSampling.clamp(DAI_ErasSampling.number(object, "emitRate", DAI_ErasSampling.number(object, "rate", 20.0D)), 0.0D, 500.0D);
        double life = Math.max(0.01D, DAI_ErasSampling.number(object, "life", 1.0D));
        int burst = (int)DAI_ErasSampling.clamp(Math.floor(DAI_ErasSampling.number(object, "burst", 0.0D)), 0.0D, 1000.0D);
        int maxParticles = (int)DAI_ErasSampling.clamp(Math.floor(DAI_ErasSampling.number(object, "maxParticles", 500.0D)), 1.0D, 1500.0D);
        int seed = stringSeed(DAI_ErasSampling.string(object, "seed", DAI_ErasSampling.string(object, "id", "")));
        double direction = Math.toRadians(DAI_ErasSampling.number(object, "direction", -90.0D));
        double spread = Math.toRadians(Math.abs(DAI_ErasSampling.number(object, "spread", 40.0D)));
        double speed = DAI_ErasSampling.number(object, "speed", 140.0D);
        double jitter = Math.max(0.0D, DAI_ErasSampling.number(object, "speedJitter", 0.25D));
        double gx = DAI_ErasSampling.number(object, "gravityX", 0.0D);
        double gy = DAI_ErasSampling.number(object, "gravityY", 0.0D);
        int timedCount = rate > 0.0D ? (int)Math.floor(elapsed * rate) + 1 : 0;
        int total = Math.min(maxParticles, burst + timedCount);
        for (int i = 0; i < total; i++) {
            double born = i < burst ? 0.0D : (i - burst) / Math.max(rate, 0.0001D);
            double age = elapsed - born;
            if (age < 0.0D || age > life) continue;
            double q = DAI_ErasSampling.clamp(age / life, 0.0D, 1.0D);
            double angle = direction + (particleRand(seed, i, 1) - 0.5D) * spread;
            double velocity = speed * (1.0D + (particleRand(seed, i, 2) * 2.0D - 1.0D) * jitter);
            double px = Math.cos(angle) * velocity * age + 0.5D * gx * age * age;
            double py = Math.sin(angle) * velocity * age + 0.5D * gy * age * age;
            double size = DAI_ErasSampling.lerp(
                    DAI_ErasSampling.number(object, "size", 8.0D),
                    DAI_ErasSampling.number(object, "sizeEnd", 0.0D),
                    q
            );
            double alpha = opacity * DAI_ErasSampling.clamp(DAI_ErasSampling.lerp(
                    DAI_ErasSampling.number(object, "particleOpacity", 1.0D),
                    DAI_ErasSampling.number(object, "opacityEnd", 0.0D),
                    q
            ), 0.0D, 1.0D);
            if (size <= 0.0D || alpha <= 0.0D) continue;
            int particleColor = mixColor(
                    DAI_ErasSampling.string(object, "color", "#ffffff"),
                    DAI_ErasSampling.string(object, "colorEnd", DAI_ErasSampling.string(object, "color", "#ffffff")),
                    q,
                    alpha
            );
            String shape = DAI_ErasSampling.string(object, "particleShape", "circle").toLowerCase(Locale.ROOT);
            if (shape.equals("rect")) {
                int half = Math.max(1, (int)Math.round(size * 0.5D));
                graphics.fill((int)Math.round(px)-half, (int)Math.round(py)-half, (int)Math.round(px)+half, (int)Math.round(py)+half, particleColor);
            } else if (shape.equals("line")) {
                drawLine(graphics, px, py, px - Math.cos(angle)*size*2.0D, py - Math.sin(angle)*size*2.0D,
                        Math.max(1, (int)Math.round(DAI_ErasSampling.number(object, "lineWidth", 2.0D))), particleColor);
            } else {
                fillEllipse(graphics, px, py, size * 0.5D, size * 0.5D, particleColor);
            }
        }
    }

    private static void render3D(GuiGraphicsExtractor graphics, DAI_ErasCinematicDefinition definition, double time) {
        DAI_ErasSampling.Camera3D camera = DAI_ErasSampling.camera3D(definition, time);
        List<JsonObject> objects = DAI_ErasSampling.worldObjects3D(definition, time);
        ArrayList<Face> faces = new ArrayList<>();
        ArrayList<SphereCommand> spheres = new ArrayList<>();

        for (JsonObject object : objects) {
            if (!DAI_ErasSampling.visible(object, time, definition.durationSeconds())) continue;
            String type = DAI_ErasSampling.string(object, "type", "").toLowerCase(Locale.ROOT);
            if (type.equals("group") || type.equals("bone") || type.isBlank()) continue;
            if (type.equals("box")) collectBox(definition, camera, object, faces);
            else if (type.equals("plane")) collectPlane(definition, camera, object, faces);
            else if (type.equals("sphere")) collectSphere(definition, camera, object, spheres);
        }

        faces.sort(Comparator.comparingDouble(Face::depth).reversed());
        for (Face face : faces) {
            if (face.fill() != 0) fillPolygon(graphics, face.points(), face.fill());
            if (face.stroke() != 0) strokePolygon(graphics, face.points(), true, face.lineWidth(), face.stroke());
        }
        spheres.sort(Comparator.comparingDouble(SphereCommand::depth).reversed());
        for (SphereCommand sphere : spheres) {
            if (sphere.fill() != 0) fillEllipse(graphics, sphere.x(), sphere.y(), sphere.radius(), sphere.radius(), sphere.fill());
            if (sphere.stroke() != 0) strokeEllipse(graphics, sphere.x(), sphere.y(), sphere.radius(), sphere.radius(), sphere.lineWidth(), sphere.stroke());
        }
    }

    private static void collectBox(
            DAI_ErasCinematicDefinition definition,
            DAI_ErasSampling.Camera3D camera,
            JsonObject object,
            List<Face> faces
    ) {
        double w = DAI_ErasSampling.number(object, "width", 2.0D) / 2.0D;
        double h = DAI_ErasSampling.number(object, "height", 2.0D) / 2.0D;
        double d = DAI_ErasSampling.number(object, "depth", 2.0D) / 2.0D;
        double[][] raw = {{-w,-h,-d},{w,-h,-d},{w,h,-d},{-w,h,-d},{-w,-h,d},{w,-h,d},{w,h,d},{-w,h,d}};
        ArrayList<DAI_ErasSampling.Vec3> vertices = new ArrayList<>();
        for (double[] p : raw) vertices.add(objectVertex(object, new DAI_ErasSampling.Vec3(p[0], p[1], p[2])));
        int[][] indices = {{0,1,2,3},{4,5,6,7},{0,1,5,4},{2,3,7,6},{1,2,6,5},{0,3,7,4}};
        for (int[] face : indices) {
            ArrayList<DAI_ErasSampling.Vec3> world = new ArrayList<>();
            for (int index : face) world.add(vertices.get(index));
            addFace(definition, camera, object, world, faces);
        }
    }

    private static void collectPlane(
            DAI_ErasCinematicDefinition definition,
            DAI_ErasSampling.Camera3D camera,
            JsonObject object,
            List<Face> faces
    ) {
        double w = DAI_ErasSampling.number(object, "width", 2.0D) / 2.0D;
        double h = DAI_ErasSampling.number(object, "height", 2.0D) / 2.0D;
        List<DAI_ErasSampling.Vec3> world = List.of(
                objectVertex(object, new DAI_ErasSampling.Vec3(-w,-h,0)),
                objectVertex(object, new DAI_ErasSampling.Vec3(w,-h,0)),
                objectVertex(object, new DAI_ErasSampling.Vec3(w,h,0)),
                objectVertex(object, new DAI_ErasSampling.Vec3(-w,h,0))
        );
        addFace(definition, camera, object, world, faces);
    }

    private static void collectSphere(
            DAI_ErasCinematicDefinition definition,
            DAI_ErasSampling.Camera3D camera,
            JsonObject object,
            List<SphereCommand> spheres
    ) {
        DAI_ErasSampling.Vec3 center = new DAI_ErasSampling.Vec3(
                DAI_ErasSampling.number(object, "x", 0.0D),
                DAI_ErasSampling.number(object, "y", 0.0D),
                DAI_ErasSampling.number(object, "z", 0.0D)
        );
        CameraPoint cameraPoint = cameraPoint(center, camera);
        if (cameraPoint.z() < camera.near() || cameraPoint.z() > camera.far()) return;
        double scale = camera.focalLength() / cameraPoint.z();
        double radius = Math.max(0.0D, DAI_ErasSampling.number(object, "radius", 1.0D)
                * DAI_ErasSampling.number(object, "scale", 1.0D) * scale);
        if (radius <= 0.0D) return;
        double x = definition.canvasWidth() * 0.5D + cameraPoint.x() * scale;
        double y = definition.canvasHeight() * 0.5D - cameraPoint.y() * scale;
        double opacity = DAI_ErasSampling.clamp(DAI_ErasSampling.number(object, "opacity", 1.0D), 0.0D, 1.0D);
        int fill = hasFill(object) ? shadedPaint(definition, object.get("fill"), opacity) : 0;
        int stroke = hasStroke(object) ? paintColor(object.get("stroke"), opacity, 0xFFFFFFFF) : 0;
        int lineWidth = Math.max(1, (int)Math.round(DAI_ErasSampling.number(object, "lineWidth", 1.0D)));
        spheres.add(new SphereCommand(x, y, radius, cameraPoint.z(), fill, stroke, lineWidth));
    }

    private static void addFace(
            DAI_ErasCinematicDefinition definition,
            DAI_ErasSampling.Camera3D camera,
            JsonObject object,
            List<DAI_ErasSampling.Vec3> world,
            List<Face> faces
    ) {
        ArrayList<Point2> projected = new ArrayList<>();
        double depth = 0.0D;
        for (DAI_ErasSampling.Vec3 point : world) {
            CameraPoint cameraPoint = cameraPoint(point, camera);
            if (cameraPoint.z() < camera.near() || cameraPoint.z() > camera.far()) return;
            double scale = camera.focalLength() / cameraPoint.z();
            projected.add(new Point2(
                    definition.canvasWidth() * 0.5D + cameraPoint.x() * scale,
                    definition.canvasHeight() * 0.5D - cameraPoint.y() * scale
            ));
            depth += cameraPoint.z();
        }
        depth /= Math.max(1, world.size());
        double opacity = DAI_ErasSampling.clamp(DAI_ErasSampling.number(object, "opacity", 1.0D), 0.0D, 1.0D);
        int fill = hasFill(object) ? shadedPaint(definition, object.get("fill"), opacity) : 0;
        int stroke = hasStroke(object) ? paintColor(object.get("stroke"), opacity, 0xFFFFFFFF) : 0;
        int lineWidth = Math.max(1, (int)Math.round(DAI_ErasSampling.number(object, "lineWidth", 1.0D)));
        faces.add(new Face(List.copyOf(projected), depth, fill, stroke, lineWidth));
    }

    private static DAI_ErasSampling.Vec3 objectVertex(JsonObject object, DAI_ErasSampling.Vec3 local) {
        double scale = DAI_ErasSampling.number(object, "scale", 1.0D);
        DAI_ErasSampling.Vec3 scaled = local.scale(scale);
        DAI_ErasSampling.Vec3 rotated = DAI_ErasSampling.rotatePoint(
                scaled,
                Math.toRadians(DAI_ErasSampling.number(object, "rotationX", 0.0D)),
                Math.toRadians(DAI_ErasSampling.number(object, "rotationY", 0.0D)),
                Math.toRadians(DAI_ErasSampling.number(object, "rotationZ", 0.0D))
        );
        return new DAI_ErasSampling.Vec3(
                rotated.x() + DAI_ErasSampling.number(object, "x", 0.0D),
                rotated.y() + DAI_ErasSampling.number(object, "y", 0.0D),
                rotated.z() + DAI_ErasSampling.number(object, "z", 0.0D)
        );
    }

    private static CameraPoint cameraPoint(DAI_ErasSampling.Vec3 point, DAI_ErasSampling.Camera3D camera) {
        DAI_ErasSampling.Vec3 relative = new DAI_ErasSampling.Vec3(
                point.x() - camera.x(), point.y() - camera.y(), point.z() - camera.z()
        );
        DAI_ErasSampling.Vec3 rotated = DAI_ErasSampling.rotatePoint(
                relative,
                -Math.toRadians(camera.rotateX()),
                -Math.toRadians(camera.rotateY()),
                -Math.toRadians(camera.rotateZ())
        );
        return new CameraPoint(rotated.x(), rotated.y(), rotated.z());
    }

    private static void applyPost(
            GuiGraphicsExtractor graphics,
            DAI_ErasCinematicDefinition definition,
            double time,
            int width,
            int height
    ) {
        JsonObject post = DAI_ErasSampling.sampledPost(definition, time);
        double exposure = Math.max(0.0D, DAI_ErasSampling.number(post, "exposure", 1.0D));
        double vignette = DAI_ErasSampling.clamp(DAI_ErasSampling.number(post, "vignette", 0.0D), 0.0D, 1.0D);
        double letterbox = DAI_ErasSampling.clamp(DAI_ErasSampling.number(post, "letterbox", 0.0D), 0.0D, 0.49D);
        double fade = DAI_ErasSampling.clamp(DAI_ErasSampling.number(post, "fade", 0.0D), 0.0D, 1.0D);
        double flash = DAI_ErasSampling.clamp(DAI_ErasSampling.number(post, "flash", 0.0D), 0.0D, 1.0D);
        double grain = DAI_ErasSampling.clamp(DAI_ErasSampling.number(post, "grain", 0.0D), 0.0D, 1.0D);
        double tintOpacity = DAI_ErasSampling.clamp(DAI_ErasSampling.number(post, "tintOpacity", 0.0D), 0.0D, 1.0D);

        if (exposure < 1.0D) graphics.fill(0, 0, width, height, color("#000000", (1.0D - exposure) * 0.55D));
        else if (exposure > 1.0D) graphics.fill(0, 0, width, height, color("#ffffff", Math.min(0.35D, (exposure - 1.0D) * 0.20D)));

        if (tintOpacity > 0.0D) graphics.fill(0, 0, width, height, color(DAI_ErasSampling.string(post, "tint", "#ffffff"), tintOpacity));

        if (vignette > 0.0D) {
            int layers = 12;
            int maxInset = Math.max(1, Math.min(width, height) / 6);
            for (int i = 0; i < layers; i++) {
                double q = 1.0D - i / (double)layers;
                int alpha = (int)Math.round(255.0D * vignette * 0.10D * q);
                if (alpha <= 0) continue;
                int c = (alpha << 24);
                int inset = (int)Math.round(maxInset * i / (double)layers);
                graphics.outline(inset, inset, Math.max(1, width - inset*2), Math.max(1, height - inset*2), c);
            }
        }

        if (grain > 0.0D) {
            int count = Math.min(420, Math.max(0, (int)Math.round(width * height / 1800.0D * grain)));
            long seed = 1469598103934665603L ^ Double.doubleToLongBits(Math.floor(time * 30.0D) / 30.0D);
            for (int i = 0; i < count; i++) {
                seed ^= seed << 13; seed ^= seed >>> 7; seed ^= seed << 17;
                int x = Math.floorMod((int)seed, Math.max(1, width));
                int y = Math.floorMod((int)(seed >>> 32), Math.max(1, height));
                int a = Math.min(40, Math.max(2, (int)Math.round(grain * 70.0D)));
                graphics.fill(x, y, x + 1, y + 1, ((a & 255) << 24) | 0x00FFFFFF);
            }
        }

        if (fade > 0.0D) graphics.fill(0, 0, width, height, color("#000000", fade));
        if (flash > 0.0D) graphics.fill(0, 0, width, height, color("#ffffff", flash));

        if (letterbox > 0.0D) {
            int bar = Math.max(1, (int)Math.round(height * letterbox));
            graphics.fill(0, 0, width, bar, 0xFF000000);
            graphics.fill(0, height - bar, width, height, 0xFF000000);
        }
    }

    private static int shadedPaint(DAI_ErasCinematicDefinition definition, JsonElement value, double opacity) {
        int base = paintColor(value, opacity, 0xFFFFFFFF);
        JsonObject ambient = DAI_ErasSampling.object(definition.lighting(), "ambient");
        double intensity = DAI_ErasSampling.clamp(DAI_ErasSampling.number(ambient, "intensity", definition.is3D() ? 0.28D : 1.0D), 0.05D, 2.0D);
        return multiplyRgb(base, intensity);
    }

    private static int paintColor(JsonElement element, double opacity, int fallback) {
        if (element == null || element.isJsonNull()) return withAlpha(fallback, opacity);
        if (element.isJsonPrimitive()) return color(element.getAsString(), opacity);
        if (element.isJsonObject()) {
            JsonObject gradient = element.getAsJsonObject();
            JsonArray stops = DAI_ErasSampling.array(gradient, "stops");
            if (!stops.isEmpty()) {
                JsonObject first = stops.get(0).isJsonObject() ? stops.get(0).getAsJsonObject() : new JsonObject();
                JsonObject last = stops.get(stops.size()-1).isJsonObject() ? stops.get(stops.size()-1).getAsJsonObject() : first;
                return mixColor(
                        DAI_ErasSampling.string(first, "color", "#ffffff"),
                        DAI_ErasSampling.string(last, "color", "#ffffff"),
                        0.5D,
                        opacity
                );
            }
        }
        return withAlpha(fallback, opacity);
    }

    private static boolean hasFill(JsonObject object) {
        return object != null && object.has("fill") && !object.get("fill").isJsonNull()
                && !(object.get("fill").isJsonPrimitive() && object.get("fill").getAsJsonPrimitive().isBoolean() && !object.get("fill").getAsBoolean());
    }

    private static boolean hasStroke(JsonObject object) {
        return object != null && object.has("stroke") && !object.get("stroke").isJsonNull();
    }

    private static void fillEllipse(GuiGraphicsExtractor graphics, double cx, double cy, double rx, double ry, int color) {
        rx = Math.abs(rx); ry = Math.abs(ry);
        int radiusY = Math.max(1, (int)Math.ceil(ry));
        for (int iy = -radiusY; iy <= radiusY; iy++) {
            double q = iy / Math.max(0.0001D, ry);
            if (Math.abs(q) > 1.0D) continue;
            double half = rx * Math.sqrt(Math.max(0.0D, 1.0D - q*q));
            int left = (int)Math.floor(cx - half);
            int right = (int)Math.ceil(cx + half);
            int y = (int)Math.round(cy + iy);
            graphics.fill(left, y, Math.max(left + 1, right), y + 1, color);
        }
    }

    private static void strokeEllipse(GuiGraphicsExtractor graphics, double cx, double cy, double rx, double ry, int lineWidth, int color) {
        int segments = Math.max(24, Math.min(160, (int)Math.ceil(Math.max(Math.abs(rx), Math.abs(ry)) * 0.8D)));
        Point2 previous = null;
        Point2 first = null;
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0D * i / segments;
            Point2 point = new Point2(cx + Math.cos(angle)*rx, cy + Math.sin(angle)*ry);
            if (first == null) first = point;
            if (previous != null) drawLine(graphics, previous.x(), previous.y(), point.x(), point.y(), lineWidth, color);
            previous = point;
        }
    }

    private static void fillPolygon(GuiGraphicsExtractor graphics, List<Point2> points, int color) {
        if (points == null || points.size() < 3) return;
        double minY = points.stream().mapToDouble(Point2::y).min().orElse(0.0D);
        double maxY = points.stream().mapToDouble(Point2::y).max().orElse(0.0D);
        int startY = (int)Math.floor(minY);
        int endY = (int)Math.ceil(maxY);
        ArrayList<Double> intersections = new ArrayList<>();
        for (int y = startY; y <= endY; y++) {
            double scanY = y + 0.5D;
            intersections.clear();
            for (int i = 0; i < points.size(); i++) {
                Point2 a = points.get(i);
                Point2 b = points.get((i + 1) % points.size());
                if ((a.y() <= scanY && b.y() > scanY) || (b.y() <= scanY && a.y() > scanY)) {
                    double t = (scanY - a.y()) / (b.y() - a.y());
                    intersections.add(DAI_ErasSampling.lerp(a.x(), b.x(), t));
                }
            }
            intersections.sort(Double::compareTo);
            for (int i = 0; i + 1 < intersections.size(); i += 2) {
                int left = (int)Math.floor(intersections.get(i));
                int right = (int)Math.ceil(intersections.get(i + 1));
                if (right > left) graphics.fill(left, y, right, y + 1, color);
            }
        }
    }

    private static void strokePolygon(GuiGraphicsExtractor graphics, List<Point2> points, boolean closed, int width, int color) {
        if (points == null || points.size() < 2) return;
        for (int i = 1; i < points.size(); i++) {
            Point2 a = points.get(i - 1), b = points.get(i);
            drawLine(graphics, a.x(), a.y(), b.x(), b.y(), width, color);
        }
        if (closed) {
            Point2 a = points.getLast(), b = points.getFirst();
            drawLine(graphics, a.x(), a.y(), b.x(), b.y(), width, color);
        }
    }

    private static void drawRectOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int thickness, int color) {
        int t = Math.max(1, thickness);
        graphics.fill(x, y, x + width, y + t, color);
        graphics.fill(x, y + height - t, x + width, y + height, color);
        graphics.fill(x, y, x + t, y + height, color);
        graphics.fill(x + width - t, y, x + width, y + height, color);
    }

    private static void drawLine(GuiGraphicsExtractor graphics, double x1, double y1, double x2, double y2, int thickness, int color) {
        double dx = x2 - x1, dy = y2 - y1;
        int steps = Math.max(1, (int)Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        int half = Math.max(0, thickness / 2);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double)steps;
            int x = (int)Math.round(DAI_ErasSampling.lerp(x1, x2, t));
            int y = (int)Math.round(DAI_ErasSampling.lerp(y1, y2, t));
            graphics.fill(x - half, y - half, x - half + Math.max(1, thickness), y - half + Math.max(1, thickness), color);
        }
    }

    private static List<Point2> points(JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        ArrayList<Point2> result = new ArrayList<>();
        for (JsonElement point : element.getAsJsonArray()) {
            try {
                if (point.isJsonArray() && point.getAsJsonArray().size() >= 2) {
                    result.add(new Point2(point.getAsJsonArray().get(0).getAsDouble(), point.getAsJsonArray().get(1).getAsDouble()));
                } else if (point.isJsonObject()) {
                    result.add(new Point2(
                            DAI_ErasSampling.number(point.getAsJsonObject(), "x", 0.0D),
                            DAI_ErasSampling.number(point.getAsJsonObject(), "y", 0.0D)
                    ));
                }
            } catch (RuntimeException ignored) {}
        }
        return List.copyOf(result);
    }

    private static Identifier resolveTexture(DAI_ErasCinematicDefinition definition, String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().replace('\\', '/');
        try {
            if (value.startsWith("assets/")) {
                String[] parts = value.split("/", 3);
                if (parts.length == 3) return Identifier.fromNamespaceAndPath(parts[1], parts[2]);
            }
            if (value.contains(":")) {
                Identifier parsed = Identifier.tryParse(value);
                if (parsed == null) return null;
                String path = parsed.getPath();
                if (!path.startsWith("textures/")) path = "textures/" + path;
                return Identifier.fromNamespaceAndPath(parsed.getNamespace(), path);
            }
            while (value.startsWith("./")) value = value.substring(2);
            while (value.startsWith("/")) value = value.substring(1);
            if (!value.startsWith("textures/")) value = "textures/dai/cinematics/" + value;
            return Identifier.fromNamespaceAndPath(definition.id().getNamespace(), value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static double parseFontPixels(String font) {
        if (font == null) return 32.0D;
        String[] tokens = font.split("\\s+");
        for (String token : tokens) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (!lower.endsWith("px")) continue;
            try { return Math.max(1.0D, Double.parseDouble(lower.substring(0, lower.length() - 2))); }
            catch (NumberFormatException ignored) {}
        }
        return 32.0D;
    }

    private static int color(String raw, double opacity) {
        int[] rgba = parseColor(raw);
        int alpha = (int)Math.round(255.0D * DAI_ErasSampling.clamp(opacity * (rgba[3] / 255.0D), 0.0D, 1.0D));
        return (alpha << 24) | (rgba[0] << 16) | (rgba[1] << 8) | rgba[2];
    }

    private static int mixColor(String a, String b, double t, double opacity) {
        int[] ca = parseColor(a), cb = parseColor(b);
        int r = clamp255((int)Math.round(DAI_ErasSampling.lerp(ca[0], cb[0], t)));
        int g = clamp255((int)Math.round(DAI_ErasSampling.lerp(ca[1], cb[1], t)));
        int bl = clamp255((int)Math.round(DAI_ErasSampling.lerp(ca[2], cb[2], t)));
        double sourceAlpha = DAI_ErasSampling.lerp(ca[3] / 255.0D, cb[3] / 255.0D, t);
        int alpha = clamp255((int)Math.round(255.0D * opacity * sourceAlpha));
        return (alpha << 24) | (r << 16) | (g << 8) | bl;
    }

    private static int[] parseColor(String raw) {
        String value = raw == null ? "#ffffff" : raw.trim().toLowerCase(Locale.ROOT);
        try {
            if (value.matches("#[0-9a-f]{3}")) {
                int r = Integer.parseInt(value.substring(1,2) + value.substring(1,2), 16);
                int g = Integer.parseInt(value.substring(2,3) + value.substring(2,3), 16);
                int b = Integer.parseInt(value.substring(3,4) + value.substring(3,4), 16);
                return new int[]{r,g,b,255};
            }
            if (value.matches("#[0-9a-f]{6}")) {
                return new int[]{Integer.parseInt(value.substring(1,3),16), Integer.parseInt(value.substring(3,5),16), Integer.parseInt(value.substring(5,7),16), 255};
            }
            if (value.startsWith("rgb(" ) || value.startsWith("rgba(")) {
                int left = value.indexOf('('), right = value.lastIndexOf(')');
                if (left >= 0 && right > left) {
                    String[] parts = value.substring(left + 1, right).split(",");
                    int r = clamp255((int)Math.round(Double.parseDouble(parts[0].trim())));
                    int g = clamp255((int)Math.round(Double.parseDouble(parts[1].trim())));
                    int b = clamp255((int)Math.round(Double.parseDouble(parts[2].trim())));
                    int a = parts.length >= 4 ? clamp255((int)Math.round(Double.parseDouble(parts[3].trim()) * 255.0D)) : 255;
                    return new int[]{r,g,b,a};
                }
            }
        } catch (RuntimeException ignored) {}
        return new int[]{170,205,220,255};
    }

    private static int multiplyRgb(int argb, double factor) {
        int a = (argb >>> 24) & 255;
        int r = clamp255((int)Math.round(((argb >>> 16) & 255) * factor));
        int g = clamp255((int)Math.round(((argb >>> 8) & 255) * factor));
        int b = clamp255((int)Math.round((argb & 255) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int argb, double opacity) {
        int baseAlpha = (argb >>> 24) & 255;
        int alpha = clamp255((int)Math.round(baseAlpha * DAI_ErasSampling.clamp(opacity, 0.0D, 1.0D)));
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    private static int clamp255(int value) { return Math.max(0, Math.min(255, value)); }

    private static int stringSeed(String value) {
        int h = 0x811C9DC5;
        for (int i = 0; i < value.length(); i++) {
            h ^= value.charAt(i);
            h *= 0x01000193;
        }
        return h;
    }

    private static double particleRand(int seed, int index, int salt) {
        int x = seed ^ (index + 1) * 0x9E3779B1 ^ (salt + 1) * 0x5F356495;
        x ^= x << 13; x ^= x >>> 17; x ^= x << 5;
        return Integer.toUnsignedLong(x) / 4294967296.0D;
    }

    private record Point2(double x, double y) {}
    private record CameraPoint(double x, double y, double z) {}
    private record Face(List<Point2> points, double depth, int fill, int stroke, int lineWidth) {}
    private record SphereCommand(double x, double y, double radius, double depth, int fill, int stroke, int lineWidth) {}
}
