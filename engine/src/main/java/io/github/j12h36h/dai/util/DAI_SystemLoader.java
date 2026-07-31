package io.github.j12h36h.dai.util;

import io.github.j12h36h.dai.core.DAI;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;


public class DAI_SystemLoader extends SimpleJsonResourceReloadListener<DAI_SystemDefinition> {

    private final DAI_MenuCategory category;

    public DAI_SystemLoader(String folder, DAI_MenuCategory category) {
        super(
                DAI_SystemDefinition.CODEC,
                FileToIdConverter.json(folder)
        );

        this.category = category;
    }

    @Override
    protected void apply(
            Map<Identifier, DAI_SystemDefinition> definitions,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {

        DAI.LOGGER.info(
                "<DAI>: Loaded {} {} definition(s)",
                definitions.size(),
                category
        );

        for (Map.Entry<Identifier, DAI_SystemDefinition> entry : definitions.entrySet()) {

            DAI.LOGGER.info(
                    "<DAI>: {} -> {}",
                    category,
                    entry.getKey()
            );

            DAI_SystemManager.register(category, entry.getValue());
        }
    }
}
