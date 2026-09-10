package io.github.j12h36h.dai.client.input;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.input.DAI_InputProfileDefinition;
import io.github.j12h36h.dai.logics.core.DAI_Config;
import io.github.j12h36h.dai.input.DAI_InputProfileRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;

import java.util.Locale;

/**
 * Generic data-driven physical input/first-person presentation bridge.
 *
 * This class contains gesture mechanics only. Item matching, action references,
 * thresholds, durations, fallback directions and every first-person pose are
 * defined by input_profiles JSON in datapacks/resource packs.
 */
public final class DAI_InputProfileRuntime {
    private static DAI_InputProfileRegistry.Match active;
    private static boolean lastPrimary;
    private static boolean lastSecondary;
    private static boolean primaryHeld;
    private static boolean secondaryHeld;
    private static float originYaw;
    private static float originPitch;
    private static int previewDirection;
    private static int primaryTicks;
    private static int releaseDirection;
    private static int releaseAgeTicks;
    private static boolean releaseActive;

    private DAI_InputProfileRuntime() {}

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!DAI_Config.featureModuleEnabled("input_profiles")
                || minecraft.player == null || minecraft.level == null || DAI_InputProfileRegistry.isEmpty()) {
            reset();
            return;
        }

        boolean primary = minecraft.options.keyAttack.isDown();
        boolean secondary = minecraft.options.keyUse.isDown();
        DAI_InputProfileRegistry.Match match = DAI_InputProfileRegistry.resolve(minecraft.player.getMainHandItem());
        if (match == null) {
            active = null;
            lastPrimary = primary;
            lastSecondary = secondary;
            clearVisualState();
            return;
        }

        if (active == null || !active.id().equals(match.id())) {
            clearVisualState();
            lastPrimary = primary;
            lastSecondary = secondary;
        }
        active = match;
        DAI_InputProfileDefinition definition = match.definition();
        JsonObject primaryDef = definition.primary();
        JsonObject secondaryDef = definition.secondary();
        JsonObject directional = definition.directional();

        String primaryMode = DAI_InputProfileDefinition.normalized(
                DAI_InputProfileDefinition.string(primaryDef, "mode", "hold"));
        boolean directionalPrimary = primaryMode.equals("directional")
                || primaryMode.equals("directional_release")
                || primaryMode.equals("drag_direction");

        if (primary && !lastPrimary && !secondary) {
            originYaw = minecraft.player.getYRot();
            originPitch = minecraft.player.getXRot();
            previewDirection = 0;
            primaryTicks = 0;
            releaseActive = false;
            primaryHeld = true;
            secondaryHeld = false;
            fire(DAI_InputProfileDefinition.string(primaryDef, "begin_action", ""), 0);
        }

        if (primary && !secondary) {
            primaryHeld = true;
            secondaryHeld = false;
            primaryTicks++;
            if (directionalPrimary) {
                previewDirection = resolveDirection(
                        originYaw,
                        originPitch,
                        minecraft.player.getYRot(),
                        minecraft.player.getXRot(),
                        (float)Math.max(0.0D, DAI_InputProfileDefinition.number(directional, "threshold_degrees", 5.0D))
                );
            }
            fire(DAI_InputProfileDefinition.string(primaryDef, "tick_action", ""), previewDirection);
        }

        if (!primary && lastPrimary) {
            int direction = directionalPrimary
                    ? resolveDirection(
                            originYaw,
                            originPitch,
                            minecraft.player.getYRot(),
                            minecraft.player.getXRot(),
                            (float)Math.max(0.0D, DAI_InputProfileDefinition.number(directional, "threshold_degrees", 5.0D))
                    )
                    : 0;
            if (direction == 0) direction = Math.max(0, Math.min(9,
                    DAI_InputProfileDefinition.integer(directional, "fallback_direction", 0)));

            releaseDirection = direction;
            releaseAgeTicks = 0;
            releaseActive = DAI_InputProfileDefinition.bool(definition.firstPerson(), "enabled", false)
                    && DAI_InputProfileDefinition.integer(definition.firstPerson(), "release_duration_ticks", 0) > 0;
            primaryHeld = false;
            fire(DAI_InputProfileDefinition.string(primaryDef, "release_action", ""), direction);
            fire(DAI_InputProfileDefinition.string(directional, "release_action_pattern", ""), direction);
        }

        if (secondary && !lastSecondary) {
            secondaryHeld = true;
            primaryHeld = false;
            releaseActive = false;
            fire(DAI_InputProfileDefinition.string(secondaryDef, "begin_action", ""), 0);
        }
        if (secondary) {
            secondaryHeld = true;
            fire(DAI_InputProfileDefinition.string(secondaryDef, "tick_action", ""), 0);
        }
        if (!secondary && lastSecondary) {
            secondaryHeld = false;
            fire(DAI_InputProfileDefinition.string(secondaryDef, "end_action", ""), 0);
        }

        if (releaseActive) {
            releaseAgeTicks++;
            int duration = Math.max(1, DAI_InputProfileDefinition.integer(definition.firstPerson(), "release_duration_ticks", 1));
            if (releaseAgeTicks >= duration) {
                releaseActive = false;
                releaseAgeTicks = 0;
            }
        }

        lastPrimary = primary;
        lastSecondary = secondary;
    }

    public static void onRenderHand(RenderHandEvent event) {
        if (event == null || event.getHand() != InteractionHand.MAIN_HAND || active == null) return;
        ItemStack stack = event.getItemStack();
        DAI_InputProfileRegistry.Match current = DAI_InputProfileRegistry.resolve(stack);
        if (current == null || !current.id().equals(active.id())) return;

        JsonObject firstPerson = active.definition().firstPerson();
        if (!DAI_InputProfileDefinition.bool(firstPerson, "enabled", false)) return;

        PoseStack poseStack = event.getPoseStack();
        float partialTick = event.getPartialTick();

        if (secondaryHeld) {
            applyPose(poseStack, pose(firstPerson, "secondary"), 1.0F);
            return;
        }

        if (releaseActive) {
            int duration = Math.max(1, DAI_InputProfileDefinition.integer(firstPerson, "release_duration_ticks", 1));
            float progress = smoothStep(clamp01((releaseAgeTicks + partialTick) / (float)duration));
            Pose start = releaseStartPose(firstPerson, releaseDirection);
            Pose end = directionPose(firstPerson, releaseDirection);
            applyPose(poseStack, lerp(start, end, progress), 1.0F);
            return;
        }

        if (primaryHeld) {
            int blendTicks = Math.max(1, DAI_InputProfileDefinition.integer(firstPerson, "ready_blend_ticks", 1));
            float weight = smoothStep(clamp01((primaryTicks + partialTick) / (float)blendTicks));
            applyPose(poseStack, pose(firstPerson, "ready"), weight);
        }
    }

    public static boolean interceptVanillaAttack() {
        DAI_InputProfileRegistry.Match match = currentMatch();
        return match != null && DAI_InputProfileDefinition.bool(match.definition().primary(), "suppress_vanilla", false);
    }

    public static boolean interceptVanillaUse() {
        DAI_InputProfileRegistry.Match match = currentMatch();
        return match != null && DAI_InputProfileDefinition.bool(match.definition().secondary(), "suppress_vanilla", false);
    }

    public static void reset() {
        active = null;
        lastPrimary = false;
        lastSecondary = false;
        clearVisualState();
    }

    private static DAI_InputProfileRegistry.Match currentMatch() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!DAI_Config.featureModuleEnabled("input_profiles")
                || minecraft.player == null || DAI_InputProfileRegistry.isEmpty()) return null;
        return DAI_InputProfileRegistry.resolve(minecraft.player.getMainHandItem());
    }

    private static void clearVisualState() {
        primaryHeld = false;
        secondaryHeld = false;
        previewDirection = 0;
        primaryTicks = 0;
        releaseDirection = 0;
        releaseAgeTicks = 0;
        releaseActive = false;
    }

    private static int resolveDirection(float originYaw, float originPitch, float currentYaw, float currentPitch, float threshold) {
        float deltaYaw = wrapDegrees(currentYaw - originYaw);
        float deltaPitch = currentPitch - originPitch;
        boolean left = deltaYaw < -threshold;
        boolean right = deltaYaw > threshold;
        boolean up = deltaPitch < -threshold;
        boolean down = deltaPitch > threshold;
        if (up && right) return 2;
        if (down && right) return 4;
        if (down && left) return 6;
        if (up && left) return 8;
        if (up) return 1;
        if (right) return 3;
        if (down) return 5;
        if (left) return 7;
        return 0;
    }

    private static Pose releaseStartPose(JsonObject firstPerson, int direction) {
        JsonObject directions = DAI_InputProfileDefinition.object(firstPerson, "directions");
        JsonObject exact = objectForKey(directions, Integer.toString(direction));
        String start = DAI_InputProfileDefinition.string(exact, "start_from", "");
        if (!start.isBlank()) {
            if (start.equalsIgnoreCase("ready")) return pose(firstPerson, "ready");
            JsonObject named = DAI_InputProfileDefinition.object(firstPerson, "poses");
            JsonObject namedPose = objectForKey(named, start);
            if (namedPose.size() > 0) return pose(namedPose);
            try { return directionPose(firstPerson, Integer.parseInt(start)); }
            catch (RuntimeException ignored) {}
        }
        int opposite = DAI_InputProfileDefinition.integer(exact, "opposite", opposite(direction));
        Pose p = directionPose(firstPerson, opposite);
        return p.isNeutral() ? pose(firstPerson, "ready") : p;
    }

    private static Pose directionPose(JsonObject firstPerson, int direction) {
        JsonObject directions = DAI_InputProfileDefinition.object(firstPerson, "directions");
        JsonObject object = objectForKey(directions, Integer.toString(direction));
        return object.size() == 0 ? Pose.NEUTRAL : pose(object);
    }

    private static Pose pose(JsonObject firstPerson, String key) {
        JsonObject object = DAI_InputProfileDefinition.object(firstPerson, key);
        if (object.size() == 0) {
            JsonObject poses = DAI_InputProfileDefinition.object(firstPerson, "poses");
            object = objectForKey(poses, key);
        }
        return pose(object);
    }

    private static Pose pose(JsonObject object) {
        if (object == null || object.size() == 0) return Pose.NEUTRAL;
        return new Pose(
                (float)DAI_InputProfileDefinition.number(object, "pitch", 0.0D),
                (float)DAI_InputProfileDefinition.number(object, "yaw", 0.0D),
                (float)DAI_InputProfileDefinition.number(object, "roll", 0.0D),
                (float)DAI_InputProfileDefinition.number(object, "x", 0.0D),
                (float)DAI_InputProfileDefinition.number(object, "y", 0.0D),
                (float)DAI_InputProfileDefinition.number(object, "z", 0.0D)
        );
    }

    private static JsonObject objectForKey(JsonObject object, String key) {
        if (object == null || key == null) return new JsonObject();
        JsonElement value = object.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static void applyPose(PoseStack stack, Pose pose, float weight) {
        if (stack == null || pose == null || weight <= 0.0F) return;
        stack.translate(pose.x * weight, pose.y * weight, pose.z * weight);
        stack.mulPose(Axis.XP.rotationDegrees(pose.pitch * weight));
        stack.mulPose(Axis.YP.rotationDegrees(pose.yaw * weight));
        stack.mulPose(Axis.ZP.rotationDegrees(pose.roll * weight));
    }

    private static Pose lerp(Pose a, Pose b, float progress) {
        return new Pose(
                lerp(a.pitch, b.pitch, progress),
                lerp(a.yaw, b.yaw, progress),
                lerp(a.roll, b.roll, progress),
                lerp(a.x, b.x, progress),
                lerp(a.y, b.y, progress),
                lerp(a.z, b.z, progress)
        );
    }

    private static void fire(String pattern, int direction) {
        if (pattern == null || pattern.isBlank()) return;
        String value = pattern.trim()
                .replace("{direction}", Integer.toString(direction))
                .replace("{direction_name}", directionName(direction));
        if (!value.isBlank()) DAI_ActionQueue.enqueueDeferredReference(value);
    }

    private static String directionName(int direction) {
        return switch (direction) {
            case 1 -> "up";
            case 2 -> "up_right";
            case 3 -> "right";
            case 4 -> "down_right";
            case 5 -> "down";
            case 6 -> "down_left";
            case 7 -> "left";
            case 8 -> "up_left";
            case 9 -> "forward";
            default -> "center";
        };
    }

    private static int opposite(int direction) {
        return switch (direction) {
            case 1 -> 5;
            case 2 -> 6;
            case 3 -> 7;
            case 4 -> 8;
            case 5 -> 1;
            case 6 -> 2;
            case 7 -> 3;
            case 8 -> 4;
            default -> 0;
        };
    }

    private static float wrapDegrees(float value) {
        value %= 360.0F;
        if (value >= 180.0F) value -= 360.0F;
        if (value < -180.0F) value += 360.0F;
        return value;
    }

    private static float smoothStep(float value) {
        value = clamp01(value);
        return value * value * (3.0F - 2.0F * value);
    }

    private static float clamp01(float value) { return Math.max(0.0F, Math.min(1.0F, value)); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private record Pose(float pitch, float yaw, float roll, float x, float y, float z) {
        private static final Pose NEUTRAL = new Pose(0, 0, 0, 0, 0, 0);
        private boolean isNeutral() { return equals(NEUTRAL); }
    }
}
