package io.github.j12h36h.dai.client.logics.validation;

public record DAI_ValidationMessage(
        DAI_ValidationSeverity severity,
        String source,
        String message
) {

    public DAI_ValidationMessage {

        if (severity == null) {
            throw new IllegalArgumentException(
                    "Validation severity cannot be null."
            );
        }

        source =
                source == null
                        ? "unknown"
                        : source;

        message =
                message == null
                        ? ""
                        : message;
    }
}