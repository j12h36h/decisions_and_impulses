package io.github.j12h36h.dai.creator;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/** Resource-pack override loader for Creator presets. */
public final class DAI_CreatorPresetLoader extends SimpleJsonResourceReloadListener<DAI_CreatorPresetDefinition> {
    public DAI_CreatorPresetLoader() {
        super(DAI_CreatorPresetDefinition.CODEC, FileToIdConverter.json(DAI_CreatorPresetDefinition.FOLDER));
    }

    @Override
    protected void apply(Map<Identifier, DAI_CreatorPresetDefinition> definitions, ResourceManager manager, ProfilerFiller profiler) {
        DAI_CreatorPresetRegistry.replaceResources(definitions);
    }
}
