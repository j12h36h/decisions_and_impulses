package io.github.j12h36h.dai.client.menus;

import io.github.j12h36h.dai.client.menus.system.DAI_SystemDefinition;
import io.github.j12h36h.dai.client.menus.system.DAI_SystemManager;

/** Public facade for menu definitions. */
public final class DAI_Menus {

    private DAI_Menus() {
        // Utility class.
    }

    public static DAI_SystemDefinition get(
            DAI_MenuCategory category,
            String id
    ) {
        return DAI_SystemManager.get(category, id);
    }

    public static boolean contains(
            DAI_MenuCategory category,
            String id
    ) {
        return DAI_SystemManager.contains(category, id);
    }
}
