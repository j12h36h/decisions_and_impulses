package io.github.j12h36h.dai.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Optional client -> server request for DAI-owned authoritative operations.
 *
 * The payload intentionally mirrors the small generic action vocabulary used
 * by DAI_ActionDefinition so client automation can request a server capability
 * without importing or touching any logical-server implementation classes.
 */
public record DAI_ServerActionPayload(
        String operation,
        String action,
        String target,
        String state,
        double value
) implements CustomPacketPayload {

    public static final Type<DAI_ServerActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    "decisions_and_impulses",
                    "server_action"
            ));

    public static final StreamCodec<ByteBuf, DAI_ServerActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    DAI_ServerActionPayload::encode,
                    DAI_ServerActionPayload::new
            );

    private DAI_ServerActionPayload(ByteBuf buffer) {
        this(
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.DOUBLE.decode(buffer)
        );
    }

    private void encode(ByteBuf buffer) {
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(operation));
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(action));
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(target));
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(state));
        ByteBufCodecs.DOUBLE.encode(buffer, value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
