package io.github.j12h36h.dai.content;

import com.mojang.serialization.Codec;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Persistent identity component attached to carrier ItemStacks. */
public final class DAI_ContentComponents {

    public static final DeferredRegister.DataComponents REGISTRAR =
            DeferredRegister.createDataComponents(
                    Registries.DATA_COMPONENT_TYPE,
                    DAI_Core.MODID
            );

    public static final Supplier<DataComponentType<String>> CONTENT_ID =
            REGISTRAR.registerComponentType(
                    "content_id",
                    builder -> builder
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
            );

    private DAI_ContentComponents() {}

    public static void initialize(IEventBus modBus) {
        REGISTRAR.register(modBus);
    }
}
