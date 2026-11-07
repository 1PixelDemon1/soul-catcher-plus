# Soul Catcher Plus

Capture the souls of defeated creatures and summon them as spectral companions.
Build your own collection, store soul energy in portable cages, and command your
companions in battle.

## Features

- Mark enemies with a soul scythe and capture their souls after defeating them.
- Collect creature forms, browse 3D previews, and keep favorites at the top.
- Store soul energy in iron, diamond, netherite, and supreme soul cages.
- Summon spectral companions and give individual or group orders: follow, wait,
  guard, attack nearby enemies, or recall.
- Progress through four scythe tiers, custom enchantments, and advancements.
- Extend creature support through a public API.

## Supported versions

The local **1.1.0** release targets **Forge 1.16.5 only**. See the
[1.1.0 release notes](docs/releases/1.1.0.md) for Forgotten Souls, scrolls,
commands, upgrade requirements, and validation status. The other maintained ports
retain their 1.0.0 releases; no new integration-addon build is included.

| Minecraft | Mod loader | Java |
| --- | --- | --- |
| 1.16.5 | Forge 36.2.42 | 8 |
| 1.19.2 | Forge 43.5.2 | 17 |
| 1.20.1 | Forge 47.4.28 | 17 |

Each Minecraft version has its own JAR. Install the matching core mod in the
`mods` folder on both client and server. The core does not require any creature
mods or animation libraries.

## Quick start

Craft a soul scythe and a soul cage. Keep the cage in your offhand or hotbar,
mark an enemy with the scythe, defeat it, and collect its soul.

With a scythe in either hand, press **O** to open the collection or hold **R**
to open the command wheel. Both keys can be changed in Controls.

## Repository branches

| Branch | Purpose |
| --- | --- |
| `main` | Release source and project overview |
| `develop` | Primary development for Forge 1.16.5 |
| `forge/1.19.2` | Forge 1.19.2 port |
| `forge/1.20.1` | Forge 1.20.1 port |
| `archive/neoforge-1.20.4` | Archived experimental port; outside the current release |

See the [changelog](CHANGELOG.md) for release history and version-specific notes.

## License

[MIT](LICENSE)
