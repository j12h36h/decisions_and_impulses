package io.github.j12h36h.dai.client.screens.data;

import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import net.minecraft.client.Minecraft;

public final class DAI_DataScreenLogic {
    private DAI_DataScreenLogic() {}
    public static void open(DAI_ActionDefinition action) {
        String id = action == null ? "" : action.action();
        DAI_DataScreenDefinition definition = DAI_DataScreenRegistry.get(id);
        if (definition == null) {
            DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
            return;
        }
        Minecraft.getInstance().gui.setScreen(new DAI_DataScreen(id, definition));
        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
    }
}
