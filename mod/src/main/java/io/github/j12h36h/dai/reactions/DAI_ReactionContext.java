package io.github.j12h36h.dai.reactions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/** Runtime context exposed to reaction conditions. */
public record DAI_ReactionContext(
        String event,
        DAI_ReactionPhase phase,
        Entity entity,
        BlockPos blockPos,
        String itemId
) {
    public DAI_ReactionContext(String event, DAI_ReactionPhase phase, Entity entity) {
        this(event, phase, entity, null, "");
    }

    public DAI_ReactionContext {
        itemId = itemId == null ? "" : itemId.trim().toLowerCase();
    }
}
