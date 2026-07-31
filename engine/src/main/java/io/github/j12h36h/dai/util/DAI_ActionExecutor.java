package io.github.j12h36h.dai.util;

import io.github.j12h36h.dai.core.DAI;
import io.github.j12h36h.dai.ui.DAI_ScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.util.HashMap;
import java.util.Map;

public final class DAI_ActionExecutor {

    private static final Map<String, Runnable> ACTIONS = new HashMap<>();

    static {

        // ---------- Minecraft ----------

        register("minecraft:pause_menu", () -> {
            Minecraft minecraft = Minecraft.getInstance();

            DAI_ScreenManager.open(
                    new PauseScreen(true)
            );
        });

        register("minecraft:inventory", () -> {
            Minecraft minecraft = Minecraft.getInstance();

            DAI_ScreenManager.push(minecraft.gui.screen());

            DAI_ScreenManager.open(
                    new InventoryScreen(minecraft.player)
            );
        });

        // ---------- DAI ----------

        register("decisions_and_impulses:social", () -> {
            // TODO
        });
    }

    private DAI_ActionExecutor() {
    }

    public static void register(String id, Runnable action) {
        ACTIONS.put(id, action);
    }

    public static void execute(String id) {

        DAI.LOGGER.info("<DAI>: Executing action '{}'", id);

        Runnable action = ACTIONS.get(id);

        if (action == null) {
            DAI.LOGGER.warn("<DAI>: Unknown action '{}'", id);
            return;
        }

        action.run();
    }
}