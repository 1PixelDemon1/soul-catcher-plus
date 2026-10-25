# Changelog

## Unreleased

- Hide discovered entity-owned companion boss events on the server, including the standard Mowzie boss-event path; preserve encounter participants and block subsequent visibility requests for suppressed instances.
- Expose `SoulAdapter.bossBars` for helper-owned events and excluding shared encounter bars. Automatic direct-field discovery is heuristic; shared, lazily shown, and fully custom HUDs require integration support.
- Replace the collection screen with a custom ink, aged-brass, and teal interface, including species cards, resource indicators, contextual controls, and companion orders.
- Add a detached 3D preview of the selected captured form with automatic rotation, drag rotation, and scroll-wheel zoom. Preview entities are not inserted into the world or AI-ticked.
- Reduce the default preview size and reserve more space for wide models, weapons, and horns: use about 66% of the available width and 70% of the height, a larger footprint margin, a lower maximum scale, and extra space below the model.
- Group collection entries by species and let players switch between forms unlocked by capturing that appearance. Capturing the same variant again converts its soul into energy.
- Add experimental `SoulAdapter.variantData` and `SoulAdapter.previewData` hooks for custom appearance identities and client-safe preview state. Default variant identity excludes equipment and transient poses.
- Request bounded preview data on demand instead of transmitting full captured snapshots; keep collection status updated while the screen is open.
- Enforce one active companion per species across its captured variants, and share recovery cooldowns across that species. Commands for the selected species reach its active form.
- Pause local singleplayer worlds while the collection is open, unless the integrated server is shared over LAN. Base recovery countdowns on world game time so they stop with the paused world.
- Use session-only monotonic-time rate limits for menu actions, previews, and refreshes so networking does not remain locked while world time is paused.
- Add 18-tick client-side departure echoes on companion death and recall, with a noisy dissolve edge travelling from feet to head, fading, soul-fire particles, and a quiet soul escape sound. Combat-entity removal continues through the existing server lifecycle; particles and sound remain available when a model echo cannot be rendered.
- Send explicit departure notifications to tracking clients and use network protocol **3** for collection, preview, and dissolve messages; clients and servers must use matching protocol-3 builds.
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
