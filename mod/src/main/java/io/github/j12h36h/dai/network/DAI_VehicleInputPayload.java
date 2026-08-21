package io.github.j12h36h.dai.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Lightweight optional client -> server vehicle control state.
 *
 * The payload is deliberately generic so any dai_vehicles definition can
 * consume the normal movement keys without adding a bespoke network channel.
 */
public record DAI_VehicleInputPayload(
        float forward,
        float strafe,
        boolean jump,
        boolean sneak,
        boolean sprint,
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
        ByteBufCodecs.FLOAT.encode(buffer, yaw);
        ByteBufCodecs.FLOAT.encode(buffer, pitch);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
