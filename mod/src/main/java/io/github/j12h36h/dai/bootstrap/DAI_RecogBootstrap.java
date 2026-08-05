package io.github.j12h36h.dai.bootstrap;


import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.recognition.DAI_RecogRequirementRegistry;
import io.github.j12h36h.dai.recognition.DAI_RecogRequirements;
public final class DAI_RecogBootstrap {

    private DAI_RecogBootstrap() {
        // Utility class.
    }

    public static void initialize() {

        DAI_Core.LOGGER.info(
                "<DAI>: Initializing recognition bootstrap..."
        );

        DAI_RecogRequirementRegistry.clear();

        DAI_RecogRequirementRegistry.register(
                "connected",
                DAI_RecogRequirements::connected
        );

        DAI_RecogRequirementRegistry.register(
                "vertical_column",
                DAI_RecogRequirements::verticalColumn
        );

        DAI_RecogRequirementRegistry.register(
                "touches_ground",
                DAI_RecogRequirements::touchesGround
        );

        DAI_RecogRequirementRegistry.register(
                "near_upper_region",
                DAI_RecogRequirements::nearUpperRegion
        );

        DAI_RecogRequirementRegistry.register(
                "contains_group",
                DAI_RecogRequirements::containsGroup
        );

        DAI_RecogRequirementRegistry.register(
                "dimensions",
                DAI_RecogRequirements::dimensions
        );

        DAI_RecogRequirementRegistry.register(
                "group_ratio",
                DAI_RecogRequirements::groupRatio
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Registered {} recognition requirement handler(s).",
                DAI_RecogRequirementRegistry.size()
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Recognition bootstrap initialized."
        );
    }
}