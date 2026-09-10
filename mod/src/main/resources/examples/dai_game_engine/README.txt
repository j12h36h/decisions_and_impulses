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

9) Native block properties
   Registry-backed dai_blocks may include an optional "block" object. All
   fields are static registry-shell properties and therefore require a full
   Minecraft restart when changed after startup.

   Supported keys:
     hardness                 (-1 = unbreakable-style destroy time)
     explosion_resistance
     sound                    (SoundType field name, e.g. stone, metal, glass)
     luminance                (0..15)
     friction
     speed_factor
     jump_factor
     requires_correct_tool
     no_occlusion
     replaceable
     random_ticks
     ignited_by_lava
     emissive_rendering       (full-bright face rendering; separate from light)
     map_color                (MapColor field name, e.g. color_cyan)
     push_reaction            (normal, destroy, block, ignore, push_only)

   native_block_properties.json demonstrates the full shape. Omitted block
   settings preserve the old DAI defaults: hardness=1.5, resistance=6,
   stone sound and no emitted light.

10) Entity gameplay events + factions
   entity.gameplay.faction now applies the referenced dai_factions tag(s) to
   the entity during its one-time gameplay initialization. The following
   entity.gameplay.events action references are consumed by the server actor
   runtime without requiring a player carrier:

     spawn
     tick                   (uses entity.behavior_interval as its cadence)
     target_acquired
     target_lost
     mount
     dismount

   Actor action sequences may use the existing movement/combat vocabulary and
   may also dispatch "function" / "run_function" / "server_run_function" or
   "command" / "run_command" / "server_command" actions as the entity.

11) Portal target classes
   Entity portal profiles remain player-only by default for compatibility.
   Add an "affects" array to opt into additional entity classes:

     players
     entities               (non-player entities)
     mobs
     items
     projectiles
     vehicles
     all

   Example:
     "affects": ["players", "vehicles", "projectiles"]

   The same trigger radius, destination, coordinate mode, cooldown, velocity,
   rotation and enter/exit command fields are reused for every subject type.

12) Sound/music runtime targeting
   dai_sounds and dai_music keep their existing carrier/source/volume/pitch
   behavior and now also honor:

     properties.audience      selector used by playsound/stopsound (default @s)
     numbers.min_volume       vanilla playsound minimum-volume argument
     target                   sound position, default ~ ~ ~

   A runtime target supplied by the calling customization action still wins
   over the definition's target field. Sound variants, weights, streaming,
   preload and subtitle ownership remain resource-pack sounds.json concerns;
   looping/crossfade state is not faked by this command-backed layer.

13) Native item/block-item components
   The short top-level "components" map is now decoded by the normal DAI
   content reload path as well as by the early startup scanner. Values remain
   open JSON and are handed to the registered Minecraft DataComponentType
   codec, so DAI does not need a hardcoded field for every vanilla/modded
   component.

   This also means editing "components" during a running JVM is visible to
   registry preflight and is correctly staged for the next launch when the
   native item shell no longer matches startup.

   The older explicit "native_components" spelling is still accepted by the
   early scanner/cache for compatibility. Prefer "components" for new packs so
   reload preflight can compare the current definition directly.

14) Scene environments
    scene_environment.json demonstrates the generic scene_environments format.
    Datapacks/resource packs define camera keyframes, interpolation, drift,
    shake, backgrounds, overlays, textures, items/blocks, text and scene
    composition. DAI supplies only projection/rendering primitives.

    Data path:
      data/<namespace>/scene_environments/*.json

    Resource path:
      assets/<namespace>/scene_environments/*.json

    A resource-pack scene with the same id may replace the data copy.

15) Screen overrides
    screen_override.json demonstrates screen_overrides. Rules match arbitrary
    vanilla or modded Screen classes/titles with globs. mode "replace" opens a
    DAI data screen; non-replace modes retain the native screen and layer
    background_scene / foreground_scene around it.

    Data path:
      data/<namespace>/screen_overrides/*.json

16) Story archives / paged viewers
    story_profile.json demonstrates story_profiles. Session start/end,
    observed-event conditions, importance, cooldowns, titles, narration,
    scene selection, page compilation, panel layout, colors and navigation are
    profile data. DAI does not assign project-specific story meaning.

    Data path:
      data/<namespace>/story_profiles/*.json

    Generic actions:
      story_open
      story_event
      story_session_start
      story_session_end

17) Input profiles
    input_profile.json demonstrates input_profiles. Matching items/content,
    primary/secondary gesture actions, directional thresholds and every
    first-person transform are authored by JSON rather than Java.

    Data path:
      data/<namespace>/input_profiles/*.json

    Resource path:
      assets/<namespace>/input_profiles/*.json

    This replaces the historical project-specific directional input path with
    a reusable pack-defined input/presentation primitive.
