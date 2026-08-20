package io.github.j12h36h.dai.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionResolver;
import io.github.j12h36h.dai.content.DAI_ContentStack;
import io.github.j12h36h.dai.logics.action.DAI_ActionLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * Physical-client bridge for Musashi Story's directional sword combat.
 *
 * The datapack owns authoritative combat state, damage, custom model data and
 * screen FX. This class owns the physical key transitions and first-person
 * hand transform that vanilla resource/data packs cannot control themselves.
 */
public final class DAI_MusashiDirectionalCombat {

    private static final float DIRECTION_THRESHOLD_DEGREES = 5.0F;
    private static final int SLASH_DURATION_TICKS = 4;
    private static final int DRAW_BLEND_TICKS = 4;

    private static boolean lastAttack;
    private static boolean lastUse;
    private static boolean attackHeld;
    private static boolean guardHeld;

    private static float drawOriginYaw;
    private static float drawOriginPitch;
    private static int previewDirection;
    private static int drawTicks;

    private static int slashDirection;
    private static int slashAgeTicks;
    private static boolean slashActive;

    private DAI_MusashiDirectionalCombat() {
        // Utility class.
    }

    /** Called once per post-client tick from DAI_ClientTick. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.level == null) {
            reset();
            return;
        }

        boolean bladeHeld = isMusashiBlade(
                minecraft.player.getMainHandItem()
        );

        boolean attack = minecraft.options.keyAttack.isDown();
        boolean use = minecraft.options.keyUse.isDown();

        if (!bladeHeld) {
            resetInputEdges(attack, use);
            clearVisualState();
            return;
        }

        /*
         * LMB owns draw/aim/release. Vanilla startAttack/continueAttack are
         * cancelled by Mixin_Minecraft while a Musashi blade is held, so the
         * key can stay physically down for item-model conditions without
         * producing Minecraft's normal swing/breaking motion.
         */
        if (attack && !lastAttack && !use) {
            drawOriginYaw = minecraft.player.getYRot();
            drawOriginPitch = minecraft.player.getXRot();
            previewDirection = 0;
            drawTicks = 0;
            slashActive = false;
            enqueueAction("musashi_story:blade_draw_begin");
        }

        if (attack && !use) {
            attackHeld = true;
            guardHeld = false;
            drawTicks++;
            previewDirection = resolveDirection(
                    drawOriginYaw,
                    drawOriginPitch,
                    minecraft.player.getYRot(),
                    minecraft.player.getXRot()
            );

            // Keep the datapack's live direction scores synchronized while
            // the player aims. Release also recomputes once server-side.
            enqueueAction("musashi_story:blade_draw_tick");
        }

        if (!attack && lastAttack) {
            int direction = resolveDirection(
                    drawOriginYaw,
                    drawOriginPitch,
                    minecraft.player.getYRot(),
                    minecraft.player.getXRot()
            );

            // No meaningful mouse displacement = thrust in look direction.
            if (direction == 0) {
                direction = 9;
            }

            slashDirection = direction;
            slashAgeTicks = 0;
            slashActive = true;
            attackHeld = false;

            enqueueAction("musashi_story:blade_release");
            enqueueAction(
                    "musashi_story:fx_blade_"
                            + directionName(direction)
            );
        }

        /*
         * RMB is a half-draw diagonal guard. Vanilla item use is cancelled
         * while the blade is held so shields/blocks/interactions do not leak
         * through the custom stance.
         */
        if (use && !lastUse) {
            guardHeld = true;
            attackHeld = false;
            slashActive = false;
            enqueueAction("musashi_story:blade_guard_begin");
        }

        if (!use && lastUse) {
            guardHeld = false;
            enqueueAction("musashi_story:blade_guard_end");
        }

        if (slashActive) {
            slashAgeTicks++;
            if (slashAgeTicks >= SLASH_DURATION_TICKS) {
                slashActive = false;
                slashAgeTicks = 0;
            }
        }

        lastAttack = attack;
        lastUse = use;
    }

    /**
     * Applies a screen-space hand + item transform before vanilla renders the
     * main hand. Because the vanilla attack itself is cancelled, there is no
     * default vertical swing/bob underneath this motion.
     */
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (!isMusashiBlade(event.getItemStack())) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        float partialTick = event.getPartialTick();

        if (guardHeld) {
            applyPose(
                    poseStack,
                    FirstPersonBladePoseContract.guard(),
                    1.0F
            );
            return;
        }

        if (slashActive && slashDirection != 0) {
            float progress = clamp01(
                    (slashAgeTicks + partialTick)
                            / (float) SLASH_DURATION_TICKS
            );
            applySlash(
                    poseStack,
                    slashDirection,
                    smoothStep(progress)
            );
            return;
        }

        if (attackHeld) {
            float drawBlend = clamp01(
                    (drawTicks + partialTick)
                            / (float) DRAW_BLEND_TICKS
            );

            applyPose(
                    poseStack,
                    FirstPersonBladePoseContract.ready(),
                    smoothStep(drawBlend)
            );
        }
    }

    /** Used by the Minecraft mixin to suppress vanilla combat/break input. */
    public static boolean interceptVanillaAttack() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && isMusashiBlade(
                minecraft.player.getMainHandItem()
        );
    }

    /** Used by the Minecraft mixin to suppress vanilla right-click use. */
    public static boolean interceptVanillaUse() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && isMusashiBlade(
                minecraft.player.getMainHandItem()
        );
    }

    public static void reset() {
        lastAttack = false;
        lastUse = false;
        clearVisualState();
    }

    private static void clearVisualState() {
        attackHeld = false;
        guardHeld = false;
        previewDirection = 0;
        drawTicks = 0;
        slashDirection = 0;
        slashAgeTicks = 0;
        slashActive = false;
    }

    private static void resetInputEdges(
            boolean attack,
            boolean use
    ) {
        lastAttack = attack;
        lastUse = use;
    }

    private static boolean isMusashiBlade(
            ItemStack stack
    ) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        String contentId = DAI_ContentStack.id(stack);

        if (contentId == null || contentId.isBlank()) {
            Identifier nativeId =
                    BuiltInRegistries.ITEM.getKey(
                            stack.getItem()
                    );

            contentId = nativeId == null
                    ? ""
                    : nativeId.toString();
        }

        return switch (contentId) {
            case "musashi_story:katana",
                 "musashi_story:nodachi",
                 "musashi_story:wakizashi",
                 "musashi_story:naginata" -> true;
            default -> false;
        };
    }

    private static int resolveDirection(
            float originYaw,
            float originPitch,
            float currentYaw,
            float currentPitch
    ) {
        float deltaYaw = wrapDegrees(
                currentYaw - originYaw
        );
        float deltaPitch =
                currentPitch - originPitch;

        boolean left =
                deltaYaw < -DIRECTION_THRESHOLD_DEGREES;
        boolean right =
                deltaYaw > DIRECTION_THRESHOLD_DEGREES;
        boolean up =
                deltaPitch < -DIRECTION_THRESHOLD_DEGREES;
        boolean down =
                deltaPitch > DIRECTION_THRESHOLD_DEGREES;

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

    private static void applySlash(
            PoseStack poseStack,
            int direction,
            float progress
    ) {
        if (direction == 9) {
            FirstPersonBladePoseContract.Pose start =
                    FirstPersonBladePoseContract.ready();
            FirstPersonBladePoseContract.Pose end =
                    FirstPersonBladePoseContract.pose(9);

            // Small recoil at the very end gives the thrust a readable hit.
            float thrust = progress < 0.78F
                    ? progress / 0.78F
                    : 1.0F - ((progress - 0.78F) / 0.22F) * 0.16F;

            applyInterpolatedPose(
                    poseStack,
                    start,
                    end,
                    clamp01(thrust)
            );
            return;
        }

        int opposite =
                FirstPersonBladePoseContract.opposite(
                        direction
                );

        FirstPersonBladePoseContract.Pose start =
                FirstPersonBladePoseContract.pose(
                        opposite
                );

        FirstPersonBladePoseContract.Pose end =
                FirstPersonBladePoseContract.pose(
                        direction
                );

        applyInterpolatedPose(
                poseStack,
                start,
                end,
                progress
        );
    }

    private static void applyInterpolatedPose(
            PoseStack poseStack,
            FirstPersonBladePoseContract.Pose start,
            FirstPersonBladePoseContract.Pose end,
            float progress
    ) {
        FirstPersonBladePoseContract.Pose pose =
                new FirstPersonBladePoseContract.Pose(
                        lerp(start.pitch(), end.pitch(), progress),
                        lerp(start.yaw(), end.yaw(), progress),
                        lerp(start.roll(), end.roll(), progress),
                        lerp(start.x(), end.x(), progress),
                        lerp(start.y(), end.y(), progress),
                        lerp(start.z(), end.z(), progress)
                );

        applyPose(
                poseStack,
                pose,
                1.0F
        );
    }

    private static void applyPose(
            PoseStack poseStack,
            FirstPersonBladePoseContract.Pose pose,
            float weight
    ) {
        if (weight <= 0.0F) {
            return;
        }

        poseStack.translate(
                pose.x() * weight,
                pose.y() * weight,
                pose.z() * weight
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        pose.pitch() * weight
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        pose.yaw() * weight
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        pose.roll() * weight
                )
        );
    }

    private static void enqueueAction(
            String actionId
    ) {
        Identifier identifier =
                DAI_ActionResolver.parseReference(
                        actionId
                );

        if (identifier == null
                || !DAI_ActionLibrary.contains(identifier)) {
            return;
        }

        DAI_ActionQueue.enqueueAll(
                DAI_ActionResolver.resolve(
                        actionId
                )
        );
    }

    private static String directionName(
            int direction
    ) {
        return switch (direction) {
            case 1 -> "up";
            case 2 -> "up_right";
            case 3 -> "right";
            case 4 -> "down_right";
            case 5 -> "down";
            case 6 -> "down_left";
            case 7 -> "left";
            case 8 -> "up_left";
            default -> "stab";
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

    private static float clamp01(float value) {
        return Math.max(
                0.0F,
                Math.min(1.0F, value)
        );
    }

    private static float lerp(
            float start,
            float end,
            float progress
    ) {
        return start
                + (end - start) * progress;
    }
}
