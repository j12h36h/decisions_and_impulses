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

public final class DAI_RecogGroupLoader extends
        SimplePreparableReloadListener<
                        List<DAI_RecogGroupLoader.LoadedGroup>
                        > {

    private static final String DIRECTORY =
            "groups";

    private static final String JSON_EXTENSION =
            ".json";

    public DAI_RecogGroupLoader() {
        // Reload listener.
    }

    @Override
    protected List<LoadedGroup> prepare(
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {

        List<LoadedGroup> loadedGroups =
                new ArrayList<>();

        Map<Identifier, List<Resource>> resourceStacks =
                resourceManager.listResourceStacks(
                        DIRECTORY,
                        identifier ->
                                identifier.getPath()
                                        .endsWith(
                                                JSON_EXTENSION
                                        )
                );

        for (
                Map.Entry<
                        Identifier,
                        List<Resource>
                        > stackEntry
                : resourceStacks.entrySet()
        ) {

            Identifier resourceId =
                    stackEntry.getKey();

            Identifier groupId =
                    toGroupId(resourceId);

            if (groupId == null) {

                DAI_Core.LOGGER.warn(
                        "<DAI>: Could not derive a recognition group id from '{}'.",
                        resourceId
                );

                continue;
            }

            for (
                    Resource resource
                    : stackEntry.getValue()
            ) {

                try (
                        BufferedReader reader =
                                resource.openAsReader()
                ) {

                    JsonElement json =
                            JsonParser.parseReader(
                                    reader
                            );

                    DAI_RecogGroupDefinition definition =
                            DAI_RecogGroupDefinition.CODEC
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

                    loadedGroups.add(
                            new LoadedGroup(
                                    groupId,
                                    definition,
                                    resource.sourcePackId()
                            )
                    );

                    DAI_Core.LOGGER.debug(
                            "<DAI>: Prepared recognition group '{}' from pack '{}'.",
                            groupId,
                            resource.sourcePackId()
                    );

                } catch (
                        IOException
                        | IllegalArgumentException exception
                ) {

                    DAI_Core.LOGGER.error(
                            "<DAI>: Failed to load recognition group '{}' from pack '{}'.",
                            groupId,
                            resource.sourcePackId(),
                            exception
                    );
                }
            }
        }

        return List.copyOf(
                loadedGroups
        );
    }

    @Override
    protected void apply(
            List<LoadedGroup> loadedGroups,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {

        DAI_RecogGroupManager.clear();

        for (LoadedGroup loadedGroup : loadedGroups) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Registering recognition group '{}' from pack '{}' with {} entry(s), replace={}.",
                    loadedGroup.id(),
                    loadedGroup.sourcePack(),
                    loadedGroup.definition()
                            .entries()
                            .size(),
                    loadedGroup.definition()
                            .replace()
            );

            DAI_RecogGroupManager.register(
                    loadedGroup.id(),
                    loadedGroup.definition()
            );
        }

        DAI_Core.LOGGER.info(
                "<DAI>: Loaded {} recognition group definition(s) across {} registered group(s).",
                loadedGroups.size(),
                DAI_RecogGroupManager.size()
        );
    }

    private static Identifier toGroupId(
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

        String groupPath =
                path.substring(
                        prefix.length(),
                        path.length()
                                - JSON_EXTENSION.length()
                );

        if (groupPath.isBlank()) {
            return null;
        }

        return Identifier.fromNamespaceAndPath(
                resourceId.getNamespace(),
                groupPath
        );
    }

    record LoadedGroup(
            Identifier id,
            DAI_RecogGroupDefinition definition,
            String sourcePack
    ) {

        LoadedGroup {

            if (id == null) {

                throw new IllegalArgumentException(
                        "Recognition group id cannot be null."
                );
            }

            if (definition == null) {

                throw new IllegalArgumentException(
                        "Recognition group definition cannot be null."
                );
            }

            sourcePack =
                    sourcePack == null
                            ? "unknown"
                            : sourcePack;
        }
    }
}
