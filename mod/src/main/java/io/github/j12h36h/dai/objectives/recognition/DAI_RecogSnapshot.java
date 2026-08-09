package io.github.j12h36h.dai.objectives.recognition;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public record DAI_RecogSnapshot(
        BlockPos origin,
        List<DAI_RecogBlock> blocks,
        Map<String, Integer> groupCounts,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ
) {

    public DAI_RecogSnapshot(
            BlockPos origin,
            List<DAI_RecogBlock> blocks,
            Map<String, Integer> groupCounts
    ) {

        this(
                requireOrigin(
                        origin
                ),
                requireBlocks(
                        blocks
                ),
                requireGroupCounts(
                        groupCounts
                ),
                calculateMinX(
                        blocks
                ),
                calculateMaxX(
                        blocks
                ),
                calculateMinY(
                        blocks
                ),
                calculateMaxY(
                        blocks
                ),
                calculateMinZ(
                        blocks
                ),
                calculateMaxZ(
                        blocks
                )
        );
    }

    public DAI_RecogSnapshot {

        if (origin == null) {

            throw new IllegalArgumentException(
                    "Recognition snapshot origin cannot be null."
            );
        }

        if (blocks == null) {

            throw new IllegalArgumentException(
                    "Recognition snapshot blocks cannot be null."
            );
        }

        if (groupCounts == null) {

            throw new IllegalArgumentException(
                    "Recognition snapshot group counts cannot be null."
            );
        }

        origin =
                origin.immutable();

        blocks =
                List.copyOf(
                        blocks
                );

        groupCounts =
                Map.copyOf(
                        groupCounts
                );

        if (
                !blocks.isEmpty()
                        && (
                        minX > maxX
                                || minY > maxY
                                || minZ > maxZ
                )
        ) {

            throw new IllegalArgumentException(
                    "Recognition snapshot bounds are invalid."
            );
        }
    }

    public int size() {

        return blocks.size();
    }

    public boolean isEmpty() {

        return blocks.isEmpty();
    }

    public int width() {

        if (isEmpty()) {
            return 0;
        }

        return maxX
                - minX
                + 1;
    }

    public int height() {

        if (isEmpty()) {
            return 0;
        }

        return maxY
                - minY
                + 1;
    }

    public int depth() {

        if (isEmpty()) {
            return 0;
        }

        return maxZ
                - minZ
                + 1;
    }

    public long volume() {

        if (isEmpty()) {
            return 0L;
        }

        return (long) width()
                * (long) height()
                * (long) depth();
    }

    public int groupCount(
            String group
    ) {

        String normalizedGroup =
                normalizeGroup(
                        group
                );

        if (normalizedGroup.isEmpty()) {
            return 0;
        }

        return groupCounts.getOrDefault(
                normalizedGroup,
                0
        );
    }

    public boolean containsGroup(
            String group
    ) {

        return groupCount(
                group
        ) > 0;
    }

    public float groupRatio(
            String group
    ) {

        if (isEmpty()) {
            return 0.0F;
        }

        return (float) groupCount(
                group
        ) / (float) size();
    }

    public BlockPos minimumOffset() {

        if (isEmpty()) {
            return BlockPos.ZERO;
        }

        return new BlockPos(
                minX,
                minY,
                minZ
        );
    }

    public BlockPos maximumOffset() {

        if (isEmpty()) {
            return BlockPos.ZERO;
        }

        return new BlockPos(
                maxX,
                maxY,
                maxZ
        );
    }

    public BlockPos minimumWorldPosition() {

        return origin.offset(
                minimumOffset()
        );
    }

    public BlockPos maximumWorldPosition() {

        return origin.offset(
                maximumOffset()
        );
    }

    public boolean containsOffset(
            BlockPos offset
    ) {

        if (
                offset == null
                        || isEmpty()
        ) {
            return false;
        }

        return offset.getX() >= minX
                && offset.getX() <= maxX
                && offset.getY() >= minY
                && offset.getY() <= maxY
                && offset.getZ() >= minZ
                && offset.getZ() <= maxZ;
    }

    public boolean containsWorldPosition(
            BlockPos worldPosition
    ) {

        if (
                worldPosition == null
                        || isEmpty()
        ) {
            return false;
        }

        return containsOffset(
                worldPosition.subtract(
                        origin
                )
        );
    }

    private static BlockPos requireOrigin(
            BlockPos origin
    ) {

        if (origin == null) {

            throw new IllegalArgumentException(
                    "Recognition snapshot origin cannot be null."
            );
        }

        return origin.immutable();
    }

    private static List<DAI_RecogBlock> requireBlocks(
            List<DAI_RecogBlock> blocks
    ) {

        if (blocks == null) {

            throw new IllegalArgumentException(
                    "Recognition snapshot blocks cannot be null."
            );
        }

        return List.copyOf(
                blocks
        );
    }

    private static Map<String, Integer> requireGroupCounts(
            Map<String, Integer> groupCounts
    ) {

        if (groupCounts == null) {

            throw new IllegalArgumentException(
                    "Recognition snapshot group counts cannot be null."
            );
        }

        return groupCounts.entrySet()
                .stream()
                .filter(
                        entry ->
                                entry.getKey() != null
                                        && !entry.getKey().isBlank()
                                        && entry.getValue() != null
                                        && entry.getValue() >= 0
                )
                .collect(
                        Collectors.toUnmodifiableMap(
                                entry ->
                                        normalizeGroup(
                                                entry.getKey()
                                        ),
                                Map.Entry::getValue,
                                Integer::sum
                        )
                );
    }

    private static String normalizeGroup(
            String group
    ) {

        return group == null
                ? ""
                : group.trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    private static int calculateMinX(
            List<DAI_RecogBlock> blocks
    ) {

        return blocks == null
                || blocks.isEmpty()
                ? 0
                : blocks.stream()
                .mapToInt(
                        block ->
                                block.offset()
                                        .getX()
                )
                .min()
                .orElse(0);
    }

    private static int calculateMaxX(
            List<DAI_RecogBlock> blocks
    ) {

        return blocks == null
                || blocks.isEmpty()
                ? 0
                : blocks.stream()
                .mapToInt(
                        block ->
                                block.offset()
                                        .getX()
                )
                .max()
                .orElse(0);
    }

    private static int calculateMinY(
            List<DAI_RecogBlock> blocks
    ) {

        return blocks == null
                || blocks.isEmpty()
                ? 0
                : blocks.stream()
                .mapToInt(
                        block ->
                                block.offset()
                                        .getY()
                )
                .min()
                .orElse(0);
    }

    private static int calculateMaxY(
            List<DAI_RecogBlock> blocks
    ) {

        return blocks == null
                || blocks.isEmpty()
                ? 0
                : blocks.stream()
                .mapToInt(
                        block ->
                                block.offset()
                                        .getY()
                )
                .max()
                .orElse(0);
    }

    private static int calculateMinZ(
            List<DAI_RecogBlock> blocks
    ) {

        return blocks == null
                || blocks.isEmpty()
                ? 0
                : blocks.stream()
                .mapToInt(
                        block ->
                                block.offset()
                                        .getZ()
                )
                .min()
                .orElse(0);
    }

    private static int calculateMaxZ(
            List<DAI_RecogBlock> blocks
    ) {

        return blocks == null
                || blocks.isEmpty()
                ? 0
                : blocks.stream()
                .mapToInt(
                        block ->
                                block.offset()
                                        .getZ()
                )
                .max()
                .orElse(0);
    }
}