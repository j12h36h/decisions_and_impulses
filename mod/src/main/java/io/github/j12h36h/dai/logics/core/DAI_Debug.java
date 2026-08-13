package io.github.j12h36h.dai.logics.core;

import io.github.j12h36h.dai.logics.DAI_AutomationLogic;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.controller.DAI_ApproachController;
import io.github.j12h36h.dai.logics.controller.DAI_BreakController;
import io.github.j12h36h.dai.logics.controller.DAI_CreativeFlightController;
import io.github.j12h36h.dai.logics.input.DAI_InputState;
import io.github.j12h36h.dai.menus.system.DAI_TargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Human-readable debug heartbeat.
 *
 * High-frequency reconstruction data belongs to DAI_HumanTrace while manual
 * gameplay is being demonstrated, and structured autonomous diagnostics belong
 * to DAI_RuntimeTelemetry. This class intentionally stays compact so normal
 * latest.log files remain readable during long automation runs.
 */
public final class DAI_Debug {

    private static final int HEARTBEAT_TICKS = 100;
    private static final int FAILURE_DETAIL_COOLDOWN_TICKS = 40;

    private static int heartbeatTicks;
    private static int failureCooldown;
    private static DAI_ActionResult lastStatus = DAI_ActionResult.SUCCESS;

    private DAI_Debug() {
        // Utility class.
    }

    public static boolean isEnabled() {
        return DAI_Config.isDebuggingEnabled();
    }

    public static void tick() {
        if (!isEnabled()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;

        /*
         * Human trace is for manual demonstrations. During automation it used
         * to record DAI-driven camera/movement as if it were human input, which
         * duplicated runtime telemetry and produced thousands of unnecessary
         * lines. Keep the rich human trace only while automation is idle.
         */
        if (!DAI_AutomationLogic.isActive()) {
            DAI_HumanTrace.tick(minecraft);
        }

        if (failureCooldown > 0) {
            failureCooldown--;
        }

        DAI_ActionResult current = DAI_ActionStatus.get();
        boolean newFailure =
                isFailure(current)
                        && current != lastStatus
                        && failureCooldown <= 0;

        heartbeatTicks++;
        boolean heartbeat = heartbeatTicks >= HEARTBEAT_TICKS;

        if (heartbeat || newFailure) {
            if (heartbeat) heartbeatTicks = 0;
            logCompactSnapshot(minecraft, newFailure);
        }

        if (newFailure) {
            failureCooldown = FAILURE_DETAIL_COOLDOWN_TICKS;
        }

        lastStatus = current;
    }

    private static void logCompactSnapshot(
            Minecraft minecraft,
            boolean includeFailureDetail
    ) {
        Vec3 position = minecraft.player.position();
        Vec3 velocity = minecraft.player.getDeltaMovement();
        BlockPos selectedBlock = DAI_TargetState.selectedBlock();
        DAI_ActionDefinition queueHead = DAI_ActionQueue.peek();

        DAI_Core.LOGGER.info(
                "<DAI:DEBUG> pos={} vel={} act={}/{} q={} dly={} head={} target={} ap={} br={} fly={} fp={} fd={}/{} stall={} assist={} hotbar={} input={},{},j{},s{} look={}/{}",
                vec(position),
                vec(velocity),
                DAI_ActionStatus.get(),
                DAI_ActionStatus.previous(),
                DAI_ActionQueue.size(),
                DAI_ActionQueue.delayTicks(),
                queueHead == null ? "none" : action(queueHead),
                selectedBlock == null ? "-" : selectedBlock,
                DAI_ApproachController.isActive(),
                DAI_BreakController.isActive(),
                DAI_CreativeFlightController.isActive(),
                DAI_CreativeFlightController.phaseName(),
                distance(DAI_CreativeFlightController.distanceToMovementTarget()),
                distance(DAI_CreativeFlightController.distanceToTarget()),
                DAI_CreativeFlightController.stalledTicks(),
                DAI_CreativeFlightController.velocityAssistActive(),
                hotbar(minecraft),
                format(DAI_InputState.movement().forward()),
                format(DAI_InputState.movement().strafe()),
                DAI_InputState.movement().jump() ? 1 : 0,
                DAI_InputState.movement().sneak() ? 1 : 0,
                format(minecraft.player.getYRot()),
                format(minecraft.player.getXRot())
        );

        if (!includeFailureDetail) return;

        DAI_Core.LOGGER.info(
                "<DAI:DEBUG:FAIL> act={}/{} head={} target={} hit={}",
                DAI_ActionStatus.get(),
                DAI_ActionStatus.previous(),
                queueHead == null ? "none" : action(queueHead),
                selectedBlock == null ? "-" : selectedBlock,
                DAI_HumanTraceFormat.describeHit(minecraft)
        );
    }

    private static boolean isFailure(DAI_ActionResult result) {
        return result == DAI_ActionResult.FAILURE
                || result == DAI_ActionResult.TIMED_OUT;
    }

    private static String hotbar(Minecraft minecraft) {
        int slot = minecraft.player.getInventory().getSelectedSlot();
        ItemStack stack = minecraft.player.getInventory().getItem(slot);

        if (stack.isEmpty()) {
            return slot + ":empty";
        }

        return slot
                + ":"
                + BuiltInRegistries.ITEM.getKey(stack.getItem())
                + "x"
                + stack.getCount();
    }

    private static String describeQueue() {
        List<DAI_ActionDefinition> actions = DAI_ActionQueue.actions();
        if (actions.isEmpty()) return "[]";

        int count = Math.min(4, actions.size());
        StringJoiner joiner = new StringJoiner(
                ",",
                "[",
                actions.size() > count ? ",...]" : "]"
        );

        for (int index = 0; index < count; index++) {
            joiner.add(action(actions.get(index)));
        }

        return joiner.toString();
    }

    private static String action(DAI_ActionDefinition action) {
        if (action == null) return "none";
        return action.type()
                + (action.hasAction() ? "(" + action.action() + ")" : "");
    }

    private static String vec(Vec3 value) {
        return format(value.x) + "," + format(value.y) + "," + format(value.z);
    }

    private static String distance(double value) {
        return value < 0.0D ? "-" : format(value);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
