package io.github.j12h36h.dai.client.logics.input;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

public final class DAI_KeybindLoader extends SimpleJsonResourceReloadListener<DAI_KeybindDefinition> {
    public static final String FOLDER = "dai_keybinds";
    public DAI_KeybindLoader() { super(DAI_KeybindDefinition.CODEC, FileToIdConverter.json(FOLDER)); }
    @Override protected void apply(Map<Identifier, DAI_KeybindDefinition> definitions, ResourceManager manager, ProfilerFiller profiler) {
        DAI_KeybindRegistry.clear();
        definitions.forEach(DAI_KeybindRegistry::register);
        DAI_Core.LOGGER.info("<DAI>: Loaded {} datapack keybind definition(s).", definitions.size());
    }
}
