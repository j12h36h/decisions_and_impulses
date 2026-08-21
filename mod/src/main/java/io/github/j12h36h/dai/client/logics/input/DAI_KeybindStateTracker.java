package io.github.j12h36h.dai.client.logics.input;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/**
 * Captures registered key-mapping edges once per gameplay tick so datapack
 * conditions can distinguish a held key from a newly pressed or released key.
 */
public final class DAI_KeybindStateTracker {

    private static final Map<String, Boolean> LAST_DOWN =
            new HashMap<>();

    private static final Map<String, Boolean> PRESSED_THIS_TICK =
            new HashMap<>();

    private static final Map<String, Boolean> RELEASED_THIS_TICK =
            new HashMap<>();

    private DAI_KeybindStateTracker() {
        // Utility class.
    }

    public static void tick() {

        Minecraft minecraft = Minecraft.getInstance();
        if (
                minecraft == null
                        || minecraft.options == null
                        || minecraft.options.keyMappings == null
        ) {
            reset();
            return;
        }

        Map<String, Boolean> nextDown =
                new HashMap<>();

        PRESSED_THIS_TICK.clear();
        RELEASED_THIS_TICK.clear();

        for (KeyMapping mapping : minecraft.options.keyMappings) {

            if (mapping == null) {
                continue;
            }

            String id =
                    DAI_KeyMappings.canonicalId(mapping);

            if (id.isEmpty()) {
                continue;
            }

            boolean down = mapping.isDown();
            boolean wasDown = LAST_DOWN.getOrDefault(id, false);

            nextDown.put(id, down);

            if (down && !wasDown) {
                PRESSED_THIS_TICK.put(id, true);
            }

            if (!down && wasDown) {
                RELEASED_THIS_TICK.put(id, true);
            }
        }

        LAST_DOWN.clear();
        LAST_DOWN.putAll(nextDown);
    }

    public static boolean isHeld(String id) {

        KeyMapping mapping =
                DAI_KeyMappings.get(id);

        return mapping != null
                && mapping.isDown();
    }

    public static boolean wasPressed(String id) {

        KeyMapping mapping =
                DAI_KeyMappings.get(id);

        if (mapping == null) {
            return false;
        }

        return PRESSED_THIS_TICK.getOrDefault(
                DAI_KeyMappings.canonicalId(mapping),
                false
        );
    }

    public static boolean wasReleased(String id) {

        KeyMapping mapping =
                DAI_KeyMappings.get(id);

        if (mapping == null) {
            return false;
        }

        return RELEASED_THIS_TICK.getOrDefault(
                DAI_KeyMappings.canonicalId(mapping),
                false
        );
    }

    public static boolean exists(String id) {
        return DAI_KeyMappings.get(id) != null;
    }

    public static void reset() {
        LAST_DOWN.clear();
        PRESSED_THIS_TICK.clear();
        RELEASED_THIS_TICK.clear();
    }
}
