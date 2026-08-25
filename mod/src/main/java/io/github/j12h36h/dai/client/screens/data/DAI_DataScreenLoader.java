package io.github.j12h36h.dai.client.screens.data;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

public final class DAI_DataScreenLoader extends SimpleJsonResourceReloadListener<DAI_DataScreenDefinition> {
    public static final String FOLDER = "dai_screens";
    public DAI_DataScreenLoader() { super(DAI_DataScreenDefinition.CODEC, FileToIdConverter.json(FOLDER)); }
    @Override protected void apply(Map<Identifier, DAI_DataScreenDefinition> definitions, ResourceManager manager, ProfilerFiller profiler) {
        DAI_DataScreenRegistry.clear();
        definitions.forEach(DAI_DataScreenRegistry::register);
        DAI_Core.LOGGER.info("<DAI>: Loaded {} data-driven screen definition(s).", definitions.size());
    }
}
