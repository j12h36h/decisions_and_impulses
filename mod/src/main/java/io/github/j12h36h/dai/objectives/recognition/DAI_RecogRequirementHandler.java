package io.github.j12h36h.dai.objectives.recognition;

import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface DAI_RecogRequirementHandler {

    boolean evaluate(
            Level level,
            DAI_RecogSnapshot snapshot,
            Map<String, List<DAI_RecogBlock>> groups,
            DAI_RecogDefinition.DAI_RecogRequirement requirement
    );
}