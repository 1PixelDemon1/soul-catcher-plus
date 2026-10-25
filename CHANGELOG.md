# Changelog

## Unreleased

- Keep compact two-pixel-wide inventory lanterns with a narrow top cap, a two-pixel-high cyan area and a dark bottom rim; keep held models unchanged.
- Redraw the 16x16 inventory scythes in a clockwise-rotated layout matching their crafting recipe: shaft from bottom-left to upper-right, thin colored blade across the top and lantern at the right. Remove the black blade band from icons only; retain held artwork, mesh, grip and view transforms.
- Move the held scythe's third-person grip to the middle of its shaft while preserving the user-approved rotations, scales and first-person framing. Replace automatically extruded held-item geometry with closed opaque rectangles and texel-center edge UVs to address see-through seams; preserve the artwork and 16x16 inventory icons. In-game confirmation remains deferred to the user.
- Replace scythe art with wooden shafts, left-facing crescent blades and soul lanterns. Keep cutting edges black while changing only blade-face colors between iron, diamond and netherite.
- Use exact 16x16 inventory sprites and separate 128x128 held textures through Forge's built-in separate-transforms model loader. Share held transforms across all tiers, with approximately player-height third-person scale and separate first-person framing; use small icons for dropped items, frames and head display.
- Keep editable inventory pixel maps, image-generation source art and packaging instructions in `art/scythes`. Held scale and grip are provisional until in-game inspection; no build or gameplay validation was run.
- Base summon cost on twice captured maximum HP (rounded up), and credited kill energy on half maximum HP (rounded down), with a minimum of one. Remove the extra energy reward for collecting an already owned form.
- Add iron, diamond and netherite soul scythes with 1 / 2 / 3 companion limits, 8 / 8 / 9 attack damage, and slower attack speeds than their corresponding vanilla axes. Craft each with two matching materials, a soul lantern and two sticks.
- Replace weighted capacity with companion counts. Check the held scythe only for new summons; changing equipment keeps existing companions. Main-hand scythes take precedence over offhand scythes.
- Keep collection and energy bound to the player's UUID, retain the old item ID for the iron scythe, and migrate old soul records to maximum-health pricing using saved attributes where available. Datapack profiles now control capture availability only.
- Show captured maximum HP and held scythe companion limits in the collection, and provide distinct material textures and English/Russian item labels.
- Hide discovered entity-owned companion boss events on the server, including the standard Mowzie boss-event path; preserve encounter participants and block subsequent visibility requests for suppressed instances.
- Expose `SoulAdapter.bossBars` for helper-owned events and excluding shared encounter bars. Automatic direct-field discovery is heuristic; shared, lazily shown, and fully custom HUDs require integration support.
- Replace the collection screen with a custom ink, aged-brass, and teal interface, including species cards, resource indicators, contextual controls, and companion orders.
- Add a detached 3D preview of the selected captured form with automatic rotation, drag rotation, and scroll-wheel zoom. Preview entities are not inserted into the world or AI-ticked.
- Reduce the default preview size and reserve more space for wide models, weapons, and horns: use about 66% of the available width and 70% of the height, a larger footprint margin, a lower maximum scale, and extra space below the model.
- Group collection entries by species and let players switch between forms unlocked by capturing that appearance.
- Add experimental `SoulAdapter.variantData` and `SoulAdapter.previewData` hooks for custom appearance identities and client-safe preview state. Default variant identity excludes equipment and transient poses.
- Request bounded preview data on demand instead of transmitting full captured snapshots; keep collection status updated while the screen is open.
- Enforce one active companion per species across its captured variants, and share recovery cooldowns across that species. Commands for the selected species reach its active form.
- Pause local singleplayer worlds while the collection is open, unless the integrated server is shared over LAN. Base recovery countdowns on world game time so they stop with the paused world.
- Use session-only monotonic-time rate limits for menu actions, previews, and refreshes so networking does not remain locked while world time is paused.
- Add 18-tick client-side departure echoes on companion death and recall, with a noisy dissolve edge travelling from feet to head, fading, soul-fire particles, and a quiet soul escape sound. Combat-entity removal continues through the existing server lifecycle; particles and sound remain available when a model echo cannot be rendered.
- Send explicit departure notifications to tracking clients and use network protocol **4** for collection, preview, dissolve and scythe-limit state; clients and servers must use matching protocol-4 builds.
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
