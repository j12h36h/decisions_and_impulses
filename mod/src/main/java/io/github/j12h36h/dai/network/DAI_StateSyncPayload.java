package io.github.j12h36h.dai.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client mirror of one visible declared state value and its schema. */
public record DAI_StateSyncPayload(
        String key,
        String valueType,
        String scope,
        boolean defaultBoolean,
        double defaultNumber,
        String defaultString,
        boolean persistent,
        boolean sync,
        boolean clientWritable,
        boolean present,
        boolean booleanValue,
        double numberValue,
        String stringValue
) implements CustomPacketPayload {
    public static final Type<DAI_StateSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("decisions_and_impulses", "state_sync")
    );

    public static final StreamCodec<ByteBuf, DAI_StateSyncPayload> STREAM_CODEC = StreamCodec.ofMember(
            DAI_StateSyncPayload::encode, DAI_StateSyncPayload::new
    );

    private DAI_StateSyncPayload(ByteBuf buffer) {
        this(
                ByteBufCodecs.STRING_UTF8.decode(buffer), ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer), ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.DOUBLE.decode(buffer), ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer), ByteBufCodecs.BOOL.decode(buffer), ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer), ByteBufCodecs.BOOL.decode(buffer), ByteBufCodecs.DOUBLE.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer)
        );
    }

    private void encode(ByteBuf buffer) {
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(key));
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(valueType));
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(scope));
        ByteBufCodecs.BOOL.encode(buffer, defaultBoolean);
        ByteBufCodecs.DOUBLE.encode(buffer, defaultNumber);
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(defaultString));
        ByteBufCodecs.BOOL.encode(buffer, persistent);
        ByteBufCodecs.BOOL.encode(buffer, sync);
        ByteBufCodecs.BOOL.encode(buffer, clientWritable);
        ByteBufCodecs.BOOL.encode(buffer, present);
        ByteBufCodecs.BOOL.encode(buffer, booleanValue);
        ByteBufCodecs.DOUBLE.encode(buffer, numberValue);
        ByteBufCodecs.STRING_UTF8.encode(buffer, safe(stringValue));
    }

    private static String safe(String value) { return value == null ? "" : value; }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
