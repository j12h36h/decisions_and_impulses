package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.client.api.DAI_EntityTargetResolver;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.network.DAI_ServerMutationPayload;
import net.minecraft.world.entity.Entity;
import io.github.j12h36h.dai.client.network.DAI_ServerBridge;

public final class DAI_StatusLogic {

    private DAI_StatusLogic() {}

    public static void setHealth(DAI_ActionDefinition action) { send(action, "status_set_health"); }
    public static void heal(DAI_ActionDefinition action) { send(action, "status_heal"); }
    public static void damage(DAI_ActionDefinition action) { send(action, "status_damage"); }
    public static void setAbsorption(DAI_ActionDefinition action) { send(action, "status_set_absorption"); }
    public static void setFood(DAI_ActionDefinition action) { send(action, "status_set_food"); }
    public static void setAir(DAI_ActionDefinition action) { send(action, "status_set_air"); }
    public static void setFireTicks(DAI_ActionDefinition action) { send(action, "status_set_fire_ticks"); }

    private static void send(DAI_ActionDefinition action, String operation) {
        Entity target = DAI_EntityTargetResolver.resolve(action == null ? "" : action.target());
        if (target == null) {
            DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
            return;
        }
        boolean sent = DAI_ServerBridge.send(new DAI_ServerMutationPayload(
                operation,
                target.getId(),
                "",
                "",
                "",
                action.value(),
                false,
                action.ticks(),
                action.slot()
        ));
        DAI_ActionStatus.set(sent ? DAI_ActionResult.SUCCESS : DAI_ActionResult.FAILURE);
    }
}
