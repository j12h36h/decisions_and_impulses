package io.github.j12h36h.dai.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Lightweight client -> server vehicle control state.
 *
 * DAI 3.3 DirtBikeLife extension: attack/use are included so authored
 * vehicles can bind throttle/brake/transmission behavior to the mouse while
 * preserving the existing movement-key vehicle and physics inputs.
 */
public record DAI_VehicleInputPayload(
        float forward,
        float strafe,
        boolean jump,
        boolean sneak,
        boolean sprint,
        boolean attack,
        boolean use,
        float yaw,
        float pitch
) implements CustomPacketPayload {

    public static final Type<DAI_VehicleInputPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    "decisions_and_impulses",
                    "vehicle_input"
            ));

    public static final StreamCodec<ByteBuf, DAI_VehicleInputPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    DAI_VehicleInputPayload::encode,
                    DAI_VehicleInputPayload::new
            );

    private DAI_VehicleInputPayload(ByteBuf buffer) {
        this(
                ByteBufCodecs.FLOAT.decode(buffer),
                ByteBufCodecs.FLOAT.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.FLOAT.decode(buffer),
                ByteBufCodecs.FLOAT.decode(buffer)
        );
    }

    private void encode(ByteBuf buffer) {
        ByteBufCodecs.FLOAT.encode(buffer, forward);
        ByteBufCodecs.FLOAT.encode(buffer, strafe);
        ByteBufCodecs.BOOL.encode(buffer, jump);
        ByteBufCodecs.BOOL.encode(buffer, sneak);
        ByteBufCodecs.BOOL.encode(buffer, sprint);
        ByteBufCodecs.BOOL.encode(buffer, attack);
        ByteBufCodecs.BOOL.encode(buffer, use);
        ByteBufCodecs.FLOAT.encode(buffer, yaw);
        ByteBufCodecs.FLOAT.encode(buffer, pitch);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
