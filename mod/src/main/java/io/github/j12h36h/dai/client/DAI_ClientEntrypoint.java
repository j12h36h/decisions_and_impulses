package io.github.j12h36h.dai.client;

import io.github.j12h36h.dai.client.bootstrap.DAI_ClientBootstrap;
import io.github.j12h36h.dai.client.data.DAI_ClientDataBootstrap;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionHandler;
import io.github.j12h36h.dai.client.logics.condition.DAI_ConditionHandler;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.registry.DAI_DynamicRegistryBootstrap;
import io.github.j12h36h.dai.client.registry.DAI_GeneratedAssetsPack;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecognitionHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** Physical-client entrypoint. Dedicated servers never load this class. */
@Mod(value = DAI_Core.MODID, dist = Dist.CLIENT)
public final class DAI_ClientEntrypoint {

    public DAI_ClientEntrypoint(IEventBus modBus, ModContainer container) {
        // Ensure native registry discovery has happened even if NeoForge
        // constructs this physical-client entrypoint before the common one.
        DAI_DynamicRegistryBootstrap.initialize(modBus);
        if (DAI_DynamicRegistryBootstrap.hasNativeContent()) {
            DAI_GeneratedAssetsPack.rebuild(DAI_DynamicRegistryBootstrap.bootSpecs().values());
            DAI_GeneratedAssetsPack.initialize(modBus);
        }

        DAI_ConditionHandler.initialize();
        DAI_ActionHandler.initialize();
        DAI_RecognitionHandler.initialize();
        DAI_ClientDataBootstrap.initialize();
        DAI_ClientBootstrap.initialize(modBus, container);

        DAI_Core.LOGGER.info("<DAI>: Physical-client layer initialized.");
    }
}
