package io.github.j12h36h.dai.packs;

/**
 * Runtime role of a DAI datapack.
 *
 * MAIN packs define a complete gameplay Experience and are mutually exclusive
 * inside one launched world runtime. They no longer own DAI launcher/title
 * presentation. ADDON packs may be layered without an
 * engine-imposed count limit. UNMANAGED packs are ordinary non-DAI datapacks
 * and are deliberately left to Minecraft/other tooling.
 */
public enum DAI_DatapackRole {
    MAIN,
    ADDON,
    UNMANAGED
}
