package io.github.j12h36h.dai.logics.navigation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds a safe standable pickup position near a dropped ItemEntity.
 *
 * The collector previously treated the item's own block as the destination,
 * which could lead the player directly into a freshly mined shaft. This
 * planner prefers rim/same-level positions and only accepts descents when a
 * reverse path back to the collection start exists.
 */
public final class DAI_ItemPickupPlanner {

    private static final double MAX_PICKUP_STANCE_DISTANCE =
            1.75D;

    private static final double BELOW_START_PENALTY =
            24.0D;

    private static final double VERTICAL_CHANGE_PENALTY =
            4.0D;

    private DAI_ItemPickupPlanner() {
        // Utility class.
    }

    public static @Nullable PickupPlan plan(
            Minecraft minecraft,
            ItemEntity item
    ) {

        if (
                minecraft == null
                        || minecraft.player == null
                        || minecraft.level == null
                        || item == null
                        || !item.isAlive()
        ) {
            return null;
        }

        BlockPos start =
                minecraft.player.blockPosition()
                        .immutable();

        BlockPos itemBlock =
                item.blockPosition()
                        .immutable();

        List<PickupPlan> plans =
                new ArrayList<>();

        for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
                for (int x = -1; x <= 1; x++) {

                    BlockPos candidate =
                            itemBlock.offset(
                                    x,
                                    y,
                                    z
                            )
                            .immutable();

                    if (
                            !DAI_PathFinder.isTraversablePosition(
                                    minecraft.level,
                                    candidate
                            )
                    ) {
                        continue;
                    }

                    if (
                            minecraft.level
                                    .getFluidState(candidate)
                                    .is(FluidTags.LAVA)
                    ) {
                        continue;
                    }

                    Vec3 stanceCenter =
                            Vec3.atBottomCenterOf(
                                    candidate
                            );

                    double itemDistance =
                            stanceCenter.distanceTo(
                                    item.position()
                            );

                    if (
                            itemDistance
                                    > MAX_PICKUP_STANCE_DISTANCE
                    ) {
                        continue;
                    }

                    DAI_Path path =
                            candidate.equals(start)
                                    ? new DAI_Path(
                                    List.of(start)
                            )
                                    : DAI_PathFinder.find(
                                    minecraft.level,
                                    start,
                                    candidate
                            );

                    if (
                            path == null
                                    || path.nodes().isEmpty()
                    ) {
                        continue;
                    }

                    /*
                     * Descending is allowed only when the player can prove a
                     * route back to the exact position from which collection
                     * started. This prevents pickup from becoming an
                     * irreversible mining-hole descent.
                     */
                    if (
                            candidate.getY()
                                    < start.getY()
                    ) {

                        DAI_Path exitPath =
                                DAI_PathFinder.find(
                                        minecraft.level,
                                        candidate,
                                        start
                                );

                        if (
                                exitPath == null
                                        || exitPath.nodes().isEmpty()
                        ) {
                            continue;
                        }
                    }

                    double score =
                            path.nodes().size()
                                    + itemDistance
                                    + Math.abs(
                                    candidate.getY()
                                            - start.getY()
                            ) * VERTICAL_CHANGE_PENALTY;

                    if (
                            candidate.getY()
                                    < start.getY()
                    ) {
                        score += BELOW_START_PENALTY;
                    }

                    plans.add(
                            new PickupPlan(
                                    candidate,
                                    path,
                                    score
                            )
                    );
                }
            }
        }

        return plans.stream()
                .min(
                        Comparator.comparingDouble(
                                PickupPlan::score
                        )
                )
                .orElse(
                        null
                );
    }

    public record PickupPlan(
            BlockPos stance,
            DAI_Path path,
            double score
    ) {

        public PickupPlan {

            if (stance == null) {
                throw new IllegalArgumentException(
                        "Pickup stance cannot be null."
                );
            }

            if (path == null) {
                throw new IllegalArgumentException(
                        "Pickup path cannot be null."
                );
            }

            stance = stance.immutable();
        }
    }
}
