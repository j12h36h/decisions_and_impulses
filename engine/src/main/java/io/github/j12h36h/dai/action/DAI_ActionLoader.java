package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.core.DAI;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

public class DAI_ActionLoader extends SimpleJsonResourceReloadListener<DAI_Action> {

    public DAI_ActionLoader(String folder) {
        super(
                DAI_Action.CODEC,
                FileToIdConverter.json(folder)
        );
    }

    @Override
    protected void apply(
            Map<Identifier, DAI_Action> definitions,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {

        DAI.LOGGER.info(
                "<DAI>: Loaded {} action definition(s)",
                definitions.size()
        );

        DAI_ActionManager.clear();

        for (Map.Entry<Identifier, DAI_Action> entry : definitions.entrySet()) {

            DAI.LOGGER.info(
                    "<DAI>: Action -> {} | type={} | conditions={}",
                    entry.getKey(),
                    entry.getValue().type(),
                    entry.getValue().conditions()
            );

            DAI_ActionManager.register(
                    entry.getKey(),
                    entry.getValue()
            );
        }
    }
}