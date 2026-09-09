package io.github.j12h36h.dai.animations.eras;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic sampler mirroring the timing rules used by the ERAS / S.A.D.
 * browser renderer. Times are seconds, not Minecraft ticks.
 */
public final class DAI_ErasSampling {
    private DAI_ErasSampling() {}

    public record Camera3D(
            double x, double y, double z,
            double rotateX, double rotateY, double rotateZ,
            double focalLength,
            double shake,
            double near,
            double far
    ) {}

    public record Camera2D(
            double x, double y,
            double zoom,
            double rotation,
            double shake
    ) {}

    public record Vec3(double x, double y, double z) {
        public Vec3 add(Vec3 other) { return new Vec3(x + other.x, y + other.y, z + other.z); }
        public Vec3 scale(double amount) { return new Vec3(x * amount, y * amount, z * amount); }
    }

    public static JsonObject resolved(
            DAI_ErasCinematicDefinition definition,
            JsonObject source,
            double timeSeconds
    ) {
        if (source == null) return new JsonObject();
        double sampleTime = sampleAnimationTime(definition, source, timeSeconds);
        JsonObject out = source.deepCopy();
        LinkedHashSet<String> keys = new LinkedHashSet<>(List.of(
                "x", "y", "z", "width", "height", "depth", "radius", "radiusX", "radiusY",
                "rotation", "rotationX", "rotationY", "rotationZ", "scale", "scaleX", "scaleY",
                "skewX", "skewY", "pivotX", "pivotY", "opacity", "visible", "anchorX", "anchorY",
                "frame", "state", "pose", "intensity", "range", "fov", "shake", "zoom",
                "targetX", "targetY", "targetZ", "volume", "pan", "rate", "frequency", "exposure",
                "vignette", "letterbox", "fade", "flash", "grain", "tintOpacity", "points"
        ));
        JsonArray frames = array(source, "keyframes");
        for (JsonElement element : frames) {
            if (!element.isJsonObject()) continue;
            for (String key : element.getAsJsonObject().keySet()) {
                if (!Set.of("t", "easing", "interpolation", "hold").contains(key)) keys.add(key);
            }
        }
        for (String key : keys) {
            JsonElement value = keyframeValue(source, key, sampleTime);
            if (value != null) out.add(key, value);
        }
        applyClip(definition, out, source, sampleTime);
        return out;
    }

    public static Camera3D camera3D(DAI_ErasCinematicDefinition definition, double timeSeconds) {
        JsonObject source = definition.camera();
        JsonObject camera = source.deepCopy();
        for (String key : List.of(
                "x", "y", "z", "rotateX", "rotateY", "rotateZ", "fov", "shake",
                "targetX", "targetY", "targetZ", "near", "far"
        )) {
            JsonElement value = cameraTrackValue(source, key, timeSeconds);
            if (value != null) camera.add(key, value);
        }

        double x = number(camera, "x", 0.0D);
        double y = number(camera, "y", 0.0D);
        double z = number(camera, "z", -8.0D);
        double rotateX = number(camera, "rotateX", 0.0D);
        double rotateY = number(camera, "rotateY", 0.0D);
        double rotateZ = number(camera, "rotateZ", 0.0D);
        double focal = Math.max(10.0D, number(camera, "fov", 520.0D));
        double shake = Math.max(0.0D, number(camera, "shake", 0.0D));
        double near = clamp(number(camera, "near", 0.2D), 0.01D, 1000.0D);
        double far = Math.max(near + 0.01D, number(camera, "far", 5000.0D));

        if (finiteElement(camera, "targetX") && finiteElement(camera, "targetY") && finiteElement(camera, "targetZ")) {
            double dx = number(camera, "targetX", x) - x;
            double dy = number(camera, "targetY", y) - y;
            double dz = number(camera, "targetZ", z) - z;
            double horizontal = Math.hypot(dx, dz);
            if (horizontal < 0.0001D) horizontal = 0.0001D;
            rotateY = Math.toDegrees(Math.atan2(dx, dz));
            rotateX = -Math.toDegrees(Math.atan2(dy, horizontal));
        }

        if (shake > 0.0D) {
            double t = timeSeconds;
            x += Math.sin(t * 47.1D) * shake * 0.035D;
            y += Math.cos(t * 39.7D) * shake * 0.027D;
            rotateZ += Math.sin(t * 61.3D) * shake * 0.42D;
            rotateX += Math.cos(t * 53.2D) * shake * 0.22D;
        }
        return new Camera3D(x, y, z, rotateX, rotateY, rotateZ, focal, shake, near, far);
    }

    public static Camera2D camera2D(DAI_ErasCinematicDefinition definition, double timeSeconds) {
        JsonObject source = definition.camera();
        JsonObject camera = source.deepCopy();
        for (String key : List.of("x", "y", "zoom", "rotation", "shake")) {
            JsonElement value = cameraTrackValue(source, key, timeSeconds);
            if (value != null) camera.add(key, value);
        }
        double x = number(camera, "x", definition.canvasWidth() / 2.0D);
        double y = number(camera, "y", definition.canvasHeight() / 2.0D);
        double zoom = clamp(number(camera, "zoom", 1.0D), 0.02D, 64.0D);
        double rotation = number(camera, "rotation", 0.0D);
        double shake = Math.max(0.0D, number(camera, "shake", 0.0D));
        if (shake > 0.0D) {
            double t = timeSeconds;
            x += Math.sin(t * 47.7D) * shake;
            y += Math.cos(t * 39.3D) * shake * 0.8D;
            rotation += Math.sin(t * 58.2D) * shake * 0.05D;
        }
        return new Camera2D(x, y, zoom, rotation, shake);
    }

    public static JsonObject sampledPost(DAI_ErasCinematicDefinition definition, double timeSeconds) {
        JsonObject source = definition.post();
        JsonObject out = source.deepCopy();
        for (String key : List.of("exposure", "vignette", "letterbox", "fade", "flash", "grain", "tint", "tintOpacity")) {
            JsonElement value = keyframeValue(source, key, timeSeconds);
            if (value != null) out.add(key, value);
        }
        return out;
    }

    /** Resolves parent transforms for the synthetic 3D ERAS scene. */
    public static List<JsonObject> worldObjects3D(DAI_ErasCinematicDefinition definition, double timeSeconds) {
        List<JsonObject> source = definition.objects();
        Map<String, JsonObject> map = new HashMap<>();
        for (JsonObject object : source) {
            String id = string(object, "id", "");
            if (!id.isBlank()) map.put(id, object);
        }
        Map<String, JsonObject> cache = new HashMap<>();
        ArrayList<JsonObject> result = new ArrayList<>();
        for (JsonObject object : source) {
            JsonObject resolved = resolveWorldObject3D(definition, object, map, cache, new HashSet<>(), timeSeconds);
            if (resolved != null) result.add(resolved);
        }
        return result;
    }

    /** Resolves parent transforms and v1.4 IK endpoint constraints for 2D scenes. */
    public static List<JsonObject> worldObjects2D(DAI_ErasCinematicDefinition definition, double timeSeconds) {
        List<JsonObject> source = definition.objects();
        Map<String, JsonObject> map = new HashMap<>();
        for (JsonObject object : source) {
            String id = string(object, "id", "");
            if (!id.isBlank()) map.put(id, object);
        }
        Map<String, JsonObject> ikOverrides = buildIkOverrides(definition, source, map, timeSeconds);
        Map<String, JsonObject> cache = new HashMap<>();
        ArrayList<JsonObject> result = new ArrayList<>();
        for (JsonObject object : source) {
            JsonObject resolved = resolveWorldObject2D(definition, object, map, cache, new HashSet<>(), ikOverrides, timeSeconds);
            if (resolved != null) result.add(resolved);
        }
        // ERAS v1.3.2 draws lower layers first while preserving the original JSON
        // object order when two objects share the same layer/zIndex. List.sort is
        // stable, so compare only the layer here. Sorting by id as a tiebreaker
        // incorrectly reordered the whole scene (for example, a later "sky"
        // object could cover earlier forest/city/title objects in Musashi Story).
        result.sort(Comparator.comparingDouble(
                (JsonObject o) -> number(o, "layer", number(o, "zIndex", 0.0D))
        ));
        return result;
    }

    private static JsonObject resolveWorldObject3D(
            DAI_ErasCinematicDefinition definition,
            JsonObject raw,
            Map<String, JsonObject> map,
            Map<String, JsonObject> cache,
            Set<String> stack,
            double time
    ) {
        String id = string(raw, "id", "");
        if (!id.isBlank() && cache.containsKey(id)) return cache.get(id).deepCopy();
        if (!id.isBlank() && stack.contains(id)) {
            JsonObject cycle = resolved(definition, raw, time);
            cycle.addProperty("visible", false);
            cycle.addProperty("_hierarchyCycle", true);
            cache.put(id, cycle);
            return cycle.deepCopy();
        }
        Set<String> next = new HashSet<>(stack);
        if (!id.isBlank()) next.add(id);
        JsonObject object = resolved(definition, raw, time);
        String parent = parentRef(object.get("parent"));
        if (parent.isBlank()) parent = parentRef(object.get("parentId"));
        if (!parent.isBlank() && map.containsKey(parent)) {
            JsonObject parentObject = resolveWorldObject3D(definition, map.get(parent), map, cache, next, time);
            if (bool(parentObject, "_hierarchyCycle", false)) {
                object.addProperty("visible", false);
                object.addProperty("_hierarchyCycle", true);
            } else {
                object = inherit3D(object, parentObject, definition.durationSeconds());
            }
        }
        if (!id.isBlank()) cache.put(id, object.deepCopy());
        return object;
    }

    private static JsonObject resolveWorldObject2D(
            DAI_ErasCinematicDefinition definition,
            JsonObject raw,
            Map<String, JsonObject> map,
            Map<String, JsonObject> cache,
            Set<String> stack,
            Map<String, JsonObject> ikOverrides,
            double time
    ) {
        String id = string(raw, "id", "");
        if (!id.isBlank() && cache.containsKey(id)) return cache.get(id).deepCopy();
        if (!id.isBlank() && stack.contains(id)) {
            JsonObject cycle = resolved(definition, raw, time);
            cycle.addProperty("visible", false);
            cycle.addProperty("_hierarchyCycle", true);
            cache.put(id, cycle);
            return cycle.deepCopy();
        }
        Set<String> next = new HashSet<>(stack);
        if (!id.isBlank()) next.add(id);
        JsonObject object = resolved(definition, raw, time);
        JsonObject override = ikOverrides.get(id);
        if (override != null) {
            if (finiteElement(override, "x")) object.addProperty("x", number(override, "x", number(object, "x", 0.0D)));
            if (finiteElement(override, "y")) object.addProperty("y", number(override, "y", number(object, "y", 0.0D)));
            if (finiteElement(override, "rotation")) object.addProperty("rotation", number(override, "rotation", number(object, "rotation", 0.0D)));
        }
        String parent = parentRef(object.get("parent"));
        if (parent.isBlank()) parent = parentRef(object.get("parentId"));
        if (!parent.isBlank() && map.containsKey(parent)) {
            JsonObject parentObject = resolveWorldObject2D(definition, map.get(parent), map, cache, next, ikOverrides, time);
            if (bool(parentObject, "_hierarchyCycle", false)) {
                object.addProperty("visible", false);
                object.addProperty("_hierarchyCycle", true);
            } else {
                object = inherit2D(object, parentObject, definition.durationSeconds());
            }
        }
        if (!id.isBlank()) cache.put(id, object.deepCopy());
        return object;
    }

    private static JsonObject inherit3D(JsonObject object, JsonObject parent, double duration) {
        JsonObject out = object.deepCopy();
        double parentScale = number(parent, "scale", 1.0D);
        Vec3 local = rotatePoint(
                new Vec3(
                        number(object, "x", 0.0D) * parentScale,
                        number(object, "y", 0.0D) * parentScale,
                        number(object, "z", 0.0D) * parentScale
                ),
                Math.toRadians(number(parent, "rotationX", 0.0D)),
                Math.toRadians(number(parent, "rotationY", 0.0D)),
                Math.toRadians(number(parent, "rotationZ", 0.0D))
        );
        out.addProperty("x", number(parent, "x", 0.0D) + local.x());
        out.addProperty("y", number(parent, "y", 0.0D) + local.y());
        out.addProperty("z", number(parent, "z", 0.0D) + local.z());
        out.addProperty("rotationX", number(parent, "rotationX", 0.0D) + number(object, "rotationX", 0.0D));
        out.addProperty("rotationY", number(parent, "rotationY", 0.0D) + number(object, "rotationY", 0.0D));
        out.addProperty("rotationZ", number(parent, "rotationZ", 0.0D) + number(object, "rotationZ", 0.0D));
        out.addProperty("scale", parentScale * number(object, "scale", 1.0D));
        out.addProperty("opacity", clamp(number(parent, "opacity", 1.0D) * number(object, "opacity", 1.0D), 0.0D, 1.0D));
        out.addProperty("visible", bool(parent, "visible", true) && bool(object, "visible", true));
        out.addProperty("start", Math.max(number(parent, "start", 0.0D), number(object, "start", 0.0D)));
        out.addProperty("end", Math.min(number(parent, "end", duration), number(object, "end", duration)));
        return out;
    }

    private static JsonObject inherit2D(JsonObject object, JsonObject parent, double duration) {
        JsonObject out = object.deepCopy();
        double parentScaleX = number(parent, "scaleX", number(parent, "scale", 1.0D));
        double parentScaleY = number(parent, "scaleY", number(parent, "scale", 1.0D));
        double parentRotation = Math.toRadians(number(parent, "rotation", 0.0D));
        double parentPivotX = number(parent, "pivotX", 0.0D);
        double parentPivotY = number(parent, "pivotY", 0.0D);
        double localX = (number(object, "x", 0.0D) - parentPivotX) * parentScaleX;
        double localY = (number(object, "y", 0.0D) - parentPivotY) * parentScaleY;
        double rotatedX = localX * Math.cos(parentRotation) - localY * Math.sin(parentRotation);
        double rotatedY = localX * Math.sin(parentRotation) + localY * Math.cos(parentRotation);
        out.addProperty("x", number(parent, "x", 0.0D) + rotatedX);
        out.addProperty("y", number(parent, "y", 0.0D) + rotatedY);
        out.addProperty("rotation", number(parent, "rotation", 0.0D) + number(object, "rotation", 0.0D));
        out.addProperty("skewX", number(parent, "skewX", 0.0D) + number(object, "skewX", 0.0D));
        out.addProperty("skewY", number(parent, "skewY", 0.0D) + number(object, "skewY", 0.0D));
        out.addProperty("scaleX", parentScaleX * number(object, "scaleX", number(object, "scale", 1.0D)));
        out.addProperty("scaleY", parentScaleY * number(object, "scaleY", number(object, "scale", 1.0D)));
        out.addProperty("scale", 1.0D);
        out.addProperty("opacity", clamp(number(parent, "opacity", 1.0D) * number(object, "opacity", 1.0D), 0.0D, 1.0D));
        out.addProperty("visible", bool(parent, "visible", true) && bool(object, "visible", true));
        out.addProperty("start", Math.max(number(parent, "start", 0.0D), number(object, "start", 0.0D)));
        out.addProperty("end", Math.min(number(parent, "end", duration), number(object, "end", duration)));
        return out;
    }

    private static Map<String, JsonObject> buildIkOverrides(
            DAI_ErasCinematicDefinition definition,
            List<JsonObject> objects,
            Map<String, JsonObject> map,
            double time
    ) {
        HashMap<String, JsonObject> overrides = new HashMap<>();
        for (JsonObject raw : objects) {
            if (!"ik".equals(string(raw, "type", "").toLowerCase(Locale.ROOT))) continue;
            JsonObject ik = resolved(definition, raw, time);
            if (!visible(ik, time, definition.durationSeconds())) continue;
            String rootId = parentRef(first(ik, "root", "rootBone"));
            String jointId = parentRef(first(ik, "joint", "jointBone"));
            if (rootId.isBlank() || jointId.isBlank() || !map.containsKey(rootId) || !map.containsKey(jointId)) continue;

            JsonObject root = resolved(definition, map.get(rootId), time);
            JsonObject joint = resolved(definition, map.get(jointId), time);
            double defaultL1 = Math.hypot(number(joint, "x", 0.0D), number(joint, "y", 0.0D));
            if (defaultL1 <= 0.001D) defaultL1 = 80.0D;
            double l1 = Math.max(0.001D, number(ik, "upperLength", defaultL1));
            double l2 = Math.max(0.001D, number(ik, "lowerLength", number(ik, "endLength", 80.0D)));
            double dx = number(ik, "targetX", 0.0D) - number(root, "x", 0.0D);
            double dy = number(ik, "targetY", 0.0D) - number(root, "y", 0.0D);
            double rawDistance = Math.hypot(dx, dy);
            double distance = clamp(rawDistance, Math.abs(l1 - l2) + 0.001D, l1 + l2 - 0.001D);
            double bend = number(ik, "bend", 1.0D) >= 0.0D ? 1.0D : -1.0D;
            double base = Math.atan2(-dx, dy);
            double shoulderOffset = Math.acos(clamp((l1*l1 + distance*distance - l2*l2) / (2.0D*l1*distance), -1.0D, 1.0D));
            double inner = Math.acos(clamp((l1*l1 + l2*l2 - distance*distance) / (2.0D*l1*l2), -1.0D, 1.0D));
            double rootRotation = Math.toDegrees(base + bend * shoulderOffset);
            double jointRotation = Math.toDegrees(-bend * (Math.PI - inner));

            JsonObject rootOverride = new JsonObject();
            rootOverride.addProperty("rotation", rootRotation);
            overrides.put(rootId, rootOverride);

            JsonObject jointOverride = new JsonObject();
            if (bool(ik, "pinJoint", true)) {
                jointOverride.addProperty("x", 0.0D);
                jointOverride.addProperty("y", l1);
            }
            jointOverride.addProperty("rotation", jointRotation);
            overrides.put(jointId, jointOverride);

            String endId = parentRef(first(ik, "end", "endBone", "effector", "endEffector"));
            if (!endId.isBlank() && map.containsKey(endId) && bool(ik, "pinEnd", true)) {
                JsonObject endOverride = new JsonObject();
                endOverride.addProperty("x", 0.0D);
                endOverride.addProperty("y", l2);
                overrides.put(endId, endOverride);
            }
        }
        return overrides;
    }

    private static void applyClip(
            DAI_ErasCinematicDefinition definition,
            JsonObject out,
            JsonObject object,
            double sampleTime
    ) {
        String name = string(object, "clip", "");
        if (name.isBlank()) return;
        JsonObject clips = definition.clips();
        if (!clips.has(name) || !clips.get(name).isJsonObject()) return;
        JsonObject clip = clips.getAsJsonObject(name);
        double duration = Math.max(0.0001D, number(clip, "duration", 1.0D));
        double rate = number(object, "clipRate", 1.0D);
        double start = number(object, "clipStart", 0.0D);
        double local = (sampleTime - start) * rate;
        if (local < 0.0D) return;
        boolean loop = object.has("clipLoop") ? bool(object, "clipLoop", true) : bool(clip, "loop", true);
        if (loop) local = positiveModulo(local, duration);
        else local = clamp(local, 0.0D, duration);

        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (JsonElement frameElement : array(clip, "keyframes")) {
            if (!frameElement.isJsonObject()) continue;
            for (String key : frameElement.getAsJsonObject().keySet()) {
                if (!Set.of("t", "easing", "interpolation", "hold").contains(key)) keys.add(key);
            }
        }
        for (String key : keys) {
            JsonElement value = keyframeValue(clip, key, local);
            if (value != null) out.add(key, value);
        }
    }

    private static double sampleAnimationTime(
            DAI_ErasCinematicDefinition definition,
            JsonObject object,
            double time
    ) {
        double t = time - number(object, "animationLag", 0.0D);
        JsonObject timing = definition.timing();
        double fps = number(object, "animationFps", number(object, "holdFps", number(timing, "objectFps", 0.0D)));
        fps = clamp(fps, 0.0D, 240.0D);
        if (fps > 0.0D) t = Math.floor(Math.max(0.0D, t) * fps + 1e-7D) / fps;
        return t;
    }

    public static JsonElement keyframeValue(JsonObject object, String key, double time) {
        if (object == null || key == null) return null;
        JsonElement base = object.has(key) ? object.get(key).deepCopy() : null;
        ArrayList<JsonObject> frames = new ArrayList<>();
        for (JsonElement element : array(object, "keyframes")) {
            if (!element.isJsonObject()) continue;
            JsonObject frame = element.getAsJsonObject();
            if (!finiteElement(frame, "t") || !frame.has(key)) continue;
            frames.add(frame);
        }
        frames.sort(Comparator.comparingDouble(frame -> number(frame, "t", 0.0D)));
        if (frames.isEmpty()) return base;
        double firstT = number(frames.getFirst(), "t", 0.0D);
        if (time < firstT) return base;
        if (time == firstT) return frames.getFirst().get(key).deepCopy();
        double lastT = number(frames.getLast(), "t", 0.0D);
        if (time >= lastT) return frames.getLast().get(key).deepCopy();
        for (int i = 0; i < frames.size() - 1; i++) {
            JsonObject from = frames.get(i);
            JsonObject to = frames.get(i + 1);
            double at = number(from, "t", 0.0D);
            double bt = number(to, "t", at);
            if (time < at || time > bt) continue;
            double raw = (time - at) / Math.max(0.000001D, bt - at);
            if (isHoldFrame(from)) return from.get(key).deepCopy();
            String easing = string(to, "easing", string(from, "easing", "linear"));
            double eased = ease(raw, easing);
            return interpolateValue(from.get(key), to.get(key), eased, raw);
        }
        return base;
    }

    private static JsonElement cameraTrackValue(JsonObject camera, String key, double time) {
        JsonElement base = camera.has(key) ? camera.get(key).deepCopy() : null;
        ArrayList<JsonObject> frames = new ArrayList<>();
        for (JsonElement element : array(camera, "keyframes")) {
            if (!element.isJsonObject()) continue;
            JsonObject frame = element.getAsJsonObject();
            if (!finiteElement(frame, "t") || !frame.has(key)) continue;
            frames.add(frame);
        }
        frames.sort(Comparator.comparingDouble(frame -> number(frame, "t", 0.0D)));
        if (frames.isEmpty()) return base;
        double firstT = number(frames.getFirst(), "t", 0.0D);
        if (time < firstT) return base;
        if (time == firstT) return frames.getFirst().get(key).deepCopy();
        double lastT = number(frames.getLast(), "t", 0.0D);
        if (time >= lastT) return frames.getLast().get(key).deepCopy();

        for (int i = 0; i < frames.size() - 1; i++) {
            JsonObject from = frames.get(i);
            JsonObject to = frames.get(i + 1);
            double at = number(from, "t", 0.0D);
            double bt = number(to, "t", at);
            if (time < at || time > bt) continue;
            JsonElement a = from.get(key);
            JsonElement b = to.get(key);
            if (a == null || b == null || !a.isJsonPrimitive() || !b.isJsonPrimitive()
                    || !a.getAsJsonPrimitive().isNumber() || !b.getAsJsonPrimitive().isNumber()) {
                return rawChoice(a, b, (time - at) / Math.max(0.000001D, bt - at));
            }
            double raw = (time - at) / Math.max(0.000001D, bt - at);
            String easing = string(to, "easing", string(from, "easing", ""));
            String interpolation = string(to, "interpolation",
                    string(from, "interpolation", string(camera, "interpolation", "spline"))).toLowerCase(Locale.ROOT);
            if (isHoldFrame(from) || Set.of("step", "hold", "event").contains(interpolation)
                    || Set.of("step", "hold", "event").contains(easing.toLowerCase(Locale.ROOT))) {
                return a.deepCopy();
            }
            double av = a.getAsDouble();
            double bv = b.getAsDouble();
            if ("linear".equals(interpolation)) return new JsonPrimitive(lerp(av, bv, ease(raw, easing.isBlank() ? "linear" : easing)));
            if (Set.of("x", "y", "z", "targetX", "targetY", "targetZ").contains(key)) {
                double p0 = av;
                double p3 = bv;
                if (i > 0 && finiteElement(frames.get(i - 1), key)) p0 = number(frames.get(i - 1), key, av);
                if (i + 2 < frames.size() && finiteElement(frames.get(i + 2), key)) p3 = number(frames.get(i + 2), key, bv);
                double spline = catmullRom(p0, av, bv, p3, raw);
                double linear = lerp(av, bv, smootherStep(raw));
                double smoothing = clamp(number(camera, "smoothing", 1.0D), 0.0D, 1.0D);
                return new JsonPrimitive(lerp(linear, spline, smoothing));
            }
            return new JsonPrimitive(lerp(av, bv, easing.isBlank() ? smootherStep(raw) : ease(raw, easing)));
        }
        return base;
    }

    private static JsonElement interpolateValue(JsonElement a, JsonElement b, double eased, double raw) {
        if (a == null) return b == null ? null : b.deepCopy();
        if (b == null) return a.deepCopy();
        if (a.isJsonPrimitive() && b.isJsonPrimitive()) {
            JsonPrimitive ap = a.getAsJsonPrimitive();
            JsonPrimitive bp = b.getAsJsonPrimitive();
            if (ap.isNumber() && bp.isNumber()) return new JsonPrimitive(lerp(ap.getAsDouble(), bp.getAsDouble(), eased));
            return raw < 1.0D ? a.deepCopy() : b.deepCopy();
        }
        if (a.isJsonArray() && b.isJsonArray() && a.getAsJsonArray().size() == b.getAsJsonArray().size()) {
            JsonArray out = new JsonArray();
            JsonArray aa = a.getAsJsonArray();
            JsonArray bb = b.getAsJsonArray();
            for (int i = 0; i < aa.size(); i++) out.add(interpolateValue(aa.get(i), bb.get(i), eased, raw));
            return out;
        }
        if (a.isJsonObject() && b.isJsonObject()) {
            JsonObject out = new JsonObject();
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            keys.addAll(a.getAsJsonObject().keySet());
            keys.addAll(b.getAsJsonObject().keySet());
            for (String key : keys) {
                JsonElement av = a.getAsJsonObject().get(key);
                JsonElement bv = b.getAsJsonObject().get(key);
                if (av != null && bv != null) out.add(key, interpolateValue(av, bv, eased, raw));
                else if (raw < 1.0D && av != null) out.add(key, av.deepCopy());
                else if (bv != null) out.add(key, bv.deepCopy());
            }
            return out;
        }
        return raw < 1.0D ? a.deepCopy() : b.deepCopy();
    }

    public static Vec3 rotatePoint(Vec3 point, double rx, double ry, double rz) {
        double x = point.x(), y = point.y(), z = point.z();
        double c = Math.cos(rx), s = Math.sin(rx);
        double y1 = y * c - z * s;
        double z1 = y * s + z * c;
        y = y1; z = z1;
        c = Math.cos(ry); s = Math.sin(ry);
        double x1 = x * c + z * s;
        z1 = -x * s + z * c;
        x = x1; z = z1;
        c = Math.cos(rz); s = Math.sin(rz);
        x1 = x * c - y * s;
        y1 = x * s + y * c;
        return new Vec3(x1, y1, z);
    }

    public static boolean visible(JsonObject object, double time, double duration) {
        return object != null
                && bool(object, "visible", true)
                && time >= number(object, "start", 0.0D)
                && time <= number(object, "end", duration);
    }

    public static double number(JsonObject object, String key, double fallback) {
        try {
            if (object != null && object.has(key)) {
                double value = object.get(key).getAsDouble();
                return Double.isFinite(value) ? value : fallback;
            }
        } catch (RuntimeException ignored) {}
        return fallback;
    }

    public static String string(JsonObject object, String key, String fallback) {
        try {
            if (object != null && object.has(key) && object.get(key).isJsonPrimitive()) return object.get(key).getAsString();
        } catch (RuntimeException ignored) {}
        return fallback == null ? "" : fallback;
    }

    public static boolean bool(JsonObject object, String key, boolean fallback) {
        try {
            if (object != null && object.has(key)) return object.get(key).getAsBoolean();
        } catch (RuntimeException ignored) {}
        return fallback;
    }

    public static JsonObject object(JsonObject parent, String key) {
        if (parent != null && parent.has(key) && parent.get(key).isJsonObject()) return parent.getAsJsonObject(key).deepCopy();
        return new JsonObject();
    }

    public static JsonArray array(JsonObject parent, String key) {
        if (parent != null && parent.has(key) && parent.get(key).isJsonArray()) return parent.getAsJsonArray(key).deepCopy();
        return new JsonArray();
    }

    public static String parentRef(JsonElement value) {
        try {
            if (value == null || value.isJsonNull()) return "";
            if (value.isJsonObject()) return string(value.getAsJsonObject(), "id", "");
            return value.getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    public static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    public static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    public static double ease(double raw, String type) {
        double t = clamp(raw, 0.0D, 1.0D);
        String normalized = type == null ? "linear" : type.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "step" -> 0.0D;
            case "ease-in", "in", "quad-in" -> t * t;
            case "ease-out", "out", "quad-out" -> 1.0D - (1.0D - t) * (1.0D - t);
            case "ease-in-out", "in-out" -> t < 0.5D ? 2.0D*t*t : 1.0D - Math.pow(-2.0D*t + 2.0D, 2.0D) / 2.0D;
            default -> t;
        };
    }

    private static boolean isHoldFrame(JsonObject frame) {
        if (frame == null) return false;
        if (bool(frame, "hold", false)) return true;
        String mode = string(frame, "interpolation", string(frame, "easing", "")).toLowerCase(Locale.ROOT);
        return mode.equals("hold") || mode.equals("event");
    }

    private static boolean finiteElement(JsonObject object, String key) {
        try {
            return object != null && object.has(key) && Double.isFinite(object.get(key).getAsDouble());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static JsonElement first(JsonObject object, String... keys) {
        if (object == null) return null;
        for (String key : keys) if (object.has(key)) return object.get(key);
        return null;
    }

    private static JsonElement rawChoice(JsonElement a, JsonElement b, double raw) {
        if (a == null) return b == null ? null : b.deepCopy();
        if (b == null) return a.deepCopy();
        return raw < 1.0D ? a.deepCopy() : b.deepCopy();
    }

    private static double catmullRom(double a, double b, double c, double d, double t) {
        double t2 = t*t, t3 = t2*t;
        return 0.5D * ((2.0D*b) + (-a+c)*t + (2.0D*a-5.0D*b+4.0D*c-d)*t2 + (-a+3.0D*b-3.0D*c+d)*t3);
    }

    private static double smootherStep(double raw) {
        double t = clamp(raw, 0.0D, 1.0D);
        return t*t*t*(t*(t*6.0D-15.0D)+10.0D);
    }

    private static double positiveModulo(double value, double divisor) {
        return ((value % divisor) + divisor) % divisor;
    }
}
