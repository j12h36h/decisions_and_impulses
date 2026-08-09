package io.github.j12h36h.dai.objectives.recognition;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class DAI_RecogRequirements {

    private DAI_RecogRequirements() {
        // Utility class.
    }

    public static boolean connected(
            Level level,
            DAI_RecogSnapshot snapshot,
            Map<String, List<DAI_RecogBlock>> groups,
            DAI_RecogDefinition.DAI_RecogRequirement requirement
    ) {

        if (
                groups == null
                        || requirement == null
        ) {
            return false;
        }

        Set<BlockPos> positions =
                new HashSet<>();

        for (String groupName : requirement.groups()) {

            for (
                    DAI_RecogBlock block
                    : groups.getOrDefault(
                    groupName,
                    List.of()
            )
            ) {

                positions.add(
                        block.offset()
                );
            }
        }

        if (positions.isEmpty()) {
            return false;
        }

        Set<BlockPos> visited =
                new HashSet<>();

        Queue<BlockPos> pending =
                new ArrayDeque<>();

        pending.add(
                positions.iterator()
                        .next()
        );

        while (!pending.isEmpty()) {

            BlockPos current =
                    pending.remove();

            if (!visited.add(current)) {
                continue;
            }

            for (Direction direction : Direction.values()) {

                BlockPos adjacent =
                        current.relative(
                                direction
                        );

                if (
                        positions.contains(adjacent)
                                && !visited.contains(adjacent)
                ) {

                    pending.add(
                            adjacent
                    );
                }
            }
        }

        return visited.size()
                == positions.size();
    }

    public static boolean verticalColumn(
            Level level,
            DAI_RecogSnapshot snapshot,
            Map<String, List<DAI_RecogBlock>> groups,
            DAI_RecogDefinition.DAI_RecogRequirement requirement
    ) {

        if (
                groups == null
                        || requirement == null
        ) {
            return false;
        }

        List<DAI_RecogBlock> blocks =
                groups.getOrDefault(
                        requirement.group(),
                        List.of()
                );

        int minimumHeight =
                requirement.minimumHeight();

        if (
                blocks.isEmpty()
                        || minimumHeight <= 0
        ) {
            return false;
        }

        Map<Column, Set<Integer>> columns =
                new HashMap<>();

        for (DAI_RecogBlock block : blocks) {

            BlockPos offset =
                    block.offset();

            columns.computeIfAbsent(
                            new Column(
                                    offset.getX(),
                                    offset.getZ()
                            ),
                            ignored ->
                                    new HashSet<>()
                    )
                    .add(
                            offset.getY()
                    );
        }

        for (Set<Integer> heights : columns.values()) {

            for (int start : heights) {

                int length = 1;
                int next = start + 1;

                while (heights.contains(next)) {

                    length++;
                    next++;
                }

                if (length >= minimumHeight) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean touchesGround(
            Level level,
            DAI_RecogSnapshot snapshot,
            Map<String, List<DAI_RecogBlock>> groups,
            DAI_RecogDefinition.DAI_RecogRequirement requirement
    ) {

        if (
                level == null
                        || snapshot == null
                        || groups == null
                        || requirement == null
        ) {
            return false;
        }

        List<DAI_RecogBlock> blocks =
                groups.getOrDefault(
                        requirement.group(),
                        List.of()
                );

        if (blocks.isEmpty()) {
            return false;
        }

        for (DAI_RecogBlock block : blocks) {

            BlockPos offset =
                    block.offset();

            BlockPos worldPosition =
                    snapshot.origin()
                            .offset(
                                    offset.getX(),
                                    offset.getY(),
                                    offset.getZ()
                            );

            BlockPos below =
                    worldPosition.below();

            BlockState belowState =
                    level.getBlockState(
                            below
                    );

            if (
                    !belowState.isAir()
                            && belowState.isCollisionShapeFullBlock(
                            level,
                            below
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    public static boolean nearUpperRegion(
            Level level,
            DAI_RecogSnapshot snapshot,
            Map<String, List<DAI_RecogBlock>> groups,
            DAI_RecogDefinition.DAI_RecogRequirement requirement
    ) {

        if (
                groups == null
                        || requirement == null
        ) {
            return false;
        }

        List<DAI_RecogBlock> targetBlocks =
                groups.getOrDefault(
                        requirement.group(),
                        List.of()
                );

        List<DAI_RecogBlock> referenceBlocks =
                groups.getOrDefault(
                        requirement.relativeTo(),
                        List.of()
                );

        if (
                targetBlocks.isEmpty()
                        || referenceBlocks.isEmpty()
        ) {
            return false;
        }

        int minimumY =
                Integer.MAX_VALUE;

        int maximumY =
                Integer.MIN_VALUE;

        for (DAI_RecogBlock block : referenceBlocks) {

            int y =
                    block.offset()
                            .getY();

            minimumY =
                    Math.min(
                            minimumY,
                            y
                    );

            maximumY =
                    Math.max(
                            maximumY,
                            y
                    );
        }

        int height =
                maximumY
                        - minimumY
                        + 1;

        int upperRegionStart =
                minimumY
                        + Math.max(
                        0,
                        (int) Math.floor(
                                height * 0.5F
                        )
                );

        int upperCount = 0;

        for (DAI_RecogBlock block : targetBlocks) {

            if (
                    block.offset()
                            .getY()
                            >= upperRegionStart
            ) {
                upperCount++;
            }
        }

        float ratio =
                (float) upperCount
                        / (float) targetBlocks.size();

        return ratio
                >= requirement.minimumRatio();
    }

    public static boolean containsGroup(
            Level level,
            DAI_RecogSnapshot snapshot,
            Map<String, List<DAI_RecogBlock>> groups,
            DAI_RecogDefinition.DAI_RecogRequirement requirement
    ) {

        if (
                snapshot == null
                        || requirement == null
        ) {
            return false;
        }

        int count =
                snapshot.groupCount(
                        requirement.group()
                );

        int minimum =
                requirement.intParameter(
                        "minimum",
                        0
                );

        int maximum =
                requirement.intParameter(
                        "maximum",
                        Integer.MAX_VALUE
                );

        if (
                minimum < 0
                        || maximum < minimum
        ) {
            return false;
        }

        return count >= minimum
                && count <= maximum;
    }

    public static boolean dimensions(
            Level level,
            DAI_RecogSnapshot snapshot,
            Map<String, List<DAI_RecogBlock>> groups,
            DAI_RecogDefinition.DAI_RecogRequirement requirement
    ) {

        if (
                snapshot == null
                        || snapshot.isEmpty()
                        || requirement == null
        ) {
            return false;
        }

        int minimumWidth =
                requirement.intParameter(
                        "minimum_width",
                        0
                );

        int minimumHeight =
                requirement.intParameter(
                        "minimum_height",
                        0
                );

        int minimumDepth =
                requirement.intParameter(
                        "minimum_depth",
                        0
                );

        int maximumWidth =
                requirement.intParameter(
                        "maximum_width",
                        Integer.MAX_VALUE
                );

        int maximumHeight =
                requirement.intParameter(
                        "maximum_height",
                        Integer.MAX_VALUE
                );

        int maximumDepth =
                requirement.intParameter(
                        "maximum_depth",
                        Integer.MAX_VALUE
                );

        if (
                minimumWidth < 0
                        || minimumHeight < 0
                        || minimumDepth < 0
                        || maximumWidth < minimumWidth
                        || maximumHeight < minimumHeight
                        || maximumDepth < minimumDepth
        ) {
            return false;
        }

        return snapshot.width() >= minimumWidth
                && snapshot.height() >= minimumHeight
                && snapshot.depth() >= minimumDepth
                && snapshot.width() <= maximumWidth
                && snapshot.height() <= maximumHeight
                && snapshot.depth() <= maximumDepth;
    }

    public static boolean groupRatio(
            Level level,
            DAI_RecogSnapshot snapshot,
            Map<String, List<DAI_RecogBlock>> groups,
            DAI_RecogDefinition.DAI_RecogRequirement requirement
    ) {

        if (
                snapshot == null
                        || snapshot.isEmpty()
                        || requirement == null
        ) {
            return false;
        }

        float minimumRatio =
                requirement.floatParameter(
                        "minimum_ratio",
                        0.0F
                );

        float maximumRatio =
                requirement.floatParameter(
                        "maximum_ratio",
                        1.0F
                );

        if (
                minimumRatio < 0.0F
                        || maximumRatio < minimumRatio
                        || maximumRatio > 1.0F
        ) {
            return false;
        }

        float ratio =
                snapshot.groupRatio(
                        requirement.group()
                );

        return ratio >= minimumRatio
                && ratio <= maximumRatio;
    }

    private record Column(
            int x,
            int z
    ) {
    }
}