# D.A.I. JSON Title Screens & Official Pack Browser

This source tree includes the first D.A.I. title-screen framework and the curated Official Packs browser.

## JSON title screens

Title definitions use:

`data/<namespace>/dai_title_screens/<name>.json`

D.A.I. can see the built-in definitions immediately at the title screen. It also early-scans local world datapacks at client startup so an installed datapack may contribute a presentation before a world is opened. Global development/user overrides can be placed in:

`config/decisions_and_impulses/title_screens/*.json`

The highest-priority enabled definition wins. Keep alternative examples disabled until they are intentionally activated.

The built-in examples are:

- `default.json` — enabled D.A.I. showcase.
- `minimal_example.json` — minimal copyable example.
- `item_icon_showcase.json` — focused live-item/animation showcase.

### Button example

```json
{
  "id": "mods",
  "label": "MODS",
  "action": "open_mods",
  "anchor": "center",
  "x": 0,
  "y": 16,
  "width": 230,
  "height": 24,
  "icon": {
    "type": "item",
    "id": "minecraft:carrot",
    "scale": 1.0,
    "offset_x": 9
  },
  "style": {
    "background": "#B82D2118",
    "hover": "#E06B3D1B",
    "border": "#FFE8893E",
    "text": "#FFFFFFFF"
  },
  "hover_animation": {
    "type": "spin",
    "speed": 0.85,
    "amount": 1.0
  }
}
```

The icon is a real Minecraft `ItemStack` rendered into the UI, so normal item models/resource-pack overrides are reused automatically.

Supported hover animations in this revision:

- `none`
- `spin`
- `bob`
- `pulse`
- `orbit`

Supported title actions:

- `open_singleplayer`
- `open_multiplayer`
- `open_options`
- `open_mods`
- `open_official_packs`
- `open_url` (uses the button's `url` field)
- `reload_title_json`
- `quit`

D.A.I. only replaces the vanilla title screen. The normal Minecraft world selector, multiplayer screen, options, and NeoForge Mods screen remain their normal destination screens.

## Official Packs browser

The title-screen action `open_official_packs` opens the D.A.I. browser.

The browser is driven by a curated website catalog, configured in:

`data/decisions_and_impulses/dai_pack_browser/browser.json`

Default catalog URL:

`https://j12h36h.github.io/dai/api/packs.json`

Until that website JSON is published, D.A.I. uses the bundled fallback:

`data/decisions_and_impulses/dai_pack_browser/official_packs.json`

A ready-to-copy website schema example is included as:

`data/decisions_and_impulses/dai_pack_browser/website_catalog_example.json`

### Pack behavior

- Datapack components install into the selected local world's `datapacks` directory.
- Resource-pack components install into D.A.I.'s managed resource directory and are registered as auto-enabled client resource packs on the next launch.
- Combo packs are one catalog card with multiple components, so data + resources install/uninstall together.
- The browser records ownership in `config/decisions_and_impulses/packs/installed.json` and only removes paths recorded as D.A.I.-managed.
- ZIP files are validated before commit. Optional SHA-256 values can be supplied by the website catalog.
- Direct download URLs are restricted to HTTPS CurseForge/ForgeCDN hosts.
- If a direct `download_url` is supplied by the website catalog, D.A.I. uses it. Otherwise the bundled compatibility path can resolve a CurseForge file id + file name.

### Restart behavior

After any successful install, update, or uninstall, the browser becomes dirty. Leaving it opens a restart-required screen instead of returning to the title screen. The user can return to the browser or exit Minecraft. D.A.I. does not attempt launcher-specific automatic relaunching.

This intentionally gives the early native-registry scanner and managed resource-pack finder a clean JVM launch with the final installed pack set.
