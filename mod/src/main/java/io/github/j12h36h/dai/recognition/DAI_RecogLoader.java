package io.github.j12h36h.dai.recognition;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DAI_RecogLoader extends
        SimplePreparableReloadListener<
                        List<DAI_RecogLoader.LoadedRecog>
                        > {

    private static final String DIRECTORY =
            "recognition";

    private static final String JSON_EXTENSION =
            ".json";

    public DAI_RecogLoader() {
        // Reload listener.
    }

    @Override
    protected List<LoadedRecog> prepare(
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {

        List<LoadedRecog> loadedRecognitions =
                new ArrayList<>();

        Map<Identifier, Resource> resources =
                resourceManager.listResources(
                        DIRECTORY,
                        identifier ->
                                identifier.getPath()
                                        .endsWith(
                                                JSON_EXTENSION
                                        )
                );

        for (
                Map.Entry<Identifier, Resource> entry
                : resources.entrySet()
        ) {

            Identifier resourceId =
                    entry.getKey();

            Identifier recognitionId =
                    toRecognitionId(
                            resourceId
                    );

            if (recognitionId == null) {

                DAI_Core.LOGGER.warn(
                        "<DAI>: Could not derive a recognition id from '{}'.",
                        resourceId
                );

                continue;
            }

            Resource resource =
                    entry.getValue();

            try (
                    BufferedReader reader =
                            resource.openAsReader()
            ) {

                JsonElement json =
                        JsonParser.parseReader(
                                reader
                        );

                DAI_RecogDefinition definition =
                        DAI_RecogDefinition.CODEC
                                .parse(
                                        JsonOps.INSTANCE,
                                        json
                                )
                                .getOrThrow(
                                        error ->
                                                new IllegalArgumentException(
                                                        error
                                                )
                                );

                loadedRecognitions.add(
                        new LoadedRecog(
                                recognitionId,
                                definition,
                                resource.sourcePackId()
                        )
                );

                DAI_Core.LOGGER.debug(
                        "<DAI>: Prepared recognition definition '{}' from pack '{}'.",
                        recognitionId,
                        resource.sourcePackId()
                );

            } catch (
                    IOException
                    | IllegalArgumentException exception
            ) {

                DAI_Core.LOGGER.error(
                        "<DAI>: Failed to load recognition definition '{}' from pack '{}'.",
                        recognitionId,
                        resource.sourcePackId(),
                        exception
                );
            }
        }

        return List.copyOf(
                loadedRecognitions
        );
    }

    @Override
    protected void apply(
            List<LoadedRecog> loadedRecognitions,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {

        DAI_RecogManager.clear();

        for (LoadedRecog loadedRecognition : loadedRecognitions) {

            DAI_RecogDefinition definition =
                    loadedRecognition.definition();

            DAI_Core.LOGGER.debug(
                    "<DAI>: Registering recognition definition '{}' from pack '{}' with type='{}', groups={}, requirements={}.",
                    loadedRecognition.id(),
                    loadedRecognition.sourcePack(),
                    definition.type(),
                    definition.groups().size(),
                    definition.requirements().size()
            );

            DAI_RecogManager.register(
                    loadedRecognition.id(),
                    definition
            );
        }

        DAI_Core.LOGGER.info(
                "<DAI>: Loaded {} recognition definition(s).",
                DAI_RecogManager.size()
        );
    }

    private static Identifier toRecognitionId(
            Identifier resourceId
    ) {

        String path =
                resourceId.getPath();

        String prefix =
                DIRECTORY + "/";

        if (
                !path.startsWith(prefix)
                        || !path.endsWith(
                        JSON_EXTENSION
                )
        ) {
            return null;
        }

        String recognitionPath =
                path.substring(
                        prefix.length(),
                        path.length()
                                - JSON_EXTENSION.length()
                );

        if (recognitionPath.isBlank()) {
            return null;
        }

        return Identifier.fromNamespaceAndPath(
                resourceId.getNamespace(),
                recognitionPath
        );
    }

    record LoadedRecog(
            Identifier id,
            DAI_RecogDefinition definition,
            String sourcePack
    ) {

        LoadedRecog {

            if (id == null) {

                throw new IllegalArgumentException(
                        "Recognition id cannot be null."
                );
            }

            if (definition == null) {

                throw new IllegalArgumentException(
                        "Recognition definition cannot be null."
                );
            }

            sourcePack =
                    sourcePack == null
                            ? "unknown"
                            : sourcePack;
        }
    }
}
