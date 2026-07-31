package io.github.j12h36h.dai.core;

import io.github.j12h36h.dai.ui.DAI_ScreenManager;
import net.minecraft.client.Minecraft;
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
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }

        // No screen is open, but DAI has one waiting.
        if (minecraft.gui.screen() == null && !DAI_ScreenManager.isEmpty()) {
            minecraft.gui.setScreen(DAI_ScreenManager.pop());
        }
    }
}
