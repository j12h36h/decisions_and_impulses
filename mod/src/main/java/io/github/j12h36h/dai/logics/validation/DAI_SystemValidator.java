package io.github.j12h36h.dai.logics.validation;

import io.github.j12h36h.dai.menus.DAI_MenuCategory;
import io.github.j12h36h.dai.menus.system.DAI_SystemButton;
import io.github.j12h36h.dai.menus.system.DAI_SystemDefinition;
import io.github.j12h36h.dai.menus.system.DAI_SystemManager;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DAI_SystemValidator {

    private DAI_SystemValidator() {
        // Utility class.
    }

    public static void validate() {

        for (
                DAI_MenuCategory category
                : DAI_MenuCategory.values()
        ) {

            validateCategory(
                    category
            );
        }
    }

    private static void validateCategory(
            DAI_MenuCategory category
    ) {

        Map<String, DAI_SystemDefinition> definitions =
                DAI_SystemManager.get(
                        category
                );

        if (definitions.isEmpty()) {

            DAI_ValidationReport.warning(
                    category.name(),
                    "No system definitions are registered for this category."
            );

            return;
        }

        for (
                Map.Entry<String, DAI_SystemDefinition> entry
                : definitions.entrySet()
        ) {

            validateDefinition(
                    category,
                    entry.getKey(),
                    entry.getValue()
            );
        }
    }

    private static void validateDefinition(
            DAI_MenuCategory category,
            String id,
            DAI_SystemDefinition definition
    ) {

        String source =
                category.name()
                        + ":"
                        + normalizeId(
                        id
                );

        if (
                id == null
                        || id.isBlank()
        ) {

            DAI_ValidationReport.error(
                    source,
                    "System definition has a null or blank identifier."
            );

            return;
        }

        if (!id.equals(id.trim())) {

            DAI_ValidationReport.warning(
                    source,
                    "System identifier contains leading or trailing whitespace."
            );
        }

        if (definition == null) {

            DAI_ValidationReport.error(
                    source,
                    "System definition is null."
            );

            return;
        }

        if (definition.priority() < 0) {

            DAI_ValidationReport.error(
                    source,
                    "System priority cannot be negative."
            );
        }

        List<DAI_SystemButton> buttons =
                definition.buttons();

        if (
                buttons == null
                        || buttons.isEmpty()
        ) {

            DAI_ValidationReport.error(
                    source,
                    "System definition must contain at least one button."
            );

            return;
        }

        Set<Integer> occupiedSlots =
                new HashSet<>();

        for (
                int index = 0;
                index < buttons.size();
                index++
        ) {

            DAI_SystemButton button =
                    buttons.get(
                            index
                    );

            String buttonSource =
                    source
                            + ".buttons["
                            + index
                            + "]";

            if (button == null) {

                DAI_ValidationReport.error(
                        buttonSource,
                        "System button is null."
                );

                continue;
            }

            if (
                    !occupiedSlots.add(
                            button.slot()
                    )
            ) {

                DAI_ValidationReport.error(
                        buttonSource,
                        "Duplicate system button slot "
                                + button.slot()
                                + "."
                );
            }

            validateButton(
                    buttonSource,
                    category,
                    button
            );
        }
    }

    private static void validateButton(
            String source,
            DAI_MenuCategory category,
            DAI_SystemButton button
    ) {

        validateStyleColor(
                source,
                "background",
                button.style().background()
        );

        validateStyleColor(
                source,
                "hover",
                button.style().hover()
        );

        validateStyleColor(
                source,
                "selected",
                button.style().selected()
        );

        validateStyleColor(
                source,
                "text",
                button.style().text()
        );

        validateStyleColor(
                source,
                "border",
                button.style().border()
        );
    }

    private static void validateStyleColor(
            String source,
            String field,
            String color
    ) {

        if (color == null || color.isBlank()) {
            return;
        }

        String normalized =
                color.startsWith("#")
                        ? color.substring(1)
                        : color;

        if (!normalized.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {

            DAI_ValidationReport.error(
                    source,
                    "Button style '"
                            + field
                            + "' must be #RRGGBB or #AARRGGBB."
            );
        }
    }

    private static String normalizeId(
            String id
    ) {

        if (
                id == null
                        || id.isBlank()
        ) {
            return "unknown";
        }

        return id.trim();
    }
}