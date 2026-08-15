package io.github.j12h36h.dai.content;

public enum DAI_ContentKind {
    ITEM("dai_items"),
    BLOCK("dai_blocks"),
    WEAPON("dai_weapons"),
    ARMOR("dai_armor"),
    EFFECT("dai_effects"),
    POTION("dai_potions"),
    PROJECTILE("dai_projectiles"),
    PARTICLE("dai_particles"),
    ENCHANTMENT("dai_enchantments"),
    ENTITY("dai_entities");

    private final String folder;

    DAI_ContentKind(String folder) {
        this.folder = folder;
    }

    public String folder() {
        return folder;
    }

    public String id() {
        return name().toLowerCase();
    }
}
