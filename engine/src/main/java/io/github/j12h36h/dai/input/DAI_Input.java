package io.github.j12h36h.dai.input;

import io.github.j12h36h.dai.action.DAI_ActionMaps;
import io.github.j12h36h.dai.core.DAI;
import net.minecraft.client.KeyMapping;

public final class DAI_Input {

    private DAI_Input() {
    }

    public static void pressKey(String key) {

        switch (key) {

            case "inventory" ->
                    Input_Manager.action().inventory(true);

            default ->
                    DAI.LOGGER.warn("<DAI>: Unknown key '{}'", key);
        }
    }
}
