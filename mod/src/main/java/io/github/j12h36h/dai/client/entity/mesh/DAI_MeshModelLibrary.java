package io.github.j12h36h.dai.client.entity.mesh;

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
import java.util.Locale;
import java.util.Map;

/**
 * Client resource-pack library for native DAI mesh models.
 *
 * Resource convention:
 * assets/<namespace>/dai/models/<path>.json -> <namespace>:<path>
 */
public final class DAI_MeshModelLibrary
        extends SimplePreparableReloadListener<Map<Identifier, DAI_MeshModel>> {

    public static final String DIRECTORY = "dai/models";
    private static volatile Map<Identifier, DAI_MeshModel> MODELS = Map.of();

    @Override
    protected Map<Identifier, DAI_MeshModel> prepare(
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        LinkedHashMap<Identifier, DAI_MeshModel> loaded = new LinkedHashMap<>();
        if (resourceManager == null) return loaded;

        Map<Identifier, Resource> resources = resourceManager.listResources(
                DIRECTORY,
                id -> id.getPath().endsWith(".json")
        );

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier resourceId = entry.getKey();
            Identifier modelId = toModelId(resourceId);
            if (modelId == null) continue;

            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject geometry = json.has("geometry") && json.get("geometry").isJsonObject()
                        ? json.getAsJsonObject("geometry")
                        : null;
                if (geometry == null
                        || !geometry.has("type")
                        || !"mesh".equalsIgnoreCase(geometry.get("type").getAsString())) {
                    // dai/models is intentionally shared with non-mesh DAI model
                    // formats. This loader only claims geometry.type=mesh.
                    continue;
                }
                DAI_MeshModel model = DAI_MeshModelParser.parse(modelId, json, resourceManager);
                loaded.put(modelId, model);
            } catch (Throwable exception) {
                DAI_Core.LOGGER.error(
                        "<DAI>: Could not load native mesh model '{}' from '{}': {}",
                        modelId,
                        resourceId,
                        exception.getMessage(),
                        exception
                );
            }
        }

        return loaded;
    }

    @Override
    protected void apply(
            Map<Identifier, DAI_MeshModel> loaded,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        MODELS = loaded == null ? Map.of() : Map.copyOf(loaded);
        int triangles = 0;
        for (DAI_MeshModel model : MODELS.values()) triangles += model.triangleCount();
        DAI_Core.LOGGER.info(
                "<DAI>: Loaded {} native mesh model(s) / {} triangle(s) from resource packs.",
                MODELS.size(),
                triangles
        );
    }

    public static DAI_MeshModel get(String reference, String fallbackNamespace) {
        Identifier id = resolveModelId(reference, fallbackNamespace);
        return id == null ? null : MODELS.get(id);
    }

    public static DAI_MeshModel get(Identifier id) {
        return id == null ? null : MODELS.get(id);
    }

    public static int size() {
        return MODELS.size();
    }

    public static Identifier resolveModelId(String reference, String fallbackNamespace) {
        if (reference == null) return null;
        String value = reference.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (value.isBlank()
                || value.equals("dai:native")
                || value.equals("decisions_and_impulses:native")
                || value.equals("native")) {
            return null;
        }

        String namespace = fallbackNamespace == null || fallbackNamespace.isBlank()
                ? DAI_Core.MODID
                : fallbackNamespace.trim().toLowerCase(Locale.ROOT);
        String path = value;

        int colon = value.indexOf(':');
        if (colon >= 0) {
            namespace = value.substring(0, colon);
            path = value.substring(colon + 1);
        }

        if (path.startsWith("assets/")) {
            // Accept an authored filesystem-ish reference such as
            // assets/foo/dai/models/entity/mob.json.
            String[] parts = path.split("/", 3);
            if (parts.length == 3) {
                namespace = parts[1];
                path = parts[2];
            }
        }

        if (path.startsWith(DIRECTORY + "/")) {
            path = path.substring((DIRECTORY + "/").length());
        }
        if (path.endsWith(".dai.json")) {
            path = path.substring(0, path.length() - 9);
        } else if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - 5);
        } else if (path.endsWith(".dai")) {
            path = path.substring(0, path.length() - 4);
        }
        while (path.startsWith("/")) path = path.substring(1);

        if (namespace.isBlank() || path.isBlank()) return null;
        try {
            return Identifier.fromNamespaceAndPath(namespace, path);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Identifier toModelId(Identifier resourceId) {
        if (resourceId == null) return null;
        String path = resourceId.getPath();
        String prefix = DIRECTORY + "/";
        if (!path.startsWith(prefix) || !path.endsWith(".json")) return null;
        String modelPath = path.substring(prefix.length());
        if (modelPath.endsWith(".dai.json")) {
            modelPath = modelPath.substring(0, modelPath.length() - 9);
        } else {
            modelPath = modelPath.substring(0, modelPath.length() - 5);
        }
        if (modelPath.isBlank()) return null;
        return Identifier.fromNamespaceAndPath(resourceId.getNamespace(), modelPath);
    }
}
