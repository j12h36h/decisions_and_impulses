package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.api.DAI_EntityTargetResolver;
import io.github.j12h36h.dai.attributes.DAI_AttributeDefinition;
import io.github.j12h36h.dai.attributes.DAI_AttributeModifier;
import io.github.j12h36h.dai.attributes.DAI_AttributeRegistry;
import io.github.j12h36h.dai.attributes.DAI_AttributeStore;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.network.DAI_ServerMutationPayload;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class DAI_AttributeLogic {

    private DAI_AttributeLogic() {}

    public static void set(DAI_ActionDefinition action) {
        Entity target = target(action);
        boolean success = DAI_AttributeStore.set(target, action.action(), action.value());
        finish(success);
        if (success) applyBinding(action.action(), target);
    }

    public static void add(DAI_ActionDefinition action) {
        Entity target = target(action);
        boolean success = DAI_AttributeStore.add(target, action.action(), action.value());
        finish(success);
        if (success) applyBinding(action.action(), target);
    }

    public static void reset(DAI_ActionDefinition action) {
        Entity target = target(action);
        boolean success = DAI_AttributeStore.reset(target, action.action());
        finish(success);
        if (success) applyBinding(action.action(), target);
    }

    /** direction=modifier id, open=operation, slot=priority. */
    public static void addModifier(DAI_ActionDefinition action) {
        Entity target = target(action);
        boolean success = DAI_AttributeStore.addModifier(
                target,
                action.action(),
                action.direction(),
                action.value(),
                DAI_AttributeModifier.Operation.parse(action.open()),
                action.slot()
        );
        finish(success);
        if (success) applyBinding(action.action(), target);
    }

    /** direction=modifier id. */
    public static void removeModifier(DAI_ActionDefinition action) {
        Entity target = target(action);
        boolean success = DAI_AttributeStore.removeModifier(
                target,
                action.action(),
                action.direction()
        );
        finish(success);
        if (success) applyBinding(action.action(), target);
    }

    public static void nativeSet(DAI_ActionDefinition action) {
        sendNative(action, "native_attribute_set");
    }

    /** direction=modifier id, open=operation, state=persistent. */
    public static void nativeAddModifier(DAI_ActionDefinition action) {
        sendNative(action, "native_attribute_modifier_add");
    }

    /** direction=modifier id. */
    public static void nativeRemoveModifier(DAI_ActionDefinition action) {
        sendNative(action, "native_attribute_modifier_remove");
    }

    private static void sendNative(DAI_ActionDefinition action, String operation) {
        Entity target = target(action);
        if (target == null) {
            finish(false);
            return;
        }
        ClientPacketDistributor.sendToServer(new DAI_ServerMutationPayload(
                operation,
                target.getId(),
                action.action(),
                action.direction(),
                action.open(),
                action.value(),
                action.state(),
                action.ticks(),
                action.slot()
        ));
        finish(true);
    }

    private static void applyBinding(String attributeId, Entity target) {
        DAI_AttributeDefinition definition = DAI_AttributeRegistry.get(attributeId);
        if (definition == null || definition.nativeBinding().isBlank() || target == null) return;
        double value = DAI_AttributeStore.value(target, attributeId);
        if (!Double.isFinite(value)) return;
        ClientPacketDistributor.sendToServer(new DAI_ServerMutationPayload(
                "native_attribute_set",
                target.getId(),
                definition.nativeBinding(),
                "",
                "",
                value,
                false,
                0,
                0
        ));
    }

    private static Entity target(DAI_ActionDefinition action) {
        return DAI_EntityTargetResolver.resolve(action == null ? "" : action.target());
    }

    private static void finish(boolean success) {
        DAI_ActionStatus.set(success ? DAI_ActionResult.SUCCESS : DAI_ActionResult.FAILURE);
    }
}
