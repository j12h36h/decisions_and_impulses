package io.github.j12h36h.dai.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Privileged in-game Creator operation. Server remains authoritative. */
public record DAI_CreatorActionPayload(
        String operation,
        String kind,
        String id,
        String key,
        String value,
        double x,
        double y,
        double z
) implements CustomPacketPayload {

    public static final Type<DAI_CreatorActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("decisions_and_impulses", "creator_action"));

    public static final StreamCodec<ByteBuf, DAI_CreatorActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(DAI_CreatorActionPayload::encode, DAI_CreatorActionPayload::new);

    private DAI_CreatorActionPayload(ByteBuf buffer) {
        this(
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.DOUBLE.decode(buffer),
                ByteBufCodecs.DOUBLE.decode(buffer),
                ByteBufCodecs.DOUBLE.decode(buffer)
        );
    }

    private void encode(ByteBuf buffer) {
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(operation));
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(kind));
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(id));
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(key));
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(value));
        ByteBufCodecs.DOUBLE.encode(buffer, x);
        ByteBufCodecs.DOUBLE.encode(buffer, y);
        ByteBufCodecs.DOUBLE.encode(buffer, z);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
