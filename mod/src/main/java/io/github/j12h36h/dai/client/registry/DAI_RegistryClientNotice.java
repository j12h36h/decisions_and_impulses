package io.github.j12h36h.dai.client.registry;

import io.github.j12h36h.dai.registry.DAI_RegistryPreflight;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client-only presentation for the common registry preflight state. */
public final class DAI_RegistryClientNotice {

    private static boolean noticeShown;

    private DAI_RegistryClientNotice() {}

    public static void tick() {
        if (!DAI_RegistryPreflight.restartRequired() || noticeShown) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return;

        noticeShown = true;

        minecraft.player.sendSystemMessage(
                Component.literal("[D.A.I.] Restart required")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
        );
        minecraft.player.sendSystemMessage(
                Component.literal(
                        DAI_RegistryPreflight.pendingSpecs().size()
                                + " new or changed registry-backed content id(s) were staged for the next launch."
                ).withStyle(ChatFormatting.YELLOW)
        );
        minecraft.player.sendSystemMessage(
                Component.literal(
                        "Exit Minecraft completely and restart before using D.A.I. in this world. D.A.I. execution is suspended to protect the save."
                ).withStyle(ChatFormatting.YELLOW)
        );
    }

    public static void resetSession() {
        noticeShown = false;
    }
}
