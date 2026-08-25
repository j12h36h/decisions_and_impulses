package io.github.j12h36h.dai.client.screens.data;

import io.github.j12h36h.dai.api.DAI_StateStore;
import io.github.j12h36h.dai.api.DAI_StateValue;
import io.github.j12h36h.dai.client.network.DAI_ServerBridge;
import io.github.j12h36h.dai.network.DAI_ServerActionPayload;
import io.github.j12h36h.dai.state.DAI_StateDefinition;
import io.github.j12h36h.dai.state.DAI_StateRegistry;

final class DAI_DataScreenState {
    private DAI_DataScreenState() {}

    static DAI_StateValue get(String key) {
        DAI_StateValue current = DAI_StateStore.get(key);
        if (!current.isMissing()) return current;
        DAI_StateDefinition definition = DAI_StateRegistry.get(key);
        return definition == null ? current : definition.defaultValue();
    }

    static void setBoolean(String key, boolean value) {
        if (sendServer(key, "boolean", Boolean.toString(value), value ? 1.0D : 0.0D)) return;
        DAI_StateStore.setBoolean(key, value);
    }

    static void setNumber(String key, double value) {
        if (sendServer(key, "number", "", value)) return;
        DAI_StateStore.setNumber(key, value);
    }

    static void setString(String key, String value) {
        if (sendServer(key, "string", value == null ? "" : value, 0.0D)) return;
        DAI_StateStore.setString(key, value);
    }

    private static boolean sendServer(String key, String type, String text, double number) {
        DAI_StateDefinition definition = DAI_StateRegistry.get(key);
        if (definition == null || !definition.serverOwned()) return false;
        if (!definition.clientWritable()) return true;
        DAI_ServerBridge.send(new DAI_ServerActionPayload("state_set", key, type, text, number));
        return true;
    }
}
