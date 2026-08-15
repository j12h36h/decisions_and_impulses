package io.github.j12h36h.dai.client.menus.system;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.j12h36h.dai.logics.condition.DAI_ConditionDefinition;

import java.util.List;

public record DAI_SystemButton(
        int slot,
        String id,
        String text,
        String action,
        List<DAI_ConditionDefinition> conditions,
        DAI_ButtonStyle style
) {

    public static final Codec<DAI_SystemButton> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.INT.fieldOf("slot").forGetter(DAI_SystemButton::slot),
                            Codec.STRING.fieldOf("id").forGetter(DAI_SystemButton::id),
                            Codec.STRING.fieldOf("text").forGetter(DAI_SystemButton::text),
                            Codec.STRING.fieldOf("action").forGetter(DAI_SystemButton::action),
                            DAI_ConditionDefinition.CODEC
                                    .listOf()
                                    .optionalFieldOf(
                                            "conditions",
                                            List.of()
                                    )
                                    .forGetter(DAI_SystemButton::conditions),
                            DAI_ButtonStyle.CODEC
                                    .optionalFieldOf(
                                            "style",
                                            DAI_ButtonStyle.EMPTY
                                    )
                                    .forGetter(DAI_SystemButton::style)
                    ).apply(instance, DAI_SystemButton::new)
            );

    public DAI_SystemButton(
            int slot,
            String id,
            String text,
            String action
    ) {
        this(
                slot,
                id,
                text,
                action,
                List.of(),
                DAI_ButtonStyle.EMPTY
        );
    }

    public DAI_SystemButton(
            int slot,
            String id,
            String text,
            String action,
            List<DAI_ConditionDefinition> conditions
    ) {
        this(slot, id, text, action, conditions, DAI_ButtonStyle.EMPTY);
    }

    public DAI_SystemButton {
        id = normalize(id);
        text = normalize(text);
        action = normalize(action);
        conditions = conditions == null
                ? List.of()
                : List.copyOf(conditions);
        style = style == null
                ? DAI_ButtonStyle.EMPTY
                : style;

        if (slot < 0) {
            throw new IllegalArgumentException(
                    "Menu button slot cannot be negative."
            );
        }

        if (id.isEmpty() || text.isEmpty() || action.isEmpty()) {
            throw new IllegalArgumentException(
                    "Menu button id, text, and action cannot be blank."
            );
        }

        if (
                conditions.stream()
                        .anyMatch(condition -> condition == null)
        ) {
            throw new IllegalArgumentException(
                    "Menu button conditions cannot contain null entries."
            );
        }
    }

    public boolean hasConditions() {
        return !conditions.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
