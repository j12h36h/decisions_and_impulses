package io.github.j12h36h.dai.client.title;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Resolves title-screen-safe icon textures without constructing ItemStacks.
 */
public final class DAI_TitleIconTextures {

    private DAI_TitleIconTextures() {}

    public static Identifier resolve(String type, String rawId) {
        if (rawId == null || rawId.isBlank()) return null;

        Identifier authored = Identifier.tryParse(rawId);
        if (authored == null) return null;

        String normalizedType = type == null ? "none" : type.trim().toLowerCase();
        String path = authored.getPath();

        Identifier resolved = switch (normalizedType) {
            case "item" -> Identifier.fromNamespaceAndPath(
                    authored.getNamespace(),
                    "textures/item/" + stripTextureWrapper(path) + ".png"
            );
            case "block" -> Identifier.fromNamespaceAndPath(
                    authored.getNamespace(),
                    "textures/block/" + stripTextureWrapper(path) + ".png"
            );
            case "texture" -> Identifier.fromNamespaceAndPath(
                    authored.getNamespace(),
                    normalizeTexturePath(path)
            );
            default -> null;
        };

        return resourceExists(resolved) ? resolved : null;
    }

    public static Identifier item(String rawId) {
        return resolve("item", rawId);
    }

    private static boolean resourceExists(Identifier identifier) {
        if (identifier == null) return false;

        try {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft != null
                    && minecraft.getResourceManager() != null
                    && minecraft.getResourceManager().getResource(identifier).isPresent();
        } catch (Throwable ignored) {
            // Title/presentation rendering must never surface Minecraft's
            // missing-texture checkerboard because a pack referenced a bad
            // icon. Treat unavailable resources as no icon instead.
            return false;
        }
    }

    private static String stripTextureWrapper(String path) {
        String normalized = path == null ? "" : path.trim();
        if (normalized.startsWith("textures/item/")) {
            normalized = normalized.substring("textures/item/".length());
        } else if (normalized.startsWith("textures/block/")) {
            normalized = normalized.substring("textures/block/".length());
        } else if (normalized.startsWith("item/")) {
            normalized = normalized.substring("item/".length());
        } else if (normalized.startsWith("block/")) {
            normalized = normalized.substring("block/".length());
        } else if (normalized.startsWith("textures/")) {
            normalized = normalized.substring("textures/".length());
        }

        if (normalized.endsWith(".png")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    private static String normalizeTexturePath(String path) {
        String normalized = path == null ? "" : path.trim();
        if (!normalized.startsWith("textures/")) {
            normalized = "textures/" + normalized;
        }
        if (!normalized.endsWith(".png")) {
            normalized += ".png";
        }
        return normalized;
    }
}
