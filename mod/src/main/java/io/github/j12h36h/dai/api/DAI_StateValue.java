package io.github.j12h36h.dai.api;

public record DAI_StateValue(
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

    public DAI_StateValue {

        if (type == null) {
            throw new IllegalArgumentException(
                    "State value type cannot be null."
            );
        }

        if (!Double.isFinite(numberValue)) {
            throw new IllegalArgumentException(
                    "State numeric value must be finite."
            );
        }

        stringValue =
                stringValue == null
                        ? ""
                        : stringValue;
    }

    public static DAI_StateValue bool(
            boolean value
    ) {

        return new DAI_StateValue(
                Type.BOOLEAN,
                value,
                0.0D,
                ""
        );
    }

    public static DAI_StateValue number(
            double value
    ) {

        return new DAI_StateValue(
                Type.NUMBER,
                false,
                value,
                ""
        );
    }

    public static DAI_StateValue string(
            String value
    ) {

        return new DAI_StateValue(
                Type.STRING,
                false,
                0.0D,
                value
        );
    }

    public static DAI_StateValue missing() {

        return new DAI_StateValue(
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
