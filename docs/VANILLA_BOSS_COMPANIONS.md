# Vanilla boss companions (Minecraft 1.16.5, 1.19.2, 1.20.1 and 26.3)

The core registers built-in `SoulAdapter` implementations for `minecraft:wither`
and `minecraft:ender_dragon`. External compatibility addons are not involved.
Other entity types and naturally spawned bosses retain their native behavior.

Both adapters use the shared command, targeting, ownership, recovery and return
rules in `CompanionBrain`. Their movement controllers observe `isReturning()` so
an idle flight offset cannot keep the return watchdog latched indefinitely.

## Wither

Native attack goals, skull entities, armor, projectile defenses, healing and head
animations remain. A priority-zero idle/hold goal prevents ground navigation from
competing with flight. The native server-only head controller is replaced for an
owned vanilla wither: every head follows the approved target; side heads retain
their native firing cadence and visibility/range checks. Idle random shots,
inherited spawn invulnerability/explosions and damage-triggered block destruction
are disabled. Wait holds position; other idle modes fly toward the owner or
guard point. Skull explosions retain damage to enemies, without block destruction
or knockback against the owner's allies.

## Ender Dragon

The original dragon entity, multipart hitboxes, flight integration, animations
and fireball/breath entities remain. An entity-local phase controller replaces
End waypoints with owner/guard flight, attack approaches and orbits. It uses the
existing hovering phase ID; no new phase registry or client protocol is needed.
Wait stops movement and attacks. Fireballs have an 80-tick cooldown.

Only the commanded target receives contact damage/knockback. Incoming attacks
use normal living-entity damage, preserving reduced damage on non-head parts;
the soul is not restricted to player/explosion damage as an encounter dragon is.
Death uses the normal soul recovery/dissolve path, without encounter XP, portals,
or dragon-fight completion. End crystals cannot heal the companion. Native block
destruction is disabled; the dragon retains its native no-physics flight.

The companion is detached from its encounter manager. The manager's two native
dragon searches also exclude soul dragons, preventing adoption or deletion during
legacy-fight detection. Global world/entity queries remain unchanged.

Breath clouds retain a persisted root UUID. Before applying effects they require
a loaded, living, canonical companion; orphaned clouds disappear. Allies are
excluded before potion processing. The harm effect retains vanilla damage and
caster attribution, but damages undead enemies instead of healing them.

## Validation and manual checks

The implementations target Forge 36.2.42, 43.5.0, 47.4.28 and 66.1.0 respectively.
Legacy Mixin aliases were checked against the
actual official-to-SRG mapping output and native methods. Compilation and client
startup do not establish gameplay correctness. Automated tests and UI automation
are not part of this refinement.

Use `/soulcatcherqa --all` in the QA-enabled development client to unlock vanilla
forms. Check Follow/Wait/Guard/Aggressive, individual/group Teleport and Recall,
owner/allied safety near explosions and breath, combat against normal and undead
enemies, and saving/reloading while companions or breath clouds exist. In the End,
check that a genuine encounter remains independent of a summoned dragon.

The full-size dragon still needs space under the existing summon/teleport
placement rules. Each maintained branch uses its version-matched native APIs.

The version-specific ports use their native damage, flight and event APIs.
Minecraft 1.20.1 and 26.3 additionally block reattachment to an End encounter;
26.3 uses the native killing-blow hook and server-level cloud effect path.
