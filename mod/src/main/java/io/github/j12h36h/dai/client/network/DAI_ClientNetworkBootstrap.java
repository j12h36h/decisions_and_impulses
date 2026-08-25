package io.github.j12h36h.dai.client.network;

import io.github.j12h36h.dai.api.DAI_StateStore;
import io.github.j12h36h.dai.api.DAI_StateValue;
import io.github.j12h36h.dai.network.DAI_StateSyncPayload;
import io.github.j12h36h.dai.state.DAI_StateDefinition;
import io.github.j12h36h.dai.state.DAI_StateRegistry;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Physical-client payload registration kept out of the dedicated-server path. */
public final class DAI_ClientNetworkBootstrap {
    private DAI_ClientNetworkBootstrap() {}

    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(DAI_StateSyncPayload.TYPE, DAI_ClientNetworkBootstrap::handleStateSync);
    }

    private static void handleStateSync(DAI_StateSyncPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (payload == null) return;
        DAI_StateDefinition definition = new DAI_StateDefinition(
                payload.valueType(), payload.scope(), payload.defaultBoolean(), payload.defaultNumber(), payload.defaultString(),
                payload.persistent(), payload.sync(), payload.clientWritable()
        );
        DAI_StateRegistry.register(payload.key(), definition);
        if (!payload.present()) {
            DAI_StateStore.remove(payload.key());
            return;
        }
        DAI_StateValue value = switch (payload.valueType()) {
            case "number" -> DAI_StateValue.number(payload.numberValue());
            case "string" -> DAI_StateValue.string(payload.stringValue());
            default -> DAI_StateValue.bool(payload.booleanValue());
        };
        DAI_StateStore.set(payload.key(), value);
    }
}
