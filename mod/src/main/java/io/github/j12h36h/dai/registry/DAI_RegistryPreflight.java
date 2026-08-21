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
 * scanner for this JVM launch.
 *
 * Runtime-only changes are immediately usable after a datapack reload. A
 * restart is required only when a reload changes the native registry shell:
 * adding a new id, removing an id that was active in this world, changing its
 * registry kind, or changing properties that Minecraft baked into the native
 * object during static registration.
 */
public final class DAI_RegistryPreflight {

    private static Map<String, DAI_RegistrySpec> desired = Map.of();
    private static Map<String, DAI_RegistrySpec> pending = Map.of();
    private static Map<String, DAI_RegistrySpec> removed = Map.of();
    private static Map<String, DAI_RegistrySpec> registered = Map.of();

    private static boolean restartRequired;
    private static boolean evaluated;

    private DAI_RegistryPreflight() {}

    public static synchronized void evaluate() {
        LinkedHashMap<String, DAI_RegistrySpec> desiredNow = new LinkedHashMap<>();
        LinkedHashMap<String, DAI_RegistrySpec> pendingNow = new LinkedHashMap<>();
        LinkedHashMap<String, DAI_RegistrySpec> removedNow = new LinkedHashMap<>();
        LinkedHashMap<String, DAI_RegistrySpec> registeredNow = new LinkedHashMap<>();

        Map<String, DAI_RegistrySpec> previousDesired = desired;
        Map<String, DAI_RegistrySpec> previousRegistered = registered;
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

        /*
         * A native id cannot be physically unregistered after Minecraft's
         * static registries freeze. On the first evaluation there is no
         * previous world-live state to compare against. On subsequent
         * /reload passes, however, a previously active native definition that
         * disappeared is a real removal and therefore restart-bound.
         *
         * Runtime behavior is still disabled immediately because the content
         * registry no longer contains the definition; this flag merely tells
         * the user that the native shell remains until the next JVM launch.
         */
        if (evaluated) {
            for (Map.Entry<String, DAI_RegistrySpec> previous : previousDesired.entrySet()) {
                if (desiredNow.containsKey(previous.getKey())) continue;
                if (!previousRegistered.containsKey(previous.getKey())) continue;
                removedNow.put(previous.getKey(), previous.getValue());
            }
        }

        desired = Collections.unmodifiableMap(desiredNow);
        pending = Collections.unmodifiableMap(pendingNow);
        removed = Collections.unmodifiableMap(removedNow);
        registered = Collections.unmodifiableMap(registeredNow);
        restartRequired = !pending.isEmpty() || !removed.isEmpty();
        evaluated = true;

        DAI_RegistryCache.merge(desired.values());
        DAI_RegistryWorldStore.refreshCurrentWorld();

        if (!pending.isEmpty()) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Registry preflight found {} native content definition(s) whose registry shell changed after startup. They are staged for the next launch.",
                    pending.size()
            );
            for (DAI_RegistrySpec spec : pending.values()) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: Pending native {} '{}'.",
                        spec.nativeRegistry().name().toLowerCase(),
                        spec.id()
                );
            }
        }

        if (!removed.isEmpty()) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Registry preflight found {} native content id(s) removed by hot reload. Their DAI behavior is disabled now, but the native registry shells remain until restart.",
                    removed.size()
            );
            for (DAI_RegistrySpec spec : removed.values()) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: Removed native {} '{}' is restart-bound.",
                        spec.nativeRegistry().name().toLowerCase(),
                        spec.id()
                );
            }
        }

        if (!restartRequired && !desired.isEmpty()) {
            DAI_Core.LOGGER.info(
                    "<DAI>: Registry preflight passed: {} native DAI content id(s) available; runtime definitions are hot-reloadable.",
                    registered.size()
            );
        }
    }

    /** Clears world-session reload history without touching JVM registry shells. */
    public static synchronized void resetSession() {
        desired = Map.of();
        pending = Map.of();
        removed = Map.of();
        registered = Map.of();
        restartRequired = false;
        evaluated = false;
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
        if (spec == null || !registered.containsKey(spec.key())) {
            return "";
        }

        /*
         * Only ITEM and BLOCK native registrations own an Item registry entry.
         * EFFECT/POTION/PARTICLE/ENTITY are valid native DAI content, but they
         * cannot be handed to the give-item mutation path.
         */
        if (
                spec.nativeRegistry() != DAI_RegistrySpec.NativeRegistry.ITEM
                        && spec.nativeRegistry() != DAI_RegistrySpec.NativeRegistry.BLOCK
        ) {
            return "";
        }

        return spec.id();
    }

    public static Map<String, DAI_RegistrySpec> desiredSpecs() {
        return desired;
    }

    public static Map<String, DAI_RegistrySpec> pendingSpecs() {
        return pending;
    }

    public static Map<String, DAI_RegistrySpec> removedSpecs() {
        return removed;
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
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.containsKey(id);
            case EFFECT -> BuiltInRegistries.MOB_EFFECT.containsKey(id);
            case POTION -> BuiltInRegistries.POTION.containsKey(id);
            case PARTICLE -> BuiltInRegistries.PARTICLE_TYPE.containsKey(id);
        };
    }
}
