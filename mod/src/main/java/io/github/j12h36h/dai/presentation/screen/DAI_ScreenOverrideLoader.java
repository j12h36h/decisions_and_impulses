package io.github.j12h36h.dai.presentation.screen;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

public final class DAI_ScreenOverrideLoader extends SimpleJsonResourceReloadListener<DAI_ScreenOverrideDefinition> {
    public static final String FOLDER = "screen_overrides";

    public DAI_ScreenOverrideLoader() {
        super(DAI_ScreenOverrideDefinition.CODEC, FileToIdConverter.json(FOLDER));
    }

    @Override
    protected void apply(Map<Identifier, DAI_ScreenOverrideDefinition> definitions, ResourceManager manager, ProfilerFiller profiler) {
        DAI_ScreenOverrideRegistry.replace(definitions);
    }
}
