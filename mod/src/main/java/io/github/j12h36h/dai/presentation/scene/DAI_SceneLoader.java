package io.github.j12h36h.dai.presentation.scene;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/** Loads scene-environment JSON from the active client resource-pack stack. */
public final class DAI_SceneLoader extends SimpleJsonResourceReloadListener<DAI_SceneDefinition> {
    public static final String FOLDER = "scene_environments";

    public DAI_SceneLoader() {
        super(DAI_SceneDefinition.CODEC, FileToIdConverter.json(FOLDER));
    }

    @Override
    protected void apply(Map<Identifier, DAI_SceneDefinition> definitions, ResourceManager manager, ProfilerFiller profiler) {
        DAI_SceneRegistry.replaceResources(definitions);
    }
}
