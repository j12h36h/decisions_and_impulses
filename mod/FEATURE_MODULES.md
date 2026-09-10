# DAI Engine Feature Modules

DAI Engine 3.9 adds creator-controlled feature modules so projects only pay the runtime cost for systems they actually use.

All modules default to `true` for backward compatibility. Configure them in `config/decisions_and_impulses-common.toml`:

```toml
[modules]
automation = true
navigation = true
combat = true
world_editing = true
interaction = true
inventory = true
creative = true
overlays = true
data_screens = true
animations = true
cinematics = true
experience = true
learning = true
customization = true
physics = true
content = true
entities = true
blocks = true
items = true
vehicles = true
projectiles = true
effects = true
audio = true
fluids = true
interactive = true
portals = true
worldgen = true
reactions = true
state = true
creator = true
managed_packs = true
title_branding = true
particles = true
comic_life = true
```

## What disabling a module does

DAI uses the module matrix in three places:

1. **Bootstrap gating** — startup/server/client runtimes are not registered when their module is disabled.
2. **Tick/render gating** — disabled live modules do not receive their normal per-tick or render callbacks.
3. **Action gating** — actions belonging to disabled controller modules are rejected instead of starting work that cannot advance.

The core configuration/action/network/data spine remains enabled so DAI can still load packs safely and report errors even in a minimal configuration.

## Startup / registry-sensitive modules

Changes to these should be made before launching Minecraft and should be followed by a restart when re-enabled:

- `content`
- `entities`
- `blocks`
- `items`
- `vehicles`
- `projectiles`
- `effects`
- `audio`
- `fluids`
- `interactive`
- `portals`
- `worldgen`
- `particles`
- `managed_packs`

These systems may participate in registry/bootstrap setup. DAI reads their values directly from the on-disk common config early enough to skip that setup.

## Runtime modules

These are also checked at dispatch/tick time, so disabling them removes their recurring runtime work without waiting for another world session:

- `automation`
- `navigation`
- `combat`
- `world_editing`
- `interaction`
- `inventory`
- `creative`
- `overlays`
- `data_screens`
- `animations`
- `cinematics`
- `experience`
- `learning`
- `customization`
- `physics`
- `reactions`
- `state`
- `creator`
- `title_branding`
- `comic_life`

A full Minecraft restart is still recommended after changing a group of module settings so every listener/registry starts from a clean state.

## ComicLife activation

`comic_life = true` only permits the engine capability. ComicLife does not allocate its archive/compiler runtime until a DAI pack contributes the `comiclife:open` marker action. This lets DAI ship the reusable ComicLife engine primitives without adding ComicLife ticking or archive memory to projects that do not use the addon.

A ComicLife-compatible project can emit custom events with the normal DAI action system:

```json
{
  "type": "comiclife_event",
  "arguments": {
    "event": "found_mysterious_relic",
    "caption": "Found the Mysterious Relic",
    "narration": "The object looked important long before its purpose became clear.",
    "importance": 78
  }
}
```

And it can open the Life Library with:

```json
{
  "type": "comiclife_open"
}
```
