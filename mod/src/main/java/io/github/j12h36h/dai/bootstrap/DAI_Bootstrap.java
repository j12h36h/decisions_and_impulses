package io.github.j12h36h.dai.bootstrap;

import io.github.j12h36h.dai.action.DAI_ActionHandler;
import io.github.j12h36h.dai.condition.DAI_ConditionHandler;
import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.recognition.DAI_RecognitionHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

public final class DAI_Bootstrap {

    private DAI_Bootstrap() {
        // Utility class.
    }

    public static void initialize(
            IEventBus modBus,
            ModContainer container
    ) {

        DAI_Core.LOGGER.info(
                "<DAI>: Initializing DAI..."
        );

        DAI_ConfigBootstrap.initialize(
                container
        );

        DAI_ConditionHandler.initialize();
        DAI_ActionHandler.initialize();
        DAI_RecognitionHandler.initialize();

        DAI_DataBootstrap.initialize();

        DAI_ClientBootstrap.initialize(
                modBus,
                container
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Bootstrap complete."
        );
    }
}