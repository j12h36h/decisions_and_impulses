package io.github.j12h36h.dai.client.logics.input;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public final class DAI_KeyMappings {

    private static final Map<String, Supplier<KeyMapping>> MAPPINGS =
            new HashMap<>();

    static {
        register("attack", () -> Minecraft.getInstance().options.keyAttack);
        register("use", () -> Minecraft.getInstance().options.keyUse);
        register("forward", () -> Minecraft.getInstance().options.keyUp);
        register("backward", () -> Minecraft.getInstance().options.keyDown);
        register("left", () -> Minecraft.getInstance().options.keyLeft);
        register("right", () -> Minecraft.getInstance().options.keyRight);
        register("jump", () -> Minecraft.getInstance().options.keyJump);
        register("sneak", () -> Minecraft.getInstance().options.keyShift);
        register("sprint", () -> Minecraft.getInstance().options.keySprint);
        register("drop", () -> Minecraft.getInstance().options.keyDrop);
        register("inventory", () -> Minecraft.getInstance().options.keyInventory);
        register("swap_offhand", () -> Minecraft.getInstance().options.keySwapOffhand);
        register("pick_block", () -> Minecraft.getInstance().options.keyPickItem);
        register("chat", () -> Minecraft.getInstance().options.keyChat);
        register("player_list", () -> Minecraft.getInstance().options.keyPlayerList);
        register("command", () -> Minecraft.getInstance().options.keyCommand);
        register("screenshot", () -> Minecraft.getInstance().options.keyScreenshot);
        register("toggle_perspective", () -> Minecraft.getInstance().options.keyTogglePerspective);
        register("smooth_camera", () -> Minecraft.getInstance().options.keySmoothCamera);
        register("fullscreen", () -> Minecraft.getInstance().options.keyFullscreen);
        register("spectator_outlines", () -> Minecraft.getInstance().options.keySpectatorOutlines);
        register("save_toolbar", () -> Minecraft.getInstance().options.keySaveHotbarActivator);
        register("load_toolbar", () -> Minecraft.getInstance().options.keyLoadHotbarActivator);
        register("advancements", () -> Minecraft.getInstance().options.keyAdvancements);
        register("hotbar_1", () -> Minecraft.getInstance().options.keyHotbarSlots[0]);
        register("hotbar_2", () -> Minecraft.getInstance().options.keyHotbarSlots[1]);
        register("hotbar_3", () -> Minecraft.getInstance().options.keyHotbarSlots[2]);
        register("hotbar_4", () -> Minecraft.getInstance().options.keyHotbarSlots[3]);
        register("hotbar_5", () -> Minecraft.getInstance().options.keyHotbarSlots[4]);
        register("hotbar_6", () -> Minecraft.getInstance().options.keyHotbarSlots[5]);
        register("hotbar_7", () -> Minecraft.getInstance().options.keyHotbarSlots[6]);
        register("hotbar_8", () -> Minecraft.getInstance().options.keyHotbarSlots[7]);
        register("hotbar_9", () -> Minecraft.getInstance().options.keyHotbarSlots[8]);
    }

    private DAI_KeyMappings() {
        // Utility class.
    }

    /**
     * Resolves either a DAI shorthand alias (for example {@code jump}) or any
     * key mapping registered with Minecraft/NeoForge by its translation id.
     *
     * Custom mappings may be referenced directly, for example
     * {@code key.examplemod.special}, or with the convenience form
     * {@code examplemod:special}.
     */
    public static KeyMapping get(String id) {

        String normalized = normalize(id);
        if (normalized.isEmpty()) {
            return null;
        }

        Supplier<KeyMapping> supplier = MAPPINGS.get(normalized);
        if (supplier != null) {
            KeyMapping mapping = safeGet(supplier);
            if (mapping != null) {
                return mapping;
            }
        }

        KeyMapping mapping = findRegistered(normalized);
        if (mapping != null) {
            return mapping;
        }

        String translationId = toTranslationId(normalized);
        if (!translationId.equals(normalized)) {
            return findRegistered(translationId);
        }

        return null;
    }

    public static String canonicalId(KeyMapping mapping) {
        if (mapping == null || mapping.getName() == null) {
            return "";
        }
        return normalize(mapping.getName());
    }

    private static KeyMapping findRegistered(String id) {

        Minecraft minecraft = Minecraft.getInstance();
        if (
                minecraft == null
                        || minecraft.options == null
                        || minecraft.options.keyMappings == null
        ) {
            return null;
        }

        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (
                    mapping != null
                            && canonicalId(mapping).equals(id)
            ) {
                return mapping;
            }
        }

        return null;
    }

    private static String toTranslationId(String id) {

        if (id.startsWith("key.")) {
            return id;
        }

        int separator = id.indexOf(':');
        if (separator <= 0 || separator >= id.length() - 1) {
            return id;
        }

        String namespace = id.substring(0, separator);
        String path = id.substring(separator + 1)
                .replace('/', '.')
                .replace(':', '.');

        return "key." + namespace + "." + path;
    }

    private static KeyMapping safeGet(Supplier<KeyMapping> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String normalize(String id) {
        return id == null
                ? ""
                : id.trim().toLowerCase(Locale.ROOT);
    }

    private static void register(String id, Supplier<KeyMapping> supplier) {
        MAPPINGS.put(normalize(id), supplier);
    }
}
