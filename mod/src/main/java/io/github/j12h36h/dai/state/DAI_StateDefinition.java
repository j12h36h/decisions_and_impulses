package io.github.j12h36h.dai.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.j12h36h.dai.api.DAI_StateValue;

import java.util.Locale;

/** Declarative state/variable schema loaded from datapack dai_state JSON definitions. */
public record DAI_StateDefinition(
        String type,
        String scope,
        boolean defaultBoolean,
        double defaultNumber,
        String defaultString,
        boolean persistent,
        boolean sync,
        boolean clientWritable
) {
    public static final Codec<DAI_StateDefinition> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("type", "boolean").forGetter(DAI_StateDefinition::type),
                    Codec.STRING.optionalFieldOf("scope", "client_session").forGetter(DAI_StateDefinition::scope),
                    Codec.BOOL.optionalFieldOf("default_boolean", false).forGetter(DAI_StateDefinition::defaultBoolean),
                    Codec.DOUBLE.optionalFieldOf("default_number", 0.0D).forGetter(DAI_StateDefinition::defaultNumber),
                    Codec.STRING.optionalFieldOf("default_string", "").forGetter(DAI_StateDefinition::defaultString),
                    Codec.BOOL.optionalFieldOf("persistent", false).forGetter(DAI_StateDefinition::persistent),
                    Codec.BOOL.optionalFieldOf("sync", true).forGetter(DAI_StateDefinition::sync),
                    Codec.BOOL.optionalFieldOf("client_writable", false).forGetter(DAI_StateDefinition::clientWritable)
            ).apply(instance, DAI_StateDefinition::new));

    public DAI_StateDefinition {
        type = normalize(type, "boolean");
        scope = normalize(scope, "client_session");
        defaultString = defaultString == null ? "" : defaultString;
        if (!Double.isFinite(defaultNumber)) throw new IllegalArgumentException("State default_number must be finite.");
        if (!type.equals("boolean") && !type.equals("number") && !type.equals("string")) {
            throw new IllegalArgumentException("Unsupported DAI state type '" + type + "'.");
        }
        if (!scope.equals("client_session") && !scope.equals("player") && !scope.equals("entity")
                && !scope.equals("dimension") && !scope.equals("world") && !scope.equals("server")) {
            throw new IllegalArgumentException("Unsupported DAI state scope '" + scope + "'.");
        }
        // A client-only session value cannot be persisted or server-synchronized.
        if (scope.equals("client_session")) {
            persistent = false;
            sync = false;
        }
    }

    public DAI_StateValue defaultValue() {
        return switch (type) {
            case "number" -> DAI_StateValue.number(defaultNumber);
            case "string" -> DAI_StateValue.string(defaultString);
            default -> DAI_StateValue.bool(defaultBoolean);
        };
    }

    public boolean serverOwned() { return !scope.equals("client_session"); }

    public boolean accepts(DAI_StateValue value) {
        if (value == null || value.isMissing()) return true;
        return switch (type) {
            case "number" -> value.type() == DAI_StateValue.Type.NUMBER;
            case "string" -> value.type() == DAI_StateValue.Type.STRING;
            default -> value.type() == DAI_StateValue.Type.BOOLEAN;
        };
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
