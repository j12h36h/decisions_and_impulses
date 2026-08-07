package io.github.j12h36h.dai.validation;

import io.github.j12h36h.dai.core.DAI_Core;

import java.util.ArrayList;
import java.util.List;

public final class DAI_ValidationReport {

    private static final List<DAI_ValidationMessage> MESSAGES =
            new ArrayList<>();

    private DAI_ValidationReport() {
        // Utility class.
    }

    public static void clear() {

        MESSAGES.clear();
    }

    public static void error(
            String source,
            String message
    ) {

        add(
                DAI_ValidationSeverity.ERROR,
                source,
                message
        );
    }

    public static void warning(
            String source,
            String message
    ) {

        add(
                DAI_ValidationSeverity.WARNING,
                source,
                message
        );
    }

    public static void info(
            String source,
            String message
    ) {

        add(
                DAI_ValidationSeverity.INFO,
                source,
                message
        );
    }

    public static boolean hasErrors() {

        return MESSAGES.stream()
                .anyMatch(
                        validationMessage ->
                                validationMessage.severity()
                                        == DAI_ValidationSeverity.ERROR
                );
    }

    public static int errorCount() {

        return count(
                DAI_ValidationSeverity.ERROR
        );
    }

    public static int warningCount() {

        return count(
                DAI_ValidationSeverity.WARNING
        );
    }

    public static int infoCount() {

        return count(
                DAI_ValidationSeverity.INFO
        );
    }

    public static int size() {
        return MESSAGES.size();
    }

    public static List<DAI_ValidationMessage> messages() {

        return List.copyOf(
                MESSAGES
        );
    }

    public static void printSummary() {

        for (
                DAI_ValidationMessage message
                : MESSAGES
        ) {

            String formatted =
                    "<DAI>: Validation [{}] {}: {}";

            switch (message.severity()) {

                case ERROR ->
                        DAI_Core.LOGGER.error(
                                formatted,
                                message.severity(),
                                message.source(),
                                message.message()
                        );

                case WARNING ->
                        DAI_Core.LOGGER.warn(
                                formatted,
                                message.severity(),
                                message.source(),
                                message.message()
                        );

                case INFO ->
                        DAI_Core.LOGGER.info(
                                formatted,
                                message.severity(),
                                message.source(),
                                message.message()
                        );
            }
        }

        DAI_Core.LOGGER.info(
                "<DAI>: Datapack validation complete: {} error(s), {} warning(s), {} informational message(s).",
                errorCount(),
                warningCount(),
                infoCount()
        );
    }

    private static void add(
            DAI_ValidationSeverity severity,
            String source,
            String message
    ) {

        MESSAGES.add(
                new DAI_ValidationMessage(
                        severity,
                        normalizeSource(
                                source
                        ),
                        normalizeMessage(
                                message
                        )
                )
        );
    }

    private static int count(
            DAI_ValidationSeverity severity
    ) {

        return (int) MESSAGES.stream()
                .filter(
                        validationMessage ->
                                validationMessage.severity()
                                        == severity
                )
                .count();
    }

    private static String normalizeSource(
            String source
    ) {

        if (
                source == null
                        || source.isBlank()
        ) {
            return "unknown";
        }

        return source.trim();
    }

    private static String normalizeMessage(
            String message
    ) {

        if (
                message == null
                        || message.isBlank()
        ) {
            return "No validation message was provided.";
        }

        return message.trim();
    }
}
