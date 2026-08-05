package io.github.j12h36h.dai.condition;


import io.github.j12h36h.dai.core.DAI_Core;

import java.util.List;
import java.util.Locale;

public final class DAI_ConditionEval {

    private static final int MAX_GROUP_DEPTH = 32;
    private static final int MAX_EVALUATED_NODES = 512;

    private DAI_ConditionEval() {
        // Utility class.
    }

    public static boolean evaluate(
            DAI_ConditionCore condition
    ) {

        DAI_ConditionContext context =
                DAI_ConditionContext.capture();

        EvaluationState state =
                new EvaluationState();

        return evaluate(
                context,
                condition,
                0,
                state
        );
    }

    public static boolean evaluateAll(
            List<DAI_ConditionCore> conditions
    ) {

        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        DAI_ConditionContext context =
                DAI_ConditionContext.capture();

        EvaluationState state =
                new EvaluationState();

        for (DAI_ConditionCore condition : conditions) {

            if (
                    !evaluate(
                            context,
                            condition,
                            0,
                            state
                    )
            ) {

                DAI_Core.LOGGER.debug(
                        "<DAI>: Condition group was blocked by '{}'.",
                        condition == null
                                ? "<null>"
                                : condition.type()
                );

                return false;
            }
        }

        return true;
    }

    private static boolean evaluate(
            DAI_ConditionContext context,
            DAI_ConditionCore condition,
            int depth,
            EvaluationState state
    ) {

        if (condition == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot evaluate a null condition."
            );

            return false;
        }

        if (context == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot evaluate condition '{}' with a null context.",
                    condition.type()
            );

            return false;
        }

        if (depth > MAX_GROUP_DEPTH) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Condition group exceeded maximum depth of {}.",
                    MAX_GROUP_DEPTH
            );

            return false;
        }

        state.evaluatedNodes++;

        if (state.evaluatedNodes > MAX_EVALUATED_NODES) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Condition evaluation exceeded maximum node count of {}.",
                    MAX_EVALUATED_NODES
            );

            return false;
        }

        boolean result =
                switch (condition.type()) {

                    case "all" ->
                            evaluateAllGroup(
                                    context,
                                    condition.conditions(),
                                    depth + 1,
                                    state
                            );

                    case "any" ->
                            evaluateAnyGroup(
                                    context,
                                    condition.conditions(),
                                    depth + 1,
                                    state
                            );

                    case "none" ->
                            !evaluateAnyGroup(
                                    context,
                                    condition.conditions(),
                                    depth + 1,
                                    state
                            );

                    case "not" ->
                            evaluateNotGroup(
                                    context,
                                    condition.conditions(),
                                    depth + 1,
                                    state
                            );

                    default ->
                            evaluateProvider(
                                    context,
                                    condition
                            );
                };

        if (condition.negate()) {
            result = !result;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Condition '{}' operator='{}' negate={} result={}.",
                condition.type(),
                condition.operator(),
                condition.negate(),
                result
        );

        return result;
    }

    private static boolean evaluateAllGroup(
            DAI_ConditionContext context,
            List<DAI_ConditionCore> conditions,
            int depth,
            EvaluationState state
    ) {

        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        for (DAI_ConditionCore condition : conditions) {

            if (
                    !evaluate(
                            context,
                            condition,
                            depth,
                            state
                    )
            ) {
                return false;
            }
        }

        return true;
    }

    private static boolean evaluateAnyGroup(
            DAI_ConditionContext context,
            List<DAI_ConditionCore> conditions,
            int depth,
            EvaluationState state
    ) {

        if (conditions == null || conditions.isEmpty()) {
            return false;
        }

        for (DAI_ConditionCore condition : conditions) {

            if (
                    evaluate(
                            context,
                            condition,
                            depth,
                            state
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private static boolean evaluateNotGroup(
            DAI_ConditionContext context,
            List<DAI_ConditionCore> conditions,
            int depth,
            EvaluationState state
    ) {

        if (conditions == null || conditions.size() != 1) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: 'not' condition requires exactly one child."
            );

            return false;
        }

        return !evaluate(
                context,
                conditions.getFirst(),
                depth,
                state
        );
    }

    private static boolean evaluateProvider(
            DAI_ConditionContext context,
            DAI_ConditionCore condition
    ) {

        DAI_ConditionValue actual =
                DAI_ConditionRegistry.read(
                        context,
                        condition
                );

        return compare(
                actual,
                condition
        );
    }

    private static boolean compare(
            DAI_ConditionValue actual,
            DAI_ConditionCore condition
    ) {

        if (
                actual == null
                        || actual.isMissing()
        ) {
            return false;
        }

        String operator =
                normalizeOperator(
                        condition.operator()
                );

        return switch (actual.type()) {

            case BOOLEAN ->
                    compareBoolean(
                            actual.booleanValue(),
                            condition.booleanValue(),
                            operator
                    );

            case NUMBER ->
                    compareNumber(
                            actual.numberValue(),
                            condition.numberValue(),
                            operator
                    );

            case STRING ->
                    compareString(
                            actual.stringValue(),
                            condition.stringValue(),
                            operator
                    );

            case MISSING ->
                    false;
        };
    }

    private static boolean compareBoolean(
            boolean actual,
            boolean expected,
            String operator
    ) {

        return switch (operator) {

            case "is_true" ->
                    actual;

            case "is_false" ->
                    !actual;

            case "equals" ->
                    actual == expected;

            case "not_equals" ->
                    actual != expected;

            default -> {

                warnUnsupported(
                        operator,
                        DAI_ConditionValue.Type.BOOLEAN
                );

                yield false;
            }
        };
    }

    private static boolean compareNumber(
            double actual,
            double expected,
            String operator
    ) {

        return switch (operator) {

            case "equals" ->
                    Double.compare(
                            actual,
                            expected
                    ) == 0;

            case "not_equals" ->
                    Double.compare(
                            actual,
                            expected
                    ) != 0;

            case "less_than" ->
                    actual < expected;

            case "less_than_or_equal" ->
                    actual <= expected;

            case "greater_than" ->
                    actual > expected;

            case "greater_than_or_equal" ->
                    actual >= expected;

            default -> {

                warnUnsupported(
                        operator,
                        DAI_ConditionValue.Type.NUMBER
                );

                yield false;
            }
        };
    }

    private static boolean compareString(
            String actual,
            String expected,
            String operator
    ) {

        String safeActual =
                actual == null
                        ? ""
                        : actual;

        String safeExpected =
                expected == null
                        ? ""
                        : expected;

        return switch (operator) {

            case "equals" ->
                    safeActual.equals(
                            safeExpected
                    );

            case "not_equals" ->
                    !safeActual.equals(
                            safeExpected
                    );

            case "equals_ignore_case" ->
                    safeActual.equalsIgnoreCase(
                            safeExpected
                    );

            case "contains" ->
                    safeActual.contains(
                            safeExpected
                    );

            case "starts_with" ->
                    safeActual.startsWith(
                            safeExpected
                    );

            case "ends_with" ->
                    safeActual.endsWith(
                            safeExpected
                    );

            default -> {

                warnUnsupported(
                        operator,
                        DAI_ConditionValue.Type.STRING
                );

                yield false;
            }
        };
    }

    private static void warnUnsupported(
            String operator,
            DAI_ConditionValue.Type valueType
    ) {

        DAI_Core.LOGGER.warn(
                "<DAI>: Unsupported condition operator '{}' for value type '{}'.",
                operator,
                valueType
        );
    }

    private static String normalizeOperator(
            String operator
    ) {

        if (operator == null || operator.isBlank()) {
            return "is_true";
        }

        return operator
                .trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    private static final class EvaluationState {

        private int evaluatedNodes;
    }
}