package io.github.j12h36h.dai.menus.system;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record DAI_SystemDefinition(
        int priority,
        List<DAI_SystemButton> buttons
) {

    public static final Codec<DAI_SystemDefinition> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.INT.fieldOf("priority").forGetter(DAI_SystemDefinition::priority),
                            DAI_SystemButton.CODEC.listOf().fieldOf("buttons").forGetter(DAI_SystemDefinition::buttons)
                    ).apply(instance, DAI_SystemDefinition::new)
            );

    public DAI_SystemDefinition {
        if (priority < 0) {
            throw new IllegalArgumentException("System definition priority cannot be negative.");
        }
        if (buttons == null || buttons.isEmpty()) {
            throw new IllegalArgumentException("System definition must contain at least one button.");
        }
        if (buttons.stream().anyMatch(button -> button == null)) {
            throw new IllegalArgumentException("System definition cannot contain null buttons.");
        }

        Set<Integer> slots = new HashSet<>();
        for (DAI_SystemButton button : buttons) {
            if (!slots.add(button.slot())) {
                throw new IllegalArgumentException(
                        "System definition contains duplicate slot " + button.slot() + "."
                );
            }
        }

        buttons = List.copyOf(buttons);
    }
}
