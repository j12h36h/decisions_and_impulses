package io.github.j12h36h.dai.logics.action;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class DAI_ActionLoader
        extends SimpleJsonResourceReloadListener<DAI_ActionDefinition> {

    private final String folder;
    private final boolean clearBeforeApply;

    public DAI_ActionLoader(
            String folder,
            boolean clearBeforeApply
    ) {

        super(
                DAI_ActionDefinition.CODEC,
                FileToIdConverter.json(folder)
        );

        this.folder = folder;
        this.clearBeforeApply = clearBeforeApply;
    }

    @Override
    protected void apply(
            Map<Identifier, DAI_ActionDefinition> definitions,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {

        DAI_Core.LOGGER.info(
                "<DAI>: Reloading action definitions from '{}'.",
                folder
        );

        Map<Identifier, DAI_ActionDefinition> flattenedDefinitions =
                new LinkedHashMap<>();

        Map<Identifier, Identifier> sourceIdentifiers =
                new HashMap<>();

        definitions.forEach((sourceIdentifier, action) -> {

            Identifier actionIdentifier =
                    flattenIdentifier(sourceIdentifier);

            Identifier previousSource =
                    sourceIdentifiers.putIfAbsent(
                            actionIdentifier,
                            sourceIdentifier
                    );

            if (previousSource != null) {

                throw new IllegalStateException(
                        String.format(
                                Locale.ROOT,
                                "Duplicate action filename '%s' found at '%s' and '%s'.",
                                actionIdentifier,
                                previousSource,
                                sourceIdentifier
                        )
                );
            }

            flattenedDefinitions.put(
                    actionIdentifier,
                    action
            );
        });

        if (clearBeforeApply) {
            DAI_ActionLibrary.clear();
        }

        flattenedDefinitions.forEach((identifier, action) -> {

            DAI_Core.debug(
                    "<DAI>: Registering action '{}' with type='{}', reference='{}', conditions={}, sequence={}, ticks={}.",
                    identifier,
                    action.type(),
                    action.action(),
                    action.conditions().size(),
                    action.sequence().size(),
                    action.ticks()
            );

            DAI_ActionLibrary.register(
                    identifier,
                    action
            );
        });

        DAI_Core.LOGGER.info(
                "<DAI>: Loaded {} action definition(s) from {} source file(s).",
                flattenedDefinitions.size(),
                definitions.size()
        );
    }

    private static Identifier flattenIdentifier(
            Identifier identifier
    ) {

        String path =
                identifier.getPath();

        int lastSeparator =
                path.lastIndexOf('/');

        String fileName =
                lastSeparator >= 0
                        ? path.substring(lastSeparator + 1)
                        : path;

        return Identifier.fromNamespaceAndPath(
                identifier.getNamespace(),
                fileName
        );
    }
}