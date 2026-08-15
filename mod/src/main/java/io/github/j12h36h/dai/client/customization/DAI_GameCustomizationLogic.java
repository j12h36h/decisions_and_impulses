package io.github.j12h36h.dai.client.customization;

import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.client.network.DAI_ServerBridge;
import io.github.j12h36h.dai.client.overlays.DAI_OverlayAnchor;
import io.github.j12h36h.dai.client.overlays.DAI_OverlayManager;
import io.github.j12h36h.dai.client.overlays.DAI_StaticSpriteLayer;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationDefinition;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationRegistry;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionLibrary;
import io.github.j12h36h.dai.logics.action.DAI_ActionReference;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.network.DAI_ServerActionPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Client dispatch/state facade for the DAI 1.9 customization registries.
 *
 * An event value may be:
 * - action:&lt;id&gt;   : enqueue a DAI action/sequence locally
 * - function:&lt;id&gt; : execute a server-owned datapack function
 * - command:&lt;cmd&gt; : execute a server-owned command
 * - &lt;action id&gt;   : enqueue when the id exists in the DAI action library
 *
 * If no event is authored, sequence is used as the default activation action;
 * command is the server-side fallback. This keeps the registry composable with
 * existing DAI actions rather than duplicating every subsystem in Java.
 */
public final class DAI_GameCustomizationLogic {

    private static final EnumMap<DAI_GameCustomizationKind, Set<String>> ACTIVE =
            new EnumMap<>(DAI_GameCustomizationKind.class);
    private static long runtimeTicks;

    static {
        for (DAI_GameCustomizationKind kind : DAI_GameCustomizationKind.values()) {
            ACTIVE.put(kind, new HashSet<>());
        }
    }

    private DAI_GameCustomizationLogic() {}

    public static boolean isActive(DAI_GameCustomizationKind kind, String rawId) {
        if (kind == null || rawId == null || rawId.isBlank()) return false;
        return ACTIVE.get(kind).contains(rawId.trim().toLowerCase(Locale.ROOT));
    }

    public static void clearState() {
        ACTIVE.values().forEach(Set::clear);
        runtimeTicks = 0L;
        setVanillaHudHidden(false);
    }

    public static void tick() {
        runtimeTicks++;
        for (DAI_GameCustomizationKind kind : DAI_GameCustomizationKind.values()) {
            for (String id : Set.copyOf(ACTIVE.get(kind))) {
                DAI_GameCustomizationRegistry.Entry entry = DAI_GameCustomizationRegistry.get(kind, id);
                if (entry == null) {
                    ACTIVE.get(kind).remove(id);
                    continue;
                }

                DAI_GameCustomizationDefinition definition = entry.definition();
                int interval = Math.max(0, (int) Math.round(definition.number("tick_interval", 0.0D)));
                if (interval <= 0 || runtimeTicks % interval != 0L) continue;

                String tickEvent = definition.event("tick");
                if (dispatchLocal(tickEvent)) continue;
                if (tickEvent.isBlank()) continue;

                DAI_ServerBridge.send(new DAI_ServerActionPayload(
                        "customization_event",
                        kind.id(),
                        id,
                        "tick\n",
                        0.0D
                ));
            }
        }
    }

    public static void genericEvent(DAI_ActionDefinition action) {
        DAI_GameCustomizationKind kind = DAI_GameCustomizationKind.parse(action.direction());
        emit(action, kind, action.open().isBlank() ? "run" : action.open(), false, false);
    }

    public static void genericActivate(DAI_ActionDefinition action) {
        DAI_GameCustomizationKind kind = DAI_GameCustomizationKind.parse(action.direction());
        emit(action, kind, action.open().isBlank() ? "activate" : action.open(), true, false);
    }

    public static void genericDeactivate(DAI_ActionDefinition action) {
        DAI_GameCustomizationKind kind = DAI_GameCustomizationKind.parse(action.direction());
        emit(action, kind, action.open().isBlank() ? "deactivate" : action.open(), false, true);
    }

    public static void soundPlay(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.SOUND, "play", true, false); }
    public static void soundStop(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.SOUND, "stop", false, true); }
    public static void musicPlay(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.MUSIC, "play", true, false); }
    public static void musicStop(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.MUSIC, "stop", false, true); }
    public static void hudShow(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.HUD, "show", true, false); }
    public static void hudHide(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.HUD, "hide", false, true); }
    public static void renderProfileApply(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.RENDER_PROFILE, "apply", true, false); }
    public static void renderProfileClear(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.RENDER_PROFILE, "clear", false, true); }
    public static void structurePlace(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.STRUCTURE, "place", false, false); }
    public static void featurePlace(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.FEATURE, "place", false, false); }
    public static void lootGrant(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.LOOT, "grant", false, false); }
    public static void currencyAdd(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.CURRENCY, "add", false, false); }
    public static void currencyTake(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.CURRENCY, "take", false, false); }
    public static void currencySet(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.CURRENCY, "set", false, false); }
    public static void shopOpen(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.SHOP, "open", true, false); }
    public static void shopPurchase(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.SHOP, "purchase", false, false); }
    public static void dialogueStart(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.DIALOGUE, "start", true, false); }
    public static void dialogueChoose(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.DIALOGUE, "choose", false, false); }
    public static void dialogueEnd(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.DIALOGUE, "end", false, true); }
    public static void questStart(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.QUEST, "start", true, false); }
    public static void questAdvance(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.QUEST, "advance", false, false); }
    public static void questComplete(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.QUEST, "complete", false, true); }
    public static void questFail(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.QUEST, "fail", false, true); }
    public static void factionJoin(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.FACTION, "join", true, false); }
    public static void factionLeave(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.FACTION, "leave", false, true); }
    public static void biomeApply(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.BIOME, "apply", false, false); }
    public static void dimensionTransfer(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.DIMENSION, "transfer", false, false); }
    public static void rulesApply(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.RULESET, "apply", true, false); }
    public static void rulesClear(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.RULESET, "clear", false, true); }
    public static void vehicleSpawn(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.VEHICLE, "spawn", true, false); }
    public static void vehicleDespawn(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.VEHICLE, "despawn", false, true); }
    public static void vehicleMount(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.VEHICLE, "mount", true, false); }
    public static void vehicleDismount(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.VEHICLE, "dismount", false, true); }
    public static void interactiveUse(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.INTERACTIVE, "use", false, false); }
    public static void fluidApply(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.FLUID, "apply", true, false); }
    public static void fluidRemove(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.FLUID, "remove", false, true); }
    public static void environmentEnter(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.ENVIRONMENT, "enter", true, false); }
    public static void environmentExit(DAI_ActionDefinition action) { emit(action, DAI_GameCustomizationKind.ENVIRONMENT, "exit", false, true); }

    private static void emit(
            DAI_ActionDefinition action,
            DAI_GameCustomizationKind kind,
            String defaultEvent,
            boolean activate,
            boolean deactivate
    ) {
        if (action == null || kind == null || !action.hasAction()) {
            fail("Customization action requires a valid registry kind and definition id in 'action'.");
            return;
        }

        DAI_GameCustomizationRegistry.Entry entry =
                DAI_GameCustomizationRegistry.get(kind, action.action());
        if (entry == null) {
            fail("Unknown " + kind.id() + " customization definition '" + action.action() + "'.");
            return;
        }

        String id = entry.id().toString();
        String eventName = defaultEvent == null || defaultEvent.isBlank()
                ? "run"
                : defaultEvent.trim().toLowerCase(Locale.ROOT);
        String runtimeTarget = action.target() == null ? "" : action.target().trim();

        boolean wasActive = ACTIVE.get(kind).contains(id);
        if (activate) ACTIVE.get(kind).add(id);
        if (deactivate) ACTIVE.get(kind).remove(id);
        if (kind == DAI_GameCustomizationKind.HUD) updateVanillaHudVisibility();

        DAI_GameCustomizationDefinition definition = entry.definition();
        String event = definition.event(eventName);

        if (kind == DAI_GameCustomizationKind.HUD
                && event.isBlank()
                && definition.sequence().isBlank()
                && !definition.texture().isBlank()) {
            boolean rendered = eventName.equals("hide")
                    ? DAI_OverlayManager.remove(hudOverlayId(id))
                    : showHudTexture(id, definition);
            boolean success = rendered || eventName.equals("hide");
            if (!success) restoreActiveState(kind, id, wasActive);
            DAI_ActionStatus.set(success
                    ? DAI_ActionResult.SUCCESS
                    : DAI_ActionResult.FAILURE);
            return;
        }

        if (dispatchLocal(event)) {
            DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
            return;
        }

        if (event.isBlank() && activate && dispatchLocal(definition.sequence())) {
            DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
            return;
        }

        boolean needsServer = !event.isBlank()
                || !definition.command().isBlank()
                || !definition.target().isBlank();

        if (needsServer) {
            boolean sent = DAI_ServerBridge.send(new DAI_ServerActionPayload(
                    "customization_event",
                    kind.id(),
                    id,
                    eventName + "\n" + runtimeTarget,
                    action.value()
            ));
            DAI_ActionStatus.set(sent ? DAI_ActionResult.SUCCESS : DAI_ActionResult.FAILURE);
            if (!sent) {
                restoreActiveState(kind, id, wasActive);
                DAI_Core.LOGGER.warn(
                        "<DAI>: {} customization event '{}:{}' requires DAI server support.",
                        kind.id(), id, eventName
                );
            }
            return;
        }

        // State-only definitions are valid (for conditions / Creator-driven
        // composition) even when they do not have a dispatch side effect.
        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
    }

    private static void updateVanillaHudVisibility() {
        boolean hide = false;
        for (String id : ACTIVE.get(DAI_GameCustomizationKind.HUD)) {
            DAI_GameCustomizationRegistry.Entry entry =
                    DAI_GameCustomizationRegistry.get(DAI_GameCustomizationKind.HUD, id);
            if (entry != null && entry.definition().flag("hide_vanilla", false)) {
                hide = true;
                break;
            }
        }
        setVanillaHudHidden(hide);
    }

    private static void restoreActiveState(
            DAI_GameCustomizationKind kind,
            String id,
            boolean wasActive
    ) {
        if (wasActive) ACTIVE.get(kind).add(id);
        else ACTIVE.get(kind).remove(id);
        if (kind == DAI_GameCustomizationKind.HUD) updateVanillaHudVisibility();
    }

    /** Best-effort full vanilla-HUD suppression for replacement HUD profiles. */
    private static void setVanillaHudHidden(boolean hidden) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) return;

        Class<?> type = minecraft.options.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField("hideGui");
                field.setAccessible(true);
                field.setBoolean(minecraft.options, hidden);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return;
            }
        }
        // Mappings can rename this option between Minecraft releases. The
        // custom HUD itself remains functional; only vanilla suppression is
        // skipped until the mapping is updated during stabilization.
    }

    private static boolean showHudTexture(
            String id,
            DAI_GameCustomizationDefinition definition
    ) {
        Identifier texture = DAI_OverlayManager.textureIdentifier(definition.texture());
        int width = (int) Math.round(definition.number("width", 0.0D));
        int height = (int) Math.round(definition.number("height", 0.0D));
        if (texture == null || width <= 0 || height <= 0) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: HUD '{}' requires a valid texture and positive numbers.width/numbers.height.",
                    id
            );
            return false;
        }

        int x = (int) Math.round(definition.number("x", 0.0D));
        int y = (int) Math.round(definition.number("y", 0.0D));
        int z = (int) Math.round(definition.number("z", 0.0D));
        int ticks = Math.max(0, (int) Math.round(definition.number("ticks", 0.0D)));
        double alpha = definition.number("alpha", 1.0D);
        boolean interactable = definition.flag("interactable", false);
        boolean consumeClick = definition.flag("consume_click", false);
        String anchor = definition.property("anchor");
        if (anchor.isBlank()) anchor = "center";
        String color = definition.property("color");
        if (color.isBlank()) color = "#FFFFFF";
        String clickAction = definition.property("click_action");

        if (interactable && clickAction.isBlank()) {
            DAI_Core.LOGGER.warn("<DAI>: Interactable HUD '{}' requires properties.click_action.", id);
            return false;
        }

        DAI_OverlayManager.put(new DAI_StaticSpriteLayer(
                hudOverlayId(id),
                texture,
                DAI_OverlayAnchor.parse(anchor),
                x, y, width, height, z, ticks,
                interactable, clickAction, consumeClick,
                DAI_OverlayManager.tint(color, alpha),
                DAI_OverlayManager.nextInsertionOrder()
        ));
        return true;
    }

    private static String hudOverlayId(String id) {
        return "dai19_hud:" + id;
    }

    private static boolean dispatchLocal(String rawReference) {
        if (rawReference == null || rawReference.isBlank()) return false;
        String reference = rawReference.trim();
        String lower = reference.toLowerCase(Locale.ROOT);

        if (lower.startsWith("function:") || lower.startsWith("command:")) {
            return false;
        }
        if (lower.startsWith("action:")) {
            reference = reference.substring("action:".length()).trim();
        }

        Identifier id = DAI_ActionReference.parse(reference);
        if (id == null || DAI_ActionLibrary.get(id) == null) return false;
        DAI_ActionQueue.enqueueDeferredReference(reference);
        return true;
    }

    private static void fail(String reason) {
        DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
        DAI_Core.LOGGER.warn("<DAI>: {}", reason);
    }
}
