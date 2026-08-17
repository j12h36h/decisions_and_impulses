# Decisions & Impulses (DAI)

Official repository for the **Decisions & Impulses (DAI)** Project, including the DAI Engine, official Data Packs, Resource Packs, example games, and supporting tools.

DAI is a data-driven Minecraft customization and game-development framework designed to let creators build complex Minecraft experiences through JSON, Data Packs, and Resource Packs instead of writing a dedicated Java mod for every project.

## Purpose

The purpose of DAI is to provide a reusable engine layer for **Minecraft: Java Edition** that separates gameplay execution, content definition, and presentation.

A DAI project can range from a small automation or visual-effects pack to a heavily customized game with its own world, progression, entities, combat systems, interfaces, quests, currencies, environments, and presentation.

---

## DAI Engine

The **DAI Engine** is the Java/NeoForge runtime that provides the systems DAI-compatible projects use.

It handles the functionality that cannot reasonably be implemented through ordinary Data Packs alone while exposing that functionality back to creators through data-driven definitions.

Current systems include:

* Actions, impulses, decisions, objectives, and sequences
* Conditions, recognition, and perception
* Player automation and navigation
* Combat and interaction behavior
* Reactions and event-driven gameplay
* Custom menus and contextual interfaces
* Screen overlays and animated presentation
* Experience launching and persistent saves
* Custom world generation
* Client/server execution and authoritative world changes
* Native custom content registration
* Hot-reloadable gameplay definitions
* Custom entities, attributes, and animations
* Custom items, blocks, weapons, armor, effects, potions, projectiles, particles, and enchantments
* Game-level customization systems for HUDs, audio, dialogue, quests, currencies, shops, factions, environments, vehicles, and more

The engine provides the reusable functionality.

Individual games and packs define what that functionality should actually do.

---

## Data Packs

DAI-compatible **Data Packs** define gameplay, content, rules, and experiences.

They can use both normal Minecraft Data Pack functionality and systems exposed by the DAI Engine.

Data Packs can define things such as:

* Gameplay actions and behavior
* Objectives and automation sequences
* Conditions and recognition rules
* Reactions to player or world events
* Custom entities and attributes
* Items, blocks, equipment, projectiles, particles, and effects
* Classes and progression systems
* Quests, dialogue, factions, shops, and currencies
* Experience startup and persistent state
* Custom world generation
* Menus and gameplay interfaces
* Server-authoritative world changes
* Compatibility with vanilla Minecraft and other projects

Because these systems live in data instead of hard-coded Java, DAI projects can extend the engine without modifying the engine's source code.

Entire games such as **ClayGrounds** and **AutoCraft MineShaft** can therefore be distributed primarily as Data Packs rather than as separate gameplay mods.

---

## Resource Packs

DAI-compatible **Resource Packs** define presentation.

They can use standard Minecraft Resource Pack functionality alongside the visual systems exposed by DAI.

Resource Packs can provide:

* Custom textures
* Custom item and block models
* Custom entity models
* Articulated entity model parts
* Custom sounds and music
* Particles and visual effects
* HUD presentation
* Menus and interface themes
* Title-screen presentation
* Sprites and overlays
* Game-specific visual identities

This allows multiple projects to use the same DAI Engine while looking and feeling completely different.

A DAI project does not have to look like vanilla Minecraft simply because Minecraft is providing the underlying runtime.

---

## Experiences

DAI can package Data Packs and Resource Packs as complete **Experiences**.

An Experience can define its own:

* Save
* World generation
* Spawn behavior
* Startup logic
* Interface
* Progression
* Content
* Rules
* Gameplay loop
* Presentation

DAI can discover installed experiences before a world exists, create or resume their owned saves, deploy the required Data Pack, and restore the experience-specific interface when launched.

This makes it possible to treat DAI projects more like individual games running through a shared engine.

---

## Creator Philosophy

DAI is built around separating responsibilities instead of forcing every system into one layer.

> **The Engine does.**
>
> **The Data Pack defines.**
>
> **The Resource Pack presents.**

The Java engine should provide reusable capabilities.

The Data Pack should decide how those capabilities create gameplay.

The Resource Pack should decide what that gameplay looks and sounds like.

That separation is what allows DAI to grow as an engine without requiring every game built with it to become another standalone Minecraft mod.
