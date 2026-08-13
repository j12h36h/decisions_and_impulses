package io.github.j12h36h.dai.reactions;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

public final class DAI_ReactionLoader
        extends SimpleJsonResourceReloadListener<DAI_ReactionDefinition> {

    public DAI_ReactionLoader() {

        super(
                DAI_ReactionDefinition.CODEC,
                FileToIdConverter.json(
                        "reactions"
                )
        );
    }

    @Override
    protected void apply(
            Map<Identifier, DAI_ReactionDefinition> definitions,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {

        DAI_ReactionLibrary.clear();

        definitions.forEach(
                DAI_ReactionLibrary::register
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Loaded {} reaction definition(s).",
                definitions.size()
        );
    }
}
