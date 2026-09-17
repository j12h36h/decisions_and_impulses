package io.github.j12h36h.dai.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client raw JSON entry for client-visible datapack presentation/reaction data. */
public record DAI_ClientDatapackSyncPayload(
        String phase,
        String kind,
        String id,
        String json
) implements CustomPacketPayload {
    public static final Type<DAI_ClientDatapackSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("decisions_and_impulses", "client_datapack_sync")
    );

    public static final StreamCodec<ByteBuf, DAI_ClientDatapackSyncPayload> STREAM_CODEC = StreamCodec.ofMember(
            DAI_ClientDatapackSyncPayload::encode,
            DAI_ClientDatapackSyncPayload::new
    );

    private DAI_ClientDatapackSyncPayload(ByteBuf buffer) {
        this(
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer)
        );
    }

    private void encode(ByteBuf buffer) {
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(phase));
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(kind));
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(id));
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(json));
    }

    private static String safe(String value) { return value == null ? "" : value; }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
