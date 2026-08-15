package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecogBlock;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecogDefinition;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecogEvaluator;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecogGroupManager;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecogScanner;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecogSnapshot;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecognitionLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight perception cache for nearby structural recognition.
 *
 * Conditions are intentionally allowed to query this every client tick.
 * Expensive world/structure scans are refreshed only on the cadence below,
 * while intermediate calls are constant-time cache reads.
 */
public final class DAI_RecognitionPerceptionLogic {

    private static final int DEFAULT_NEARBY_RADIUS =
            16;

    private static final int MAXIMUM_NEARBY_RADIUS =
            48;

    /**
     * Two structural perception refreshes per second at Minecraft's normal
     * 20 TPS. Menus still reevaluate at 20 TPS; only the expensive scan is
     * throttled.
     */
    private static final int REFRESH_INTERVAL_TICKS =
            10;

    private static Object cacheLevel;

    private static final Map<
            NearbyRecognitionKey,
            CachedRecognition
            > NEARBY_CACHE =
            new HashMap<>();

    private DAI_RecognitionPerceptionLogic() {
        // Utility class.
    }

    public static boolean recognizesNearby(
            Identifier recognitionId,
            int requestedRadius
    ) {

        if (recognitionId == null) {
            return false;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {
            return false;
        }

        int radius =
                Math.max(
                        1,
                        Math.min(
                                requestedRadius > 0
                                        ? requestedRadius
                                        : DEFAULT_NEARBY_RADIUS,
                                MAXIMUM_NEARBY_RADIUS
                        )
                );

        if (cacheLevel != minecraft.level) {

            cacheLevel =
                    minecraft.level;

            NEARBY_CACHE.clear();
        }

        NearbyRecognitionKey key =
                new NearbyRecognitionKey(
                        recognitionId,
                        radius
                );

        long gameTick =
                minecraft.level
                        .getGameTime();

        CachedRecognition cached =
                NEARBY_CACHE.get(
                        key
                );

        if (
                cached != null
                        && cacheIsFresh(
                        cached,
                        gameTick
                )
        ) {
            return cached.recognized();
        }

        BlockPos playerPosition =
                minecraft.player
                        .blockPosition()
                        .immutable();

        boolean recognized =
                scanNearby(
                        minecraft,
                        recognitionId,
                        radius,
                        playerPosition
                );

        NEARBY_CACHE.put(
                key,
                new CachedRecognition(
                        gameTick,
                        recognized
                )
        );

        return recognized;
    }

    private static boolean cacheIsFresh(
            CachedRecognition cached,
            long gameTick
    ) {

        long age =
                gameTick
                        - cached.refreshTick();

        return age >= 0L
                && age < REFRESH_INTERVAL_TICKS;
    }

    private static boolean scanNearby(
            Minecraft minecraft,
            Identifier recognitionId,
            int radius,
            BlockPos playerPosition
    ) {

        for (Identifier definitionId : DAI_RecognitionLibrary.ids()) {

            DAI_RecogDefinition definition =
                    DAI_RecognitionLibrary.get(
                            definitionId
                    );

            if (definition == null) {
                continue;
            }

            Identifier resultId =
                    Identifier.tryParse(
                            definition.result()
                                    .id()
                    );

            if (
                    !recognitionId.equals(definitionId)
                            && !recognitionId.equals(resultId)
            ) {
                continue;
            }

            if (
                    recognizesDefinitionNearby(
                            minecraft,
                            definition,
                            radius,
                            playerPosition
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private static boolean recognizesDefinitionNearby(
            Minecraft minecraft,
            DAI_RecogDefinition definition,
            int radius,
            BlockPos playerPosition
    ) {

        int definitionVerticalRange =
                Math.max(
                        definition.scan()
                                .maxRadius(),
                        Math.max(
                                definition.scan()
                                        .upwardRange(),
                                definition.scan()
                                        .downwardRange()
                        )
                );

        int verticalRadius =
                Math.min(
                        radius,
                        Math.max(
                                4,
                                definitionVerticalRange
                        )
                );

        List<Identifier> groupIds =
                recognitionGroupIds(
                        definition
                );

        if (groupIds.isEmpty()) {
            return false;
        }

        Set<BlockPos> testedBlocks =
                new HashSet<>();

        BlockPos.MutableBlockPos candidate =
                new BlockPos.MutableBlockPos();

        int playerX =
                playerPosition.getX();

        int playerY =
                playerPosition.getY();

        int playerZ =
                playerPosition.getZ();

        /*
         * Cheap block/group matching happens first. The expensive structural
         * scanner/evaluator is invoked only for a matching candidate, and
         * blocks already consumed by one snapshot are not tested again.
         */
        for (int ring = 0; ring <= radius; ring++) {

            for (int deltaX = -ring; deltaX <= ring; deltaX++) {

                for (int deltaZ = -ring; deltaZ <= ring; deltaZ++) {

                    if (
                            ring > 0
                                    && Math.max(
                                    Math.abs(deltaX),
                                    Math.abs(deltaZ)
                            ) != ring
                    ) {
                        continue;
                    }

                    int x =
                            playerX + deltaX;

                    int z =
                            playerZ + deltaZ;

                    for (
                            int verticalOffset = -verticalRadius;
                            verticalOffset <= verticalRadius;
                            verticalOffset++
                    ) {

                        candidate.set(
                                x,
                                playerY + verticalOffset,
                                z
                        );

                        if (!minecraft.level.hasChunkAt(candidate)) {
                            continue;
                        }

                        BlockPos immutableCandidate =
                                candidate.immutable();

                        if (testedBlocks.contains(immutableCandidate)) {
                            continue;
                        }

                        BlockState state =
                                minecraft.level
                                        .getBlockState(
                                                candidate
                                        );

                        if (
                                !matchesAnyGroup(
                                        state,
                                        groupIds
                                )
                        ) {
                            continue;
                        }

                        DAI_RecogSnapshot snapshot =
                                DAI_RecogScanner.scan(
                                        minecraft.level,
                                        immutableCandidate,
                                        definition
                                );

                        markTestedBlocks(
                                testedBlocks,
                                snapshot,
                                immutableCandidate
                        );

                        DAI_RecogEvaluator.Evaluation evaluation =
                                DAI_RecogEvaluator.evaluate(
                                        minecraft.level,
                                        definition,
                                        snapshot
                                );

                        if (evaluation.matched()) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static List<Identifier> recognitionGroupIds(
            DAI_RecogDefinition definition
    ) {

        List<Identifier> result =
                new ArrayList<>();

        for (
                DAI_RecogDefinition.DAI_RecogGroupRule rule
                : definition.groups()
                .values()
        ) {

            if (rule == null) {
                continue;
            }

            Identifier groupId =
                    Identifier.tryParse(
                            rule.registry()
                    );

            if (groupId != null) {
                result.add(groupId);
            }
        }

        return List.copyOf(
                result
        );
    }

    private static boolean matchesAnyGroup(
            BlockState state,
            List<Identifier> groupIds
    ) {

        if (state == null) {
            return false;
        }

        for (Identifier groupId : groupIds) {

            if (
                    DAI_RecogGroupManager.matches(
                            groupId,
                            state
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private static void markTestedBlocks(
            Set<BlockPos> testedBlocks,
            DAI_RecogSnapshot snapshot,
            BlockPos fallbackOrigin
    ) {

        if (snapshot == null || snapshot.isEmpty()) {

            testedBlocks.add(
                    fallbackOrigin
            );

            return;
        }

        BlockPos origin =
                snapshot.origin();

        for (DAI_RecogBlock block : snapshot.blocks()) {

            BlockPos offset =
                    block.offset();

            testedBlocks.add(
                    origin.offset(
                            offset.getX(),
                            offset.getY(),
                            offset.getZ()
                    )
            );
        }
    }

    private record NearbyRecognitionKey(
            Identifier recognitionId,
            int radius
    ) {
    }

    private record CachedRecognition(
            long refreshTick,
            boolean recognized
    ) {
    }
}
