package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.client.network.DAI_ServerBridge;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.network.DAI_ServerActionPayload;

/** Client stubs for server-authoritative JSON actions. */
public final class DAI_ServerActionLogic {

    private DAI_ServerActionLogic() {}

    public static void runFunction(DAI_ActionDefinition action) {
        send(action, "function");
    }

    public static void setBlock(DAI_ActionDefinition action) {
        send(action, "set_block");
    }

    public static void breakBlock(DAI_ActionDefinition action) {
        send(action, "break_block");
    }

    public static void giveItem(DAI_ActionDefinition action) {
        send(action, "give_item");
    }

    public static void takeItem(DAI_ActionDefinition action) {
        send(action, "take_item");
    }

    public static void spawnProjectile(DAI_ActionDefinition action) {
        send(action, "projectile_spawn");
    }


    public static void emitParticle(DAI_ActionDefinition action) { send(action, "particle_emit"); }

    public static void applyEffect(DAI_ActionDefinition action) {
        if (action == null) { fail("Server effect action requires an action definition."); return; }
        boolean sent = DAI_ServerBridge.send(new DAI_ServerActionPayload(
                "effect_apply", action.action(), Integer.toString(action.ticks()), Boolean.toString(action.state()), action.value()));
        DAI_ActionStatus.set(sent ? DAI_ActionResult.SUCCESS : DAI_ActionResult.FAILURE);
    }

    public static void removeEffect(DAI_ActionDefinition action) { send(action, "effect_remove"); }
    public static void applyPotion(DAI_ActionDefinition action) { send(action, "potion_apply"); }

    public static void markExperienceStarted(DAI_ActionDefinition action) {
        send(action, "experience_startup_dispatched");
    }

    private static void send(DAI_ActionDefinition action, String operation) {
        if (action == null) {
            fail("Server action requires an action definition.");
            return;
        }

        boolean sent = DAI_ServerBridge.send(new DAI_ServerActionPayload(
                operation,
                action.action(),
                action.target(),
                Boolean.toString(action.state()),
                action.value()
        ));

        DAI_ActionStatus.set(sent ? DAI_ActionResult.SUCCESS : DAI_ActionResult.FAILURE);

        if (!sent) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Action '{}' requires a DAI-enabled server; client-only automation remains available.",
                    operation
            );
        }
    }

    private static void fail(String reason) {
        DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
        DAI_Core.LOGGER.warn("<DAI>: {}", reason);
    }
}
