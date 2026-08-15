package io.github.j12h36h.dai.client.network;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client-side capability bridge to an optional DAI server.
 *
 * DAI's player-simulation features must remain usable when the remote server
 * does not have DAI installed. Every DAI-specific payload therefore passes
 * through this negotiated-channel check before it is sent.
 */
public final class DAI_ServerBridge {

    private DAI_ServerBridge() {}

    public static boolean available(CustomPacketPayload payload) {
        if (payload == null) return false;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getConnection() == null) return false;

        try {
            return minecraft.getConnection().hasChannel(payload);
        } catch (RuntimeException exception) {
            DAI_Core.debug(
                    "<DAI>: Could not query optional server capability '{}'.",
                    payload.type(),
                    exception
            );
            return false;
        }
    }

    public static boolean send(CustomPacketPayload payload) {
        if (!available(payload)) {
            DAI_Core.debug(
                    "<DAI>: Server capability '{}' is not available on this connection.",
                    payload == null ? "unknown" : payload.type()
            );
            return false;
        }

        try {
            Minecraft.getInstance().getConnection().send(payload);
            return true;
        } catch (RuntimeException exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Failed to send optional server payload '{}'.",
                    payload.type(),
                    exception
            );
            return false;
        }
    }
}
