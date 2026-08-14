package io.github.j12h36h.dai.content;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

public final class DAI_ContentLoader extends SimpleJsonResourceReloadListener<DAI_ContentDefinition> {

    private final DAI_ContentKind kind;

    public DAI_ContentLoader(DAI_ContentKind kind) {
        super(DAI_ContentDefinition.CODEC, FileToIdConverter.json(kind.folder()));
        this.kind = kind;
    }

    @Override
    protected void apply(
            Map<Identifier, DAI_ContentDefinition> definitions,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        DAI_ContentRuntime.clear();
        DAI_ContentRegistry.clear(kind);
        definitions.forEach((id, definition) -> DAI_ContentRegistry.register(kind, id, definition));
        DAI_Core.LOGGER.info(
                "<DAI>: Loaded {} {} definition(s) from '{}'.",
                definitions.size(),
                kind.id(),
                kind.folder()
        );
    }
}
