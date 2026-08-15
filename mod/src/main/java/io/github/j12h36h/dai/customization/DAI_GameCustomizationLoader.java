package io.github.j12h36h.dai.customization;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/** Server/integrated-client JSON reload listener for one 1.9 customization kind. */
public final class DAI_GameCustomizationLoader
        extends SimpleJsonResourceReloadListener<DAI_GameCustomizationDefinition> {

    private final DAI_GameCustomizationKind kind;

    public DAI_GameCustomizationLoader(DAI_GameCustomizationKind kind) {
        super(DAI_GameCustomizationDefinition.CODEC, FileToIdConverter.json(kind.folder()));
        this.kind = kind;
    }

    @Override
    protected void apply(
            Map<Identifier, DAI_GameCustomizationDefinition> definitions,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        DAI_GameCustomizationRegistry.clear(kind);
        definitions.forEach((id, definition) ->
                DAI_GameCustomizationRegistry.register(kind, id, definition));
        DAI_Core.LOGGER.info(
                "<DAI>: Loaded {} {} customization definition(s) from '{}'.",
                definitions.size(), kind.id(), kind.folder()
        );
    }
}
