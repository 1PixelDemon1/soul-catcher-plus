# Changelog

## Unreleased

- Fix production startup on Minecraft 1.20.1 by reobfuscating the Warden anger-management shadow field instead of aliasing its package-private runtime name.

## 1.2.2 — 2026-11-12

Core/Compat targets: Minecraft 1.16.5 / Forge 36.2.42 / Java 8, 1.19.2 / Forge 43.5.0 / Java 17, 1.20.1 / Forge 47.4.28 / Java 17, and 26.3 / Forge 66.1.0 / Java 25.

- Add adapter-level `followRange` and detached `SoulPreviews` APIs across ports. Keep ordinary 40/64-block defaults while allowing integrations to raise the owner range and hard-teleport ceiling.
- On 1.19.2/1.20.1/26.3, correct perched Raven rotation sampling/yaw wrapping and owned Wither hover/return/head interpolation.
- Add command-driven Warden melee/Sonic Boom and suppress autonomous encounter targeting/digging/darkness on those newer ports; no Warden is added to 1.16.5.
- Remove the artificial Patchouli minimum while requiring a matching Minecraft/Forge artifact. Forge 26.3 Patchouli support remains unverified.
- Retain standalone core/Compat isolation and historical release requirements.

All four fresh core builds, archive audits, and dedicated-server startups passed; servers stopped normally with exit 0. The 1.16.5 core/Compat client completed resource/sound/atlas startup. Source/API review found no remaining actionable issues. No automated tests/GameTests/UI or gameplay/multiplayer tests ran. See [release notes](docs/releases/1.2.2.md) for profiles, frozen outputs, and known external diagnostics.

## 1.2.1 — 2026-11-10

Core-only release for Minecraft 1.16.5 / Forge 36.2.42 / Java 8, 1.19.2 / Forge 43.5.0 / Java 17, 1.20.1 / Forge 47.4.28 / Java 17, and 26.3 / Forge 66.1.0 / Java 25. The 26.3 target/minimum changes from 66.1.5; historical releases keep their original requirements. Compat is not updated or released, and its compatibility with core 1.2.1 was not verified.

- Add built-in vanilla Wither and Ender Dragon companion adapters/controllers on all four platforms, retaining native entities and shared commands, ownership, targeting, return, recall, and recovery rules. Direct Wither heads at approved targets without random idle shots or spawn/terrain effects; replace Dragon encounter flight paths with owner/guard/attack movement.
- Scope boss protection changes to owned vanilla companions: filter allied explosion/contact/breath effects and terrain damage, detach soul dragons from End-fight adoption/completion, prevent crystal healing/encounter rewards, and validate persisted breath ownership. Native enemy attacks and multipart bodies remain; gameplay correctness is not established by startup checks.
- Add production `/soulcatcher scroll <namespace:entity|namespace@entity> [player]`, permission level 2, for a registered mob type's default form. Validate an unspawned sample, use shared scroll classification, and refuse inventory overflow. Retain production energy commands; neither requires QA sources.
- Add an inventory-triggered Forgotten Soul advancement for obtaining a Forgotten Soul Scroll.
- Revise Russian names and translate unnamed built-in companions in the wheel per client's language while preserving custom names. Use protocol 13; client and server must run the matching release for the same Minecraft version.
- Fix collection group-order highlighting/mode availability to ignore unloaded and custom-brain recipients such as Raven while keeping Recall/Teleport as separate actions.
- Expand the optional English/Russian guide to 18 entries and five categories per language (46 localized content JSON files plus the book definition), with production command examples and four recipe-only pages showing four recipes per spread. Use real recipe IDs and keep prose outside recipe spreads. No working Forge Patchouli integration is claimed for 26.3.
- Remove obsolete/generated asset intermediates and unsupported legacy 1.16.5 test sources/dependency. Retain approved authoring/runtime inputs and consolidate workflows in `docs/ASSETS.md`.

All four fresh `build -x test` runs and JAR audits passed, including ZIP/class-major 52/61/61/69, metadata/JSON, guide pagination/recipe references, and exclusion of QA/JUnit/Jabel/addon content. All four dedicated servers reached Done in isolated playerless worlds and saved/stopped with exit 0, including the fresh 26.3 repeat after final breath-Mixin corrections. The 1.16.5 development client loaded Patchouli/resources, sound, and atlases with Twilight Forest/Aquamirae present and was left open for the user; no fresh other-platform client result is claimed. Known external Twilight resource and development Realms warnings remain.

No automated tests, GameTests, or UI automation ran. These are build/archive/startup checks, not gameplay, multiplayer, boss/terrain/friendly-fire, or exhaustive visual checks. Earlier release results are not reused as fresh evidence. See [1.2.1 release notes](docs/releases/1.2.1.md) for frozen runtime hashes and limits.

## 1.2.0 — 2026-11-10

Release core builds for Minecraft 1.16.5 / Forge 36.2.42 / Java 8, Minecraft 1.19.2 / Forge 43.5.0 / Java 17, Minecraft 1.20.1 / Forge 47.4.28 / Java 17, and Minecraft 26.3 / Forge 66.1.5 / Java 25. Each has a separate matching 1.2.0 Compat build.

- Add Soul Shaman, a passive 8-HP Forgotten Soul. When near its owner, its cast grants Strength I and Regeneration I for eight seconds to the owner and eligible active souls within six blocks of the owner. Casts begin at seven-second intervals; native effect merging/immunities remain in force.
- Add Shaman to Forgotten Scroll creative presets and the existing 2% root-chest loot roll, sharing successful drops equally with Raven and Courier. Shaman unlocks through scrolls rather than ordinary scythe capture.
- Give Forgotten Soul models shared amulet artwork and synchronized Shaman casting, with editable model sources and export tooling.
- Add an optional English/Russian Patchouli guide, first-login delivery, conditional book-plus-soul-sand recipe, and creative availability. Paginate translations independently with reserved title/footer space and escaped percent signs; recipe pages reference real recipe IDs. Baselines are 53.3/77/85 for 1.16.5/1.19.2/1.20.1. No matching Forge 26.3 Patchouli build was identified for this release, so guide availability is not claimed there.
- Adopt the supplied transparent branding: lettered presentation banner, 256-pixel mod icon, and 32-pixel creative icon, preserving original PNGs.
- Bring the older maintained ports forward from 1.0.0, including the 1.1.0 Forgotten Souls, scrolls, cage models, appearance settings, offline persistence, and Teleport/return behavior. Keep generic extension APIs in the core and mod-specific corrections in the standalone Compat project.
- Add a native 26.3 port using queued rendering, SDL input, item-component NBT, dynamic enchantments, current recipe formats, and a legacy flat-ledger fallback. This data fallback does not establish universal cross-Minecraft world migration.
- Use Forge 43.5.0 as the new 1.19.2 build target/minimum and retain Forge 47.4.28 for 1.20.1. Existing released artifacts/notes keep their historical baselines. All four core builds use protocol 12; matching protocol values do not allow cross-Minecraft connections.

All four core JARs built successfully. Three existing persistence JUnit checks passed on each of 1.19.2 and 1.20.1, and three adapted checks passed on 26.3; legacy automated tests were not run on 1.16.5. All four core clients reached title/resource reload. Patchouli startup and the 15-entry bilingual guide (38 JSON resources) passed resource loading on the older three versions. All four dedicated core servers reached the startup marker in isolated new worlds without players and saved/stopped normally.

These are build, persistence-unit, and startup checks. They do not establish in-game loot, buffs, combat, multiplayer, every renderer, or universal world migration. Compat's 26.3 startup used only the core; Mowzie/Aquamirae/Patchouli Forge 26.3 releases were not identified in the release lookup, and an official Twilight Forest Forge 26 build was not verified. See [release notes](docs/releases/1.2.0.md).

## 1.1.0+1.16.5 — 2026-11-08

Local release for **Minecraft 1.16.5 / Forge 36.2.42 / Java 8**. This milestone updates the core only; the 1.19.2/1.20.1 ports and external integration addon have no new release here.

### Forgotten Souls and scrolls

- Add rare Forgotten Soul Scrolls to root loot tables whose path begins with `chests/`, in any namespace. Each table generation gets one 2% chance to append one scroll, with equal Raven/Courier selection. The additive Forge global loot modifier is datapack-configurable; generated chests and nonstandard chest paths are not rerolled or automatically covered.
- Add Soul Raven, a passive 4-HP collector with its own AI. It fetches eligible real dropped items and owner-bound soul remnants within 20 blocks, carries up to eight entities, delivers to the owner, and perches on their shoulder when idle. Soul delivery requires an eligible cage, but no held scythe. Preserve actual cargo and ordinary pickup rules; inventory overflow remains in the world rather than being copied or discarded.
- Add Soul Courier, a passive 8-HP porter whose chest opens each visitor's own 27-slot Ender Chest. Animate its chest and pause walking while it is open. Both Forgotten Souls unlock through scrolls rather than ordinary combat capture.
- Add consumable Soul, Boss, and Forgotten Scroll families with distinct 16×16 sprites and explicit Forgotten creative variants. Ordinary scrolls default to Pig; Boss Scrolls default to Wither. Successful use adds a fresh collection identity without energy and consumes one scroll even in creative mode; known forms, invalid data, and full collections keep the item. Retain the old courier-scroll ID outside the creative tab.

### Companion behavior and presentation

- Preserve active companions, minions, native saved state, and carried entities across logout/world reload. Pause valid owned entities' native ticks while their owner is offline; resume loaded entities when the owner returns. Player death and dimension changes retain their recall behavior.
- Replace the old 24-block forced teleport with a standard-brain Follow/Aggressive return policy: abandon combat beyond 40 blocks, path back until within four blocks, and attempt a safe teleport beyond 64 blocks or after 100 ticks without progress. Failed searches retry at most once per 20 ticks.
- Add manual Teleport in the collection and wheel for one recipient or the group, requiring a held scythe and loaded companions in the current dimension. Preserve HP, energy, identity, and behavior mode; clear targets and move Guard anchors to the destination without loading chunks.
- Lock wheel recipients until the pointer returns to the center, preserving selection through roster changes and preventing paging from switching a locked recipient. Highlight the current collection order, including group scope only when all active entries share it.
- Add a local soul color/opacity editor with live previews, HSV/HEX controls, reset, and 10–100% opacity (42% default). Save per-client settings without synchronizing them to other players.
- Synchronize names as `PlayerName's CreatureName`, preserving captured names and exposing ownership through normal entity metadata while hiding floating soul nameplates.
- Render all four cages as animated 3D meshes in first/third-person hands, with glass, chain grips, orbiting souls, and material-specific details. Retain inventory sprites and add editable Blockbench/model/sprite sources and export scripts.

### Core isolation and upgrade

- Move mod-specific integration sources/resources into an external project. Routine core builds, development launches, and playtest packaging no longer include the addon; generic public APIs remain in the core, including `SoulAdapter.usesStandardBrain` for custom behavior with canonical ownership/lifecycle checks.
- Register creative-scroll use through the common `SoulEvents` handler instead of subscribing the item class, avoiding dedicated-server event-bus reflection over its client tooltip signature. This correction was checked in source/bytecode; a dedicated server was not launched.
- Use protocol **12** for Teleport support; clients and servers must update together from 1.0.0/protocol 11. Retain ledger schema 3 and compatible 1.16.5 collection/cage data. Normalize the old 20-HP Courier prototype to 8 HP without replacing its identity.
- Do not assume a 1.0.0 integration addon accepts this core: earlier addon metadata requires an exact core version, and addon compatibility is outside this release's validation.

### Validation

Final release packaging succeeded in 18 seconds with `.\scripts\gradle.ps1 build -x test --offline` using no QA or playtest flags. Read-only artifact inspection passed: 146 Java 8 runtime classes, 46 parsed JSON documents, correct version/dependencies, five advancements, resource format 6, scroll/model/loot resources, and no QA/test helpers, Jabel, embedded JARs, addon classes, or Java 17 record-runtime leaks. Final source review found no further blocking issues after the creative-scroll registration fix.

The development client compiled and reached sound-engine/texture-atlas startup with the manual QA sources, Twilight Forest, and Aquamirae enabled, without the compatibility addon; it was left open for the user. Existing Twilight resource and unauthenticated-development Realms warnings remain, with no fatal Soul Catcher error observed during startup. The QA command was not executed. No automated tests, GameTests, UI automation, dedicated-server launch, multiplayer checks, or in-game chest-loot verification were performed. Earlier development/prototype results do not validate this final release. See [1.1.0 release notes](docs/releases/1.1.0.md) for artifacts, checksums, and validation limits.

## 1.0.0+1.19.2 — 2026-10-31

Add a Forge **1.19.2 / 43.5.2 / Java 17** release of the 1.0.0 core and separate combined integration addon, identified as `v1.0.0+1.19.2`. This is an additive platform release; the original `v1.0.0` tag and its 1.16.5/1.20.1 release history remain unchanged.

- Bring the earlier 1.19.2 development port to the 1.0.0 feature set: four scythe/cage tiers, Supreme equipment, Soul Capacity/Harvester/Slot, five advancements, scrolling/searchable collection, favorites, confirmed form deletion, individual/group commands, Aggressive mode, minion ownership/cleanup, and spectral presentation.
- Use native 1.19.2 UI, explicit supported summoner links, and the version's music APIs. Netherite smithing uses a diamond item and netherite ingot without an upgrade template, preserving cage contents.
- Require Forge `[43.5.2,44)` and matching `1.0.0+1.19.2` core/addon builds. Optional integration API baselines are Mowzie's Mobs 1.6.3 with GeckoLib 3.1.40, Twilight Forest 4.2.1518, and Aquamirae 6.API15 with Obscure API 15 or later for the matching Minecraft version. Source/bytecode inspection is not gameplay validation or a promise covering every third-party version.
- Use protocol **11** with collection snapshots split into at most 32 rows per part. Update clients, servers, and installed addons together; the old 1.19.2 development protocol 9 is incompatible. Preserve compatible same-Minecraft collection data; old player energy is not converted into cage energy, and cross-Minecraft world migration is not supplied.

Release packaging with `.\scripts\gradle.ps1 build -x test` and separate isolated compilation of the manual QA command succeeded. Both runtime JARs passed archive, JSON, version metadata, Java 17 bytecode, and core/addon isolation checks, with five core advancements, resource format 9, legacy smithing recipes, and no bundled QA/test helpers, Jabel, or embedded dependencies. Tests were excluded; no game launch, automated tests, GameTests, or UI automation were run for this supplement. See the [Forge 1.19.2 release supplement](docs/releases/1.0.0-forge-1.19.2.md) for artifacts, checksums, installation, and validation limits.

## 1.0.0 — 2026-10-31

First major release, with separate Forge builds for **Minecraft 1.16.5 / Forge 36.2.42 / Java 8** and **Minecraft 1.20.1 / Forge 47.4.28 / Java 17**. Artifact versions are `1.0.0+1.16.5` and `1.0.0+1.20.1`; each has a core JAR and a separate combined integration addon. These are version-specific builds, not one cross-version JAR.

### Equipment and soul energy

- Move energy from the player ledger into transferable, non-stackable soul cages. Iron, diamond, netherite, and supreme cages hold 150, 400, 1000, and 2000 energy before enchantments. Old player energy is not converted; collection entries and favorites remain player-owned.
- Use only cages in the offhand or hotbar. Spending uses one active cage, preferring the offhand and then the leftmost hotbar cage; an empty or undersized active cage is not bypassed, and cages never pool a summon price. Rewards and recall returns fill eligible cages in that order; excess energy is lost.
- Add the supreme bone scythe and Nether Star cage. Scythes have base companion limits of 1/2/3/4, attack damage of 8/8/9/10, and attack speeds of 0.7/0.8/0.8/0.8. Preserve the original iron scythe item ID.
- Add Soul Capacity I–III for cages (+one third of base capacity per level), Soul Harvester I–II for held scythes (+50%/+100% victory energy), and rare Soul Slot I armor (+one companion per worn piece, up to four). A supreme scythe with four enchanted armor pieces allows eight companions; changing equipment does not dismiss existing summons.
- Keep health-based summon prices and victory rewards, and `floor(current HP / 2)` recall returns for living, loaded root companions only. Capturing an already known form grants no second energy reward. Capturing requires an eligible cage but spends no energy.
- Add crafting recipes for cages and supreme equipment. Netherite smithing preserves cage contents; 1.16.5 uses diamond equipment plus an ingot, while 1.20.1 also requires the vanilla upgrade template.

### Collection, commands, and presentation

- Require a soul scythe in either hand for the remappable O collection and held-R wheel. Add whole-group wheel orders and Aggressive following, which selects eligible visible hostiles within 16 blocks of both companion and owner.
- Force companions in Follow or Aggressive mode to leave combat and attempt a safe return when more than 24 blocks from the owner. Retry every five server ticks independently of native attack goals.
- Replace collection paging with scrolling and name/ID search. Keep species favorites and captured-form previews, and add confirmed deletion of one form, recalling it first if active. Other forms remain; a deleted form can be captured again.
- Highlight species containing a captured form with at least 150 maximum HP in purple. Use floating soul-lantern remnants graded by base victory energy and a flaming skull for the same high-health threshold; this is a health rule, not a universal boss detector.
- Add animated four-frame soul cages, refreshed scythe icons and held meshes, supreme bone/Nether Star artwork, a dedicated creative tab, and a medium soul-lantern mod logo.
- Add five advancements for joining, first capture, obtaining both supreme items, capturing a high-health soul, and reaching eight available companion slots.
- Correct the 1.20.1 follow-goal repath delay for its reduced tick cadence and include the medium-soul creative icon in that version's item atlas.

### Integrations and synchronization

- Separate Mowzie's Mobs, Twilight Forest, and Aquamirae corrections into optional `soulcatcherplus-compat` JARs. Each addon requires its exact matching core version; install chosen creature mods and their libraries separately. The core requires no creature mod or animation library.
- Keep generic capture, native companion AI, spectral rendering, ownership, boss-bar suppression, music filtering, and public extension hooks in the core. Preserve ownership-based minion cleanup, with native master restoration before AI and opt-in attribution for children constructed during a summoner tick. Unrecognized summons still require adapters.
- Add Twilight Forest Snow Queen ice-crystal attribution and Lich clone/minion master links, Wroughtnaut activation/appearance and area-attack ally filtering, and version-specific Aquamirae Cornelia music-cooldown hooks. Suppress identifiable companion boss music without globally muting hostile bosses or ordinary sounds.
- Use network protocol **11** on both Minecraft lines. Split collection snapshots into parts of at most 32 rows and display only fully assembled snapshots. Update clients, servers, and installed addons together; protocol 6 from 0.2.0 is incompatible.
- Write ledger schema 3. Retain compatible same-version collection data, while storing new energy on cages; no cross-Minecraft world migration is supplied.

### Validation and limitations

Release packaging succeeded with `.\scripts\gradle.ps1 build -x test` for both Minecraft versions. All four runtime JARs passed archive/JSON/metadata/isolation inspection, with Java class versions 52 for 1.16.5 and 61 for 1.20.1, five advancements in each core, and no bundled Jabel, QA helpers, or nested third-party JARs. The 1.20.1 manual QA command was also compiled separately and is excluded from release JARs; compilation did not execute tests.

At the user's request, no release client/server launch, automated tests, GameTests, or UI automation were run. Earlier user gameplay reports apply to their development snapshots, not to comprehensive validation of these final artifacts or the new 1.20.1 port. Dedicated source/API review and successful packaging do not establish universal mod compatibility. See [1.0.0 release notes](docs/releases/1.0.0.md) for installation, integration baselines, migration, and remaining limits, and the [validation record](docs/releases/1.0.0-validation.md) for source revisions and completed checks.

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
