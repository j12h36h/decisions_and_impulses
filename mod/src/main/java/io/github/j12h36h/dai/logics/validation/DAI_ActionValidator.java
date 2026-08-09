package io.github.j12h36h.dai.logics.validation;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionLibrary;
import io.github.j12h36h.dai.logics.action.DAI_ActionRegistry;
import io.github.j12h36h.dai.logics.action.DAI_ActionResolver;
import io.github.j12h36h.dai.logics.condition.DAI_ConditionDefinition;
import io.github.j12h36h.dai.logics.condition.DAI_ConditionRegistry;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class DAI_ActionValidator {

    private static final int MAX_VALIDATION_DEPTH =
            64;

    private static final Set<String> CONDITION_GROUP_TYPES =
            Set.of(
                    "all",
                    "any",
                    "not"
            );

    private static final Set<String> MOVEMENT_DIRECTIONS =
            Set.of(
                    "",
                    "forward",
                    "backward",
                    "left",
                    "right",
                    "stop"
            );

    private DAI_ActionValidator() {
        // Utility class.
    }

    public static void validate() {

        Map<Identifier, DAI_ActionDefinition> actions =
                DAI_ActionLibrary.actions();

        for (
                Map.Entry<
                        Identifier,
                        DAI_ActionDefinition
                        > entry
                : actions.entrySet()
        ) {

            Identifier id =
                    entry.getKey();

            DAI_ActionDefinition action =
                    entry.getValue();

            validateAction(
                    id.toString(),
                    action,
                    0
            );

            validateReferenceGraph(
                    id,
                    new HashSet<>(),
                    new HashSet<>()
            );
        }
    }

    private static void validateAction(
            String source,
            DAI_ActionDefinition action,
            int depth
    ) {

        if (action == null) {

            DAI_ValidationReport.error(
                    source,
                    "Action definition is null."
            );

            return;
        }

        if (depth >= MAX_VALIDATION_DEPTH) {

            DAI_ValidationReport.error(
                    source,
                    "Nested action depth exceeds "
                            + MAX_VALIDATION_DEPTH
                            + "."
            );

            return;
        }

        boolean sequenceContainer =
                isSequenceContainer(
                        action
                );

        boolean pureReference =
                isPureReference(
                        action
                );

        if (
                !action.hasType()
                        && !action.hasAction()
                        && !action.hasSequence()
        ) {

            DAI_ValidationReport.error(
                    source,
                    "Action node has no type, action reference, or sequence."
            );
        }

        if (
                action.hasType()
                        && !sequenceContainer
                        && !DAI_ActionRegistry.contains(
                        action.type()
                )
        ) {

            DAI_ValidationReport.error(
                    source,
                    "Unknown action type '"
                            + action.type()
                            + "'."
            );
        }

        if (
                pureReference
                        && parseReference(
                        action.action()
                ) == null
        ) {

            DAI_ValidationReport.error(
                    source,
                    "Invalid action reference '"
                            + action.action()
                            + "'."
            );
        }

        if (
                sequenceContainer
                        && !action.hasSequence()
        ) {

            DAI_ValidationReport.warning(
                    source,
                    "Sequence container has no child actions."
            );
        }

        validateMovementDirection(
                source,
                action
        );

        if (
                "hotbar_select".equals(
                        action.type()
                )
                        && (
                        action.slot() < 0
                                || action.slot() > 8
                )
        ) {

            DAI_ValidationReport.error(
                    source,
                    "Hotbar slot must be between 0 and 8."
            );
        }

        validateConditions(
                source,
                action.conditions(),
                depth
        );

        for (
                int index = 0;
                index < action.sequence().size();
                index++
        ) {

            validateAction(
                    source
                            + ".sequence["
                            + index
                            + "]",
                    action.sequence().get(
                            index
                    ),
                    depth + 1
            );
        }
    }

    private static void validateMovementDirection(
            String source,
            DAI_ActionDefinition action
    ) {

        if (
                action.direction().isEmpty()
                        || !usesMovementDirection(
                        action.type()
                )
        ) {
            return;
        }

        if (
                MOVEMENT_DIRECTIONS.contains(
                        action.direction()
                                .toLowerCase()
                )
        ) {
            return;
        }

        DAI_ValidationReport.warning(
                source,
                "Unrecognized movement direction '"
                        + action.direction()
                        + "'."
        );
    }

    private static boolean usesMovementDirection(
            String type
    ) {

        if (
                type == null
                        || type.isBlank()
        ) {
            return false;
        }

        return "move".equals(
                type
        )
                || "move_set".equals(
                type
        )
                || "move_press".equals(
                type
        )
                || "move_release".equals(
                type
        );
    }

    private static void validateConditions(
            String source,
            Iterable<DAI_ConditionDefinition> conditions,
            int depth
    ) {

        int index =
                0;

        for (
                DAI_ConditionDefinition condition
                : conditions
        ) {

            String conditionSource =
                    source
                            + ".conditions["
                            + index
                            + "]";

            validateCondition(
                    conditionSource,
                    condition,
                    depth + 1
            );

            index++;
        }
    }

    private static void validateCondition(
            String source,
            DAI_ConditionDefinition condition,
            int depth
    ) {

        if (condition == null) {

            DAI_ValidationReport.error(
                    source,
                    "Condition is null."
            );

            return;
        }

        if (depth >= MAX_VALIDATION_DEPTH) {

            DAI_ValidationReport.error(
                    source,
                    "Nested condition depth exceeds "
                            + MAX_VALIDATION_DEPTH
                            + "."
            );

            return;
        }

        String type =
                condition.type();

        boolean groupType =
                CONDITION_GROUP_TYPES.contains(
                        type
                );

        if (
                !groupType
                        && !DAI_ConditionRegistry.contains(
                        type
                )
        ) {

            DAI_ValidationReport.error(
                    source,
                    "Unknown condition type '"
                            + type
                            + "'."
            );
        }

        if (
                groupType
                        && !condition.hasConditions()
        ) {

            DAI_ValidationReport.warning(
                    source,
                    "Condition group '"
                            + type
                            + "' has no child conditions."
            );
        }

        if (
                "not".equals(type)
                        && condition.conditions().size() != 1
        ) {

            DAI_ValidationReport.error(
                    source,
                    "The 'not' condition requires exactly one child condition."
            );
        }

        validateConditions(
                source,
                condition.conditions(),
                depth
        );
    }

    private static void validateReferenceGraph(
            Identifier id,
            Set<Identifier> visiting,
            Set<Identifier> visited
    ) {

        if (visited.contains(id)) {
            return;
        }

        if (!visiting.add(id)) {

            DAI_ValidationReport.error(
                    id.toString(),
                    "Circular action reference detected."
            );

            return;
        }

        DAI_ActionDefinition action =
                DAI_ActionLibrary.get(
                        id
                );

        if (action != null) {

            validateReferencesInNode(
                    id.toString(),
                    action,
                    visiting,
                    visited
            );
        }

        visiting.remove(
                id
        );

        visited.add(
                id
        );
    }

    private static void validateReferencesInNode(
            String source,
            DAI_ActionDefinition action,
            Set<Identifier> visiting,
            Set<Identifier> visited
    ) {

        if (isPureReference(action)) {

            Identifier referencedId =
                    parseReference(
                            action.action()
                    );

            if (referencedId == null) {
                return;
            }

            if (
                    !DAI_ActionLibrary.contains(
                            referencedId
                    )
            ) {

                DAI_ValidationReport.error(
                        source,
                        "Unknown action reference '"
                                + referencedId
                                + "'."
                );

                return;
            }

            if (visiting.contains(referencedId)) {

                DAI_ValidationReport.error(
                        source,
                        "Circular action reference detected through '"
                                + referencedId
                                + "'."
                );

                return;
            }

            validateReferenceGraph(
                    referencedId,
                    visiting,
                    visited
            );
        }

        for (
                int index = 0;
                index < action.sequence().size();
                index++
        ) {

            validateReferencesInNode(
                    source
                            + ".sequence["
                            + index
                            + "]",
                    action.sequence().get(
                            index
                    ),
                    visiting,
                    visited
            );
        }
    }

    private static boolean isSequenceContainer(
            DAI_ActionDefinition action
    ) {

        return "sequence".equals(
                action.type()
        )
                || (
                !action.hasType()
                        && action.hasSequence()
        );
    }

    private static boolean isPureReference(
            DAI_ActionDefinition action
    ) {

        return !action.hasType()
                && action.hasAction();
    }

    private static Identifier parseReference(
            String reference
    ) {

        return DAI_ActionResolver.parseReference(
                reference
        );
    }
}