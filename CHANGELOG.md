# Changelog

## Unreleased

No changes yet.

## 0.2.0+1.20.1 — 2026-10-27

Release tag: `v0.2.0`. Artifact version: `0.2.0+1.20.1`. Targets Minecraft 1.20.1, Forge 47.4.28, and Java 17.

### Controls, collection, and following

- Fix intermittent following with a goal-local path timer and immediate path updates when the follow goal starts, instead of relying on global mob-tick parity. Preserve native combat and command behavior.
- Add remappable O and held-R bindings for the collection and a non-pausing command wheel, replacing Shift + right-click menu opening. Viewing collections and issuing orders no longer require a held scythe; new summons still do.
- Show soul energy, active companion names, live HP, and current orders in the wheel. Hover a companion and a command, then release the trigger to issue an individual follow/wait/guard/recall order. Releasing in the center/outside, Escape, or focus loss cancels.
- Keep wheel requests separate from collection selection/group scope, validate ownership on the server, and mark unloaded companion health as unknown without loading chunks.
- Add persistent species-wide favorites with a star control and stable favorites-first collection ordering. Older saves begin with no favorites; selection, commands, energy, and captured variants remain independent of the favorite flag.

### Rendering, recalls, and summoned minions

- Apply spectral rendering once through the entity dispatcher, extending support to renderers that bypass `RenderLivingEvent`, including compatible GeckoLib renderers. Preserve ordinary entity buffers and separate dissolve fades; custom buffers, materials, and shaders can still require adapters.
- Refund `floor(current HP / 2)` energy when recalling a living, loaded root companion, with a minimum of zero. Use actual current health, invalidate the reservation before callbacks, and grant no refund for dead, missing, or unloaded companions. Single, group, and automatic recalls use the same rule.
- Let recognized summoned Mob descendants inherit the root companion's player allegiance, permitted target, spectral rendering, and lifecycle through explicit ownership chains. Preserve native child movement/attacks and provide adapter hooks for custom ownership and AI.
- Keep child mobs outside the collection, scythe limits, and independent refunds. Their deaths produce no loot, XP, or soul energy; their credited enemy kills belong to the player.
- Persist minion root links, restore Vex ownership and idle origin when the root is loaded, and keep valid minions idle while the root is unloaded. Recall, death, or permanent removal of the root dismisses loaded descendants; unloaded descendants are rejected when reloaded after invalidation.
- Add `SoulAdapter.summoner`, `configureMinion`, `tickMinion`, and the server-side `SoulMinions.attach` integration hook. Discovery uses ownership rather than proximity guesses; unknown creator links and nonstandard AI can require explicit integration.

### Developer support and compatibility

- Add dev-only `/soulcatcherqa --all` to populate one constructor-default capturable form per registered Mob type in bounded server-tick batches. Preserve known entries and selection, summarize unsupported samples, and enforce the shared 4096-entry collection limit. This tool does not enumerate every appearance and is excluded from normal release builds.
- Use network protocol **6** for collection, favorites, wheel, preview, and departure messages. Update clients and servers together; released 0.1.0 uses protocol 4.

### Validation and limitations

The user manually reported that following, controls, transparency, favorites, and summoned minions work, including an overall positive report for the latest development state. These are user observations, not automated or exhaustive compatibility evidence. No new automated tests, Minecraft launches, or UI automation were run by the agent for this release.

Fresh release packaging completed successfully with `./scripts/gradle.ps1 clean build -x test`, without `-PgameTests` or `-PwithMowzies`. Compilation, resources, runtime JAR reobfuscation, and sources packaging succeeded. Artifact inspection confirmed version 0.2.0+1.20.1, the new controls/minion/rendering code and client Mixin configuration, and exclusion of QA/GameTest content and bundled third-party packages. Tests were excluded; successful packaging is not gameplay validation. Historical 0.1.0 checks do not validate 0.2.0. This remains a pre-1.0 release with experimental APIs and integration limits; see [release notes](docs/releases/0.2.0.md).

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
