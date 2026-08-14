package io.github.j12h36h.dai.reactions;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

public final class DAI_ReactionEventLoader
        extends SimpleJsonResourceReloadListener<DAI_ReactionEventDefinition> {

    public DAI_ReactionEventLoader() {

        super(
                DAI_ReactionEventDefinition.CODEC,
                FileToIdConverter.json(
                        "reaction_events"
                )
        );
    }

    @Override
    protected void apply(
            Map<Identifier, DAI_ReactionEventDefinition> definitions,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {

        DAI_ReactionEventRegistry.clearCustom();

        definitions.forEach(
                (sourceId, definition) -> {

                    String eventId =
                            definition.id().isBlank()
                                    ? sourceId.toString()
                                    : definition.id();

                    if (
                            DAI_ReactionEventRegistry.isBuiltIn(
                                    eventId
                            )
                    ) {

                        DAI_Core.LOGGER.warn(
                                "<DAI>: Ignoring datapack reaction event '{}' because it is a built-in event.",
                                eventId
                        );

                        return;
                    }

                    DAI_ReactionEventRegistry.register(
                            definition.withId(
                                    eventId
                            )
                    );
                }
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Loaded {} datapack reaction event definition(s).",
                definitions.size()
        );
    }
}
