package io.github.j12h36h.dai.server.runtime;

import com.google.gson.JsonObject;
import io.github.j12h36h.dai.api.DAI_StateValue;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationDefinition;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationRegistry;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.action.DAI_ActionArguments;
import io.github.j12h36h.dai.network.DAI_ServerActionPayload;
import io.github.j12h36h.dai.server.action.DAI_ServerActionExecutor;
import io.github.j12h36h.dai.server.state.DAI_ServerStateRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative runtime for datapack-defined dai_skills entries.
 *
 * Skill definitions intentionally reuse the open customization schema.
 * Supported fields:
 *  numbers.cooldown_ticks, numbers.resource_cost, numbers.required_level,
 *  numbers.required_rank, numbers.max_rank
 *  properties.cooldown_group, properties.resource_state,
 *  properties.level_state, properties.job_state, properties.required_job,
 *  properties.rank_state, properties.required_tag, properties.blocked_tag
 *  events.cast / command
 */
public final class DAI_SkillRuntime {

    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new HashMap<>();

    private DAI_SkillRuntime() {}

    public static synchronized boolean cast(ServerPlayer actor, String rawId) {
        if (actor == null || rawId == null || rawId.isBlank()) return false;

        String id = normalize(rawId);
        var entry = DAI_GameCustomizationRegistry.get(DAI_GameCustomizationKind.SKILL, id);
        if (entry == null) {
            DAI_Core.LOGGER.warn("<DAI>: Unknown skill '{}'.", rawId);
            feedback(actor, "Unknown skill: " + rawId);
            return false;
        }

        DAI_GameCustomizationDefinition skill = entry.definition();
        long now = actor.level().getGameTime();
        String cooldownKey = skill.property("cooldown_group");
        if (cooldownKey.isBlank()) cooldownKey = entry.id().toString();
        long readyAt = COOLDOWNS
                .computeIfAbsent(actor.getUUID(), ignored -> new HashMap<>())
                .getOrDefault(cooldownKey, 0L);

        if (now < readyAt) {
            double seconds = (readyAt - now) / 20.0D;
            feedback(actor, String.format(Locale.ROOT, "%s ready in %.1fs", name(skill, entry.id().toString()), seconds));
            return false;
        }

        if (!meetsLevel(actor, skill)) return false;
        if (!meetsJob(actor, skill)) return false;
        if (!meetsRank(actor, skill)) return false;
        if (!meetsTags(actor, skill)) return false;

        String resourceState = skill.property("resource_state");
        double cost = Math.max(0.0D, skill.number("resource_cost", 0.0D));
        if (!resourceState.isBlank() && cost > 0.0D) {
            DAI_StateValue value = DAI_ServerStateRuntime.get(actor, actor, resourceState);
            if (value.type() != DAI_StateValue.Type.NUMBER || value.numberValue() + 1.0E-9D < cost) {
                feedback(actor, "Not enough resource for " + name(skill, entry.id().toString()));
                return false;
            }
        }

        String projectile = skill.property("projectile");
        boolean projectileOk = true;
        if (!projectile.isBlank()) {
            JsonObject args = new JsonObject();
            int count = Math.max(1, Math.min(64, (int)Math.round(skill.number("projectile_count", 1.0D))));
            double spread = Math.max(0.0D, Math.min(180.0D, skill.number("projectile_spread", 0.0D)));
            double speed = skill.number("projectile_speed", 0.0D);
            double forward = Math.max(0.0D, Math.min(4.0D, skill.number("projectile_forward_offset", 0.35D)));
            args.addProperty("count", count);
            args.addProperty("spread", spread);
            if (speed > 0.0D) args.addProperty("speed", speed);
            args.addProperty("forward_offset", forward);
            projectileOk = DAI_ProjectileRuntime.spawn(actor, projectile, new DAI_ActionArguments(args));
        }

        String dispatch = skill.event("cast");
        if (dispatch.isBlank()) dispatch = skill.command();
        boolean dispatchOk = dispatch.isBlank() || dispatch(actor, dispatch);
        if (projectile.isBlank() && dispatch.isBlank()) {
            DAI_Core.LOGGER.warn("<DAI>: Skill '{}' has no cast event, command, or projectile.", entry.id());
            return false;
        }
        if (!projectileOk || !dispatchOk) return false;

        if (!resourceState.isBlank() && cost > 0.0D) {
            DAI_ServerStateRuntime.mutate(
                    actor, resourceState, "add", DAI_StateValue.number(-cost), actor
            );
        }

        long cooldownTicks = Math.max(0L, Math.round(skill.number("cooldown_ticks", 0.0D)));
        if (cooldownTicks > 0L) {
            COOLDOWNS.get(actor.getUUID()).put(cooldownKey, now + cooldownTicks);
        }

        DAI_Core.debug("<DAI>: Player '{}' cast skill '{}' cooldown={}t.",
                actor.getUUID(), entry.id(), cooldownTicks);
        return true;
    }

    private static boolean meetsLevel(ServerPlayer actor, DAI_GameCustomizationDefinition skill) {
        String key = skill.property("level_state");
        double required = Math.max(0.0D, skill.number("required_level", 0.0D));
        if (key.isBlank() || required <= 0.0D) return true;
        DAI_StateValue value = DAI_ServerStateRuntime.get(actor, actor, key);
        boolean pass = value.type() == DAI_StateValue.Type.NUMBER && value.numberValue() >= required;
        if (!pass) feedback(actor, "Requires level " + (int)Math.ceil(required));
        return pass;
    }

    private static boolean meetsJob(ServerPlayer actor, DAI_GameCustomizationDefinition skill) {
        String key = skill.property("job_state");
        String required = skill.property("required_job");
        if (key.isBlank() || required.isBlank()) return true;
        DAI_StateValue value = DAI_ServerStateRuntime.get(actor, actor, key);
        boolean pass = value.type() == DAI_StateValue.Type.STRING
                && required.equalsIgnoreCase(value.stringValue());
        if (!pass) feedback(actor, "Requires job: " + required);
        return pass;
    }

    private static boolean meetsRank(ServerPlayer actor, DAI_GameCustomizationDefinition skill) {
        String key = skill.property("rank_state");
        double required = Math.max(0.0D, skill.number("required_rank", key.isBlank() ? 0.0D : 1.0D));
        if (key.isBlank() || required <= 0.0D) return true;
        DAI_StateValue value = DAI_ServerStateRuntime.get(actor, actor, key);
        boolean pass = value.type() == DAI_StateValue.Type.NUMBER && value.numberValue() >= required;
        if (!pass) feedback(actor, "Skill is not learned yet.");
        return pass;
    }


    private static boolean meetsTags(ServerPlayer actor, DAI_GameCustomizationDefinition skill) {
        String required = skill.property("required_tag");
        if (!required.isBlank() && !actor.tags().toList().contains(required)) {
            feedback(actor, "This skill is not available to your active job.");
            return false;
        }
        String blocked = skill.property("blocked_tag");
        if (!blocked.isBlank() && actor.tags().toList().contains(blocked)) {
            feedback(actor, "This skill cannot be used in your current state.");
            return false;
        }
        return true;
    }

    private static boolean dispatch(ServerPlayer actor, String rawDispatch) {
        String dispatch = rawDispatch == null ? "" : rawDispatch.trim();
        if (dispatch.isBlank()) return false;
        String lower = dispatch.toLowerCase(Locale.ROOT);

        if (lower.startsWith("function:")) {
            return execute(actor, "function", dispatch.substring("function:".length()).trim());
        }
        if (lower.startsWith("command:")) {
            return execute(actor, "command", dispatch.substring("command:".length()).trim());
        }
        if (Identifier.tryParse(dispatch) != null) {
            return execute(actor, "function", dispatch);
        }
        return execute(actor, "command", dispatch);
    }

    private static boolean execute(ServerPlayer actor, String operation, String action) {
        return DAI_ServerActionExecutor.executeTrusted(
                actor,
                new DAI_ServerActionPayload(operation, action, "", "", 0.0D, "{}")
        );
    }

    private static String name(DAI_GameCustomizationDefinition definition, String fallback) {
        return definition.displayName().isBlank() ? fallback : definition.displayName();
    }

    private static void feedback(ServerPlayer actor, String text) {
        if (actor != null && text != null && !text.isBlank()) {
            actor.sendSystemMessage(Component.literal(text));
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
