package io.github.j12h36h.dai.learning;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

public final class DAI_LearningAgentLoader extends SimpleJsonResourceReloadListener<DAI_LearningAgentDefinition> {
    public static final String FOLDER = "learning/agents";

    public DAI_LearningAgentLoader() {
        super(DAI_LearningAgentDefinition.CODEC, FileToIdConverter.json(FOLDER));
    }

    @Override
    protected void apply(Map<Identifier, DAI_LearningAgentDefinition> definitions,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        applyDefinitions(definitions);
    }

    public static void applyDefinitions(Map<Identifier, DAI_LearningAgentDefinition> definitions) {
        DAI_LearningLibrary.clear();
        if (definitions != null) definitions.forEach(DAI_LearningLibrary::register);
        DAI_Core.LOGGER.info("<DAI>: Loaded {} learning agent definition(s).", DAI_LearningLibrary.size());
    }
}
