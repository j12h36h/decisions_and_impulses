package io.github.j12h36h.dai.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.j12h36h.dai.condition.DAI_Condition;

import java.util.List;
public record DAI_Action(
        String type,
        List<DAI_Condition> conditions,
        List<DAI_Action> sequence,
        String menu,
        String open,
        float yaw,
        float pitch,
        String direction,
        int ticks
) {

    public static final Codec<DAI_Action> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(

                            Codec.STRING.fieldOf("type")
                                    .forGetter(DAI_Action::type),

                            DAI_Condition.CODEC.listOf()
                                    .optionalFieldOf("conditions", List.of())
                                    .forGetter(DAI_Action::conditions),

                            Codec.lazyInitialized(() -> DAI_Action.CODEC)
                                    .listOf()
                                    .optionalFieldOf("sequence", List.of())
                                    .forGetter(DAI_Action::sequence),

                            Codec.STRING.optionalFieldOf("menu", "")
                                    .forGetter(DAI_Action::menu),

                            Codec.STRING.optionalFieldOf("open", "")
                                    .forGetter(DAI_Action::open),

                            Codec.FLOAT.optionalFieldOf("yaw", 0.0F)
                                    .forGetter(DAI_Action::yaw),

                            Codec.FLOAT.optionalFieldOf("pitch", 0.0F)
                                    .forGetter(DAI_Action::pitch),

                            Codec.STRING.optionalFieldOf("direction", "")
                                    .forGetter(DAI_Action::direction),

                            Codec.INT.optionalFieldOf("ticks", 0)
                                    .forGetter(DAI_Action::ticks)

                    ).apply(instance, DAI_Action::new)
            );
}