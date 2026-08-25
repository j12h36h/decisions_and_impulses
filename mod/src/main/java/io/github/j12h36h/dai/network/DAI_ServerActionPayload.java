package io.github.j12h36h.dai.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server request for DAI-owned authoritative operations. */
public record DAI_ServerActionPayload(
        String operation,
        String action,
        String target,
        String state,
        double value,
        String argumentsJson
) implements CustomPacketPayload {

    public static final Type<DAI_ServerActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("decisions_and_impulses", "server_action"));

    public static final StreamCodec<ByteBuf, DAI_ServerActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(DAI_ServerActionPayload::encode, DAI_ServerActionPayload::new);

    public DAI_ServerActionPayload(String operation, String action, String target, String state, double value) {
        this(operation, action, target, state, value, "{}");
    }

    private DAI_ServerActionPayload(ByteBuf buffer) {
        this(
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.DOUBLE.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer)
        );
    }

    private void encode(ByteBuf buffer) {
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(operation));
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(action));
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(target));
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(state));
        ByteBufCodecs.DOUBLE.encode(buffer, value);
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(argumentsJson).isBlank() ? "{}" : argumentsJson);
    }

    private static String safe(String value) { return value == null ? "" : value; }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
