package io.github.j12h36h.dai.client.logics.validation;

import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecogGroupDefinition;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecogGroupManager;
import net.minecraft.resources.Identifier;

import java.util.Collection;

public final class DAI_GroupValidator {

    private static final String GROUP_PREFIX =
            "@";

    private DAI_GroupValidator() {
        // Utility class.
    }

    public static void validate() {

        Collection<Identifier> groups =
                DAI_RecogGroupManager.ids();

        if (groups.isEmpty()) {

            DAI_ValidationReport.warning(
                    "recognition_groups",
                    "No recognition groups are registered."
            );

            return;
        }

        for (Identifier id : groups) {

            validateGroup(
                    id
            );
        }
    }

    private static void validateGroup(
            Identifier id
    ) {

        DAI_RecogGroupDefinition definition =
                DAI_RecogGroupManager.get(
                        id
                );

        String source =
                id.toString();

        if (definition == null) {

            DAI_ValidationReport.error(
                    source,
                    "Recognition group definition is null."
            );

            return;
        }

        if (
                definition.entries() == null
                        || definition.entries().isEmpty()
        ) {

            DAI_ValidationReport.error(
                    source,
                    "Recognition group contains no entries."
            );

            return;
        }

        for (
                int index = 0;
                index < definition.entries().size();
                index++
        ) {

            String entry =
                    definition.entries().get(
                            index
                    );

            validateEntry(
                    source
                            + ".entries["
                            + index
                            + "]",
                    id,
                    entry
            );
        }
    }

    private static void validateEntry(
            String source,
            Identifier parent,
            String entry
    ) {

        if (
                entry == null
                        || entry.isBlank()
        ) {

            DAI_ValidationReport.error(
                    source,
                    "Recognition group entry is blank."
            );

            return;
        }

        if (
                entry.startsWith(
                        GROUP_PREFIX
                )
        ) {

            validateGroupReference(
                    source,
                    parent,
                    entry
            );
        }
    }

    private static void validateGroupReference(
            String source,
            Identifier parent,
            String entry
    ) {

        String reference =
                entry.substring(1).trim();

        if (reference.isEmpty()) {

            DAI_ValidationReport.error(
                    source,
                    "Recognition group reference is empty."
            );

            return;
        }

        Identifier id;

        if (reference.contains(":")) {

            id =
                    Identifier.tryParse(
                            reference
                    );

        } else {

            id =
                    Identifier.fromNamespaceAndPath(
                            parent.getNamespace(),
                            reference
                    );
        }

        if (id == null) {

            DAI_ValidationReport.error(
                    source,
                    "Invalid recognition group reference '"
                            + entry
                            + "'."
            );

            return;
        }

        if (
                !DAI_RecogGroupManager.contains(
                        id
                )
        ) {

            DAI_ValidationReport.error(
                    source,
                    "Unknown recognition group '"
                            + id
                            + "'."
            );
        }
    }
}
