package io.github.j12h36h.dai.core;

import io.github.j12h36h.dai.action.DAI_ActionQueue;
import io.github.j12h36h.dai.input.Input_Action;
import io.github.j12h36h.dai.input.Input_Look;
import io.github.j12h36h.dai.input.Input_Manager;
import io.github.j12h36h.dai.ui.DAI_ScreenManager;
import io.github.j12h36h.dai.util.DAI_Targeting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = DAI.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = DAI.MODID, value = Dist.CLIENT)
public class DAI_Client {

    public DAI_Client(ModContainer container) {
        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                ConfigurationScreen::new
        );
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }

        // Restore the previous DAI screen when a vanilla screen closes.
        if (minecraft.gui.screen() == null && !DAI_ScreenManager.isEmpty()) {
            minecraft.gui.setScreen(DAI_ScreenManager.pop());
        }

        // Apply DAI look input.
        Input_Look look = Input_Manager.look();

        minecraft.player.setYRot(look.yaw());
        minecraft.player.setXRot(look.pitch());

        minecraft.player.setYHeadRot(look.yaw());
        minecraft.player.setYBodyRot(look.yaw());

        Input_Action action = Input_Manager.action();

        if (action.attack()) {

            if (minecraft.gameMode != null) {

                Entity target = DAI_Targeting.nearestEntity();

                if (target != null) {
                    minecraft.gameMode.attack(minecraft.player, target);
                    minecraft.player.swing(minecraft.player.getUsedItemHand());
                }
            }

            action.attack(false);
        }
        DAI_ActionQueue.tick();
    }
}