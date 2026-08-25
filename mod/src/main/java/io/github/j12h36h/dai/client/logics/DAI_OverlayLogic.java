package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.action.DAI_SpriteOverlayDefinition;
import io.github.j12h36h.dai.logics.action.DAI_SpriteSheetOverlayDefinition;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.overlays.DAI_OverlayAnchor;
import io.github.j12h36h.dai.client.overlays.DAI_OverlayManager;
import io.github.j12h36h.dai.client.overlays.DAI_SpriteSheetLayer;
import io.github.j12h36h.dai.client.overlays.DAI_StaticSpriteLayer;
import io.github.j12h36h.dai.client.overlays.DAI_TextOverlayLayer;
import io.github.j12h36h.dai.client.overlays.DAI_ButtonOverlayLayer;
import io.github.j12h36h.dai.client.logics.input.DAI_MouseState;
import io.github.j12h36h.dai.client.logics.core.DAI_DebugProbe;
import net.minecraft.resources.Identifier;

public final class DAI_OverlayLogic {

    private DAI_OverlayLogic() {
        // Utility class.
    }

    public static void sprite(DAI_ActionDefinition action) {
        DAI_SpriteOverlayDefinition definition = action.sprite();

        if (definition == null || definition.isEmpty()) {
            fail("overlay_sprite requires a non-empty 'sprite' object.");
            return;
        }

        Identifier texture = DAI_OverlayManager.textureIdentifier(definition.texture());
        if (!validCommon(definition.id(), texture, definition.width(), definition.height(), definition.interactable(), definition.clickAction())) {
            return;
        }

        DAI_OverlayManager.put(
                new DAI_StaticSpriteLayer(
                        definition.id(),
                        texture,
                        DAI_OverlayAnchor.parse(definition.anchor()),
                        definition.x(),
                        definition.y(),
                        definition.width(),
                        definition.height(),
                        definition.z(),
                        definition.ticks(),
                        definition.interactable(),
                        definition.clickAction(),
                        definition.consumeClick(),
                        DAI_OverlayManager.tint(definition.color(), definition.alpha()),
                        DAI_OverlayManager.nextInsertionOrder()
                )
        );

        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
    }

    public static void text(DAI_ActionDefinition action) {
        if (action.action() == null || action.action().isBlank()) {
            fail("overlay_text requires action='<overlay id>'.");
            return;
        }
        if (action.target() == null || action.target().isBlank()) {
            fail("overlay_text requires target='<text>'.");
            return;
        }
        int width = action.value() <= 0.0D ? 240 : Math.max(1, (int) Math.round(action.value()));
        int color = textColor(action.open());
        DAI_OverlayManager.put(
                new DAI_TextOverlayLayer(
                        action.action(),
                        DAI_OverlayAnchor.parse(action.direction()),
                        Math.round(action.yaw()),
                        Math.round(action.pitch()),
                        width,
                        action.slot(),
                        action.ticks(),
                        action.target(),
                        color,
                        DAI_OverlayManager.nextInsertionOrder()
                )
        );
        DAI_DebugProbe.record("overlay", "text id=" + action.action() + " width=" + width);
        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
    }

    public static void button(DAI_ActionDefinition action) {
        if (action.action() == null || action.action().isBlank()) { fail("overlay_button requires action='<overlay id>'."); return; }
        if (action.target() == null || action.target().isBlank()) { fail("overlay_button requires target='<button text>'."); return; }
        if (action.open() == null || action.open().isBlank()) { fail("overlay_button requires open='<click action id>'."); return; }
        int width = action.value() <= 0.0D ? 150 : Math.max(40, (int) Math.round(action.value()));
        DAI_OverlayManager.put(new DAI_ButtonOverlayLayer(action.action(), DAI_OverlayAnchor.parse(action.direction()), Math.round(action.yaw()), Math.round(action.pitch()), width, action.slot(), action.ticks(), action.target(), action.open(), DAI_OverlayManager.nextInsertionOrder()));
        DAI_DebugProbe.record("overlay", "button id=" + action.action() + " width=" + width);
        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
    }

    public static void spriteSheet(DAI_ActionDefinition action) {
        DAI_SpriteSheetOverlayDefinition definition = action.spriteSheet();

        if (definition == null || definition.isEmpty()) {
            fail("overlay_sprite_sheet requires a non-empty 'sprite_sheet' object.");
            return;
        }

        Identifier texture = DAI_OverlayManager.textureIdentifier(definition.texture());
        if (!validCommon(definition.id(), texture, definition.width(), definition.height(), definition.interactable(), definition.clickAction())) {
            return;
        }

        if (definition.animation().frameWidth() <= 0
                || definition.animation().frameHeight() <= 0
                || definition.animation().frameCount() <= 0
                || definition.animation().columns() <= 0
                || definition.animation().frameTicks() <= 0) {
            fail("overlay_sprite_sheet requires positive frame_width, frame_height, frame_count, columns, and frame_ticks.");
            return;
        }

        DAI_OverlayManager.put(
                new DAI_SpriteSheetLayer(
                        definition.id(),
                        texture,
                        DAI_OverlayAnchor.parse(definition.anchor()),
                        definition.x(),
                        definition.y(),
                        definition.width(),
                        definition.height(),
                        definition.animation().frameWidth(),
                        definition.animation().frameHeight(),
                        definition.animation().frameCount(),
                        definition.animation().columns(),
                        definition.animation().frameTicks(),
                        definition.animation().loop(),
                        definition.z(),
                        definition.ticks(),
                        definition.interactable(),
                        definition.clickAction(),
                        definition.consumeClick(),
                        DAI_OverlayManager.tint(definition.color(), definition.alpha()),
                        DAI_OverlayManager.nextInsertionOrder()
                )
        );

        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
    }

    public static void remove(DAI_ActionDefinition action) {
        if (action.action() == null || action.action().isBlank()) {
            fail("overlay_remove requires action='<overlay id>'.");
            return;
        }

        boolean removed = DAI_OverlayManager.remove(action.action());
        DAI_ActionStatus.set(removed ? DAI_ActionResult.SUCCESS : DAI_ActionResult.FAILURE);
    }

    public static void clear(DAI_ActionDefinition action) {
        DAI_OverlayManager.clear();
        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
    }

    /**
     * Generic transform actions intentionally reuse existing action fields so
     * old packs and constructor call sites stay binary/source compatible:
     * action = overlay id; yaw/pitch = x/y or dx/dy or width/height; value = z/speed; state = boolean.
     */
    public static void setPosition(DAI_ActionDefinition action) {
        transformResult(
                DAI_OverlayManager.setPosition(
                        action.action(),
                        Math.round(action.yaw()),
                        Math.round(action.pitch())
                ),
                "set_position",
                action.action()
        );
    }

    public static void move(DAI_ActionDefinition action) {
        transformResult(
                DAI_OverlayManager.move(
                        action.action(),
                        Math.round(action.yaw()),
                        Math.round(action.pitch())
                ),
                "move",
                action.action()
        );
    }

    public static void setSize(DAI_ActionDefinition action) {
        transformResult(
                DAI_OverlayManager.setSize(
                        action.action(),
                        Math.round(action.yaw()),
                        Math.round(action.pitch())
                ),
                "set_size",
                action.action()
        );
    }

    public static void setZ(DAI_ActionDefinition action) {
        transformResult(
                DAI_OverlayManager.setZ(action.action(), (int) Math.round(action.value())),
                "set_z",
                action.action()
        );
    }

    public static void setInteractable(DAI_ActionDefinition action) {
        transformResult(
                DAI_OverlayManager.setInteractable(action.action(), action.state()),
                "set_interactable",
                action.action()
        );
    }

    public static void lockTransform(DAI_ActionDefinition action) {
        transformResult(
                DAI_OverlayManager.setTransformLocked(action.action(), action.state()),
                "lock",
                action.action()
        );
    }

    public static void clampToScreen(DAI_ActionDefinition action) {
        transformResult(
                DAI_OverlayManager.clampToScreen(action.action()),
                "clamp",
                action.action()
        );
    }

    public static void repelMouse(DAI_ActionDefinition action) {
        double speed = action.value() <= 0.0D ? 2.0D : action.value();
        transformResult(
                DAI_OverlayManager.moveAwayFrom(
                        action.action(),
                        DAI_MouseState.x(),
                        DAI_MouseState.y(),
                        speed
                ),
                "repel_mouse",
                action.action()
        );
    }

    private static void transformResult(boolean success, String operation, String id) {
        DAI_ActionStatus.set(success ? DAI_ActionResult.SUCCESS : DAI_ActionResult.FAILURE);
        DAI_DebugProbe.record(
                "overlay",
                operation + " id=" + (id == null ? "" : id) + " ok=" + success
        );
    }

    private static boolean validCommon(
            String id,
            Identifier texture,
            int width,
            int height,
            boolean interactable,
            String clickAction
    ) {
        if (id == null || id.isBlank()) {
            fail("Overlay id cannot be blank.");
            return false;
        }
        if (texture == null) {
            fail("Overlay texture must be a valid resource identifier.");
            return false;
        }
        if (width <= 0 || height <= 0) {
            fail("Overlay width and height must be positive.");
            return false;
        }
        if (interactable && (clickAction == null || clickAction.isBlank())) {
            fail("Interactable overlays require click_action.");
            return false;
        }
        return true;
    }

    private static int textColor(String authored) {
        String value = authored == null ? "" : authored.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (value.isBlank()) return 0xFFFFFFFF;
        try {
            if (value.length() == 6) return 0xFF000000 | Integer.parseUnsignedInt(value, 16);
            if (value.length() == 8) return (int) Long.parseLong(value, 16);
        } catch (NumberFormatException ignored) {
            DAI_Core.LOGGER.warn("<DAI>: Invalid overlay_text color '{}'; using white.", authored);
        }
        return 0xFFFFFFFF;
    }

    private static void fail(String message) {
        DAI_Core.LOGGER.warn("<DAI>: {}", message);
        DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
    }
}
