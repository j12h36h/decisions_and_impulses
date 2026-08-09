package io.github.j12h36h.dai.logics.condition;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.Identifier;

public final class DAI_ConditionsKnowledge {

    private DAI_ConditionsKnowledge() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "biome_known",
                (context, condition) -> {

                    Identifier biomeId =
                            parseIdentifier(
                                    condition.parameter()
                            );

                    if (biomeId == null) {

                        DAI_Core.LOGGER.warn(
                                "<DAI>: biome_known requires a valid biome id."
                        );

                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            DAI_ConditionMemory.knowsBiome(
                                    biomeId
                            )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "structure_known",
                (context, condition) -> {

                    Identifier structureId =
                            parseIdentifier(
                                    condition.parameter()
                            );

                    if (structureId == null) {

                        DAI_Core.LOGGER.warn(
                                "<DAI>: structure_known requires a valid structure id."
                        );

                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            DAI_ConditionMemory.knowsStructure(
                                    structureId
                            )
                    );
                }
        );
    }

    private static Identifier parseIdentifier(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {
            return null;
        }

        String normalized =
                value.trim();

        if (!normalized.contains(":")) {

            normalized =
                    "minecraft:"
                            + normalized;
        }

        return Identifier.tryParse(
                normalized
        );
    }
}
