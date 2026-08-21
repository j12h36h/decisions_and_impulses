package io.github.j12h36h.dai.server.runtime;

import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Loop/priority/transition controller layered over resource-pack sound events. */
public final class DAI_AudioRuntime {
    private record Playing(String id, long nextTick, int priority) {}
    private static final Map<UUID, Playing> MUSIC = new HashMap<>();
    private static long ticks;
    private static boolean initialized;

    private DAI_AudioRuntime() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.register(DAI_AudioRuntime.class);
    }

    public static void onCustomizationEvent(ServerPlayer player, DAI_GameCustomizationKind kind,
                                             DAI_GameCustomizationRegistry.Entry entry, String event) {
        if (player == null || kind != DAI_GameCustomizationKind.MUSIC || entry == null) return;
        String e = event == null ? "" : event.toLowerCase();
        if (e.equals("stop") || e.equals("end")) { MUSIC.remove(player.getUUID()); return; }
        if (!(e.equals("run") || e.equals("play") || e.equals("start") || e.equals("transition"))) return;
        int priority = (int)entry.definition().number("priority", 0);
        Playing current = MUSIC.get(player.getUUID());
        if (current != null && current.priority() > priority && !e.equals("transition")) return;
        int loopTicks = Math.max(1, (int)entry.definition().number("loop_ticks", 2400));
        if (entry.definition().flag("loop", false)) MUSIC.put(player.getUUID(), new Playing(entry.id().toString(), ticks + loopTicks, priority));
        else MUSIC.remove(player.getUUID());
        String transition = entry.definition().property("transition_to");
        if (e.equals("transition") && !transition.isBlank()) {
            var next = DAI_GameCustomizationRegistry.get(DAI_GameCustomizationKind.MUSIC, transition);
            if (next != null) MUSIC.put(player.getUUID(), new Playing(next.id().toString(), ticks + Math.max(1,(int)next.definition().number("loop_ticks",2400)), (int)next.definition().number("priority",0)));
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        ticks++;
        for (var item : Map.copyOf(MUSIC).entrySet()) {
            if (item.getValue().nextTick() > ticks) continue;
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(item.getKey());
            if (player == null) { MUSIC.remove(item.getKey()); continue; }
            var entry = DAI_GameCustomizationRegistry.get(DAI_GameCustomizationKind.MUSIC, item.getValue().id());
            if (entry == null || entry.definition().carrier().isBlank()) { MUSIC.remove(item.getKey()); continue; }
            var def = entry.definition();
            double volume = def.number("volume", 1.0), pitch = def.number("pitch", 1.0), min = Math.max(0, def.number("min_volume", 0));
            DAI_RuntimeDispatch.dispatch(player, "command:playsound " + def.carrier() + " music @s ~ ~ ~ " + volume + " " + pitch + " " + min);
            MUSIC.put(item.getKey(), new Playing(item.getValue().id(), ticks + Math.max(1,(int)def.number("loop_ticks",2400)), item.getValue().priority()));
        }
    }
}
