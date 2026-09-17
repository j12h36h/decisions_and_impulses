package io.github.j12h36h.dai.server.network;

import com.google.gson.JsonObject;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.network.DAI_ClientDatapackSyncPayload;
import io.github.j12h36h.dai.sync.DAI_ClientDatapackSyncRepository;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;

/** Sends the active server datapack's client-visible JSON to each DAI client. */
public final class DAI_ClientDatapackSyncRuntime {
    private static boolean initialized;

    private DAI_ClientDatapackSyncRuntime() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener(DAI_ClientDatapackSyncRuntime::onPlayerLoggedIn);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncAll(player);
    }

    public static void syncAll(ServerPlayer player) {
        if (player == null) return;
        for (String kind : DAI_ClientDatapackSyncRepository.KINDS) syncKind(player, kind);
    }

    public static void syncKind(ServerPlayer player, String kind) {
        PacketDistributor.sendToPlayer(player, new DAI_ClientDatapackSyncPayload("begin", kind, "", ""));
        int sent = 0;
        for (Map.Entry<Identifier, JsonObject> entry : DAI_ClientDatapackSyncRepository.snapshot(kind).entrySet()) {
            PacketDistributor.sendToPlayer(player, new DAI_ClientDatapackSyncPayload(
                    "entry", kind, entry.getKey().toString(), entry.getValue().toString()
            ));
            sent++;
        }
        PacketDistributor.sendToPlayer(player, new DAI_ClientDatapackSyncPayload("end", kind, "", ""));
        DAI_Core.debug("<DAI>: Synced {} '{}' datapack definition(s) to player '{}'.", sent, kind, player.getUUID());
    }
}
