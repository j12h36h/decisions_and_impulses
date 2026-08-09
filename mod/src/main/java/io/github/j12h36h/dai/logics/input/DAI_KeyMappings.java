package io.github.j12h36h.dai.logics.input;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
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
    }

    private DAI_KeyMappings() {
        // Utility class.
    }

    public static KeyMapping get(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Supplier<KeyMapping> supplier = MAPPINGS.get(id.trim());
        return supplier == null ? null : supplier.get();
    }

    private static void register(String id, Supplier<KeyMapping> supplier) {
        MAPPINGS.put(id, supplier);
    }
}
