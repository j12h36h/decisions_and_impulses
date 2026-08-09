package io.github.j12h36h.dai.logics.creation;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DAI_RecipeLoader
        extends SimplePreparableReloadListener<
        Map<Identifier, Resource>
        > {

    public static final String DIRECTORY =
            "dai_recipes";

    public DAI_RecipeLoader() {
        // Reload listener.
    }

    @Override
    protected Map<Identifier, Resource> prepare(
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {

        if (resourceManager == null) {
            return Map.of();
        }

        return new LinkedHashMap<>(
                resourceManager.listResources(
                        DIRECTORY,
                        id -> id.getPath()
                                .endsWith(
                                        ".json"
                                )
                )
        );
    }

    @Override
    protected void apply(
            Map<Identifier, Resource> resources,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {

        DAI_RecipeRegistry.clear();

        if (
                resources == null
                        || resources.isEmpty()
        ) {

            DAI_Core.LOGGER.info(
                    "<DAI>: Loaded 0 DAI processing recipe definition(s) from '{}'.",
                    DIRECTORY
            );

            return;
        }

        for (
                Map.Entry<Identifier, Resource> entry
                : resources.entrySet()
        ) {

            DAI_RecipeParser.load(
                    entry.getKey(),
                    entry.getValue()
            );
        }

        DAI_Core.LOGGER.info(
                "<DAI>: Loaded {} DAI processing recipe definition(s) from '{}'.",
                DAI_RecipeRegistry.size(),
                DIRECTORY
        );
    }
}