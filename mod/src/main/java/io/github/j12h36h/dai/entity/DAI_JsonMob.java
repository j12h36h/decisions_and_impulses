package io.github.j12h36h.dai.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * Generic physical mob used by native JSON-defined DAI entities.
 *
 * This class intentionally contributes no species-specific goal AI. Movement,
 * targeting and combat decisions are supplied by DAI behavior_sequence JSON at
 * runtime. Minecraft still supplies the generic LivingEntity/Mob physics,
 * navigation and networking primitives that every physical mob needs.
 */
public final class DAI_JsonMob extends PathfinderMob {

    @SuppressWarnings("unchecked")
    public DAI_JsonMob(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        // Native DAI entities own their AI through JSON behavior sequences.
    }
}
