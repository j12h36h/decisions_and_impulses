package io.github.j12h36h.dai.objectives.recognition;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class DAI_RecogScanner {

    private DAI_RecogScanner() {
        // Utility class.
    }

    public static DAI_RecogSnapshot scan(
            Level level,
            BlockPos origin,
            DAI_RecogDefinition definition
    ) {

        if (
                level == null
                        || origin == null
                        || definition == null
        ) {

            return emptySnapshot(
                    origin
            );
        }

        BlockPos resolvedOrigin =
                resolveOrigin(
                        level,
                        origin,
                        definition
                );

        if (resolvedOrigin == null) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Recognition scan could not resolve a valid origin."
            );

            return emptySnapshot(
                    origin
            );
        }

        return switch (
                definition.scan()
                        .mode()
                ) {

            case "connected" ->
                    scanConnected(
                            level,
                            resolvedOrigin,
                            definition
                    );

            case "bounded_region" ->
                    scanBoundedRegion(
                            level,
                            resolvedOrigin,
                            definition
                    );

            case "volume" ->
                    scanVolume(
                            level,
                            resolvedOrigin,
                            definition
                    );

            case "regional" ->
                    scanRegional(
                            level,
                            resolvedOrigin,
                            definition
                    );

            default -> {

                DAI_Core.LOGGER.warn(
                        "<DAI>: Unknown recognition scan mode '{}'; falling back to connected.",
                        definition.scan()
                                .mode()
                );

                yield scanConnected(
                        level,
                        resolvedOrigin,
                        definition
                );
            }
        };
    }

    private static BlockPos resolveOrigin(
            Level level,
            BlockPos suppliedOrigin,
            DAI_RecogDefinition definition
    ) {

        String originMode =
                definition.scan()
                        .origin();

        return switch (originMode) {

            case "targeted_block" ->
                    suppliedOrigin.immutable();

            case "nearest_match" ->
                    findNearestMatchingOrigin(
                            level,
                            suppliedOrigin,
                            definition
                    );

            case "player" -> {

                DAI_Core.LOGGER.warn(
                        "<DAI>: Recognition scan origin 'player' is not supported by this scanner entry point; using the supplied origin."
                );

                yield suppliedOrigin.immutable();
            }

            default -> {

                DAI_Core.LOGGER.warn(
                        "<DAI>: Unknown recognition scan origin '{}'; falling back to targeted_block.",
                        originMode
                );

                yield suppliedOrigin.immutable();
            }
        };
    }

    private static BlockPos findNearestMatchingOrigin(
            Level level,
            BlockPos suppliedOrigin,
            DAI_RecogDefinition definition
    ) {

        DAI_RecogDefinition.DAI_RecogScan scan =
                definition.scan();

        int horizontalRadius =
                scan.horizontalRadius();

        int upwardRange =
                scan.upwardRange();

        int downwardRange =
                scan.downwardRange();

        BlockPos bestPosition =
                null;

        long bestDistanceSquared =
                Long.MAX_VALUE;

        for (
                int y = -downwardRange;
                y <= upwardRange;
                y++
        ) {

            for (
                    int x = -horizontalRadius;
                    x <= horizontalRadius;
                    x++
            ) {

                for (
                        int z = -horizontalRadius;
                        z <= horizontalRadius;
                        z++
                ) {

                    BlockPos candidate =
                            suppliedOrigin.offset(
                                    x,
                                    y,
                                    z
                            );

                    BlockState state =
                            level.getBlockState(
                                    candidate
                            );

                    List<String> matchedGroups =
                            matchingGroups(
                                    state,
                                    definition.groups()
                            );

                    if (matchedGroups.isEmpty()) {
                        continue;
                    }

                    long distanceSquared =
                            squaredDistance(
                                    suppliedOrigin,
                                    candidate
                            );

                    if (
                            distanceSquared
                                    >= bestDistanceSquared
                    ) {
                        continue;
                    }

                    bestDistanceSquared =
                            distanceSquared;

                    bestPosition =
                            candidate.immutable();
                }
            }
        }

        if (bestPosition == null) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Recognition nearest-match origin found no configured group within horizontalRadius={}, upwardRange={}, downwardRange={}.",
                    horizontalRadius,
                    upwardRange,
                    downwardRange
            );

            return null;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Recognition nearest-match origin resolved from {} to {}.",
                suppliedOrigin,
                bestPosition
        );

        return bestPosition;
    }

    private static long squaredDistance(
            BlockPos first,
            BlockPos second
    ) {

        long x =
                (long) second.getX()
                        - first.getX();

        long y =
                (long) second.getY()
                        - first.getY();

        long z =
                (long) second.getZ()
                        - first.getZ();

        return x * x
                + y * y
                + z * z;
    }

    private static DAI_RecogSnapshot scanConnected(
            Level level,
            BlockPos origin,
            DAI_RecogDefinition definition
    ) {

        DAI_RecogDefinition.DAI_RecogScan scan =
                definition.scan();

        BlockState originState =
                level.getBlockState(
                        origin
                );

        List<String> originGroups =
                matchingGroups(
                        originState,
                        definition.groups()
                );

        if (originGroups.isEmpty()) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Recognition connected scan ignored because the resolved origin block does not match any configured group."
            );

            return emptySnapshot(
                    origin
            );
        }

        Queue<BlockPos> pending =
                new ArrayDeque<>();

        Set<BlockPos> visited =
                new HashSet<>();

        List<DAI_RecogBlock> blocks =
                new ArrayList<>();

        Map<String, Integer> groupCounts =
                new HashMap<>();

        BlockPos immutableOrigin =
                origin.immutable();

        pending.add(
                immutableOrigin
        );

        while (
                !pending.isEmpty()
                        && blocks.size()
                        < scan.maxBlocks()
        ) {

            BlockPos current =
                    pending.remove();

            if (!visited.add(current)) {
                continue;
            }

            if (
                    current.distManhattan(
                            immutableOrigin
                    ) > scan.maxRadius()
            ) {
                continue;
            }

            BlockState state =
                    level.getBlockState(
                            current
                    );

            List<String> matchedGroups =
                    matchingGroups(
                            state,
                            definition.groups()
                    );

            if (matchedGroups.isEmpty()) {
                continue;
            }

            blocks.add(
                    new DAI_RecogBlock(
                            current.subtract(
                                    immutableOrigin
                            ),
                            state
                    )
            );

            addGroupCounts(
                    groupCounts,
                    matchedGroups
            );

            for (
                    Direction direction
                    : Direction.values()
            ) {

                BlockPos adjacent =
                        current.relative(
                                direction
                        );

                if (!visited.contains(adjacent)) {

                    pending.add(
                            adjacent
                    );
                }
            }
        }

        logLimitReached(
                blocks.size(),
                scan
        );

        logCollected(
                "connected",
                blocks.size(),
                definition.groups()
                        .size()
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Recognition connected scan group counts={}.",
                groupCounts
        );

        return new DAI_RecogSnapshot(
                immutableOrigin,
                blocks,
                groupCounts
        );
    }

    private static DAI_RecogSnapshot scanBoundedRegion(
            Level level,
            BlockPos origin,
            DAI_RecogDefinition definition
    ) {

        DAI_RecogDefinition.DAI_RecogScan scan =
                definition.scan();

        BlockPos immutableOrigin =
                origin.immutable();

        List<DAI_RecogBlock> blocks =
                new ArrayList<>();

        Map<String, Integer> groupCounts =
                new HashMap<>();

        int horizontalRadius =
                scan.horizontalRadius();

        int upwardRange =
                scan.upwardRange();

        int downwardRange =
                scan.downwardRange();

        outer:
        for (
                int y = -downwardRange;
                y <= upwardRange;
                y++
        ) {

            for (
                    int x = -horizontalRadius;
                    x <= horizontalRadius;
                    x++
            ) {

                for (
                        int z = -horizontalRadius;
                        z <= horizontalRadius;
                        z++
                ) {

                    if (
                            blocks.size()
                                    >= scan.maxBlocks()
                    ) {
                        break outer;
                    }

                    BlockPos current =
                            immutableOrigin.offset(
                                    x,
                                    y,
                                    z
                            );

                    BlockState state =
                            level.getBlockState(
                                    current
                            );

                    List<String> matchedGroups =
                            matchingGroups(
                                    state,
                                    definition.groups()
                            );

                    if (matchedGroups.isEmpty()) {
                        continue;
                    }

                    blocks.add(
                            new DAI_RecogBlock(
                                    current.subtract(
                                            immutableOrigin
                                    ),
                                    state
                            )
                    );

                    addGroupCounts(
                            groupCounts,
                            matchedGroups
                    );
                }
            }
        }

        logLimitReached(
                blocks.size(),
                scan
        );

        logCollected(
                "bounded_region",
                blocks.size(),
                definition.groups()
                        .size()
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Recognition bounded-region scan horizontalRadius={}, upwardRange={}, downwardRange={}, groupCounts={}.",
                horizontalRadius,
                upwardRange,
                downwardRange,
                groupCounts
        );

        return new DAI_RecogSnapshot(
                immutableOrigin,
                blocks,
                groupCounts
        );
    }

    private static DAI_RecogSnapshot scanVolume(
            Level level,
            BlockPos origin,
            DAI_RecogDefinition definition
    ) {

        DAI_Core.LOGGER.debug(
                "<DAI>: Volume recognition scan is not implemented yet; using bounded-region scan."
        );

        return scanBoundedRegion(
                level,
                origin,
                definition
        );
    }

    private static DAI_RecogSnapshot scanRegional(
            Level level,
            BlockPos origin,
            DAI_RecogDefinition definition
    ) {

        DAI_Core.LOGGER.debug(
                "<DAI>: Regional recognition scan is not implemented yet; using bounded-region scan."
        );

        return scanBoundedRegion(
                level,
                origin,
                definition
        );
    }

    private static List<String> matchingGroups(
            BlockState state,
            Map<
                    String,
                    DAI_RecogDefinition.DAI_RecogGroupRule
                    > groups
    ) {

        if (
                state == null
                        || groups == null
                        || groups.isEmpty()
        ) {
            return List.of();
        }

        List<String> matches =
                new ArrayList<>();

        for (
                Map.Entry<
                        String,
                        DAI_RecogDefinition.DAI_RecogGroupRule
                        > entry
                : groups.entrySet()
        ) {

            String localGroup =
                    normalizeGroup(
                            entry.getKey()
                    );

            DAI_RecogDefinition.DAI_RecogGroupRule rule =
                    entry.getValue();

            if (
                    localGroup.isEmpty()
                            || rule == null
            ) {
                continue;
            }

            Identifier groupId =
                    Identifier.tryParse(
                            rule.registry()
                    );

            if (groupId == null) {

                DAI_Core.LOGGER.warn(
                        "<DAI>: Invalid recognition group registry id '{}'.",
                        rule.registry()
                );

                continue;
            }

            if (
                    DAI_RecogGroupManager.matches(
                            groupId,
                            state
                    )
            ) {

                matches.add(
                        localGroup
                );
            }
        }

        return List.copyOf(
                matches
        );
    }

    private static void addGroupCounts(
            Map<String, Integer> groupCounts,
            List<String> matchedGroups
    ) {

        if (
                groupCounts == null
                        || matchedGroups == null
                        || matchedGroups.isEmpty()
        ) {
            return;
        }

        for (String matchedGroup : matchedGroups) {

            groupCounts.merge(
                    matchedGroup,
                    1,
                    Integer::sum
            );
        }
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

    private static void logLimitReached(
            int blockCount,
            DAI_RecogDefinition.DAI_RecogScan scan
    ) {

        if (
                blockCount
                        < scan.maxBlocks()
        ) {
            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Recognition scan reached the configured block limit of {}.",
                scan.maxBlocks()
        );
    }

    private static void logCollected(
            String mode,
            int blockCount,
            int groupCount
    ) {

        DAI_Core.LOGGER.debug(
                "<DAI>: Recognition {} scan collected {} block(s) for {} configured group(s).",
                mode,
                blockCount,
                groupCount
        );
    }

    private static DAI_RecogSnapshot emptySnapshot(
            BlockPos origin
    ) {

        return new DAI_RecogSnapshot(
                origin == null
                        ? BlockPos.ZERO
                        : origin.immutable(),
                List.of(),
                Map.of()
        );
    }
}