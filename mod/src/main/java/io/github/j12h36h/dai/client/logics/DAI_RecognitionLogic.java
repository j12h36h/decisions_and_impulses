package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecogDefinition;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecogEvaluator;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecogScanner;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecogSnapshot;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecognitionLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

public final class DAI_RecognitionLogic {

    private DAI_RecognitionLogic() {
        // Utility class.
    }

    public static List<DAI_RecogEvaluator.Evaluation> recognizeTarget() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_Core.debug(
                    "<DAI>: Recognition ignored because no client level is available."
            );

            return List.of();
        }

        if (
                !(minecraft.hitResult
                        instanceof BlockHitResult blockHitResult)
        ) {

            DAI_Core.debug(
                    "<DAI>: Recognition ignored because no block is targeted."
            );

            return List.of();
        }

        BlockPos origin =
                blockHitResult
                        .getBlockPos()
                        .immutable();

        List<DAI_RecogEvaluator.Evaluation> matches =
                new ArrayList<>();

        for (Identifier definitionId : DAI_RecognitionLibrary.ids()) {

            DAI_RecogDefinition definition =
                    DAI_RecognitionLibrary.get(
                            definitionId
                    );

            if (definition == null) {
                continue;
            }

            DAI_RecogSnapshot snapshot =
                    DAI_RecogScanner.scan(
                            minecraft.level,
                            origin,
                            definition
                    );

            DAI_RecogEvaluator.Evaluation evaluation =
                    DAI_RecogEvaluator.evaluate(
                            minecraft.level,
                            definition,
                            snapshot
                    );

            if (!evaluation.matched()) {

                DAI_Core.debug(
                        "<DAI>: Target did not match recognition definition '{}': {}.",
                        definitionId,
                        evaluation.failures()
                );

                continue;
            }

            matches.add(
                    evaluation
            );

            DAI_Core.LOGGER.info(
                    "<DAI>: Recognized '{}' using definition '{}'.",
                    evaluation.resultId(),
                    definitionId
            );
        }

        if (matches.isEmpty()) {

            DAI_Core.LOGGER.info(
                    "<DAI>: No recognition definitions matched the targeted structure."
            );

        } else {

            DAI_Core.LOGGER.info(
                    "<DAI>: Recognition completed with {} match(es).",
                    matches.size()
            );
        }

        return List.copyOf(
                matches
        );
    }

    public static boolean recognizes(
            Identifier recognitionId
    ) {

        if (recognitionId == null) {
            return false;
        }

        for (
                DAI_RecogEvaluator.Evaluation evaluation
                : recognizeTarget()
        ) {

            Identifier resultId =
                    Identifier.tryParse(
                            evaluation.resultId()
                    );

            if (recognitionId.equals(resultId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Reads continuously refreshed nearby recognition perception.
     *
     * Available menus may call this every client tick. The perception helper
     * deliberately performs structural world scans on a slower fixed cadence
     * and returns the cached result between refreshes. The check remains live
     * without turning every menu refresh into a structure scan.
     *
     * This method never changes DAI's selected block/entity target.
     */
    public static boolean recognizesNearby(
            Identifier recognitionId,
            int requestedRadius
    ) {

        return DAI_RecognitionPerceptionLogic.recognizesNearby(
                recognitionId,
                requestedRadius
        );
    }
}
