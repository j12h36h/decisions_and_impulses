package io.github.j12h36h.dai.validation;

import io.github.j12h36h.dai.core.DAI_Core;

public final class DAI_DatapackValidator {

    private DAI_DatapackValidator() {
        // Utility class.
    }

    public static boolean validate() {

        DAI_ValidationReport.clear();

        DAI_ActionValidator.validate();
        DAI_SystemValidator.validate();
        DAI_GroupValidator.validate();
        DAI_RecognitionValidator.validate();

        DAI_ValidationReport.printSummary();

        boolean valid =
                !DAI_ValidationReport.hasErrors();

        if (valid) {

            DAI_Core.LOGGER.info(
                    "<DAI>: Datapack validation passed."
            );

        } else {

            DAI_Core.LOGGER.error(
                    "<DAI>: Datapack validation failed with {} error(s).",
                    DAI_ValidationReport.errorCount()
            );
        }

        return valid;
    }
}