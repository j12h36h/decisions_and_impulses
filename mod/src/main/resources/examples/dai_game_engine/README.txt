D.A.I. JSON Game-Engine Examples
================================

These files are examples only; this folder is intentionally outside data/ so
nothing here registers automatically.

1) title_button.json
   A custom title screen button can use action "launch_experience" and point
   at an id from data/<namespace>/dai_experiences/*.json.

2) experience.json
   Defines the named save, create/load policy, auto_create behavior, worldgen
   profile, first-join and normal-join DAI action sequences, and experience UI
   input behavior. With auto_create=true, DAI configures Minecraft's normal
   Create World state and invokes Create automatically when the requested world
   preset is available.

3) worldgen.json
   Lives in data/<namespace>/dai_worldgen/*.json. world_preset names a normal
   Minecraft datapack world preset, so raw Minecraft/NeoForge worldgen JSON can
   still define dimensions, noise, biomes, features, structures, structure
   sets, template pools, etc. generation_commands and initial_structures add a
   simpler DAI bootstrap layer for staged/structure-driven games. A non-vanilla
   world_preset must be available to Minecraft's Create World registry; if it
   is not, DAI leaves the configured Create World screen open instead of making
   a world with the wrong preset.

4) entity_pig.json
   Lives in data/<namespace>/dai_entities/*.json. DAI registers a real custom
   EntityType at startup, uses a supported vanilla entity/renderer template,
   applies native attributes from JSON, can run a DAI behavior sequence, and
   can naturally spawn from the embedded spawning policy.

5) behavior_sequence.json
   A normal DAI action definition that can be referenced by an entity's
   entity.behavior_sequence field. Entity actor support currently understands
   movement/look/target/wander/jump/stop/wait-style actions and leaves vanilla
   goal AI enabled by default. This is deliberately the same DAI sequence data
   format; unsupported player-only actions are skipped rather than run against
   the wrong actor.

Supported initial vanilla entity templates:
  minecraft:pig
  minecraft:cow
  minecraft:chicken
  minecraft:sheep
  minecraft:wolf
  minecraft:cat
  minecraft:slime
  minecraft:zombie
  minecraft:villager

Static registry note:
  dai_entities are registry-backed content. They must be present before the
  corresponding static registry event. DAI's early scanner and tombstone cache
  handle this the same way as registry-backed DAI items/blocks; an entity first
  introduced after startup is staged for the next full launch.
