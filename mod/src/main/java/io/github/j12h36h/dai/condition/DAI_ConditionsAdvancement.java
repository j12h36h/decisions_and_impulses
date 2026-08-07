package io.github.j12h36h.dai.condition;

import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.mixin.Mixin_Advancements;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.Map;

public final class DAI_ConditionsAdvancement {

    private DAI_ConditionsAdvancement() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "advancement_complete",
                (context, condition) -> {

                    ClientAdvancements advancements =
                            getAdvancements();

                    if (advancements == null) {
                        return DAI_ConditionValue.missing();
                    }

                    Identifier id =
                            parseIdentifier(
                                    condition.parameter()
                            );

                    if (id == null) {

                        DAI_Core.LOGGER.warn(
                                "<DAI>: advancement_complete requires a valid advancement id."
                        );

                        return DAI_ConditionValue.missing();
                    }

                    Map<AdvancementHolder, AdvancementProgress> progressMap =
                            progressMap(
                                    advancements
                            );

                    AdvancementHolder holder =
                            progressMap.keySet()
                                    .stream()
                                    .filter(candidate ->
                                            candidate.id()
                                                    .equals(id)
                                    )
                                    .findFirst()
                                    .orElse(null);

                    if (holder == null) {
                        return DAI_ConditionValue.bool(
                                false
                        );
                    }

                    AdvancementProgress progress =
                            progressMap.get(
                                    holder
                            );

                    return DAI_ConditionValue.bool(
                            progress != null
                                    && progress.isDone()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "advancement_category_complete",
                (context, condition) -> {

                    ClientAdvancements advancements =
                            getAdvancements();

                    if (advancements == null) {
                        return DAI_ConditionValue.missing();
                    }

                    String category =
                            normalizeCategory(
                                    condition.parameter()
                            );

                    if (category.isEmpty()) {

                        DAI_Core.LOGGER.warn(
                                "<DAI>: advancement_category_complete requires a category."
                        );

                        return DAI_ConditionValue.missing();
                    }

                    Map<AdvancementHolder, AdvancementProgress> progressMap =
                            progressMap(
                                    advancements
                            );

                    boolean found =
                            false;

                    for (
                            AdvancementHolder holder
                            : progressMap.keySet()
                    ) {

                        Identifier id =
                                holder.id();

                        if (
                                !matchesCategory(
                                        id,
                                        category
                                )
                        ) {
                            continue;
                        }

                        found =
                                true;

                        AdvancementProgress progress =
                                progressMap.get(
                                        holder
                                );

                        if (
                                progress == null
                                        || !progress.isDone()
                        ) {
                            return DAI_ConditionValue.bool(
                                    false
                            );
                        }
                    }

                    return found
                            ? DAI_ConditionValue.bool(
                            true
                    )
                            : DAI_ConditionValue.missing();
                }
        );
    }

    private static ClientAdvancements getAdvancements() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.getConnection()
                        == null
        ) {
            return null;
        }

        return minecraft.getConnection()
                .getAdvancements();
    }

    private static Map<AdvancementHolder, AdvancementProgress> progressMap(
            ClientAdvancements advancements
    ) {

        return (
                (Mixin_Advancements) advancements
        ).dai$getProgress();
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

        if (
                !normalized.contains(
                        ":"
                )
        ) {
            normalized =
                    "minecraft:"
                            + normalized;
        }

        return Identifier.tryParse(
                normalized
        );
    }

    private static boolean matchesCategory(
            Identifier id,
            String category
    ) {

        String fullId =
                id.getNamespace()
                        + ":"
                        + id.getPath();

        if (
                category.contains(
                        ":"
                )
        ) {
            return fullId.startsWith(
                    category + "/"
            );
        }

        return id.getNamespace()
                .equals(
                        "minecraft"
                )
                && id.getPath()
                .startsWith(
                        category + "/"
                );
    }

    private static String normalizeCategory(
            String value
    ) {

        if (value == null) {
            return "";
        }

        String normalized =
                value.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        while (
                normalized.endsWith(
                        "/"
                )
        ) {
            normalized =
                    normalized.substring(
                            0,
                            normalized.length() - 1
                    );
        }

        return normalized;
    }
}