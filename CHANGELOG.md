# Changelog

## Unreleased

- Fix intermittent companion following caused by checking global mob ticks from a goal that may execute only on odd ticks. Use a goal-local path timer and request movement immediately when the goal starts; preserve native combat and command behavior.
- Add configurable O and held-R key bindings for the full collection and a non-pausing command wheel, replacing Shift + right-click menu opening. Menus work without a held scythe; new summons still require one.
- Display soul energy, active companion names, live HP and current orders in the wheel. Hover a companion and a command, then release the trigger to issue an individual follow/wait/guard/recall order; release in the center or outside, or Escape/focus loss, cancels.
- Use bounded active-only server snapshots with explicit unknown health for unloaded companions, per-entry ownership validation, and independent wheel requests that preserve collection selection/group scope. Use network protocol 5; released 0.1.0 remains protocol 4.

The user confirmed the following fix in-game. The new key bindings and command wheel have only received source review: no build, compilation, tests or game launch was run for these changes.

## 0.1.0+1.20.1 — 2026-10-25

First tagged pre-1.0 milestone. Release tag: `v0.1.0`. Artifact version: `0.1.0+1.20.1`.

### Soul collection and companions

- Add a Forge 1.20.1 mod targeting Java 17, with a Gradle wrapper and local development bootstrap.
- Mark enemies with a scythe for three minutes, earn energy for credited victories, and collect owner-bound soul remnants within one minute.
- Store the collection and energy by player UUID, independently of the scythe. Group entries by species and unlock appearance variants through capture.
- Restore the original mob type as a spectral companion with follow, wait, guard, attack, and recall commands for one species or the group.
- Enforce one active form per species, a shared 60-second recovery cooldown after defeat, and the held scythe's companion limit. Existing companions remain when equipment changes.
- Validate commands on the server, reject stale summons, block allied damage, and suppress companion loot and energy rewards.
- Add an optional Ferrous Wroughtnaut adapter that preserves native combat and excludes allies from area-target queries.
- Suppress discovered entity-owned companion boss bars while preserving encounter participants. Add `SoulAdapter.bossBars` for helper-owned events and excluding shared bars; automatic discovery remains heuristic.

### Scythes, economy, and artwork

- Add iron, diamond, and netherite scythes with limits of 1, 2, and 3 companions; attack damage of 8, 8, and 9; and attack speeds of 0.7, 0.8, and 0.8 attacks per second.
- Set summon cost to `ceil(captured maximum HP * 2)` and victory energy to `floor(maximum HP / 2)`, each at least one. Collecting an already owned variant gives no second energy reward.
- Craft iron and diamond scythes using two matching materials, a soul lantern, and two sticks. Upgrade the diamond scythe with a netherite ingot and netherite upgrade template at a smithing table.
- Add material-specific scythe artwork with exact 16x16 inventory icons and separate 128x128 held textures. Icons follow the crafting layout and use compact lanterns with two-pixel-high cyan flames.
- Keep enlarged held scythes one model pixel thick, anchor their shafts at the fist, and use closed opaque meshes to address see-through seams. Keep editable pixel maps, source artwork, and packaging instructions in `art/scythes`.

### Interface, effects, and integrations

- Add an English/Russian ink, brass, and teal collection interface with species cards, captured-form selection, health and resource indicators, and contextual orders.
- Add detached, rotatable, zoomable previews with extra margins for large models and weapons. Request bounded appearance data instead of full captured snapshots; preview entities are not inserted into the world or AI-ticked.
- Pause unpublished singleplayer worlds while the collection is open. Tie recovery displays to world time and use session-only monotonic network rate limits so menu actions remain available during a pause.
- Add 18-tick client-side departure echoes on death and recall, with a noisy bottom-to-top dissolve, fading, soul-fire particles, and a quiet escape sound. Keep server entity removal in its existing lifecycle and retain a particle/sound fallback when an echo cannot be rendered.
- Expose experimental adapter, event, appearance-identity, preview, and visual hooks. Datapack profiles control capture availability; legacy cost, slots, and energy overrides are ignored.
- Use protocol **4** for collection, preview, departure, and scythe-limit state. Update clients and servers together.
- Write ledger schema 2 and migrate older entries to maximum-health pricing from saved attributes where available, falling back to half the old cost as inferred HP. Keep the iron scythe's original item ID.

### Validation and limitations

Fresh release packaging completed successfully with `./scripts/gradle.ps1 clean build -x test`, without development test sources or optional integration dependencies. The runtime JAR was inspected for version metadata, final artwork, meshes, smithing recipe, mixins, and exclusion of test content and third-party packages. Tests were not run.

The user reported that earlier base gameplay and the held grip/seam correction work in game. The latest inventory artwork, independent held-model depth, and netherite smithing changes have not been explicitly confirmed in game. No new automated tests, game launches, or UI automation were run for this milestone at the user's request. Successful packaging is not gameplay validation.

The passing checks in [historical prototype validation](docs/VALIDATION.md) describe an earlier development snapshot, not this release. Balance and public interfaces remain provisional; arbitrary modpacks, custom AI/renderers, shaders, and two-client multiplayer are not comprehensively validated. See [release notes](docs/releases/0.1.0.md).
