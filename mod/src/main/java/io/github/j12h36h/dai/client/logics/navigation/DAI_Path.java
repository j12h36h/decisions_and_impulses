package io.github.j12h36h.dai.client.logics.navigation;

import net.minecraft.core.BlockPos;

import java.util.List;

public final class DAI_Path {

    private final List<BlockPos> nodes;

    public DAI_Path(
            List<BlockPos> nodes
    ) {

        if (
                nodes == null
                        || nodes.isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "Path nodes cannot be null or empty."
            );
        }

        this.nodes =
                nodes.stream()
                        .map(
                                BlockPos::immutable
                        )
                        .toList();
    }

    public List<BlockPos> nodes() {
        return nodes;
    }

    public int size() {
        return nodes.size();
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public BlockPos first() {
        return nodes.getFirst();
    }

    public BlockPos last() {
        return nodes.getLast();
    }

    public BlockPos node(
            int index
    ) {
        return nodes.get(
                index
        );
    }
}