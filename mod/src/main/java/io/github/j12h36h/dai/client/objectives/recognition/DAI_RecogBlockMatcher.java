package io.github.j12h36h.dai.client.objectives.recognition;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class DAI_RecogBlockMatcher {

    private static final String TAG_PREFIX =
            "#";

    private DAI_RecogBlockMatcher() {
        // Utility class.
    }

    public static boolean matches(
            String entry,
            BlockState state
    ) {

        if (
                entry == null
                        || entry.isBlank()
                        || state == null
        ) {
            return false;
        }

        String normalizedEntry =
                entry.trim()
                        .toLowerCase();

        if (
                normalizedEntry.startsWith(
                        TAG_PREFIX
                )
        ) {

            return matchesTag(
                    normalizedEntry.substring(
                            TAG_PREFIX.length()
                    ),
                    state
            );
        }

        return matchesBlock(
                normalizedEntry,
                state
        );
    }

    private static boolean matchesBlock(
            String entry,
            BlockState state
    ) {

        Identifier expectedId =
                Identifier.tryParse(
                        entry
                );

        if (expectedId == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Invalid recognition block id '{}'.",
                    entry
            );

            return false;
        }

        Identifier actualId =
                BuiltInRegistries.BLOCK.getKey(
                        state.getBlock()
                );

        return expectedId.equals(
                actualId
        );
    }

    private static boolean matchesTag(
            String entry,
            BlockState state
    ) {

        Identifier tagId =
                Identifier.tryParse(
                        entry
                );

        if (tagId == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Invalid recognition block tag '#{}'.",
                    entry
            );

            return false;
        }

        TagKey<Block> tag =
                TagKey.create(
                        Registries.BLOCK,
                        tagId
                );

        return state.is(
                tag
        );
    }
}
