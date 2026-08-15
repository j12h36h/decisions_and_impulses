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

    private static void fail(String message) {
        DAI_Core.LOGGER.warn("<DAI>: {}", message);
        DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
    }
}
