package io.github.j12h36h.dai.logics.bootstrap;

import io.github.j12h36h.dai.logics.core.DAI_Config;
import io.github.j12h36h.dai.client.config.DAI_ClientConfig;
import io.github.j12h36h.dai.server.config.DAI_ServerConfig;
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

        container.registerConfig(
                ModConfig.Type.CLIENT,
                DAI_ClientConfig.SPEC,
                "decisions_and_impulses-client.toml"
        );

        container.registerConfig(
                ModConfig.Type.SERVER,
                DAI_ServerConfig.SPEC,
                "decisions_and_impulses-server.toml"
        );

        modBus.addListener(
                DAI_ConfigBootstrap::onConfigLoading
        );

        modBus.addListener(
                DAI_ConfigBootstrap::onConfigReloading
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Registered common, client, and server configurations."
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
     * choices. Version 2 added the master debugging switch. Version 3 adds the
     * player/creator controls while retaining every pre-existing key/path.
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

        /*
         * v2 -> v3: creator-friendly player controls. Missing values receive
         * the same permissive defaults DAI used before these knobs existed,
         * so old datapacks and old user configs behave exactly as before.
         */
        if (loadedVersion < 3) {
            if (!data.contains("automationEnabled")) data.set("automationEnabled", true);
            if (!data.contains("automationMovement")) data.set("automationMovement", true);
            if (!data.contains("automationCombat")) data.set("automationCombat", true);
            if (!data.contains("automationWorldEditing")) data.set("automationWorldEditing", true);
            if (!data.contains("maxActionsPerSecond")) data.set("maxActionsPerSecond", 10);
            if (!data.contains("maxActionQueueSize")) data.set("maxActionQueueSize", 128);
            if (!data.contains("autoEnableAddons")) data.set("autoEnableAddons", true);
            if (!data.contains("autoEnableManagedResourcePacks")) data.set("autoEnableManagedResourcePacks", true);
            if (!data.contains("customTitleScreens")) data.set("customTitleScreens", true);
            if (!data.contains("overlayOpacity")) data.set("overlayOpacity", 1.0D);

            loadedVersion = 3;
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
