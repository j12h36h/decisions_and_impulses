package io.github.j12h36h.dai.input;

import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.client.KeyMapping;

public final class DAI_KeyController {

    private DAI_KeyController() {
        // Utility class.
    }

    public static void press(String key) {
        set(key, true);
    }

    public static void release(String key) {
        set(key, false);
    }

    public static void toggle(String key) {
        KeyMapping mapping = get(key);
        if (mapping == null) {
            return;
        }
        boolean down = !mapping.isDown();
        mapping.setDown(down);
        DAI_Core.LOGGER.debug("<DAI>: Setting key '{}' to {}.", key, down ? "DOWN" : "UP");
    }

    private static void set(String key, boolean down) {
        KeyMapping mapping = get(key);
        if (mapping == null) {
            return;
        }
        mapping.setDown(down);
        DAI_Core.LOGGER.debug("<DAI>: Setting key '{}' to {}.", key, down ? "DOWN" : "UP");
    }

    private static KeyMapping get(String key) {
        KeyMapping mapping = DAI_KeyMappings.get(key);
        if (mapping == null) {
            DAI_Core.LOGGER.warn("<DAI>: Unknown key '{}'.", key);
        }
        return mapping;
    }
}
