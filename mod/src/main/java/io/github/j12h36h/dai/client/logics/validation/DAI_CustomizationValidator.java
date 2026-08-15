package io.github.j12h36h.dai.client.logics.validation;

import io.github.j12h36h.dai.customization.DAI_GameCustomizationDefinition;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationRegistry;
import io.github.j12h36h.dai.logics.action.DAI_ActionLibrary;
import io.github.j12h36h.dai.logics.action.DAI_ActionReference;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/** Static consistency checks for DAI 1.9 customization definitions. */
public final class DAI_CustomizationValidator {

    private DAI_CustomizationValidator() {}

    public static void validate() {
        for (DAI_GameCustomizationKind kind : DAI_GameCustomizationKind.values()) {
            for (var entry : DAI_GameCustomizationRegistry.entries(kind).values()) {
                String source = kind.folder() + "/" + entry.id();
                validateDefinition(source, kind, entry.definition());
            }
        }
    }

    private static void validateDefinition(
            String source,
            DAI_GameCustomizationKind kind,
            DAI_GameCustomizationDefinition definition
    ) {
        if (!definition.carrier().isBlank()
                && expectsResourceId(kind)
                && Identifier.tryParse(definition.carrier()) == null) {
            DAI_ValidationReport.error(source, "carrier must be a valid resource identifier.");
        }

        validateActionReference(source, "sequence", definition.sequence(), false);

        for (var event : definition.events().entrySet()) {
            String value = event.getValue() == null ? "" : event.getValue().trim();
            if (value.isBlank()) continue;
            String lower = value.toLowerCase(Locale.ROOT);
            if (lower.startsWith("command:") || lower.startsWith("function:")) continue;
            if (lower.startsWith("action:")) {
                validateActionReference(source, "event '" + event.getKey() + "'", value.substring("action:".length()), true);
                continue;
            }

            // Unprefixed ids are deliberately allowed to be either DAI action
            // references (client) or datapack functions (server). If the text
            // is not even a valid id, it will be treated as a command string.
            Identifier id = DAI_ActionReference.parse(value);
            if (id != null && DAI_ActionLibrary.get(id) == null) {
                DAI_ValidationReport.info(
                        source,
                        "event '" + event.getKey() + "' references '" + value
                                + "' which is not a client action; it will be resolved server-side as a function id."
                );
            }
        }

        if (definition.sequence().isBlank()
                && definition.command().isBlank()
                && definition.events().isEmpty()
                && definition.entries().isEmpty()) {
            DAI_ValidationReport.info(
                    source,
                    "definition is state/metadata-only; no runtime dispatch is authored."
            );
        }
    }

    private static void validateActionReference(
            String source,
            String field,
            String raw,
            boolean required
    ) {
        if (raw == null || raw.isBlank()) {
            if (required) DAI_ValidationReport.error(source, field + " requires a DAI action id.");
            return;
        }
        Identifier id = DAI_ActionReference.parse(raw.trim());
        if (id == null || DAI_ActionLibrary.get(id) == null) {
            DAI_ValidationReport.error(source, field + " references unknown DAI action '" + raw + "'.");
        }
    }

    private static boolean expectsResourceId(DAI_GameCustomizationKind kind) {
        return switch (kind) {
            case SOUND, MUSIC, STRUCTURE, FEATURE, LOOT, BIOME, DIMENSION, VEHICLE, FLUID -> true;
            default -> false;
        };
    }
}
