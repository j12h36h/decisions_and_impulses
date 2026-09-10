package io.github.j12h36h.dai.creator;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/** Allows resource packs to reskin/reorganize the Creator schema catalog. */
public final class DAI_CreatorSchemaLoader extends SimpleJsonResourceReloadListener<DAI_CreatorSchemaDefinition> {
    public DAI_CreatorSchemaLoader() {
        super(DAI_CreatorSchemaDefinition.CODEC, FileToIdConverter.json(DAI_CreatorSchemaDefinition.FOLDER));
    }

    @Override
    protected void apply(Map<Identifier, DAI_CreatorSchemaDefinition> definitions, ResourceManager manager, ProfilerFiller profiler) {
        DAI_CreatorSchemaRegistry.replaceResources(definitions);
    }
}
