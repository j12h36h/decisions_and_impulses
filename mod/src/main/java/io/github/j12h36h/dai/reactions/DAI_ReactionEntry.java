package io.github.j12h36h.dai.reactions;

import net.minecraft.resources.Identifier;

public record DAI_ReactionEntry(
        Identifier id,
        DAI_ReactionDefinition definition
) {
}
