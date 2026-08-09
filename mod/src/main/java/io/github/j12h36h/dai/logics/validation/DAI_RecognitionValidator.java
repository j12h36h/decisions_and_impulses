package io.github.j12h36h.dai.logics.validation;

import io.github.j12h36h.dai.objectives.recognition.DAI_RecogDefinition;
import io.github.j12h36h.dai.objectives.recognition.DAI_RecogGroupManager;
import io.github.j12h36h.dai.objectives.recognition.DAI_RecogRequirementRegistry;
import io.github.j12h36h.dai.objectives.recognition.DAI_RecognitionLibrary;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class DAI_RecognitionValidator {

    private static final Set<String> SUPPORTED_TYPES =
            Set.of(
                    "structure"
            );

    private static final Set<String> SUPPORTED_SCAN_MODES =
            Set.of(
                    "connected",
                    "bounded_region",
                    "volume",
                    "regional"
            );

    private static final Set<String> SUPPORTED_SCAN_ORIGINS =
            Set.of(
                    "targeted_block",
                    "nearest_match",
                    "player"
            );

    private DAI_RecognitionValidator() {
        // Utility class.
    }

    public static void validate() {

        if (DAI_RecognitionLibrary.ids().isEmpty()) {

            DAI_ValidationReport.warning(
                    "recognition",
                    "No recognition definitions are registered."
            );

            return;
        }

        for (
                Identifier id
                : DAI_RecognitionLibrary.ids()
        ) {

            validateDefinition(
                    id,
                    DAI_RecognitionLibrary.get(
                            id
                    )
            );
        }
    }

    private static void validateDefinition(
            Identifier id,
            DAI_RecogDefinition definition
    ) {

        String source =
                id == null
                        ? "recognition:unknown"
                        : id.toString();

        if (id == null) {

            DAI_ValidationReport.error(
                    source,
                    "Recognition identifier is null."
            );

            return;
        }

        if (definition == null) {

            DAI_ValidationReport.error(
                    source,
                    "Recognition definition is null."
            );

            return;
        }

        validateType(
                source,
                definition
        );

        validateScan(
                source,
                definition.scan()
        );

        validateGroups(
                source,
                definition.groups()
        );

        validateRequirements(
                source,
                definition
        );

        validateResult(
                source,
                definition.result()
        );
    }

    private static void validateType(
            String source,
            DAI_RecogDefinition definition
    ) {

        String type =
                normalize(
                        definition.type()
                );

        if (type.isEmpty()) {

            DAI_ValidationReport.error(
                    source,
                    "Recognition type is blank."
            );

            return;
        }

        if (!SUPPORTED_TYPES.contains(type)) {

            DAI_ValidationReport.warning(
                    source,
                    "Recognition type '"
                            + type
                            + "' is not currently recognized by the validator."
            );
        }
    }

    private static void validateScan(
            String source,
            DAI_RecogDefinition.DAI_RecogScan scan
    ) {

        if (scan == null) {

            DAI_ValidationReport.error(
                    source,
                    "Recognition scan definition is null."
            );

            return;
        }

        String scanSource =
                source
                        + ".scan";

        String mode =
                normalize(
                        scan.mode()
                );

        if (mode.isEmpty()) {

            DAI_ValidationReport.error(
                    scanSource,
                    "Scan mode is blank."
            );

        } else if (!SUPPORTED_SCAN_MODES.contains(mode)) {

            DAI_ValidationReport.error(
                    scanSource,
                    "Unknown scan mode '"
                            + mode
                            + "'."
            );
        }

        String origin =
                normalize(
                        scan.origin()
                );

        if (origin.isEmpty()) {

            DAI_ValidationReport.error(
                    scanSource,
                    "Scan origin is blank."
            );

        } else if (!SUPPORTED_SCAN_ORIGINS.contains(origin)) {

            DAI_ValidationReport.error(
                    scanSource,
                    "Unknown scan origin '"
                            + origin
                            + "'."
            );
        }

        if (scan.maxBlocks() <= 0) {

            DAI_ValidationReport.error(
                    scanSource,
                    "max_blocks must be greater than zero."
            );
        }

        if (scan.maxRadius() <= 0) {

            DAI_ValidationReport.error(
                    scanSource,
                    "max_radius must be greater than zero."
            );
        }

        if (scan.horizontalRadius() <= 0) {

            DAI_ValidationReport.error(
                    scanSource,
                    "horizontal_radius must be greater than zero."
            );
        }

        if (scan.upwardRange() < 0) {

            DAI_ValidationReport.error(
                    scanSource,
                    "upward_range cannot be negative."
            );
        }

        if (scan.downwardRange() < 0) {

            DAI_ValidationReport.error(
                    scanSource,
                    "downward_range cannot be negative."
            );
        }

        if (
                "player".equals(origin)
                        && !"regional".equals(mode)
        ) {

            DAI_ValidationReport.warning(
                    scanSource,
                    "The current scanner entry point does not fully support origin='player'; it may fall back to the supplied target origin."
            );
        }
    }

    private static void validateGroups(
            String source,
            Map<
                    String,
                    DAI_RecogDefinition.DAI_RecogGroupRule
                    > groups
    ) {

        if (
                groups == null
                        || groups.isEmpty()
        ) {

            DAI_ValidationReport.error(
                    source,
                    "Recognition definition contains no groups."
            );

            return;
        }

        Set<String> normalizedNames =
                new HashSet<>();

        for (
                Map.Entry<
                        String,
                        DAI_RecogDefinition.DAI_RecogGroupRule
                        > entry
                : groups.entrySet()
        ) {

            String groupName =
                    normalize(
                            entry.getKey()
                    );

            String groupSource =
                    source
                            + ".groups["
                            + (
                            groupName.isEmpty()
                                    ? "unknown"
                                    : groupName
                    )
                            + "]";

            if (groupName.isEmpty()) {

                DAI_ValidationReport.error(
                        groupSource,
                        "Recognition group alias is blank."
                );

                continue;
            }

            if (!normalizedNames.add(groupName)) {

                DAI_ValidationReport.error(
                        groupSource,
                        "Duplicate recognition group alias after normalization."
                );
            }

            DAI_RecogDefinition.DAI_RecogGroupRule rule =
                    entry.getValue();

            if (rule == null) {

                DAI_ValidationReport.error(
                        groupSource,
                        "Recognition group rule is null."
                );

                continue;
            }

            validateGroupRule(
                    groupSource,
                    rule
            );
        }
    }

    private static void validateGroupRule(
            String source,
            DAI_RecogDefinition.DAI_RecogGroupRule rule
    ) {

        String registry =
                normalize(
                        rule.registry()
                );

        if (registry.isEmpty()) {

            DAI_ValidationReport.error(
                    source,
                    "Recognition group registry is blank."
            );

            return;
        }

        Identifier registryId =
                Identifier.tryParse(
                        registry
                );

        if (registryId == null) {

            DAI_ValidationReport.error(
                    source,
                    "Invalid recognition group identifier '"
                            + rule.registry()
                            + "'."
            );

        } else if (
                !DAI_RecogGroupManager.contains(
                        registryId
                )
        ) {

            DAI_ValidationReport.error(
                    source,
                    "Unknown recognition group registry '"
                            + registryId
                            + "'."
            );
        }

        if (rule.minimum() < 0) {

            DAI_ValidationReport.error(
                    source,
                    "Group minimum cannot be negative."
            );
        }

        if (rule.maximum() < rule.minimum()) {

            DAI_ValidationReport.error(
                    source,
                    "Group maximum cannot be less than its minimum."
            );
        }

        if (rule.maximum() == 0) {

            DAI_ValidationReport.warning(
                    source,
                    "Group maximum is zero, so this rule can only succeed when no matching blocks are found."
            );
        }
    }

    private static void validateRequirements(
            String source,
            DAI_RecogDefinition definition
    ) {

        if (definition.requirements() == null) {

            DAI_ValidationReport.error(
                    source,
                    "Recognition requirements list is null."
            );

            return;
        }

        for (
                int index = 0;
                index < definition.requirements().size();
                index++
        ) {

            DAI_RecogDefinition.DAI_RecogRequirement requirement =
                    definition.requirements()
                            .get(
                                    index
                            );

            validateRequirement(
                    source
                            + ".requirements["
                            + index
                            + "]",
                    definition,
                    requirement
            );
        }
    }

    private static void validateRequirement(
            String source,
            DAI_RecogDefinition definition,
            DAI_RecogDefinition.DAI_RecogRequirement requirement
    ) {

        if (requirement == null) {

            DAI_ValidationReport.error(
                    source,
                    "Recognition requirement is null."
            );

            return;
        }

        String type =
                normalize(
                        requirement.type()
                );

        if (type.isEmpty()) {

            DAI_ValidationReport.error(
                    source,
                    "Recognition requirement type is blank."
            );

        } else if (
                !DAI_RecogRequirementRegistry.contains(
                        type
                )
        ) {

            DAI_ValidationReport.error(
                    source,
                    "Unknown recognition requirement type '"
                            + type
                            + "'."
            );
        }

        validateRequirementGroup(
                source,
                definition,
                requirement.group(),
                "group"
        );

        validateRequirementGroup(
                source,
                definition,
                requirement.relativeTo(),
                "relative_to"
        );

        for (
                int index = 0;
                index < requirement.groups().size();
                index++
        ) {

            validateRequirementGroup(
                    source
                            + ".groups["
                            + index
                            + "]",
                    definition,
                    requirement.groups().get(
                            index
                    ),
                    "group"
            );
        }

        if (requirement.minimumHeight() < 0) {

            DAI_ValidationReport.error(
                    source,
                    "minimum_height cannot be negative."
            );
        }

        if (requirement.minimumRatio() < 0.0F) {

            DAI_ValidationReport.error(
                    source,
                    "minimum_ratio cannot be negative."
            );
        }

        if (requirement.minimumRatio() > 1.0F) {

            DAI_ValidationReport.warning(
                    source,
                    "minimum_ratio is greater than 1.0 and may be impossible to satisfy."
            );
        }
    }

    private static void validateRequirementGroup(
            String source,
            DAI_RecogDefinition definition,
            String group,
            String fieldName
    ) {

        String normalized =
                normalize(
                        group
                );

        if (normalized.isEmpty()) {
            return;
        }

        if (
                !definition.groups()
                        .containsKey(
                                normalized
                        )
        ) {

            DAI_ValidationReport.error(
                    source,
                    "Requirement "
                            + fieldName
                            + " references unknown local group alias '"
                            + normalized
                            + "'."
            );
        }
    }

    private static void validateResult(
            String source,
            DAI_RecogDefinition.DAI_RecogResultDefinition result
    ) {

        if (result == null) {

            DAI_ValidationReport.error(
                    source,
                    "Recognition result definition is null."
            );

            return;
        }

        String resultId =
                normalize(
                        result.id()
                );

        if (resultId.isEmpty()) {

            DAI_ValidationReport.error(
                    source
                            + ".result",
                    "Recognition result identifier is blank."
            );

            return;
        }

        Identifier parsed =
                Identifier.tryParse(
                        resultId
                );

        if (parsed == null) {

            DAI_ValidationReport.warning(
                    source
                            + ".result",
                    "Recognition result id '"
                            + result.id()
                            + "' is not a namespaced identifier."
            );
        }
    }

    private static String normalize(
            String value
    ) {

        return value == null
                ? ""
                : value.trim()
                .toLowerCase();
    }
}
