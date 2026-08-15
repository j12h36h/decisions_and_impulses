package io.github.j12h36h.dai.client.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.class)
public interface Mixin_CreativeModeInventoryScreen {

    @Invoker("selectTab")
    void dai$selectTab(
            CreativeModeTab tab
    );

    @Accessor("searchBox")
    EditBox dai$getSearchBox();

    @Invoker("refreshSearchResults")
    void dai$refreshSearchResults();
}
