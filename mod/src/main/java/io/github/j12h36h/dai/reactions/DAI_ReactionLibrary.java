package io.github.j12h36h.dai.reactions;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DAI_ReactionLibrary {

    private static final Map<Identifier, DAI_ReactionDefinition> REACTIONS =
            new LinkedHashMap<>();

    private static final Comparator<DAI_ReactionEntry> ORDER =
            Comparator
                    .comparingInt((DAI_ReactionEntry entry) ->
                            entry.definition()
                                    .priority()
                    )
                    .reversed()
                    .thenComparing(entry ->
                            entry.id()
                                    .toString()
                    );

    private DAI_ReactionLibrary() {
        // Utility class.
    }

    public static void register(
            Identifier id,
            DAI_ReactionDefinition definition
    ) {

        if (id == null || definition == null) {
            return;
        }

        REACTIONS.put(
                id,
                definition
        );
    }

    public static List<DAI_ReactionEntry> matching(
            String event,
            DAI_ReactionPhase phase
    ) {

        if (
                event == null
                        || event.isBlank()
                        || phase == null
                        || phase == DAI_ReactionPhase.UNKNOWN
        ) {
            return List.of();
        }

        List<DAI_ReactionEntry> matching =
                new ArrayList<>();

        REACTIONS.forEach((id, definition) -> {

            if (
                    event.equals(
                            definition.event()
                    )
                            && phase
                            == definition.reactionPhase()
            ) {

                matching.add(
                        new DAI_ReactionEntry(
                                id,
                                definition
                        )
                );
            }
        });

        matching.sort(
                ORDER
        );

        return List.copyOf(
                matching
        );
    }

    public static Map<Identifier, DAI_ReactionDefinition> reactions() {

        return Map.copyOf(
                REACTIONS
        );
    }

    public static int size() {
        return REACTIONS.size();
    }

    public static void clear() {

        int removed =
                REACTIONS.size();

        REACTIONS.clear();

        DAI_Core.debug(
                "<DAI>: Cleared {} reaction definition(s).",
                removed
        );
    }
}
