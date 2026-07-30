package io.github.j12h36h.dai.core;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = DAI.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = DAI.MODID, value = Dist.CLIENT)
public class DAI_Client {
    public DAI_Client(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        DAI.LOGGER.info("<DAI>: Client Setup");
        DAI.LOGGER.info("<DAI>: Player = {}", Minecraft.getInstance().getUser().getName());
    }
}
