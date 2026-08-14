package io.github.j12h36h.dai.attributes;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

public final class DAI_AttributeLoader extends SimpleJsonResourceReloadListener<DAI_AttributeDefinition> {

    public DAI_AttributeLoader() {
        super(DAI_AttributeDefinition.CODEC, FileToIdConverter.json("dai_attributes"));
    }

    @Override
    protected void apply(
            Map<Identifier, DAI_AttributeDefinition> definitions,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        DAI_AttributeRegistry.clear();
        definitions.forEach(DAI_AttributeRegistry::register);
        DAI_Core.LOGGER.info("<DAI>: Loaded {} custom DAI attribute definition(s).", definitions.size());
    }
}
