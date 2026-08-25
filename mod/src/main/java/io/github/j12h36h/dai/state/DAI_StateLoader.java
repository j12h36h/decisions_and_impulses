package io.github.j12h36h.dai.state;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

public final class DAI_StateLoader extends SimpleJsonResourceReloadListener<DAI_StateDefinition> {
    public static final String FOLDER = "dai_state";

    public DAI_StateLoader() { super(DAI_StateDefinition.CODEC, FileToIdConverter.json(FOLDER)); }

    @Override
    protected void apply(Map<Identifier, DAI_StateDefinition> definitions, ResourceManager manager, ProfilerFiller profiler) {
        DAI_StateRegistry.clear();
        definitions.forEach(DAI_StateRegistry::register);
        DAI_Core.LOGGER.info("<DAI>: Loaded {} declarative state definition(s).", definitions.size());
    }
}
