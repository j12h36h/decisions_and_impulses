package io.github.j12h36h.dai.logics.condition;

public record DAI_ConditionValue(
        Type type,
        boolean booleanValue,
        double numberValue,
        String stringValue
) {

    public enum Type {
        BOOLEAN,
        NUMBER,
        STRING,
        MISSING
    }

    public DAI_ConditionValue {

        if (type == null) {
            throw new IllegalArgumentException(
                    "Condition value type cannot be null."
            );
        }

        if (!Double.isFinite(numberValue)) {
            throw new IllegalArgumentException(
                    "Condition numeric value must be finite."
            );
        }

        stringValue =
                stringValue == null
                        ? ""
                        : stringValue;
    }

    public static DAI_ConditionValue bool(
            boolean value
    ) {
        return new DAI_ConditionValue(
                Type.BOOLEAN,
                value,
                0.0D,
                ""
        );
    }

    public static DAI_ConditionValue number(
            double value
    ) {
        return new DAI_ConditionValue(
                Type.NUMBER,
                false,
                value,
                ""
        );
    }

    public static DAI_ConditionValue string(
            String value
    ) {
        return new DAI_ConditionValue(
                Type.STRING,
                false,
                0.0D,
                value
        );
    }

    public static DAI_ConditionValue missing() {
        return new DAI_ConditionValue(
                Type.MISSING,
                false,
                0.0D,
                ""
        );
    }

    public boolean isMissing() {
        return type == Type.MISSING;
    }
}