package io.github.j12h36h.dai.reactions;

import net.minecraft.world.entity.Entity;

public record DAI_ReactionContext(
        String event,
        DAI_ReactionPhase phase,
        Entity entity
) {
}
