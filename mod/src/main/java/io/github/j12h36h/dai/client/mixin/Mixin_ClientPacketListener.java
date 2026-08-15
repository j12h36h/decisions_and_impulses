package io.github.j12h36h.dai.client.mixin;

import io.github.j12h36h.dai.client.menus.system.DAI_ClientRuntime;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class Mixin_ClientPacketListener {

    @Inject(
            method = "handleRespawn",
            at = @At("TAIL")
    )
    private void dai$handleRespawn(
            ClientboundRespawnPacket packet,
            CallbackInfo ci
    ) {

        DAI_ClientRuntime.requestInitialize();
    }

    @Inject(
            method = "handleLogin",
            at = @At("TAIL")
    )
    private void dai$handleLogin(
            ClientboundLoginPacket packet,
            CallbackInfo ci
    ) {

        DAI_ClientRuntime.requestInitialize();
    }
}