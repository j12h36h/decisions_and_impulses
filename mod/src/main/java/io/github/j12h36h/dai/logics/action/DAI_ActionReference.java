package io.github.j12h36h.dai.logics.action;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.Identifier;

/** Side-neutral action-id parser shared by client automation and server actors. */
public final class DAI_ActionReference {
    private DAI_ActionReference() {}

    public static Identifier parse(String reference) {
        if (reference == null || reference.isBlank()) return null;
        String normalized = reference.trim();
        if (!normalized.contains(":")) normalized = DAI_Core.MODID + ":" + normalized;
        return Identifier.tryParse(normalized);
    }
}
