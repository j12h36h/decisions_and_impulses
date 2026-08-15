package io.github.j12h36h.dai.client.logics.input;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.KeyMapping;

public final class DAI_KeyInput {

    private static final String PICK_BLOCK =
            "pick_block";

    private DAI_KeyInput() {
        // Utility class.
    }

    public static void press(
            String key
    ) {

        set(
                key,
                true
        );
    }

    public static void release(
            String key
    ) {

        set(
                key,
                false
        );
    }

    public static void click(
            String key
    ) {

        KeyMapping mapping =
                get(
                        key
                );

        if (mapping == null) {
            return;
        }

        KeyMapping.click(
                mapping.getKey()
        );

        DAI_Core.debug(
                "<DAI>: Clicking key '{}'.",
                key
        );
    }

    public static void toggle(
            String key
    ) {

        KeyMapping mapping =
                get(
                        key
                );

        if (mapping == null) {
            return;
        }

        boolean down =
                !mapping.isDown();

        mapping.setDown(
                down
        );

        DAI_Core.debug(
                "<DAI>: Setting key '{}' to {}.",
                key,
                down
                        ? "DOWN"
                        : "UP"
        );
    }

    /*
     * Typed key actions
     */

    public static void pickBlock() {

        click(
                PICK_BLOCK
        );
    }

    private static void set(
            String key,
            boolean down
    ) {

        KeyMapping mapping =
                get(
                        key
                );

        if (mapping == null) {
            return;
        }

        mapping.setDown(
                down
        );

        DAI_Core.debug(
                "<DAI>: Setting key '{}' to {}.",
                key,
                down
                        ? "DOWN"
                        : "UP"
        );
    }

    private static KeyMapping get(
            String key
    ) {

        if (
                key == null
                        || key.isBlank()
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot resolve an empty key."
            );

            return null;
        }

        String normalizedKey =
                key.trim();

        KeyMapping mapping =
                DAI_KeyMappings.get(
                        normalizedKey
                );

        if (mapping == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Unknown key '{}'.",
                    normalizedKey
            );
        }

        return mapping;
    }
}