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

    private DAI_ConfigBootstrap() {}

    public static void initialize(IEventBus modBus, ModContainer container) {
        /* Keep this filename explicit: DAI_Config reads the same common file
         * before normal NeoForge config load so registry/startup modules can be
         * skipped without allocating their runtime at all. */
        container.registerConfig(
                ModConfig.Type.COMMON,
                DAI_Config.SPEC,
                "decisions_and_impulses-common.toml"
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

        modBus.addListener(DAI_ConfigBootstrap::onConfigLoading);
        modBus.addListener(DAI_ConfigBootstrap::onConfigReloading);

        DAI_Core.LOGGER.info("<DAI>: Registered common, client, and server configurations.");
    }

    private static void onConfigLoading(ModConfigEvent.Loading event) {
        migrateIfNeeded(event.getConfig());
        logModules(event.getConfig());
    }

    private static void onConfigReloading(ModConfigEvent.Reloading event) {
        migrateIfNeeded(event.getConfig());
        logModules(event.getConfig());
    }

    /**
     * Upgrades older DAI configs while preserving existing choices.
     * v2 added diagnostics, v3 added player/creator controls, v4 introduced
     * feature modules, and v5 replaces project-specific module switches with
     * generic presentation/story capability switches. Missing v4 module values intentionally
     * use the enabled defaults from DAI_Config so old installations behave
     * exactly as before until a creator opts out of modules.
     */
    private static void migrateIfNeeded(ModConfig config) {
        if (config == null
                || config.getSpec() != DAI_Config.SPEC
                || !DAI_Core.MODID.equals(config.getModId())) return;

        var loaded = config.getLoadedConfig();
        if (loaded == null) return;
        var data = loaded.config();

        Object rawVersion = data.get("configVersion");
        int loadedVersion = rawVersion instanceof Number number ? number.intValue() : 0;

        if (loadedVersion > DAI_Config.CURRENT_CONFIG_VERSION) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Config version {} is newer than supported version {}; leaving it unchanged.",
                    loadedVersion,
                    DAI_Config.CURRENT_CONFIG_VERSION
            );
            return;
        }
        if (loadedVersion == DAI_Config.CURRENT_CONFIG_VERSION) return;

        int originalVersion = loadedVersion;

        if (loadedVersion < 2) {
            if (!data.contains("debugging")) data.set("debugging", false);
            loadedVersion = 2;
        }

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

        /* v3 -> v4: ModConfigSpec supplies all [modules] entries as true when
         * absent. We deliberately do not write nested values here, because
         * preserving an older hand-edited config is more important than
         * forcing every new optional key into it. */
        if (loadedVersion < 4) loadedVersion = 4;
        if (loadedVersion < 5) loadedVersion = 5;

        data.set("configVersion", DAI_Config.CURRENT_CONFIG_VERSION);

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

    private static void logModules(ModConfig config) {
        if (config == null || config.getSpec() != DAI_Config.SPEC) return;
        long enabled = DAI_Config.featureModuleSnapshot().values().stream().filter(Boolean::booleanValue).count();
        int total = DAI_Config.featureModuleSnapshot().size();
        DAI_Core.LOGGER.info("<DAI>: Feature modules enabled: {}/{}.", enabled, total);
    }
}
