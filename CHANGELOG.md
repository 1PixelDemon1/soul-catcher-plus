# Changelog

## Unreleased

- Replace the collection screen with a custom ink, aged-brass, and teal interface, including species cards, resource indicators, contextual controls, and companion orders.
- Add a detached 3D preview of the selected captured form with automatic rotation, drag rotation, and scroll-wheel zoom. Preview entities are not inserted into the world or AI-ticked.
- Group collection entries by species and let players switch between forms unlocked by capturing that appearance. Capturing the same variant again converts its soul into energy.
- Add experimental `SoulAdapter.variantData` and `SoulAdapter.previewData` hooks for custom appearance identities and client-safe preview state. Default variant identity excludes equipment and transient poses.
- Request bounded preview data on demand instead of transmitting full captured snapshots; keep collection status updated while the screen is open.
- Enforce one active companion per species across its captured variants, and share recovery cooldowns across that species. Commands for the selected species reach its active form.
- Use network protocol **2** for the expanded collection and preview messages; clients and servers must use matching protocol-2 builds.
- Extend English and Russian interface text for species, captured forms, preview controls, and summon availability.

Validation was not run for these changes at the user's request. No build, compilation, unit tests, GameTests, game launch, or UI automation was performed; gameplay and visual verification are deferred to the user. The earlier 0.1.0 validation results do not validate this unreleased work.

## 0.1.0+1.20.1 — Initial prototype — 2026-10-25

- Add a Forge 1.20.1 project targeting Java 17, with a Gradle wrapper and local development bootstrap.
- Add the Soul Scythe, harvest/command modes, and an English/Russian collection screen.
- Mark enemies for three minutes and collect their soul remnants within one minute of a credited victory.
- Store soul collections and energy independently of the scythe; convert repeated captures into energy.
- Restore the original mob type as a companion with follow, wait, guard, attack, and recall commands.
- Enforce summon costs, active capacity, one active instance per soul, and a recovery cooldown after defeat.
- Validate commands on the server, reject stale summons, block allied damage, and suppress companion loot and energy rewards.
- Add spectral rendering for standard living-entity renderers.
- Add an optional Ferrous Wroughtnaut adapter that preserves native combat and filters allies from Mowzie's area attacks.
- Expose experimental adapter, event, and visual hooks, plus datapack balance profiles.
- Add persistence tests, gameplay tests, and API/validation documentation.

This is a pre-1.0 prototype. Balance and UI are provisional; arbitrary modpacks, custom renderers, shaders, and two-client multiplayer have not been fully validated. See [release notes](docs/releases/0.1.0.md).
