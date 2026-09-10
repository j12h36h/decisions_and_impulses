package io.github.j12h36h.dai.input;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/** Loads resource-pack-side input profile overrides. */
public final class DAI_InputProfileLoader extends SimpleJsonResourceReloadListener<DAI_InputProfileDefinition> {
    public DAI_InputProfileLoader() {
        super(DAI_InputProfileDefinition.CODEC, FileToIdConverter.json(DAI_InputProfileDefinition.FOLDER));
    }

    @Override
    protected void apply(Map<Identifier, DAI_InputProfileDefinition> definitions, ResourceManager manager, ProfilerFiller profiler) {
        DAI_InputProfileRegistry.replaceResources(definitions);
    }
}
