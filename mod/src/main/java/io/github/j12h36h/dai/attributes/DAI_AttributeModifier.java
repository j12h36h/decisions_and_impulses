package io.github.j12h36h.dai.attributes;

public record DAI_AttributeModifier(
        String id,
        double amount,
        Operation operation,
        int priority
) {
    public enum Operation {
        ADD,
        MULTIPLY_BASE,
        MULTIPLY_TOTAL,
        SET;

        public static Operation parse(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase();
            return switch (normalized) {
                case "add_multiplied_base", "multiply_base", "mul_base" -> MULTIPLY_BASE;
                case "add_multiplied_total", "multiply_total", "mul_total" -> MULTIPLY_TOTAL;
                case "set", "override" -> SET;
                default -> ADD;
            };
        }
    }
}
