package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.menus.system.DAI_WaypointMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public final class DAI_SpeedrunLogic {

    private static final String DEFAULT_PORTAL_WAYPOINT =
            "sr_portal_origin";

    private static final int PORTAL_SITE_SEARCH_RADIUS =
            20;

    private static final int PORTAL_SITE_VERTICAL_RADIUS =
            6;

    private DAI_SpeedrunLogic() {
        // Utility class.
    }

    /**
     * Finds a nearby flat, dry, accessible 4x5 vertical portal plane.
     *
     * The datapack builds a minimum ten-obsidian frame using three temporary
     * cobblestone corner/support blocks, so the chosen plane must be empty and
     * supported by four solid ground blocks. No terrain modification is done
     * here: Speedrun simply chooses the nearest usable site.
     */
    public static void findPortalSite(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            fail(
                    "Cannot find a Speedrun portal site without an active player and level."
            );

            return;
        }

        String waypointName =
                action == null
                        || action.open().isBlank()
                        ? DEFAULT_PORTAL_WAYPOINT
                        : action.open();

        BlockPos playerPosition =
                minecraft.player.blockPosition();

        for (
                int radius = 3;
                radius <= PORTAL_SITE_SEARCH_RADIUS;
                radius++
        ) {

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {

                    if (
                            Math.max(
                                    Math.abs(dx),
                                    Math.abs(dz)
                            )
                                    != radius
                    ) {
                        continue;
                    }

                    for (
                            int verticalStep = 0;
                            verticalStep
                                    <= PORTAL_SITE_VERTICAL_RADIUS * 2;
                            verticalStep++
                    ) {

                        int dy =
                                verticalStep == 0
                                        ? 0
                                        : (
                                        (verticalStep + 1) / 2
                                                * (
                                                verticalStep % 2 == 0
                                                        ? 1
                                                        : -1
                                        )
                                );

                        BlockPos origin =
                                playerPosition.offset(
                                        dx,
                                        dy,
                                        dz
                                );

                        if (
                                isPortalSite(
                                        minecraft,
                                        origin
                                )
                        ) {

                            DAI_WaypointMemory.remember(
                                    waypointName,
                                    minecraft.level.dimension(),
                                    origin
                            );

                            DAI_ActionStatus.set(
                                    DAI_ActionResult.SUCCESS
                            );

                            DAI_Core.LOGGER.info(
                                    "<DAI>: Speedrun selected portal site '{}' at {} (distanceRadius={}).",
                                    waypointName,
                                    origin,
                                    radius
                            );

                            return;
                        }
                    }
                }
            }
        }

        fail(
                "No clear Speedrun portal site found within "
                        + PORTAL_SITE_SEARCH_RADIUS
                        + " blocks."
        );
    }

    private static boolean isPortalSite(
            Minecraft minecraft,
            BlockPos origin
    ) {

        /*
         * Frame plane is X-wide and Z-thin:
         *
         * y=4   C O O .
         * y=3   O . . O
         * y=2   O . . O
         * y=1   O . . O
         * y=0   C O O C
         *
         * C = temporary cobblestone support/corner
         * O = obsidian
         *
         * Portal corners may be non-obsidian, preserving the minimum
         * ten-obsidian frame.
         */
        for (int x = 0; x < 4; x++) {

            BlockPos ground =
                    origin.offset(
                            x,
                            -1,
                            0
                    );

            if (
                    !minecraft.level.hasChunkAt(
                            ground
                    )
                    || !isSolidGround(
                            minecraft,
                            ground
                    )
            ) {
                return false;
            }

            for (int y = 0; y < 5; y++) {

                BlockPos cell =
                        origin.offset(
                                x,
                                y,
                                0
                        );

                if (
                        !minecraft.level.hasChunkAt(
                                cell
                        )
                        || !isReplaceableDry(
                                minecraft,
                                cell
                        )
                ) {
                    return false;
                }
            }
        }

        /*
         * Keep one-block approach/entry lanes clear on both faces through the
         * lower four blocks. This prevents choosing a frame plane embedded in
         * a cliff or tree canopy.
         */
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z : new int[]{-1, 1}) {

                    BlockPos lane =
                            origin.offset(
                                    x,
                                    y,
                                    z
                            );

                    if (
                            !minecraft.level.hasChunkAt(
                                    lane
                            )
                            || !isReplaceableDry(
                                    minecraft,
                                    lane
                            )
                    ) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static boolean isReplaceableDry(
            Minecraft minecraft,
            BlockPos position
    ) {

        BlockState state =
                minecraft.level.getBlockState(
                        position
                );

        return state.getFluidState()
                .isEmpty()
                && (
                state.isAir()
                        || state.canBeReplaced()
        );
    }

    private static boolean isSolidGround(
            Minecraft minecraft,
            BlockPos position
    ) {

        BlockState state =
                minecraft.level.getBlockState(
                        position
                );

        return !state.isAir()
                && !state.is(
                BlockTags.LEAVES
        )
                && !state.is(
                BlockTags.LOGS
        )
                && state.getFluidState()
                .isEmpty()
                && !state.getCollisionShape(
                minecraft.level,
                position
        )
                .isEmpty();
    }

    private static void fail(
            String reason
    ) {

        DAI_ActionStatus.set(
                DAI_ActionResult.FAILURE
        );

        DAI_Core.LOGGER.warn(
                "<DAI>: {}",
                reason
        );
    }
}
