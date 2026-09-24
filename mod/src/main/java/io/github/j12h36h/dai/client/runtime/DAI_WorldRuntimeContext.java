package io.github.j12h36h.dai.client.runtime;

import io.github.j12h36h.dai.client.experience.DAI_ExperienceRuntime;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import net.minecraft.client.Minecraft;

/**
 * Authoritative 4.3 ownership view for the currently attached world.
 *
 * Launcher code should ask whether DAI owns the shell; gameplay systems should
 * ask which world runtime is active. Installation alone never activates an
 * Experience.
 */
public final class DAI_WorldRuntimeContext {
    public enum Mode { NONE, SHELL, VANILLA, EXPERIENCE }

    private DAI_WorldRuntimeContext() {}

    public static Mode mode() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) return Mode.NONE;
        if (DAI_ShellWorldRuntime.isShellActive() || DAI_ShellWorldRuntime.isBootstrapping()) return Mode.SHELL;
        return DAI_ExperienceRuntime.active() == null ? Mode.VANILLA : Mode.EXPERIENCE;
    }

    public static boolean isShell() { return mode() == Mode.SHELL; }
    public static boolean isVanilla() { return mode() == Mode.VANILLA; }
    public static boolean isExperience() { return mode() == Mode.EXPERIENCE; }

    public static DAI_ExperienceDefinition experience() {
        return isExperience() ? DAI_ExperienceRuntime.active() : null;
    }

    public static String experienceId() {
        DAI_ExperienceDefinition definition = experience();
        return definition == null ? "" : definition.id();
    }

    public static DAI_ExperienceDefinition.Ui ui() {
        DAI_ExperienceDefinition definition = experience();
        return definition == null ? DAI_ExperienceDefinition.Ui.DEFAULT : definition.ui();
    }
}
