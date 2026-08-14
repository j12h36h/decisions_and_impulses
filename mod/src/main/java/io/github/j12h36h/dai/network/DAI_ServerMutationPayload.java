package io.github.j12h36h.dai.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DAI_ServerMutationPayload(
        String operation,
        int targetEntityId,
        String attribute,
        String modifier,
        String modifierOperation,
        double value,
        boolean persistent,
        int ticks,
        int amplifier
) implements CustomPacketPayload {

    public static final Type<DAI_ServerMutationPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    "decisions_and_impulses",
                    "server_mutation"
            ));

    public static final StreamCodec<ByteBuf, DAI_ServerMutationPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    DAI_ServerMutationPayload::encode,
                    DAI_ServerMutationPayload::new
            );

    private DAI_ServerMutationPayload(ByteBuf buffer) {
        this(
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.DOUBLE.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer)
        );
    }

    private void encode(ByteBuf buffer) {
        ByteBufCodecs.STRING_UTF8.encode(buffer, operation == null ? "" : operation);
        ByteBufCodecs.VAR_INT.encode(buffer, targetEntityId);
        ByteBufCodecs.STRING_UTF8.encode(buffer, attribute == null ? "" : attribute);
        ByteBufCodecs.STRING_UTF8.encode(buffer, modifier == null ? "" : modifier);
        ByteBufCodecs.STRING_UTF8.encode(buffer, modifierOperation == null ? "" : modifierOperation);
        ByteBufCodecs.DOUBLE.encode(buffer, value);
        ByteBufCodecs.BOOL.encode(buffer, persistent);
        ByteBufCodecs.VAR_INT.encode(buffer, ticks);
        ByteBufCodecs.VAR_INT.encode(buffer, amplifier);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
