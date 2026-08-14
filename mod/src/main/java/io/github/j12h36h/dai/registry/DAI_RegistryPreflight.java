package io.github.j12h36h.dai.registry;

import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compares reloadable DAI content with the native ids discovered by the early
 * scanner for this JVM launch. Content already present on disk should pass on
 * first load. Only definitions introduced after static registry events have
 * fired fall back to staging for the next launch.
 */
public final class DAI_RegistryPreflight {

    private static Map<String, DAI_RegistrySpec> desired = Map.of();
    private static Map<String, DAI_RegistrySpec> pending = Map.of();
    private static Map<String, DAI_RegistrySpec> registered = Map.of();

    private static boolean restartRequired;

    private DAI_RegistryPreflight() {}

    public static void evaluate() {
        LinkedHashMap<String, DAI_RegistrySpec> desiredNow = new LinkedHashMap<>();
        LinkedHashMap<String, DAI_RegistrySpec> pendingNow = new LinkedHashMap<>();
        LinkedHashMap<String, DAI_RegistrySpec> registeredNow = new LinkedHashMap<>();

        Map<String, DAI_RegistrySpec> bootSpecs = DAI_DynamicRegistryBootstrap.bootSpecs();

        for (String contentId : DAI_ContentRegistry.ids()) {
            DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(contentId);
            if (entry == null || !entry.definition().registryBacked()) continue;

            DAI_RegistrySpec spec = DAI_RegistrySpec.from(entry);
            if (spec == null) continue;
            desiredNow.put(spec.key(), spec);

            DAI_RegistrySpec bootSpec = bootSpecs.get(spec.key());
            boolean bootMatches = spec.sameStaticDefinition(bootSpec);
            boolean nativePresent = nativePresent(spec);
            boolean registeredByDai = DAI_DynamicRegistryBootstrap.registeredByDai(spec);

            if (bootMatches && nativePresent && registeredByDai) {
                registeredNow.put(spec.key(), spec);
            } else {
                pendingNow.put(spec.key(), spec);
            }
        }

        desired = Collections.unmodifiableMap(desiredNow);
        pending = Collections.unmodifiableMap(pendingNow);
        registered = Collections.unmodifiableMap(registeredNow);
        restartRequired = !pending.isEmpty();

        DAI_RegistryCache.merge(desired.values());
        DAI_RegistryWorldStore.refreshCurrentWorld();

        if (restartRequired) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Registry preflight found {} late native content id(s) that were added after startup. They are staged for the next launch.",
                    pending.size()
            );
            for (DAI_RegistrySpec spec : pending.values()) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: Pending native {} '{}'.",
                        spec.nativeRegistry().name().toLowerCase(),
                        spec.id()
                );
            }
        } else if (!desired.isEmpty()) {
            DAI_Core.LOGGER.info(
                    "<DAI>: Registry preflight passed: {} native DAI content id(s) available.",
                    registered.size()
            );
        }
    }

    public static boolean restartRequired() {
        return restartRequired;
    }

    public static boolean isUsable(String contentId) {
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(contentId);
        if (entry == null) return false;
        if (!entry.definition().registryBacked()) return true;

        DAI_RegistrySpec spec = DAI_RegistrySpec.from(entry);
        return spec != null && registered.containsKey(spec.key());
    }

    /**
     * Returns the real registered item id for content that owns one, otherwise
     * the legacy carrier id. Pending registry-backed definitions deliberately
     * return an empty string rather than falling back silently.
     */
    public static String itemId(DAI_ContentRegistry.Entry entry) {
        if (entry == null) return "";

        if (!entry.definition().registryBacked()) {
            return entry.definition().carrier();
        }

        DAI_RegistrySpec spec = DAI_RegistrySpec.from(entry);
        if (spec == null || !registered.containsKey(spec.key())) return "";
        return spec.id();
    }

    public static Map<String, DAI_RegistrySpec> desiredSpecs() {
        return desired;
    }

    public static Map<String, DAI_RegistrySpec> pendingSpecs() {
        return pending;
    }

    public static Map<String, DAI_RegistrySpec> registeredSpecs() {
        return registered;
    }

    private static boolean nativePresent(DAI_RegistrySpec spec) {
        Identifier id = spec.identifier();
        if (id == null) return false;

        return switch (spec.nativeRegistry()) {
            case ITEM -> BuiltInRegistries.ITEM.containsKey(id);
            case BLOCK -> BuiltInRegistries.BLOCK.containsKey(id)
                    && BuiltInRegistries.ITEM.containsKey(id);
        };
    }
}
