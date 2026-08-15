package io.github.j12h36h.dai.client.logics.validation;

/**
 * Severity levels used by the datapack validation system.
 *
 * ERROR
 *     The datapack contains an invalid definition that may prevent
 *     the affected content from functioning correctly.
 *
 * WARNING
 *     The datapack is valid, but something appears suspicious or
 *     suboptimal.
 *
 * INFO
 *     Informational validation message that does not indicate a
 *     problem.
 */
public enum DAI_ValidationSeverity {

    ERROR,

    WARNING,

    INFO
}
