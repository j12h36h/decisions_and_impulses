package io.github.j12h36h.dai.registry;

import io.github.j12h36h.dai.content.DAI_ContentKind;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Minimal startup-safe description of a DAI-backed native registry object.
 *
 * This record intentionally contains only values that must exist before a
 * world/datapack is selected. Runtime behavior remains owned by the normal
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
        int durability
) {

    public enum NativeRegistry {
        ITEM,
        BLOCK;

        public static NativeRegistry parse(String value, DAI_ContentKind kind) {
            String normalized = value == null
                    ? ""
                    : value.trim().toLowerCase(Locale.ROOT);

            if (normalized.isBlank()) {
                return kind == DAI_ContentKind.BLOCK
                        ? BLOCK
                        : ITEM;
            }

            return switch (normalized) {
                case "item" -> ITEM;
                case "block" -> BLOCK;
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

        return new DAI_RegistrySpec(
                entry.id().toString(),
                nativeRegistry,
                entry.kind().id(),
                entry.definition().displayName(),
                entry.definition().model(),
                entry.definition().carrier(),
                entry.definition().stats().stackSize(),
                entry.definition().stats().durability()
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
        return id.equals(other.id)
                && nativeRegistry == other.nativeRegistry
                && contentKind.equals(other.contentKind)
                && displayName.equals(other.displayName)
                && model.equals(other.model)
                && carrier.equals(other.carrier)
                && stackSize == other.stackSize
                && durability == other.durability;
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }
}
