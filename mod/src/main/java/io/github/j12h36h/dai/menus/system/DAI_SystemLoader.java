package io.github.j12h36h.dai.menus.system;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.menus.DAI_MenuCategory;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;
public final class DAI_SystemLoader
        extends SimpleJsonResourceReloadListener<DAI_SystemDefinition> {

    private final String folder;
    private final DAI_MenuCategory category;

    public DAI_SystemLoader(
            String folder,
            DAI_MenuCategory category
    ) {

        super(
                DAI_SystemDefinition.CODEC,
                FileToIdConverter.json(folder)
        );

        this.folder = folder;
        this.category = category;
    }

    @Override
    protected void apply(
            Map<Identifier, DAI_SystemDefinition> definitions,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {

        DAI_Core.LOGGER.info(
                "<DAI>: Reloading {} system definitions from '{}'.",
                category,
                folder
        );

        DAI_SystemManager.clear(category);

        definitions.forEach((identifier, definition) -> {

            String id = identifier.getPath();

            DAI_Core.LOGGER.debug(
                    "<DAI>: Registering {} system definition '{}' with priority {} and {} button(s).",
                    category,
                    identifier,
                    definition.priority(),
                    definition.buttons().size()
            );

            DAI_SystemManager.register(
                    category,
                    id,
                    definition
            );
        });

        DAI_Core.LOGGER.info(
                "<DAI>: Loaded {} {} system definition(s).",
                definitions.size(),
                category
        );
    }
}