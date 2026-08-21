package io.github.j12h36h.dai.content;

import io.github.j12h36h.dai.server.runtime.DAI_RuntimeDispatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Actual Minecraft MobEffect whose runtime callbacks are owned by reloadable DAI JSON. */
public final class DAI_JsonMobEffect extends MobEffect {
    private final String contentId;
    private final int fallbackInterval;

    public DAI_JsonMobEffect(String contentId, MobEffectCategory category, int color, int fallbackInterval) {
        super(category, color);
        this.contentId = contentId;
        this.fallbackInterval = Math.max(1, fallbackInterval);
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(contentId);
        if (entry != null) DAI_RuntimeDispatch.contentEvent(entity, entry, "tick");
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(contentId);
        int interval = entry == null ? fallbackInterval : entry.definition().effect().tickInterval();
        return duration % Math.max(1, interval) == 0;
    }

    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(contentId);
        if (entry != null) {
            DAI_RuntimeDispatch.contentEvent(entity, entry, "apply");
            DAI_RuntimeDispatch.contentEvent(entity, entry, "start");
        }
    }
}
