package io.github.j12h36h.dai.client.menus;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

public final class DAI_ScreenProfileLoader
        extends SimpleJsonResourceReloadListener<DAI_ScreenProfile> {

    private static final String FOLDER =
            "screen_profiles";

    public DAI_ScreenProfileLoader() {

        super(
                DAI_ScreenProfile.CODEC,
                FileToIdConverter.json(
                        FOLDER
                )
        );
    }

    @Override
    protected void apply(
            Map<Identifier, DAI_ScreenProfile> definitions,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {

        DAI_Core.LOGGER.info(
                "<DAI>: Reloading screen profiles from '{}'.",
                FOLDER
        );

        DAI_ScreenProfileManager.clear();

        definitions.forEach(
                (identifier, profile) -> {

                    if (
                            identifier == null
                                    || profile == null
                    ) {
                        return;
                    }

                    /*
                     * Keep the full datapack identifier.
                     *
                     * Unlike action definitions, screen profiles should
                     * preserve folders because compatibility packs may want
                     * organizational structures such as:
                     *
                     * screen_profiles/minecraft/furnace.json
                     * screen_profiles/create/crusher.json
                     *
                     * These therefore resolve as:
                     *
                     * namespace:minecraft/furnace
                     * namespace:create/crusher
                     */
                    String profileId =
                            identifier.toString();

                    DAI_ScreenProfileManager.register(
                            profileId,
                            profile
                    );

                    DAI_Core.debug(
                            "<DAI>: Loaded screen profile '{}' with {} variant(s).",
                            profileId,
                            profile.variants().size()
                    );
                }
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Loaded {} screen profile(s).",
                DAI_ScreenProfileManager.size()
        );
    }
}