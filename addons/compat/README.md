# Soul Catcher Plus: Mod Integrations

Combined integration addon for Minecraft **1.20.1 / Forge 47.4.28 / Java 17**.
The separate 1.16.5 build is maintained on `develop`.
Use only the core/addon pair matching your Minecraft version; see
[release notes](../../docs/releases/1.0.0.md).
Mod ID: `soulcatcherplus_compat`. Java 17 runtime and build toolchain, like the core. This is a separate installable JAR, not a nested dependency.

The addon currently contains:

- Mowzie's Mobs 1.8.2: Wroughtnaut activation/appearance adapter, area-attack ally
  filtering and its soul profile.
- Twilight Forest 4.3.2508: Snow Queen ice-crystal spawn attribution and native
  master links for Lich clones and Lich minions.
- Aquamirae 6.1.2/6.4.0: the client hook preserving the regular Cornelia music cooldown
  when its source is a summoned companion.

All creature mods are optional. Adapters register only when their mod is loaded;
optional `@Pseudo` mixins are skipped if their target classes are absent.
No third-party mod classes or assets are bundled.

The core retains generic capture/summoning, AI commands, ownership inheritance,
boss-bar suppression, spectral rendering, music source tracking and the public
extension APIs. Without this addon, these generic features still work, but the
specific corrections listed above are absent. Existing collection IDs, ownership
tags and data-profile paths are unchanged. Removing an addon may therefore affect
the behavior of its supported creatures, without deleting their saved souls.

## Build and install

From the repository root:

```powershell
.\scripts\gradle.ps1 build -x test
```

Outputs under `build/1.20.1/libs`:

- `soulcatcherplus-1.0.0+1.20.1.jar` — core.
- `soulcatcherplus-compat-1.0.0+1.20.1.jar` — this addon.

Install both on client and server, with the chosen creature mods and their
dependencies. The addon requires the matching core version; update the pair
together. Third-party dependencies are not bundled in release artifacts.

## Development layout

`build.gradle` owns the `compat` source set and `compatJar`/`reobfCompatJar` tasks.
The addon compiles against the core output, but packages only its own source-set
output. A normal build assembles both reobfuscated JARs. Development `runClient`
and `runServer` include both mod source sets and both Mixin configurations.
Use `-PwithoutCompatAddon` to launch only the core for manual isolation checks.

Keep each integration behind its optional-mod check and use
`SoulAdapters.register(entityId, adapter)` during common setup. Do not copy the
core's ownership or economy rules. See [the public API](../../docs/API.md).
Future per-mod addons can move these adapters and mixins into their own source
sets/JARs; they must remove their registration from this combined addon to avoid
duplicate adapters. The current registry deliberately rejects duplicate IDs.
