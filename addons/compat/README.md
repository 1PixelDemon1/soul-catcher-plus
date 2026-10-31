# Soul Catcher Plus: Mod Integrations — Forge 1.19.2

Optional, separately installable combined addon for Soul Catcher Plus 1.0.0+1.19.2.
Requires Minecraft 1.19.2, Forge 43.5.2 and Java 17. Install the exact matching core
and addon on both client and server. No creature mods or animation libraries are
required by the core, and no third-party code or assets are bundled.

API/bytecode-reviewed integration baselines:

- Mowzie's Mobs 1.6.3: Wroughtnaut activation, preview and area-attack ally filtering;
  its development profile uses GeckoLib 3.1.40.
- Twilight Forest 4.2.1518: Snow Queen spawn attribution and Lich/minion native master links.
- Aquamirae 6.API15: companion music and shared cooldown handling; install its
  required Minecraft 1.19.2 Obscure API 15+ separately.

Source/API review is not gameplay validation. See
[release notes](../../docs/releases/1.0.0-forge-1.19.2.md).

Build both JARs with `.\scripts\gradle.ps1 build -x test`. Outputs are
`build/1.19.2/libs/soulcatcherplus-1.0.0+1.19.2.jar` and
`build/1.19.2/libs/soulcatcherplus-compat-1.0.0+1.19.2.jar`.
The `compat` source set compiles against the core but packages only addon classes/resources.
Development runs include the addon unless `-PwithoutCompatAddon` is specified.

The core owns generic economy, collection, AI commands, rendering, ownership and
music-source tracking. Register mod-specific adapters during common setup through
`SoulAdapters.register`, behind an optional-mod check. Keep economy/ownership rules
in the core and avoid registering the same entity adapter from multiple addons.
