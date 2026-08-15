package io.github.j12h36h.dai.client.logics.creation;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

public record DAI_RecipeDefinition(
        Identifier id,
        String type,
        String container,
        int width,
        int height,
        List<String> pattern,
        Map<Character, Ingredient> key,
        Map<String, Ingredient> slots,
        Result result,
        int processTicks
) {

    public record Ingredient(
            Identifier item,
            Identifier tag,
            int count
    ) {

        public boolean isValid() {

            return item != null
                    || tag != null;
        }
    }

    public record Result(
            Identifier item,
            int count
    ) {

        public boolean isValid() {

            return item != null
                    && count > 0;
        }
    }

    public DAI_RecipeDefinition(
            Identifier id,
            String type,
            String container,
            int width,
            int height,
            List<String> pattern,
            Map<Character, Ingredient> key,
            Result result
    ) {

        this(
                id,
                type,
                container,
                width,
                height,
                pattern,
                key,
                Map.of(),
                result,
                0
        );
    }

    public boolean isCrafting() {

        return "crafting_shaped".equalsIgnoreCase(
                type
        );
    }

    public boolean isSmelting() {

        return "smelting".equalsIgnoreCase(
                type
        );
    }

    public boolean isContainerProcess() {

        return "container_process".equalsIgnoreCase(
                type
        );
    }
}
