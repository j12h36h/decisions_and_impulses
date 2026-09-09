package io.github.j12h36h.dai.client.animations.eras;

import io.github.j12h36h.dai.animations.eras.DAI_ErasCinematicDefinition;
import io.github.j12h36h.dai.animations.eras.DAI_ErasCinematicRegistry;
import io.github.j12h36h.dai.animations.eras.DAI_ErasSampling;
import io.github.j12h36h.dai.logics.action.DAI_ActionArguments;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Client cinematic player for unchanged ERAS / Simple Animation Designer JSON.
 *
 * A 3D ERAS camera is anchored to the player's eye at play time. Authored +X
 * maps to camera-right, +Y to camera-up, and +Z to camera-forward, so a browser
 * cinematic can be authored around an arbitrary local origin and then reused
 * at any Minecraft location.
 */
public final class DAI_ErasCinematicRuntime {
    public static final Identifier GUI_LAYER = Identifier.fromNamespaceAndPath(
            DAI_Core.MODID,
            "eras_cinematic"
    );

    private static final Set<String> FINISHED = new HashSet<>();
    private static Active active;

    private DAI_ErasCinematicRuntime() {}

    public static boolean play(String rawId) {
        return play(rawId, DAI_ActionArguments.EMPTY);
    }

    public static boolean play(String rawId, DAI_ActionArguments arguments) {
        DAI_ErasCinematicDefinition definition = DAI_ErasCinematicRegistry.get(rawId);
        Minecraft minecraft = Minecraft.getInstance();
        if (definition == null || minecraft == null || minecraft.player == null || minecraft.level == null) return false;

        DAI_ActionArguments args = arguments == null ? DAI_ActionArguments.EMPTY : arguments;
        String id = definition.id().toString();
        Vec3 anchor = minecraft.player.getEyePosition();
        float baseYaw = minecraft.player.getYRot();
        float basePitch = minecraft.player.getXRot();
        DAI_ErasSampling.Camera3D baseCamera = definition.is3D()
                ? DAI_ErasSampling.camera3D(definition, 0.0D)
                : new DAI_ErasSampling.Camera3D(0,0,0,0,0,0,520,0,0.2,5000);

        boolean driveCamera = args.bool("drive_camera", args.bool("camera", definition.is3D()));
        boolean renderScene = args.bool("render_scene", true);
        boolean renderBackground = args.bool("render_background", definition.is2D());
        boolean renderPost = args.bool("render_post", true);
        boolean hideHud = args.bool("hide_hud", true);
        boolean lockInput = args.bool("lock_input", true);
        boolean relativeCamera = !"absolute".equals(args.normalized("camera_space", "relative"));
        double worldScale = clampFinite(args.number("world_scale", 1.0D), 0.0001D, 4096.0D, 1.0D);
        double speed = clampFinite(args.number("speed", 1.0D), 0.01D, 100.0D, 1.0D);
        double startSeconds = clampFinite(args.number("start_time", 0.0D), 0.0D, definition.durationSeconds(), 0.0D);
        boolean loop = args.has("loop") ? args.bool("loop", definition.loop()) : definition.loop();

        active = new Active(
                id,
                definition,
                anchor,
                baseYaw,
                basePitch,
                baseCamera,
                driveCamera,
                renderScene,
                renderBackground,
                renderPost,
                hideHud,
                lockInput,
                relativeCamera,
                worldScale,
                speed,
                loop,
                startSeconds * 20.0D,
                false,
                System.nanoTime()
        );
        FINISHED.remove(id);
        DAI_Core.LOGGER.info(
                "<DAI>: Playing ERAS cinematic '{}' mode={} duration={}s scene={} camera={}.",
                id,
                definition.is3D() ? "3D" : "2D",
                definition.durationSeconds(),
                renderScene,
                driveCamera
        );
        return true;
    }

    public static boolean stop(String rawId) {
        if (active == null) return false;
        String id = normalize(rawId);
        if (!id.isBlank() && !active.id.equals(id)) return false;
        finishActive();
        return true;
    }

    public static boolean pause(String rawId) {
        if (!matches(rawId)) return false;
        active.paused = true;
        return true;
    }

    public static boolean resume(String rawId) {
        if (!matches(rawId)) return false;
        active.paused = false;
        active.lastTickNanos = System.nanoTime();
        return true;
    }

    public static boolean isPlaying(String rawId) { return matches(rawId); }
    public static boolean isPaused(String rawId) { return matches(rawId) && active.paused; }
    public static boolean finished(String rawId) { return FINISHED.contains(normalize(rawId)); }
    public static boolean active() { return active != null; }
    public static boolean ownsInput() { return active != null && active.lockInput; }
    public static boolean hidesHud() { return active != null && active.hideHud; }
    public static String activeId() { return active == null ? "" : active.id; }

    public static double timeSeconds() {
        return active == null ? 0.0D : renderTimeSeconds(active);
    }

    public static void tick() {
        if (active == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            clear();
            return;
        }
        if (active.paused) {
            active.lastTickNanos = System.nanoTime();
            return;
        }

        active.tickPosition += active.speed;
        active.lastTickNanos = System.nanoTime();
        double durationTicks = Math.max(1.0D, active.definition.durationSeconds() * 20.0D);
        if (active.tickPosition >= durationTicks) {
            if (active.loop) {
                active.tickPosition = active.tickPosition % durationTicks;
            } else {
                finishActive();
            }
        }
    }

    public static Vec3 cameraPosition(float ignoredPartialTick) {
        Active current = active;
        if (current == null || !current.driveCamera || !current.definition.is3D()) return null;
        DAI_ErasSampling.Camera3D camera = DAI_ErasSampling.camera3D(current.definition, renderTimeSeconds(current));
        if (!current.relativeCamera) {
            return new Vec3(camera.x() * current.worldScale, camera.y() * current.worldScale, camera.z() * current.worldScale);
        }

        double dx = (camera.x() - current.baseCamera.x()) * current.worldScale;
        double dy = (camera.y() - current.baseCamera.y()) * current.worldScale;
        double dz = (camera.z() - current.baseCamera.z()) * current.worldScale;

        Basis basis = basis(current.baseYaw, current.basePitch);
        return current.anchor
                .add(basis.right.scale(dx))
                .add(basis.up.scale(dy))
                .add(basis.forward.scale(dz));
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Active current = active;
        if (current == null || !current.driveCamera || !current.definition.is3D()) return;
        DAI_ErasSampling.Camera3D camera = DAI_ErasSampling.camera3D(current.definition, renderTimeSeconds(current));
        double yawDelta = camera.rotateY() - current.baseCamera.rotateY();
        double pitchDelta = camera.rotateX() - current.baseCamera.rotateX();
        double rollDelta = camera.rotateZ() - current.baseCamera.rotateZ();
        event.setYaw((float)(current.baseYaw + yawDelta));
        event.setPitch((float)(current.basePitch + pitchDelta));
        event.setRoll((float)rollDelta);
    }

    public static void onFov(ViewportEvent.ComputeFov event) {
        Active current = active;
        if (current == null || !current.driveCamera || !current.definition.is3D()) return;
        DAI_ErasSampling.Camera3D camera = DAI_ErasSampling.camera3D(current.definition, renderTimeSeconds(current));
        double focal = Math.max(10.0D, camera.focalLength());
        double verticalFov = Math.toDegrees(2.0D * Math.atan(current.definition.canvasHeight() / (2.0D * focal)));
        event.setFOV((float)Math.max(1.0D, Math.min(175.0D, verticalFov)));
    }

    public static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Active current = active;
        if (current == null || graphics == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui.screen() != null) return;
        DAI_ErasCinematicRenderer.render(
                graphics,
                current.definition,
                renderTimeSeconds(current),
                current.renderScene,
                current.renderBackground,
                current.renderPost
        );
    }

    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        Active current = active;
        if (current == null || !current.hideHud || event == null) return;
        Identifier name = event.getName();
        if (GUI_LAYER.equals(name)) return;
        event.setCanceled(true);
    }

    public static void rebindReloadedDefinitions() {
        Active current = active;
        if (current == null) return;
        DAI_ErasCinematicDefinition refreshed = DAI_ErasCinematicRegistry.get(current.id);
        if (refreshed == null) {
            finishActive();
            return;
        }
        current.definition = refreshed;
        if (refreshed.is3D()) current.baseCamera = DAI_ErasSampling.camera3D(refreshed, 0.0D);
        double durationTicks = Math.max(1.0D, refreshed.durationSeconds() * 20.0D);
        if (current.tickPosition >= durationTicks) {
            if (current.loop) current.tickPosition %= durationTicks;
            else finishActive();
        }
    }

    public static void clear() {
        active = null;
        FINISHED.clear();
    }

    private static void finishActive() {
        if (active == null) return;
        String id = active.id;
        active = null;
        FINISHED.add(id);
        DAI_Core.LOGGER.info("<DAI>: ERAS cinematic '{}' finished/stopped.", id);
    }

    private static boolean matches(String rawId) {
        if (active == null) return false;
        String id = normalize(rawId);
        return id.isBlank() || active.id.equals(id);
    }

    private static double renderTimeSeconds(Active current) {
        if (current == null) return 0.0D;
        double ticks = current.tickPosition;
        if (!current.paused) {
            long elapsed = Math.max(0L, System.nanoTime() - current.lastTickNanos);
            double fraction = Math.min(1.0D, elapsed / 50_000_000.0D);
            ticks += fraction * current.speed;
        }
        double seconds = ticks / 20.0D;
        if (current.loop && current.definition.durationSeconds() > 0.0D) {
            seconds = ((seconds % current.definition.durationSeconds()) + current.definition.durationSeconds()) % current.definition.durationSeconds();
        }
        return Math.max(0.0D, Math.min(current.definition.durationSeconds(), seconds));
    }

    private static Basis basis(float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        Vec3 forward = normalize(new Vec3(
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)
        ));
        Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = normalize(cross(forward, worldUp));
        if (right.lengthSqr() < 1.0e-8D) right = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 up = normalize(cross(right, forward));
        return new Basis(right, up, forward);
    }

    private static Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(
                a.y * b.z - a.z * b.y,
                a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x
        );
    }

    private static Vec3 normalize(Vec3 value) {
        double length = Math.sqrt(value.lengthSqr());
        return length <= 1.0e-12D ? Vec3.ZERO : value.scale(1.0D / length);
    }

    private static double clampFinite(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Basis(Vec3 right, Vec3 up, Vec3 forward) {}

    private static final class Active {
        private final String id;
        private DAI_ErasCinematicDefinition definition;
        private final Vec3 anchor;
        private final float baseYaw;
        private final float basePitch;
        private DAI_ErasSampling.Camera3D baseCamera;
        private final boolean driveCamera;
        private final boolean renderScene;
        private final boolean renderBackground;
        private final boolean renderPost;
        private final boolean hideHud;
        private final boolean lockInput;
        private final boolean relativeCamera;
        private final double worldScale;
        private final double speed;
        private final boolean loop;
        private double tickPosition;
        private boolean paused;
        private long lastTickNanos;

        private Active(
                String id,
                DAI_ErasCinematicDefinition definition,
                Vec3 anchor,
                float baseYaw,
                float basePitch,
                DAI_ErasSampling.Camera3D baseCamera,
                boolean driveCamera,
                boolean renderScene,
                boolean renderBackground,
                boolean renderPost,
                boolean hideHud,
                boolean lockInput,
                boolean relativeCamera,
                double worldScale,
                double speed,
                boolean loop,
                double tickPosition,
                boolean paused,
                long lastTickNanos
        ) {
            this.id = id;
            this.definition = definition;
            this.anchor = anchor;
            this.baseYaw = baseYaw;
            this.basePitch = basePitch;
            this.baseCamera = baseCamera;
            this.driveCamera = driveCamera;
            this.renderScene = renderScene;
            this.renderBackground = renderBackground;
            this.renderPost = renderPost;
            this.hideHud = hideHud;
            this.lockInput = lockInput;
            this.relativeCamera = relativeCamera;
            this.worldScale = worldScale;
            this.speed = speed;
            this.loop = loop;
            this.tickPosition = tickPosition;
            this.paused = paused;
            this.lastTickNanos = lastTickNanos;
        }
    }
}
