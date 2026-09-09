package io.github.j12h36h.dai.animations.eras;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads unchanged ERAS / S.A.D. exported JSON projects from DAI packs. */
public final class DAI_ErasCinematicLoader
        extends SimplePreparableReloadListener<Map<Identifier, DAI_ErasCinematicDefinition>> {

    public enum Source { SERVER_DATA, CLIENT_RESOURCES }

    public static final String SERVER_DIRECTORY = "dai_cinematics";
    public static final String CLIENT_DIRECTORY = "dai/cinematics";

    private final Source source;
    private final String directory;

    public DAI_ErasCinematicLoader(Source source) {
        this.source = source == null ? Source.SERVER_DATA : source;
        this.directory = this.source == Source.SERVER_DATA ? SERVER_DIRECTORY : CLIENT_DIRECTORY;
    }

    @Override
    protected Map<Identifier, DAI_ErasCinematicDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        LinkedHashMap<Identifier, DAI_ErasCinematicDefinition> loaded = new LinkedHashMap<>();
        if (manager == null) return loaded;

        Map<Identifier, Resource> resources = manager.listResources(
                directory,
                id -> id.getPath().endsWith(".json")
        );

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier cinematicId = toCinematicId(entry.getKey());
            if (cinematicId == null) continue;
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                loaded.put(cinematicId, DAI_ErasCinematicDefinition.parse(cinematicId, json));
            } catch (Throwable exception) {
                DAI_Core.LOGGER.error(
                        "<DAI>: Could not load ERAS cinematic '{}' from '{}': {}",
                        cinematicId,
                        entry.getKey(),
                        exception.getMessage(),
                        exception
                );
            }
        }
        return loaded;
    }

    @Override
    protected void apply(
            Map<Identifier, DAI_ErasCinematicDefinition> loaded,
            ResourceManager manager,
            ProfilerFiller profiler
    ) {
        Map<Identifier, DAI_ErasCinematicDefinition> safe = loaded == null ? Map.of() : Map.copyOf(loaded);
        if (source == Source.SERVER_DATA) DAI_ErasCinematicRegistry.replaceServer(safe);
        else DAI_ErasCinematicRegistry.replaceClient(safe);

        DAI_Core.LOGGER.info(
                "<DAI>: Loaded {} ERAS cinematic project(s) from {} '{}'.",
                safe.size(),
                source == Source.SERVER_DATA ? "datapack" : "resource-pack",
                directory
        );
    }

    private Identifier toCinematicId(Identifier resourceId) {
        if (resourceId == null) return null;
        String path = resourceId.getPath();
        String prefix = directory + "/";
        if (!path.startsWith(prefix) || !path.endsWith(".json")) return null;
        String relative = path.substring(prefix.length(), path.length() - 5);
        if (relative.isBlank()) return null;
        return Identifier.fromNamespaceAndPath(resourceId.getNamespace(), relative);
    }
}
