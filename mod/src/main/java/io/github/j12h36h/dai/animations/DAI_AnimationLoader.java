package io.github.j12h36h.dai.animations;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

public final class DAI_AnimationLoader extends SimpleJsonResourceReloadListener<DAI_AnimationDefinition> {
    public DAI_AnimationLoader() {
        super(DAI_AnimationDefinition.CODEC, FileToIdConverter.json("dai_animations"));
    }

    @Override
    protected void apply(Map<Identifier, DAI_AnimationDefinition> definitions, ResourceManager manager, ProfilerFiller profiler) {
        DAI_AnimationRegistry.clear();
        definitions.forEach(DAI_AnimationRegistry::register);
        DAI_Core.LOGGER.info("<DAI>: Loaded {} custom animation definition(s).", definitions.size());
    }
}
