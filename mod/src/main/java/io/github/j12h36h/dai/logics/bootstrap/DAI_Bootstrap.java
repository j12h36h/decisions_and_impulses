package io.github.j12h36h.dai.logics.bootstrap;

import io.github.j12h36h.dai.logics.action.DAI_ActionHandler;
import io.github.j12h36h.dai.logics.condition.DAI_ConditionHandler;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.objectives.recognition.DAI_RecognitionHandler;
import io.github.j12h36h.dai.network.DAI_NetworkBootstrap;
import io.github.j12h36h.dai.content.DAI_ContentComponents;
import io.github.j12h36h.dai.reactions.DAI_ReactionEventRegistry;
import io.github.j12h36h.dai.registry.DAI_DynamicRegistryBootstrap;
import io.github.j12h36h.dai.registry.DAI_RegistryWorldStore;
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

        // Native content discovery must happen before every other DAI
        // subsystem so the complete registry plan exists before NeoForge
        // begins firing static RegisterEvent instances.
        DAI_DynamicRegistryBootstrap.initialize(modBus);

        DAI_ConfigBootstrap.initialize(
                modBus,
                container
        );

        DAI_ContentComponents.initialize(modBus);
        DAI_RegistryWorldStore.initialize();

        DAI_ConditionHandler.initialize();
        DAI_ActionHandler.initialize();
        DAI_RecognitionHandler.initialize();
        DAI_ReactionEventRegistry.initialize();
        DAI_NetworkBootstrap.initialize(modBus);

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