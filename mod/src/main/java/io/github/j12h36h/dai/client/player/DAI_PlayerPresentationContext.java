package io.github.j12h36h.dai.client.player;

import io.github.j12h36h.dai.client.experience.DAI_ExperienceRuntime;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Avatar;

import java.lang.reflect.Method;

/** Experience-owned identity stored directly on NeoForge's detached avatar render state. */
public final class DAI_PlayerPresentationContext {

    public static final ContextKey<Context> KEY = new ContextKey<>(
            Identifier.fromNamespaceAndPath(DAI_Core.MODID, "player_presentation")
    );

    private DAI_PlayerPresentationContext() {}

    public static void capture(Avatar avatar, AvatarRenderState state) {
        if (avatar == null || state == null) return;
        String profile = profileFor(avatar);
        state.setRenderData(KEY, profile.isBlank() ? null : new Context(profile, avatar.getYRot()));
    }

    public static Context get(AvatarRenderState state) {
        return state == null ? null : state.getRenderData(KEY);
    }

    public static String localProfile() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) return "";
        return profileFor(minecraft.player);
    }

    private static String profileFor(Avatar avatar) {
        DAI_ExperienceDefinition experience = DAI_ExperienceRuntime.active();
        if (experience == null) return "";
        DAI_ExperienceDefinition.PlayerPresentation policy = experience.playerPresentation();
        if (policy == null || !policy.enabled()) return "";

        String uuid = avatar.getUUID().toString().toLowerCase(java.util.Locale.ROOT);
        String explicit = policy.players().get(uuid);
        if (explicit != null && !explicit.isBlank()) return explicit;

        String team = teamName(avatar);
        if (!team.isBlank()) {
            String teamProfile = policy.teams().get(team.toLowerCase(java.util.Locale.ROOT));
            if (teamProfile != null && !teamProfile.isBlank()) return teamProfile;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null
                && minecraft.player.getUUID().equals(avatar.getUUID())
                && !policy.localProfile().isBlank()) {
            return policy.localProfile();
        }
        return policy.defaultProfile();
    }

    private static String teamName(Object avatar) {
        try {
            Method getTeam = avatar.getClass().getMethod("getTeam");
            Object team = getTeam.invoke(avatar);
            if (team == null) return "";
            Method getName = team.getClass().getMethod("getName");
            Object value = getName.invoke(team);
            return value == null ? "" : value.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    public record Context(String profile, float bodyYaw) {}
}
