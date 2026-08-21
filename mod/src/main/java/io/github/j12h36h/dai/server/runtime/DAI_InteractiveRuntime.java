package io.github.j12h36h.dai.server.runtime;

import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationRegistry;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Set;

/** Invisible/visible logical interaction volumes backed by dai_interactives. */
public final class DAI_InteractiveRuntime {
    private static final Set<String> INSIDE = new HashSet<>();
    private static boolean initialized;

    private DAI_InteractiveRuntime() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.register(DAI_InteractiveRuntime.class);
        DAI_Core.LOGGER.info("<DAI>: Interactive-volume runtime initialized.");
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        Set<String> now = new HashSet<>();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            for (var entry : DAI_GameCustomizationRegistry.entries(DAI_GameCustomizationKind.INTERACTIVE).values()) {
                var def = entry.definition();
                if (!def.flag("enabled", true) || !DAI_VolumeUtil.dimensionMatches(def, player)
                        || !DAI_VolumeUtil.requirementsPass(def, player) || !DAI_VolumeUtil.contains(def, player)) continue;
                String key = entry.id() + "|" + player.getUUID();
                now.add(key);
                if (!INSIDE.contains(key)) dispatch(player, def.event("enter"));
                if (player.tickCount % Math.max(1, (int)def.number("tick_interval", 1)) == 0) dispatch(player, def.event("tick"));
            }
        }
        for (String old : Set.copyOf(INSIDE)) {
            if (now.contains(old)) continue;
            int split = old.lastIndexOf('|');
            if (split <= 0) continue;
            var entry = DAI_GameCustomizationRegistry.get(DAI_GameCustomizationKind.INTERACTIVE, old.substring(0, split));
            try {
                var uuid = java.util.UUID.fromString(old.substring(split + 1));
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(uuid);
                if (player != null && entry != null) dispatch(player, entry.definition().event("exit"));
            } catch (IllegalArgumentException ignored) {}
        }
        INSIDE.clear(); INSIDE.addAll(now);
    }

    @SubscribeEvent
    public static void useItem(PlayerInteractEvent.RightClickItem event) { if (event.getEntity() instanceof ServerPlayer p) dispatchAtPlayer(p, "use"); }
    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) { if (event.getEntity() instanceof ServerPlayer p) dispatchAtPlayer(p, "use"); }
    @SubscribeEvent
    public static void attack(AttackEntityEvent event) { if (event.getEntity() instanceof ServerPlayer p) dispatchAtPlayer(p, "attack"); }

    private static void dispatchAtPlayer(ServerPlayer player, String name) {
        for (var entry : DAI_GameCustomizationRegistry.entries(DAI_GameCustomizationKind.INTERACTIVE).values()) {
            var def = entry.definition();
            if (DAI_VolumeUtil.dimensionMatches(def, player) && DAI_VolumeUtil.requirementsPass(def, player) && DAI_VolumeUtil.contains(def, player)) {
                dispatch(player, def.event(name));
            }
        }
    }

    private static void dispatch(ServerPlayer player, String ref) { if (ref != null && !ref.isBlank()) DAI_RuntimeDispatch.dispatch(player, ref); }
}
