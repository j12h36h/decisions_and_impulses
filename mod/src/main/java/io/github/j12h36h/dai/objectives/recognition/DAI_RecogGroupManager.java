package io.github.j12h36h.dai.objectives.recognition;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.stream.Stream;

public final class DAI_RecogGroupManager {

    private static final String GROUP_PREFIX =
            "@";

    private static final Map<
            Identifier,
            DAI_RecogGroupDefinition
            > GROUPS =
            new HashMap<>();

    private DAI_RecogGroupManager() {
        // Utility class.
    }

    public static void clear() {

        int removed =
                GROUPS.size();

        GROUPS.clear();

        DAI_Core.LOGGER.debug(
                "<DAI>: Cleared {} recognition group definition(s).",
                removed
        );
    }

    public static void register(
            Identifier id,
            DAI_RecogGroupDefinition definition
    ) {

        requireId(id);
        requireDefinition(definition);

        DAI_RecogGroupDefinition existing =
                GROUPS.get(id);

        if (
                existing == null
                        || definition.replace()
        ) {

            GROUPS.put(
                    id,
                    definition
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Registered recognition group '{}' with {} entrie(s), replace={}.",
                    id,
                    definition.entries().size(),
                    definition.replace()
            );

            return;
        }

        List<String> mergedEntries =
                Stream.concat(
                                existing.entries().stream(),
                                definition.entries().stream()
                        )
                        .distinct()
                        .toList();

        GROUPS.put(
                id,
                new DAI_RecogGroupDefinition(
                        false,
                        mergedEntries
                )
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Merged recognition group '{}' to {} entrie(s).",
                id,
                mergedEntries.size()
        );
    }

    public static DAI_RecogGroupDefinition get(
            Identifier id
    ) {

        return GROUPS.get(
                requireId(id)
        );
    }

    public static boolean contains(
            Identifier id
    ) {

        return GROUPS.containsKey(
                requireId(id)
        );
    }

    public static boolean matches(
            Identifier id,
            BlockState state
    ) {

        if (state == null) {
            return false;
        }

        return matches(
                requireId(id),
                state,
                new HashSet<>()
        );
    }

    private static boolean matches(
            Identifier id,
            BlockState state,
            Set<Identifier> resolving
    ) {

        DAI_RecogGroupDefinition definition =
                GROUPS.get(id);

        if (definition == null) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Recognition group '{}' is not registered.",
                    id
            );

            return false;
        }

        /*
         * Prevent recursive group loops such as:
         *
         * group_a -> @group_b
         * group_b -> @group_a
         */
        if (!resolving.add(id)) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Circular recognition group reference detected at '{}'.",
                    id
            );

            return false;
        }

        try {

            for (String entry : definition.entries()) {

                if (
                        entry.startsWith(
                                GROUP_PREFIX
                        )
                ) {

                    Identifier nestedId =
                            parseGroupReference(
                                    id,
                                    entry
                            );

                    if (nestedId == null) {
                        continue;
                    }

                    if (
                            matches(
                                    nestedId,
                                    state,
                                    resolving
                            )
                    ) {
                        return true;
                    }

                    continue;
                }

                if (
                        DAI_RecogBlockMatcher.matches(
                                entry,
                                state
                        )
                ) {
                    return true;
                }
            }

            return false;

        } finally {

            resolving.remove(id);
        }
    }

    public static Collection<Identifier> ids() {

        return Collections.unmodifiableSet(
                GROUPS.keySet()
        );
    }

    public static int size() {
        return GROUPS.size();
    }

    private static Identifier parseGroupReference(
            Identifier parentId,
            String entry
    ) {

        String reference =
                entry.substring(
                        GROUP_PREFIX.length()
                ).trim();

        if (reference.isEmpty()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Empty recognition group reference inside '{}'.",
                    parentId
            );

            return null;
        }

        /*
         * A reference without a namespace inherits the namespace
         * of the group containing it.
         */
        if (!reference.contains(":")) {

            return Identifier.fromNamespaceAndPath(
                    parentId.getNamespace(),
                    reference
            );
        }

        Identifier id =
                Identifier.tryParse(
                        reference
                );

        if (id == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Invalid recognition group reference '{}' inside '{}'.",
                    entry,
                    parentId
            );
        }

        return id;
    }

    private static Identifier requireId(
            Identifier id
    ) {

        if (id == null) {

            throw new IllegalArgumentException(
                    "Recognition group id cannot be null."
            );
        }

        return id;
    }

    private static DAI_RecogGroupDefinition requireDefinition(
            DAI_RecogGroupDefinition definition
    ) {

        if (definition == null) {

            throw new IllegalArgumentException(
                    "Recognition group definition cannot be null."
            );
        }

        return definition;
    }
}