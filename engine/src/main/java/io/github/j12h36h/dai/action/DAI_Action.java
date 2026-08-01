package io.github.j12h36h.dai.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.j12h36h.dai.condition.DAI_Condition;

import java.util.List;
public record DAI_Action(
        String type,
        List<DAI_Condition> conditions,
        String menu,
        String open
) {

    public static final Codec<DAI_Action> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.STRING.fieldOf("type")
                                    .forGetter(DAI_Action::type),

                            DAI_Condition.CODEC.listOf()
                                    .optionalFieldOf("conditions", List.of())
                                    .forGetter(DAI_Action::conditions),

                            Codec.STRING.optionalFieldOf("menu", "")
                                    .forGetter(DAI_Action::menu),

                            Codec.STRING.optionalFieldOf("open", "")
                                    .forGetter(DAI_Action::open)

                    ).apply(instance, DAI_Action::new)
            );
}