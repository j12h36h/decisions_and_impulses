package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.api.DAI_EntityTargetResolver;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.content.DAI_ContentRuntime;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.network.DAI_ServerMutationPayload;
import io.github.j12h36h.dai.registry.DAI_RegistryPreflight;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class DAI_ContentLogic {

    private DAI_ContentLogic() {}

    /** action=content id, target=entity reference, ticks=duration override, slot=amplifier. */
    public static void activate(DAI_ActionDefinition action) {
        Entity target = DAI_EntityTargetResolver.resolve(action.target());
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(action.action());
        if (target == null || entry == null || !DAI_RegistryPreflight.isUsable(action.action())) {
            finish(false);
            return;
        }

        boolean success = DAI_ContentRuntime.activate(
                target,
                action.action(),
                action.ticks(),
                action.slot()
        );

        finish(success);
    }

    public static void deactivate(DAI_ActionDefinition action) {
        Entity target = DAI_EntityTargetResolver.resolve(action.target());
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(action.action());
        if (target == null || entry == null || !DAI_RegistryPreflight.isUsable(action.action())) {
            finish(false);
            return;
        }
        finish(DAI_ContentRuntime.deactivate(target, action.action()));
    }

    /** direction=event name (use, attack, equip, consume, impact, custom...). */
    public static void emitEvent(DAI_ActionDefinition action) {
        Entity target = DAI_EntityTargetResolver.resolve(action.target());
        if (!DAI_RegistryPreflight.isUsable(action.action())) {
            finish(false);
            return;
        }
        finish(DAI_ContentRuntime.emit(target, action.action(), action.direction()));
    }

    /**
     * Gives either the true registry-backed item id or the legacy carrier.
     * Registry-backed content deliberately fails while a restart is pending.
     */
    public static void giveCarrier(DAI_ActionDefinition action) {
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(action.action());
        if (entry == null) {
            finish(false);
            return;
        }

        String itemId = DAI_RegistryPreflight.itemId(entry);
        if (itemId == null || itemId.isBlank()) {
            finish(false);
            return;
        }

        int count = action.slot() > 0 ? action.slot() : 1;
        ClientPacketDistributor.sendToServer(new DAI_ServerMutationPayload(
                "content_give_carrier",
                -1,
                itemId,
                entry.definition().displayName(),
                action.action(),
                count,
                false,
                0,
                0
        ));
        finish(true);
    }

    private static void finish(boolean success) {
        DAI_ActionStatus.set(success ? DAI_ActionResult.SUCCESS : DAI_ActionResult.FAILURE);
    }
}
