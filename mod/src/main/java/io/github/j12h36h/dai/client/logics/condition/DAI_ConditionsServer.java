package io.github.j12h36h.dai.client.logics.condition;

import io.github.j12h36h.dai.client.network.DAI_ServerBridge;
import io.github.j12h36h.dai.network.DAI_ServerActionPayload;

/** Conditions describing whether the current connection negotiated DAI server authority. */
public final class DAI_ConditionsServer {

    private static final DAI_ServerActionPayload PROBE =
            new DAI_ServerActionPayload("probe", "", "", "", 0.0D);

    private DAI_ConditionsServer() {}

    public static void registerAll() {
        DAI_ConditionProvider provider = (context, condition) ->
                DAI_ConditionValue.bool(DAI_ServerBridge.available(PROBE));

        DAI_ConditionRegistry.register("dai_server_available", provider);
        DAI_ConditionRegistry.register("server_dai_available", provider);
        DAI_ConditionRegistry.register("server_authority_available", provider);
    }
}
