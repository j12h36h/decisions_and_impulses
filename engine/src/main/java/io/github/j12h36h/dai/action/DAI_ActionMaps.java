package io.github.j12h36h.dai.action;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class DAI_ActionMaps {

    private DAI_ActionMaps() {
    }

    public static KeyMapping get(String key) {

        Minecraft minecraft = Minecraft.getInstance();

        return switch (key) {
            case "attack" -> minecraft.options.keyAttack;
            case "use" -> minecraft.options.keyUse;
            case "forward" -> minecraft.options.keyUp;
            case "back" -> minecraft.options.keyDown;
            case "left" -> minecraft.options.keyLeft;
            case "right" -> minecraft.options.keyRight;
            case "jump" -> minecraft.options.keyJump;
            case "sneak" -> minecraft.options.keyShift;
            case "sprint" -> minecraft.options.keySprint;
            case "drop" -> minecraft.options.keyDrop;
            case "inventory" -> minecraft.options.keyInventory;
            case "swap_offhand" -> minecraft.options.keySwapOffhand;
            case "pick_block" -> minecraft.options.keyPickItem;
            case "chat" -> minecraft.options.keyChat;
            case "player_list" -> minecraft.options.keyPlayerList;
            case "command" -> minecraft.options.keyCommand;
            case "screenshot" -> minecraft.options.keyScreenshot;
            case "toggle_perspective" -> minecraft.options.keyTogglePerspective;
            case "smooth_camera" -> minecraft.options.keySmoothCamera;
            case "fullscreen" -> minecraft.options.keyFullscreen;
            case "spectator_outlines" -> minecraft.options.keySpectatorOutlines;
            case "save_toolbar" -> minecraft.options.keySaveHotbarActivator;
            case "load_toolbar" -> minecraft.options.keyLoadHotbarActivator;
            case "advancements" -> minecraft.options.keyAdvancements;
            default -> null;
        };
    }
}
