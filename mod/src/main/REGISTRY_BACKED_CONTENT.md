# D.A.I. Registry-Backed Content

D.A.I. supports two content identity modes.

## Reloadable / virtual content

The default mode uses an existing Minecraft item as a carrier. It remains fully
reloadable and does not add a new static Minecraft registry id.

## Registry-backed content

Set:

```json
{
  "registry_backed": true,
  "native_registry": "item"
}
```

or, for a real block plus matching BlockItem:

```json
{
  "registry_backed": true,
  "native_registry": "block"
}
```

A definition at:

```text
data/world_trigger/dai_weapons/kogetsu.json
```

can therefore own the real id:

```text
world_trigger:kogetsu
```

## First-launch early discovery

Registry-backed definitions that are already installed when Minecraft starts no
longer require an initial discovery restart.

During D.A.I.'s earliest bootstrap pass, `DAI_EarlyRegistryScanner` directly
examines:

- D.A.I./mod classpath data resources;
- every installed singleplayer world's `datapacks/` folder;
- dedicated-server world datapack folders directly under the game directory;
- an optional global `datapacks/` folder;
- folder datapacks;
- `.zip` datapacks;
- `.jar` resources on the classpath.

The scanner only parses D.A.I. content folders and only keeps definitions with
`registry_backed: true`. It builds the union registry plan before NeoForge fires
its static `RegisterEvent`s.

The launch sequence is therefore:

```text
Minecraft starts
    -> DAI_EarlyRegistryScanner
    -> current installed datapacks + tombstone cache
    -> generated client aliases
    -> NeoForge RegisterEvent
    -> real Item / Block ids
    -> normal resource loading
    -> normal datapack loading
    -> DAI registry preflight verification
```

If content was already on disk before process startup, no extra restart is
required.

## Late additions while Minecraft is already running

Minecraft's static item/block registration window cannot be reopened by
`/reload`. If a brand-new registry-backed id is installed after Minecraft has
already started, D.A.I. keeps the existing safe fallback:

1. discover the late id during normal datapack reload;
2. write it to the registry startup cache;
3. suspend D.A.I. execution for that pending native definition;
4. show the restart notice;
5. register it on the next process launch.

This is now a fallback path, not the normal installation path.

## Generated vanilla/model aliases

The early registry plan also generates a required hidden client resource pack at:

```text
config/decisions_and_impulses/registry_cache/generated_assets/
```

For an item such as:

```json
{
  "model": "minecraft:iron_sword"
}
```

D.A.I. generates the equivalent of:

```text
assets/world_trigger/items/kogetsu.json
    -> minecraft:item/iron_sword
```

No custom texture is required.

For a block, D.A.I. additionally generates its blockstate alias directly to the
selected vanilla/modded block model. The BlockItem gets an item alias as well.

## Tombstones

The startup cache remains append-compatible for removed content. If an id once
existed in an installed world and is later removed from its datapack, its cached
registry shell is retained so old saved inventories/block data do not suddenly
reference an unknown registry id.

Current installed definitions take precedence over their old cached static
properties on a fresh JVM launch. Cache-only ids act as tombstones.

## Cross-world conflicts

The scanner must build one global static registry plan before the player chooses
a world. If two installed worlds define the same native id with incompatible
static definitions, D.A.I. logs an early registry conflict. Identical definitions
across multiple worlds are deduplicated normally.
