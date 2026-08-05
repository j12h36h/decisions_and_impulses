package io.github.j12h36h.dai.recognition;

import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public final class DAI_RecogEvaluator {

    private DAI_RecogEvaluator() {
        // Utility class.
    }

    public static Evaluation evaluate(
            Level level,
            DAI_RecogDefinition definition,
            DAI_RecogSnapshot snapshot
    ) {

        if (
                level == null
                        || definition == null
                        || snapshot == null
        ) {

            return Evaluation.failed(
                    "Recognition evaluation received invalid input."
            );
        }

        if (snapshot.isEmpty()) {

            return Evaluation.failed(
                    "Recognition snapshot is empty."
            );
        }

        Map<String, List<DAI_RecogBlock>> classified =
                classify(
                        definition,
                        snapshot
                );

        List<String> failures =
                new ArrayList<>();

        validateGroupCounts(
                definition,
                classified,
                failures
        );

        for (
                DAI_RecogDefinition.DAI_RecogRequirement requirement
                : definition.requirements()
        ) {

            boolean passed =
                    DAI_RecogRequirementRegistry.evaluate(
                            level,
                            snapshot,
                            classified,
                            requirement
                    );

            if (!passed) {

                failures.add(
                        "Requirement failed: "
                                + requirement.type()
                );
            }
        }

        boolean matched =
                failures.isEmpty();

        if (matched) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Recognition evaluation matched result '{}'.",
                    definition.result().id()
            );

        } else {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Recognition evaluation for '{}' failed with {} issue(s): {}.",
                    definition.result().id(),
                    failures.size(),
                    failures
            );
        }

        return new Evaluation(
                matched,
                definition.result().id(),
                classified,
                failures
        );
    }

    private static Map<String, List<DAI_RecogBlock>> classify(
            DAI_RecogDefinition definition,
            DAI_RecogSnapshot snapshot
    ) {

        Map<String, List<DAI_RecogBlock>> classified =
                new LinkedHashMap<>();

        for (String groupName : definition.groups().keySet()) {

            classified.put(
                    groupName,
                    new ArrayList<>()
            );
        }

        for (DAI_RecogBlock block : snapshot.blocks()) {

            for (
                    Map.Entry<
                            String,
                            DAI_RecogDefinition.DAI_RecogGroupRule
                            > entry
                    : definition.groups().entrySet()
            ) {

                Identifier registryId =
                        Identifier.tryParse(
                                entry.getValue().registry()
                        );

                if (registryId == null) {

                    DAI_Core.LOGGER.warn(
                            "<DAI>: Invalid recognition registry id '{}' for group '{}'.",
                            entry.getValue().registry(),
                            entry.getKey()
                    );

                    continue;
                }

                if (
                        DAI_RecogGroupManager.matches(
                                registryId,
                                block.state()
                        )
                ) {

                    classified
                            .get(entry.getKey())
                            .add(block);
                }
            }
        }

        Map<String, List<DAI_RecogBlock>> immutable =
                new LinkedHashMap<>();

        classified.forEach(
                (name, blocks) ->
                        immutable.put(
                                name,
                                List.copyOf(blocks)
                        )
        );

        return Map.copyOf(
                immutable
        );
    }

    private static void validateGroupCounts(
            DAI_RecogDefinition definition,
            Map<String, List<DAI_RecogBlock>> classified,
            List<String> failures
    ) {

        for (
                Map.Entry<
                        String,
                        DAI_RecogDefinition.DAI_RecogGroupRule
                        > entry
                : definition.groups().entrySet()
        ) {

            String groupName =
                    entry.getKey();

            DAI_RecogDefinition.DAI_RecogGroupRule rule =
                    entry.getValue();

            int count =
                    classified.getOrDefault(
                            groupName,
                            List.of()
                    ).size();

            if (count < rule.minimum()) {

                failures.add(
                        "Group '"
                                + groupName
                                + "' contains "
                                + count
                                + " block(s), minimum is "
                                + rule.minimum()
                                + "."
                );
            }

            if (count > rule.maximum()) {

                failures.add(
                        "Group '"
                                + groupName
                                + "' contains "
                                + count
                                + " block(s), maximum is "
                                + rule.maximum()
                                + "."
                );
            }
        }
    }

    public record Evaluation(
            boolean matched,
            String resultId,
            Map<String, List<DAI_RecogBlock>> groups,
            List<String> failures
    ) {

        public Evaluation {

            resultId =
                    resultId == null
                            ? ""
                            : resultId;

            groups =
                    groups == null
                            ? Map.of()
                            : Map.copyOf(groups);

            failures =
                    failures == null
                            ? List.of()
                            : List.copyOf(failures);
        }

        public static Evaluation failed(
                String reason
        ) {

            return new Evaluation(
                    false,
                    "",
                    Map.of(),
                    List.of(reason)
            );
        }

        public int groupCount(
                String group
        ) {

            return groups.getOrDefault(
                    group,
                    List.of()
            ).size();
        }
    }
}