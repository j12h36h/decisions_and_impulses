package io.github.j12h36h.dai.client.combat.indicator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Generic client renderer for DI markers. Visual behavior comes exclusively from resource-pack JSON. */
public final class DAI_DamageIndicatorRenderRuntime {
    private static final String PREFIX = "DAI_DI|";
    private static final Map<EntityRenderState, RenderInfo> INFO = Collections.synchronizedMap(new WeakHashMap<>());

    private DAI_DamageIndicatorRenderRuntime() {}

    public static void initialize(IEventBus modBus) {
        modBus.addListener(DAI_DamageIndicatorRenderRuntime::registerReloadListeners);
        NeoForge.EVENT_BUS.addListener(RenderNameTagEvent.CanRender.class, DAI_DamageIndicatorRenderRuntime::onCanRender);
        NeoForge.EVENT_BUS.addListener(RenderNameTagEvent.DoRender.class, DAI_DamageIndicatorRenderRuntime::onDoRender);
    }

    private static void registerReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(DAI_Core.MODID, "damage_indicator_skins"), new DAI_DamageIndicatorSkinLibrary());
    }

    private static void onCanRender(RenderNameTagEvent.CanRender event) {
        Entity entity = event.getEntity();
        Component customName = entity == null ? null : entity.getCustomName();
        String raw = customName == null ? "" : customName.getString();
        if (!raw.startsWith(PREFIX)) return;

        Parsed parsed = Parsed.parse(raw);
        DAI_DamageIndicatorSkin skin = DAI_DamageIndicatorSkinLibrary.active();
        DAI_DamageIndicatorSkin.Category category = skin == null || parsed == null ? null : skin.category(parsed.category());
        if (parsed == null || category == null) {
            event.setCanRender(TriState.FALSE);
            return;
        }

        float age = entity.tickCount + event.getPartialTick();
        if (age >= category.totalDuration()) {
            event.setCanRender(TriState.FALSE);
            return;
        }

        Component content = Component.literal(parsed.value()).withStyle(style -> style.withFont(new FontDescription.Resource(category.font())));
        INFO.put(event.getEntityRenderState(), new RenderInfo(category, age));
        event.setContent(content);
        event.setCanRender(TriState.TRUE);
    }

    private static void onDoRender(RenderNameTagEvent.DoRender event) {
        RenderInfo info = INFO.remove(event.getEntityRenderState());
        if (info == null) return;
        event.setCanceled(true);

        DAI_DamageIndicatorSkin.Category category = info.category();
        Sample sample = sample(category, info.age());
        if (sample.alpha() <= 0.001F || sample.scale() <= 0.001F) return;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        EntityRenderState state = event.getEntityRenderState();
        Vec3 attachment = state.nameTagAttachment;
        if (attachment != null) pose.translate(attachment.x, attachment.y, attachment.z);
        pose.translate(sample.x(), sample.y(), 0.0D);
        pose.mulPose(event.getCameraRenderState().orientation);
        if (sample.rotation() != 0.0F) pose.mulPose(Axis.ZP.rotationDegrees(sample.rotation()));
        float scale = 0.025F * category.baseScale() * sample.scale();
        pose.scale(scale, -scale, scale);

        Component content = event.getContent();
        Font font = Minecraft.getInstance().font;
        float x = -font.width(content) * 0.5F;
        int color = applyAlpha(category.color(), sample.alpha());
        Font.DisplayMode mode = category.seeThrough() ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        event.getSubmitNodeCollector().submitText(
                pose,
                x,
                0.0F,
                content.getVisualOrderText(),
                category.shadow(),
                mode,
                state.lightCoords,
                color,
                0,
                0
        );
        pose.popPose();
    }

    private static Sample sample(DAI_DamageIndicatorSkin.Category category, float age) {
        DAI_DamageIndicatorSkin.Phase spawn = category.spawn();
        DAI_DamageIndicatorSkin.Phase hold = category.hold();
        DAI_DamageIndicatorSkin.Phase exit = category.exit();
        if (age < spawn.duration()) return phase(spawn, age);
        age -= spawn.duration();
        if (age < hold.duration()) return phase(hold, age);
        age -= hold.duration();
        return phase(exit, age);
    }

    private static Sample phase(DAI_DamageIndicatorSkin.Phase phase, float localAge) {
        float duration = Math.max(1.0F, phase.duration());
        float t = Math.max(0.0F, Math.min(1.0F, localAge / duration));
        float e = ease(phase.easing(), t);
        return new Sample(
                lerp(phase.xFrom(), phase.xTo(), e),
                lerp(phase.yFrom(), phase.yTo(), e),
                lerp(phase.scaleFrom(), phase.scaleTo(), e),
                lerp(phase.rotationFrom(), phase.rotationTo(), e),
                lerp(phase.alphaFrom(), phase.alphaTo(), e)
        );
    }

    private static float ease(String raw, float t) {
        String value = raw == null ? "linear" : raw.trim().toLowerCase();
        return switch (value) {
            case "ease_in" -> t * t;
            case "ease_out" -> 1.0F - (1.0F - t) * (1.0F - t);
            case "ease_in_out" -> t < 0.5F ? 2.0F*t*t : 1.0F - (float)Math.pow(-2.0F*t + 2.0F, 2.0D) / 2.0F;
            case "back_out" -> {
                float c1 = 1.70158F; float c3 = c1 + 1.0F; float u = t - 1.0F;
                yield 1.0F + c3*u*u*u + c1*u*u;
            }
            case "bounce_out" -> bounceOut(t);
            default -> t;
        };
    }

    private static float bounceOut(float x) {
        float n1 = 7.5625F, d1 = 2.75F;
        if (x < 1.0F/d1) return n1*x*x;
        if (x < 2.0F/d1) { x -= 1.5F/d1; return n1*x*x + 0.75F; }
        if (x < 2.5F/d1) { x -= 2.25F/d1; return n1*x*x + 0.9375F; }
        x -= 2.625F/d1; return n1*x*x + 0.984375F;
    }

    private static float lerp(float a, float b, float t) { return a + (b-a)*t; }
    private static int applyAlpha(int argb, float alpha) {
        int base = (argb >>> 24) & 255;
        int out = Math.max(0, Math.min(255, Math.round(base * alpha)));
        return (argb & 0x00FFFFFF) | (out << 24);
    }

    private record Parsed(String category, String value) {
        static Parsed parse(String raw) {
            String[] parts = raw.split("\\|", 3);
            if (parts.length != 3 || !"DAI_DI".equals(parts[0]) || parts[1].isBlank() || parts[2].isBlank()) return null;
            return new Parsed(parts[1], parts[2]);
        }
    }
    private record RenderInfo(DAI_DamageIndicatorSkin.Category category, float age) {}
    private record Sample(float x, float y, float scale, float rotation, float alpha) {}
}
