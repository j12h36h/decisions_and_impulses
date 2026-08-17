package io.github.j12h36h.dai.client.packs;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.core.DAI_Config;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Auto-enables resource packs installed through D.A.I.'s Official Packs browser. */
public final class DAI_ManagedResourcePackBootstrap {

    private static boolean initialized;

    private DAI_ManagedResourcePackBootstrap() {}

    public static synchronized void initialize(IEventBus modBus) {
        if (initialized) return;
        initialized = true;
        // Run before Minecraft validates options.txt so renamed/versioned
        // companion packs can replace stale file/... selections without the
        // user opening the Resource Packs screen.
        DAI_CompanionResourcePackPreferences.reconcileSavedSelectionEarly();

        modBus.addListener(DAI_ManagedResourcePackBootstrap::addPackFinders);
    }

    private static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;

        // Required repository entries make managed packs active immediately.
        // Persist the same stable ids into vanilla's saved pack selection so
        // installs/updates remain enabled without visiting the pack screen.
        DAI_ManagedResourcePackPreferences.reconcileSavedSelection();
        DAI_ManagedResourcePackPreferences.reconcileLiveSelection();
        DAI_CompanionResourcePackPreferences.reconcileSavedSelectionEarly();
        DAI_CompanionResourcePackPreferences.reconcileLiveSelection();

        if (!DAI_Config.autoEnableManagedResourcePacks()) {
            DAI_Core.LOGGER.info(
                    "<DAI>: Managed resource-pack auto-enable is disabled by configuration."
            );
            return;
        }

        int[] count = {0};
        for (DAI_PackInstallManager.InstalledPack installed : DAI_PackInstallManager.installedPacks()) {
            for (DAI_PackInstallManager.InstalledComponent component : installed.components()) {
                if (!"resource_pack".equals(component.type())) continue;

                Path root = DAI_PackInstallManager.resolveManagedResourceRoot(component.path());
                if (root == null
                        || !Files.isDirectory(root)
                        || !Files.isRegularFile(root.resolve("pack.mcmeta"))) {
                    DAI_Core.LOGGER.warn(
                            "<DAI>: Managed resource pack '{}' is missing from '{}'.",
                            component.id(),
                            root
                    );
                    continue;
                }

                String packId = DAI_ManagedResourcePackPreferences.packId(
                        installed,
                        component
                );

                PackLocationInfo location = new PackLocationInfo(
                        packId,
                        Component.literal("D.A.I. · " + installed.id()),
                        PackSource.BUILT_IN,
                        Optional.empty()
                );

                Pack.ResourcesSupplier resources =
                        new PathPackResources.PathResourcesSupplier(root);

                Pack.Metadata metadata = new Pack.Metadata(
                        Component.literal("Automatically enabled D.A.I.-managed resource pack"),
                        PackCompatibility.COMPATIBLE,
                        FeatureFlagSet.of(),
                        List.of(),
                        false
                );

                PackSelectionConfig selection = new PackSelectionConfig(
                        true,
                        Pack.Position.TOP,
                        true
                );

                Pack pack = new Pack(location, resources, metadata, selection);
                event.addRepositorySource(output -> output.accept(pack));
                count[0]++;
            }
        }

        if (count[0] > 0) {
            DAI_Core.LOGGER.info(
                    "<DAI>: Auto-enabled {} D.A.I.-managed resource pack(s).",
                    count[0]
            );
        }
    }

}
