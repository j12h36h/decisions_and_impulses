package io.github.j12h36h.dai.logics.bootstrap;

import io.github.j12h36h.dai.logics.core.DAI_Config;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

public final class DAI_ConfigBootstrap {

    private DAI_ConfigBootstrap() {
        // Utility class.
    }

    public static void initialize(
            IEventBus modBus,
            ModContainer container
    ) {

        container.registerConfig(
                ModConfig.Type.COMMON,
                DAI_Config.SPEC
        );

        modBus.addListener(
                DAI_ConfigBootstrap::onConfigLoading
        );

        modBus.addListener(
                DAI_ConfigBootstrap::onConfigReloading
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Registered common configuration."
        );
    }

    private static void onConfigLoading(
            ModConfigEvent.Loading event
    ) {
        migrateIfNeeded(
                event.getConfig()
        );
    }

    private static void onConfigReloading(
            ModConfigEvent.Reloading event
    ) {
        migrateIfNeeded(
                event.getConfig()
        );
    }

    /**
     * Upgrades older DAI configs in place while preserving all existing user
     * choices. Version 2 adds the master debugging switch; future migrations
     * can append their new defaults here before the version is committed.
     */
    private static void migrateIfNeeded(
            ModConfig config
    ) {

        if (
                config == null
                        || config.getSpec() != DAI_Config.SPEC
                        || !DAI_Core.MODID.equals(config.getModId())
        ) {
            return;
        }

        var loaded =
                config.getLoadedConfig();

        if (loaded == null) {
            return;
        }

        var data =
                loaded.config();

        Object rawVersion =
                data.get(
                        "configVersion"
                );

        int loadedVersion =
                rawVersion instanceof Number number
                        ? number.intValue()
                        : 0;

        if (loadedVersion > DAI_Config.CURRENT_CONFIG_VERSION) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Config version {} is newer than supported version {}; leaving it unchanged.",
                    loadedVersion,
                    DAI_Config.CURRENT_CONFIG_VERSION
            );
            return;
        }

        if (loadedVersion == DAI_Config.CURRENT_CONFIG_VERSION) {
            return;
        }

        int originalVersion =
                loadedVersion;

        /*
         * v1 -> v2: release diagnostics become opt-in. Do not overwrite an
         * explicit value if a user already added the new key manually.
         */
        if (loadedVersion < 2) {
            if (!data.contains("debugging")) {
                data.set(
                        "debugging",
                        false
                );
            }

            loadedVersion = 2;
        }

        data.set(
                "configVersion",
                DAI_Config.CURRENT_CONFIG_VERSION
        );

        try {
            loaded.save();

            DAI_Core.LOGGER.info(
                    "<DAI>: Migrated configuration from version {} to {}.",
                    originalVersion,
                    DAI_Config.CURRENT_CONFIG_VERSION
            );

        } catch (RuntimeException exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Failed to save migrated configuration; existing in-memory values remain available.",
                    exception
            );
        }
    }
}
