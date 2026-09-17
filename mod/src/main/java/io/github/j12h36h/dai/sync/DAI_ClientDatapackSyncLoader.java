package io.github.j12h36h.dai.sync;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.server.network.DAI_ClientDatapackSyncRuntime;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/** Captures raw datapack JSON without loading physical-client classes on a dedicated server. */
public final class DAI_ClientDatapackSyncLoader extends SimpleJsonResourceReloadListener<JsonObject> {
    private static final Codec<JsonObject> JSON_OBJECT_CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> {
                Object value = dynamic.convert(JsonOps.INSTANCE).getValue();
                return value instanceof JsonObject object ? object.deepCopy() : new JsonObject();
            },
            object -> new Dynamic<>(JsonOps.INSTANCE, object == null ? new JsonObject() : object.deepCopy())
    );

    private final String kind;

    public DAI_ClientDatapackSyncLoader(String kind, String folder) {
        super(JSON_OBJECT_CODEC, FileToIdConverter.json(folder));
        this.kind = kind;
    }

    @Override
    protected void apply(Map<Identifier, JsonObject> definitions, ResourceManager manager, ProfilerFiller profiler) {
        DAI_ClientDatapackSyncRepository.replace(kind, definitions);
        DAI_Core.debug("<DAI>: Captured {} '{}' definition(s) for client datapack sync.", definitions.size(), kind);

        // During /reload, immediately update already-connected clients. On the
        // initial server load there are normally no players yet; login sync
        // covers those connections later.
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (var player : server.getPlayerList().getPlayers()) {
                DAI_ClientDatapackSyncRuntime.syncKind(player, kind);
            }
        }
    }
}
