package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.api.DAI_CapabilityStore;
import io.github.j12h36h.dai.api.DAI_Reference;
import io.github.j12h36h.dai.client.api.DAI_ReferenceStore;
import io.github.j12h36h.dai.api.DAI_StateStore;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.client.menus.system.DAI_TargetState;
import io.github.j12h36h.dai.client.reactions.DAI_ReactionDispatcher;
import io.github.j12h36h.dai.reactions.DAI_ReactionOutcome;
import io.github.j12h36h.dai.reactions.DAI_ReactionPhase;
import io.github.j12h36h.dai.client.reactions.DAI_ReactionRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class DAI_ExtensionLogic {

    private DAI_ExtensionLogic() {
        // Utility class.
    }

    public static void setStateBoolean(
            DAI_ActionDefinition action
    ) {

        DAI_StateStore.setBoolean(
                action.action(),
                action.state()
        );
    }

    public static void setStateNumber(
            DAI_ActionDefinition action
    ) {

        DAI_StateStore.setNumber(
                action.action(),
                action.value()
        );
    }

    /**
     * Uses direction as the string payload so this feature can ship without
     * widening DAI_ActionDefinition and invalidating existing constructors.
     * A dedicated generic text field can replace this compatibility bridge
     * in a later schema revision.
     */
    public static void setStateString(
            DAI_ActionDefinition action
    ) {

        DAI_StateStore.setString(
                action.action(),
                action.direction()
        );
    }

    public static void addStateNumber(
            DAI_ActionDefinition action
    ) {

        DAI_StateStore.addNumber(
                action.action(),
                action.value()
        );
    }

    public static void toggleStateBoolean(
            DAI_ActionDefinition action
    ) {

        DAI_StateStore.toggleBoolean(
                action.action()
        );
    }

    public static void clearState(
            DAI_ActionDefinition action
    ) {

        DAI_StateStore.remove(
                action.action()
        );
    }

    public static void addCapability(
            DAI_ActionDefinition action
    ) {

        DAI_CapabilityStore.add(
                action.action()
        );
    }

    public static void removeCapability(
            DAI_ActionDefinition action
    ) {

        DAI_CapabilityStore.remove(
                action.action()
        );
    }

    public static void clearCapabilities(
            DAI_ActionDefinition action
    ) {

        DAI_CapabilityStore.clear();
    }

    public static void rememberTargetEntity(
            DAI_ActionDefinition action
    ) {

        Entity entity =
                DAI_TargetState.selected();

        if (entity == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            return;
        }

        DAI_ReferenceStore.rememberEntity(
                action.action(),
                entity
        );
    }

    public static void rememberReactionEntity(
            DAI_ActionDefinition action
    ) {

        var context =
                DAI_ReactionRuntime.current();

        Entity entity =
                context == null
                        ? null
                        : context.entity();

        if (entity == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            return;
        }

        DAI_ReferenceStore.rememberEntity(
                action.action(),
                entity
        );
    }

    public static void rememberTargetBlock(
            DAI_ActionDefinition action
    ) {

        BlockPos block =
                DAI_TargetState.selectedBlock();

        if (block == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            return;
        }

        DAI_ReferenceStore.rememberBlock(
                action.action(),
                block
        );
    }

    public static void rememberPlayerPosition(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft == null
                        || minecraft.player == null
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            return;
        }

        DAI_ReferenceStore.rememberPosition(
                action.action(),
                minecraft.player.position()
        );
    }

    public static void selectReference(
            DAI_ActionDefinition action
    ) {

        DAI_Reference reference =
                DAI_ReferenceStore.get(
                        action.action()
                );

        if (reference == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            return;
        }

        switch (reference.type()) {

            case ENTITY -> {

                Entity entity =
                        DAI_ReferenceStore.resolveEntity(
                                action.action()
                        );

                if (entity == null) {

                    DAI_ActionStatus.set(
                            DAI_ActionResult.FAILURE
                    );

                    return;
                }

                DAI_TargetState.select(
                        entity
                );
            }

            case BLOCK -> {

                BlockPos block =
                        reference.blockPosition();

                if (block == null) {

                    DAI_ActionStatus.set(
                            DAI_ActionResult.FAILURE
                    );

                    return;
                }

                DAI_TargetState.selectBlock(
                        block
                );
            }

            case POSITION -> {

                Vec3 position =
                        reference.position();

                if (position == null) {

                    DAI_ActionStatus.set(
                            DAI_ActionResult.FAILURE
                    );

                    return;
                }

                DAI_TargetState.selectBlock(
                        BlockPos.containing(
                                position.x,
                                position.y,
                                position.z
                        )
                );
            }
        }
    }

    public static void clearReference(
            DAI_ActionDefinition action
    ) {

        DAI_ReferenceStore.remove(
                action.action()
        );
    }

    /**
     * Emits a registered reaction event. action.action is the event id.
     * direction may be pre/during/post; blank defaults to during.
     * The currently selected entity is supplied as reaction context.
     */
    public static void emitReactionEvent(
            DAI_ActionDefinition action
    ) {

        DAI_ReactionPhase phase =
                DAI_ReactionPhase.parse(
                        action.direction()
                );

        if (phase == DAI_ReactionPhase.UNKNOWN) {
            phase = DAI_ReactionPhase.DURING;
        }

        DAI_ReactionOutcome outcome =
                DAI_ReactionDispatcher.fire(
                        action.action(),
                        phase,
                        DAI_TargetState.selected()
                );

        if (outcome == DAI_ReactionOutcome.CANCEL) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.CANCELLED
            );
        }
    }
}
