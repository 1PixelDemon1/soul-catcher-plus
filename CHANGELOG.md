# Changelog

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
