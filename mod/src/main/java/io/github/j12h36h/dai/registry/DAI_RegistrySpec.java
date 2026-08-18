package io.github.j12h36h.dai.registry;

import io.github.j12h36h.dai.content.DAI_ContentKind;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.entity.DAI_EntitySettings;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.Map;

/**
 * Startup-safe description of a DAI-backed native registry object.
 *
 * Values stored here are the subset that must be known before the static
 * Minecraft registries finish. Runtime behavior remains owned by the normal
 * reloadable DAI content definition.
 */
public record DAI_RegistrySpec(
        String id,
        NativeRegistry nativeRegistry,
        String contentKind,
        String displayName,
        String model,
        String carrier,
        int stackSize,
        int durability,
        String entityCategory,
        float entityWidth,
        float entityHeight,
        int entityTrackingRange,
        int entityUpdateInterval,
        boolean entityFireImmune,
        boolean entitySummonable,
        boolean entitySaveable,
        Map<String, Double> nativeAttributes,
        Map<String, String> nativeComponents
) {

    public enum NativeRegistry {
        ITEM,
        BLOCK,
        ENTITY;

        public static NativeRegistry parse(String value, DAI_ContentKind kind) {
            String normalized = value == null
                    ? ""
                    : value.trim().toLowerCase(Locale.ROOT);

            if (normalized.isBlank()) {
                if (kind == DAI_ContentKind.BLOCK) return BLOCK;
                if (kind == DAI_ContentKind.ENTITY) return ENTITY;
                return ITEM;
            }

            return switch (normalized) {
                case "item" -> ITEM;
                case "block" -> BLOCK;
                case "entity", "entity_type" -> ENTITY;
                default -> null;
            };
        }
    }

    public DAI_RegistrySpec {
        id = normalize(id);
        contentKind = normalize(contentKind);
        displayName = displayName == null ? "" : displayName.trim();
        model = normalize(model);
        carrier = normalize(carrier);
        stackSize = Math.max(1, Math.min(99, stackSize));
        durability = Math.max(0, durability);
        entityCategory = normalize(entityCategory);
        entityWidth = finiteClamp(entityWidth, 0.05F, 32.0F, 0.6F);
        entityHeight = finiteClamp(entityHeight, 0.05F, 32.0F, 1.0F);
        entityTrackingRange = Math.max(1, Math.min(64, entityTrackingRange));
        entityUpdateInterval = Math.max(1, Math.min(1200, entityUpdateInterval));
        nativeAttributes = nativeAttributes == null ? Map.of() : Map.copyOf(nativeAttributes);
        nativeComponents = nativeComponents == null ? Map.of() : Map.copyOf(nativeComponents);
    }

    public static DAI_RegistrySpec from(DAI_ContentRegistry.Entry entry) {
        if (entry == null || !entry.definition().registryBacked()) {
            return null;
        }

        NativeRegistry nativeRegistry =
                NativeRegistry.parse(
                        entry.definition().nativeRegistry(),
                        entry.kind()
                );

        if (nativeRegistry == null) {
            return null;
        }

        DAI_EntitySettings entity = entry.definition().entity();
        String model = entry.definition().model();
        if (model.isBlank()) model = entry.definition().carrier();

        String id = entry.id().toString();
        String key = nativeRegistry.name().toLowerCase(Locale.ROOT) + "|" + normalize(id);
        DAI_RegistrySpec boot = DAI_DynamicRegistryBootstrap.bootSpecs().get(key);
        Map<String, String> nativeComponents = boot == null
                ? Map.of()
                : boot.nativeComponents();

        return new DAI_RegistrySpec(
                id,
                nativeRegistry,
                entry.kind().id(),
                entry.definition().displayName(),
                model,
                entry.definition().carrier(),
                entry.definition().stats().stackSize(),
                entry.definition().stats().durability(),
                entity.category(),
                entity.width(),
                entity.height(),
                entity.trackingRange(),
                entity.updateInterval(),
                entity.fireImmune(),
                entity.summonable(),
                entity.saveable(),
                entry.definition().nativeAttributes(),
                nativeComponents
        );
    }

    public Identifier identifier() {
        return Identifier.tryParse(id);
    }

    public String key() {
        return nativeRegistry.name().toLowerCase(Locale.ROOT) + "|" + id;
    }

    public boolean sameStaticDefinition(DAI_RegistrySpec other) {
        if (other == null) return false;

        boolean common = id.equals(other.id)
                && nativeRegistry == other.nativeRegistry
                && contentKind.equals(other.contentKind)
                && displayName.equals(other.displayName)
                && model.equals(other.model)
                && carrier.equals(other.carrier)
                && stackSize == other.stackSize
                && durability == other.durability;

        if (!common) return false;

        /*
         * Entity registration metadata must only participate in static
         * equality for ENTITY_TYPE entries. Before entity support existed,
         * item/block JSON had no entity section at all. Comparing their
         * synthesized entity defaults made every already-registered item and
         * block look "changed" after reload, which incorrectly armed the
         * restart safety gate on Start Journey.
         */
        if (nativeRegistry != NativeRegistry.ENTITY) {
            return nativeComponents.equals(other.nativeComponents);
        }

        return entityCategory.equals(other.entityCategory)
                && Float.compare(entityWidth, other.entityWidth) == 0
                && Float.compare(entityHeight, other.entityHeight) == 0
                && entityTrackingRange == other.entityTrackingRange
                && entityUpdateInterval == other.entityUpdateInterval
                && entityFireImmune == other.entityFireImmune
                && entitySummonable == other.entitySummonable
                && entitySaveable == other.entitySaveable
                && nativeAttributes.equals(other.nativeAttributes);
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private static float finiteClamp(float value, float min, float max, float fallback) {
        if (!Float.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }
}
