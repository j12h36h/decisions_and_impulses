package io.github.j12h36h.dai.client.objectives.recognition;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public record DAI_RecogBlock(
        BlockPos offset,
        BlockState state
) {

    public DAI_RecogBlock {

        if (offset == null) {

            throw new IllegalArgumentException(
                    "Recognition block offset cannot be null."
            );
        }

        if (state == null) {

            throw new IllegalArgumentException(
                    "Recognition block state cannot be null."
            );
        }

        offset =
                offset.immutable();
    }
}