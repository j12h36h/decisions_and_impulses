package io.github.j12h36h.dai.util;

import io.github.j12h36h.dai.core.DAI;
import io.github.j12h36h.dai.ui.DAI_MenuCategory;
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

            String id = entry.getKey().getPath();

            DAI.LOGGER.info(
                    "<DAI>: {} -> {}",
                    category,
                    id
            );

            DAI_SystemManager.register(
                    category,
                    id,
                    entry.getValue()
            );
        }
    }
}
