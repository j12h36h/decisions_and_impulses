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

4) entity_native.json + native_hunter_behavior.json
   Preferred fully-native entity path. Leave carrier absent (or use dai:native)
   and DAI registers a real custom EntityType backed by DAI_JsonMob instead of a
   pig/zombie/spider class. entity.width + entity.height are the actual physical
   hitbox dimensions. entity.behavior_sequence owns movement, targeting, combat
   and other AI decisions. The native renderer is intentionally empty so a
   datapack/resource pack can own presentation without replacing vanilla mobs.

5) entity_pig.json
   Legacy compatibility path. Supplying a supported vanilla carrier still
   constructs that vanilla mob class and renderer under the custom EntityType.

6) behavior_sequence.json
   A normal DAI action definition that can be referenced by an entity's
   entity.behavior_sequence field. Native entity actor support understands
   targeting, chase/follow, look, melee attack, flee, wander, jump, stop and
   wait/idle actions. Conditions can branch on health, target presence/distance,
   line of sight, player distance, water/ground state and random chance.

Native entity mode:
  omit carrier
  dai:native

Legacy vanilla entity templates:
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

7) Experience player controls (optional)
   experience.json may include a small "controls" object for autonomous DAI
   gameplay. This is a creator ceiling, not a forced client setting: the player
   may always choose stricter automation permissions/performance limits in the
   DAI config. Omit controls entirely for legacy/permissive behavior.

   Supported keys:
     automation
     automation_movement
     automation_combat
     automation_world_editing
     max_actions_per_second   (0 = no experience-specific cap)
     max_action_queue_size    (0 = no experience-specific cap)

   This separation is intentional. Scripted game logic, quests, UI, bootstrap
   functions and other direct datapack actions are not disabled just because a
   player turns off DAI's autonomous gameplay helper.

8) Reading player preferences from JSON logic
   Client action conditions can use type "config_value" with target set to one
   of the stable keys below. Boolean and numeric values use the normal DAI
   condition operators, letting creators provide graceful fallbacks without
   taking ownership of the player's config.

   automation_enabled
   automation_movement
   automation_combat
   automation_world_editing
   max_actions_per_second
   max_action_queue_size
   auto_enable_addons
   auto_enable_managed_resource_packs
   custom_title_screens
   overlay_opacity
   debugging
