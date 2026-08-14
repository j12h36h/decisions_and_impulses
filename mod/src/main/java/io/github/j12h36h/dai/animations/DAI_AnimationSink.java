package io.github.j12h36h.dai.animations;

import net.minecraft.world.entity.Entity;

/** Optional renderer/model adapter for consuming DAI animation tracks. */
public interface DAI_AnimationSink {
    default void onPlay(Entity entity, String animationId, DAI_AnimationDefinition definition) {}
    default void onTick(Entity entity, String animationId, DAI_AnimationDefinition definition, int tick) {}
    default void onStop(Entity entity, String animationId, DAI_AnimationDefinition definition) {}
}
