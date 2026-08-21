package io.github.j12h36h.dai.server.runtime;

import io.github.j12h36h.dai.content.DAI_ContentKind;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;

/** Applies the composed DAI effects listed by a dai_potions definition. */
public final class DAI_PotionRuntime {
    private DAI_PotionRuntime() {}
    public static boolean apply(LivingEntity entity, String rawId) {
        var entry = DAI_ContentRegistry.get(rawId);
        if (entity == null || entry == null || entry.kind() != DAI_ContentKind.POTION) return false;
        if (entry.definition().registryBacked()) {
            var holder = BuiltInRegistries.POTION.get(entry.id()).orElse(null);
            if (holder != null) {
                boolean nativeAny = false;
                for (MobEffectInstance effect : holder.value().getEffects()) {
                    nativeAny |= entity.addEffect(new MobEffectInstance(effect));
                }
                DAI_RuntimeDispatch.contentEvent(entity, entry, "apply");
                return nativeAny || holder.value().getEffects().isEmpty();
            }
        }
        boolean any = false;
        for (String spec : entry.definition().potion().effects()) {
            if (spec == null || spec.isBlank()) continue;
            String[] p = spec.trim().split("\\s+");
            String id = p[0]; int duration = 0, amplifier = 0;
            try { if (p.length > 1) duration = Integer.parseInt(p[1]); } catch (NumberFormatException ignored) {}
            try { if (p.length > 2) amplifier = Integer.parseInt(p[2]); } catch (NumberFormatException ignored) {}
            any |= DAI_EffectRuntime.apply(entity, id, duration, amplifier);
        }
        DAI_RuntimeDispatch.contentEvent(entity, entry, "apply");
        return any || entry.definition().potion().effects().isEmpty();
    }
}
