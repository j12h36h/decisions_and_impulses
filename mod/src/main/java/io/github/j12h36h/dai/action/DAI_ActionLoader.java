package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

public final class DAI_ActionLoader
        extends SimpleJsonResourceReloadListener<DAI_ActionCore> {

    private final String folder;

    public DAI_ActionLoader(String folder) {

        super(
                DAI_ActionCore.CODEC,
                FileToIdConverter.json(folder)
        );

        this.folder = folder;
    }

    @Override
    protected void apply(
            Map<Identifier, DAI_ActionCore> definitions,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {

        DAI_Core.LOGGER.info(
                "<DAI>: Reloading action definitions from '{}'.",
                folder
        );

        DAI_ActionManager.clear();

        definitions.forEach((identifier, action) -> {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Registering action '{}' with type='{}', reference='{}', conditions={}, sequence={}, ticks={}.",
                    identifier,
                    action.type(),
                    action.action(),
                    action.conditions().size(),
                    action.sequence().size(),
                    action.ticks()
            );

            DAI_ActionManager.register(
                    identifier,
                    action
            );
        });

        DAI_Core.LOGGER.info(
                "<DAI>: Loaded {} action definition(s).",
                definitions.size()
        );
    }
}