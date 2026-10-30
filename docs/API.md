# Soul Catcher Plus API — 0.1 (experimental)

Target: Minecraft 1.16.5, Forge 36.2.42, Java 8 (port branch). API stability is not yet guaranteed.
The main mod is required on client and server. Third-party creature mods remain optional.

## Entity adapters

Register exactly one adapter per entity ID during `FMLCommonSetupEvent.enqueueWork`:

```java
SoulAdapters.register(new ResourceLocation("yourmod", "your_mob"), new SoulAdapter() {
    @Override
    public void prepareSummon(MobEntity mob, ServerPlayerEntity owner) {
        mob.clearRestriction();
        // Reset your arena/encounter state here; keep native combat behavior.
    }
});
```

Imports: `dev.soulcatcherplus.api.SoulAdapters`, `dev.soulcatcherplus.api.SoulAdapter`.
Default adapters are resolved by entity registry ID; explicit adapters take precedence.
Register only when the target mod is loaded. Keep its classes behind that optional dependency.

Lifecycle:

1. `canCapture(mob, player)`: check before marking and again on death.
2. `capture(mob)`: return serializable state. The core removes identity, death,
   passengers, old targets, temporary potion effects, and this mod's ownership fields.
   Persistent data belonging to other mods is retained; an adapter must remove any
   encounter-specific state that is unsafe to restore.
   `variantData(snapshot)` determines whether the captured appearance is already
   owned. Return only stable appearance traits: no UUID, health, cooldown, equipment
   durability, temporary anger or arbitrary encounter state. Equal compounds mean
   the same form; repeated captures do not add another reward beyond kill energy. Different forms
   share one species card and are unlocked separately by capture.
3. Core recreates the original entity type, loads a defensive copy, assigns a fresh
   UUID and full health, then sets ownership.
4. `prepareSummon(mob, owner)`: reset custom encounter state before world insertion.
5. `configureCompanion(mob)`: replace target selection and add companion movement.
   Also called when an active mob reloads from disk. The default keeps native
   combat goals and replaces the target selector. Override for Brain-based AI.
6. `tick(mob, owner)`: every five server ticks. Avoid resetting native animations.
7. `onRecall(mob, owner)`: optional cleanup for loaded entities. Unloaded entities
   are invalidated in the ledger and rejected when their chunk next loads.
   The core samples the refund from current HP before this callback. A cleanup
   `RuntimeException` is logged and does not prevent removal or the refund.

All callbacks run on the server thread. Do not access client classes here.
Do not call `configureCompanion` repeatedly each tick or retain entity references globally.
Default follow/wait/guard behavior lives in `CompanionBrain`; read `CompanionBrain.mode(mob)`
from a custom controller. A dedicated `CompanionController` interface is not needed in
this initial API: adapters own that lifecycle directly.

Only one form of a species can be active at a time. Defeat cooldowns are shared
across its forms. Selected-species commands target the active form even when the
player is inspecting another captured appearance. Original snapshots supply the
appearance of older saved entries.

## Companion boss bars

`SoulAdapter.bossBars(mob)` supplies the **exclusively entity-owned**
`ServerBossEvent` instances to hide. The default uses `SoulBossBars.discover(mob)`:
it caches accessible, non-static fields declared as `BossEvent` or a subtype in
the mob's class hierarchy, then reads their current values. This is a heuristic,
not proof of exclusive ownership. No display-name matching, static-field scan,
global encounter manager or recursive object-graph search is used.

The core applies suppression before insertion/tracking, on reload, and during
the existing five-tick companion update. It marks the selected event instance,
calls `setVisible(false)` to remove any existing display, and prevents later
`setVisible(true)` through a required vanilla mixin. Registered viewers remain
intact: some mods use `getPlayers()` for encounter behavior. Native add/update
packets respect the invisible flag. The built-in Mowzie boss-event subclass uses
this standard path; it does not need a name-based client overlay filter.

Override the hook for bars held inside helper objects, and **exclude any shared
encounter event** even when it is referenced by an instance field:

```java
@Override
public Collection<ServerBossEvent> bossBars(MobEntity mob) {
    return List.of(myEntityOwnedEvent(mob));
    // Return List.of() when this mob only references a shared encounter bar.
}
```

Only return an event if hiding it cannot also hide the UI for other hostile
participants. Suppression lasts for the event object's lifetime; there is no
automatic unhide of an event incorrectly claimed as private by an adapter.

For lazily created bars, initialize them in `prepareSummon`/`configureCompanion`
before tracking, or call `SoulBossBars.suppress(mob)` immediately after creation
and **before** registering viewers. Five-tick rediscovery alone cannot prevent
a first-frame display when a mod creates and shows a new event between updates.
Fully custom HUDs that bypass `ServerBossEvent`, and subclasses that bypass the
base packet/visibility implementation, require their own compatibility hook.
Generic suppression is not a guarantee for every mod's overlay.

## Collection model previews

`previewData(snapshot)` returns a small NBT compound containing client-safe visual
state. The default implementation includes common appearance keys and visible
equipment. Override it for custom skin IDs or render-state flags. The Wroughtnaut
adapter includes its awake/active flags so it can be shown standing in the archive.
Both methods receive a defensive copy of the stored snapshot.

```java
@Override
public CompoundTag variantData(CompoundTag snapshot) {
    CompoundTag appearance = SoulAdapter.super.variantData(snapshot);
    appearance.putString("MySkin", snapshot.getString("MySkin"));
    return appearance;
}

@Override
public CompoundTag previewData(CompoundTag snapshot) {
    CompoundTag preview = SoulAdapter.super.previewData(snapshot);
    preview.putString("MySkin", snapshot.getString("MySkin"));
    return preview;
}
```

Do not send the complete saved entity. The server only answers owned-entry preview
requests from a living, non-spectator owner, strips identity and Forge persistent
data/capabilities, and limits serialized visual data to 16 KiB. Larger/invalid visual
data falls back to default entity appearance. Complex nested custom state should
be reduced to the fields that the renderer actually needs.

Preview mobs are detached client entities: they are never added to the level or
AI-ticked. Their renderer and visible equipment are reused. Third-party renderers
that require live-world ticking or special synchronized data can need further
integration; a failed preview shows a placeholder without removing the soul.
Current development builds use protocol **9**, adding active cage capacity/slot
to collection and wheel snapshots and new cage availability states; clients and
servers must update together. Released 0.2.0 artifacts
use protocol 6, and released 0.1.0 artifacts remain on protocol 4.

## Soul remnant appearance

`SoulRemnant` chooses a visual from the captured `SoulEntry.maxHealth`. At **150 HP
or above**, the soul is a skull in blue fire. Below that threshold, the lantern tier
uses `SoulBalance.victoryEnergy(maxHealth)`: less than 20 is small, 20 through 40
inclusive is medium, and more than 40 is large. The skull rule takes priority.
Current health, summon cost, boss bars and boss tags do not affect this choice.

The server synchronizes only the appearance's stable string ID through entity
metadata; the captured snapshot remains server-side. On chunk/world reload,
appearance is derived from the already saved max health, including older remnants.
No extra NBT fields, collection-schema change or captured-mob instantiation is needed.

`SoulRemnantRenderer` draws the four transparent 32x32 textures as camera-facing
quads with full-bright lighting and gentle vertical bobbing. The existing pickup
ownership, scythe requirement, soul particles and one-minute lifetime are unchanged.

The collection screen pauses an unpublished integrated singleplayer server.
Recovery remains authoritative on world game time; cosmetic screen ticks do not
advance its displayed countdown. Packet-rate limits use a session-only monotonic
clock so requests remain usable while world time is frozen. Dedicated servers and
LAN sessions retain Minecraft's normal non-pausing behavior.

### Collection favorites

`PlayerSouls` stores favorite entity-type IDs in the optional `Favorites` string
list. Old saves load with no favorites; loading retains only IDs represented in
the player's collection. `isFavorite(entityType)` queries the species-wide flag;
`toggleFavorite(entryId)` resolves an owned entry before changing it. All current
and future captured forms of that type share the flag.

Collection `Row` packets include `favorite`; `Action.TOGGLE_FAVORITE` uses the
existing owned-entry request, rate limit, dirty marking and state reply. It does
not change selection, group scope, active summons or energy. The screen performs
a stable favorites-first sort of species cards, preserving capture order within
each tier. A separate keyboard-accessible star button toggles the flag without
selecting the card. This ordering applies to the collection screen only.

## Key-bound collection and command wheel

Client-only `ClientControls` registers remappable defaults O (collection) and held R
(wheel) through Forge's `FMLClientSetupEvent` and `ClientRegistry`. Both menus require a soul scythe
of any tier in the main hand or offhand to open. Key presses without one are consumed
without opening a screen or sending an open request. The client rechecks this rule
when a collection reply arrives, and the server rejects opens without a held scythe.
`SoulService.summon` still applies the held scythe's capacity rules.

The wheel opens locally and receives update-only `WheelState` packets: late replies
cannot reopen it after release. The inner ring has an All sector plus up to five
active entries per page. All addresses the full group across pages. The outer ring
offers FOLLOW, AGGRESSIVE, WAIT, GUARD and RECALL; All + RECALL recalls everyone.
Hovering an inner sector selects the recipient; hovering the outer ring arms a
command. Releasing the trigger commits only the currently hovered enabled command.
Center, ring gaps, outside, Escape, lost focus,
death, disconnect, losing the held scythe or a changed client level close without
executing an armed action.
It is a non-pausing screen, unlike the collection.

Minecraft clears key states on screen opening and deactivates IN_GAME key contexts.
The wheel therefore handles matching keyboard/mouse release events in the screen
and polls physical keys/buttons as a fallback. Scancodes use matching input events
instead of being treated as GLFW keycodes. Closing with a still-held trigger blocks
wheel reopening until release; it does not block the collection shortcut.

`WheelState` contains active cage energy/capacity/inventory slot and active entries, with current/max HP read from
loaded, canonically owned mobs. Unloaded entries carry explicit unknown health;
menu snapshots never load chunks. Refreshes are throttled to 250 ms per player
using session monotonic time, with a separate 100 ms command throttle.

`WheelRequest` carries an explicit group flag; its two-argument constructor still
means a single entry. `SoulService.commandOne(player, entryId, mode)` looks up the
entry in that player's ledger; `commandAll(player, mode)` iterates only that same
player's ledger. Both share command application with collection orders, checking
loaded entity UUID, owner and entry identity. The server also requires a held
scythe for wheel requests. Wheel orders never modify `selected` or `commandGroup`;
a single order cannot silently become a group order. Group modes affect loaded
companions only; recalls also invalidate unloaded reservations through the normal
ledger path. Existing command buttons remain in the collection, with AGGRESSIVE
added there as well. Direct target attacks remain scythe interactions.

### Aggressive follow

`CompanionBrain.Mode.AGGRESSIVE` uses normal owner following and return teleportation
when idle. Every five ticks, it may acquire the nearest visible, attackable hostile
MobEntity within 16 blocks of both itself and the owner. Beyond 24 blocks from the owner,
the companion abandons combat and returns; a target beyond that owner radius is
also dropped. Existing WAIT, GUARD and reactive FOLLOW rules are unchanged.

`SoulAdapters.get(candidate).isHostile(candidate, owner)` classifies automatic
targets. Its default recognizes `Enemy`, `MobCategory.MONSTER` or a mob currently
targeting the owner; neutral monster types may therefore qualify. Override this
hook on the candidate's adapter for faction or other custom hostility rules.
`CompanionBrain.canAttack` still rejects the owner, allied companions, the owner's
pets, team allies and otherwise unattackable targets before the hook is used.
Native attacks and integration ticks remain in place; inherited minions use their
root companion's chosen target rather than running their own target search.

## Summoned minions

`SoulMinions` discovers MobEntity descendants through `SoulAdapter.summoner(mob)`. Its
default uses native Vex, projectile, evoker-fangs, area-effect-cloud and TNT owners,
plus tameable-animal and horse owner UUIDs. UUID lookup stays within loaded entities
in the same server world; custom creator relationships use the adapter API. Discovery
requires a chain reaching a marked companion; player-owned pets and unrelated
nearby mobs are not adopted. Traversal stops on cycles, dimension boundaries or
after 32 creators. The standard path uses no reflection, proximity guessing or
chunk loading; optional integrations may expose explicit native owner links.

For ownerless synchronous summons, the creator's adapter may opt in with
`inheritsTickSpawn(MobEntity summoner, EntityType<?> childType)`. It defaults to false:
execution nesting alone does not prove ownership (damage/death callbacks can
create another mob's children). Approve only known child types. The callback runs
during the child's base constructor and receives its type, never a partially
initialized MobEntity to inspect or modify.

`SoulSpawnContext` brackets the server's native `tick` and `rideTick` calls in
try/finally, preserving nested sources and masking outer sources during ordinary
entity ticks. The construction event records weak child/creator references only
for adapter-approved types created by a companion with a canonical reservation.
The load hook clears construction provenance; the join event consumes any remaining record, including rejected joins.
Only a fresh join may use the construction fallback, and an explicitly resolved
creator takes precedence even if it belongs to somebody else. An entity constructed
outside the scope and inserted later is not adopted merely due to insertion timing.
No world-radius search, extra packet type or saved creation record is involved.

The child stores player `scp_owner`, collection `scp_entry` and a separate
`scp_minion_root` UUID in entity persistent data. `CompanionBrain.activeEntry(mob)`
validates the root UUID against the canonical reservation, rather than comparing
the child's UUID. Nested summons flatten to the same root. Children never become
collection entries or consume slots, and cannot be captured or refunded. Their
own deaths give no soul energy, drops or XP; their enemy kills credit the player.
Right-clicking an owned child with the scythe selects its root collection entry.

Discovery runs on join, periodically on living ticks, and at target/combat and
interaction boundaries to handle creators assigned after insertion. Attachment
only changes metadata and sends the existing Ghost packet to current trackers.
Target-selector configuration runs on join or before AI on a living tick, never
while a target/attack callback might be iterating goals. A transient weak-key set
prevents repeated configuration without retaining entity instances or save state.
Existing tracking synchronization supplies the spectral tint; minion allegiance
does not add fields to the collection, wheel or Ghost packets.

`configureMinion(mob)` defaults to clearing only `targetSelector`; native movement
and attack goals remain. Every five ticks a loaded root's permitted target is
inherited; WAIT clears combat. Independent native Brain/custom attacks can need
an adapter, while the shared target and damage guards still reject allies.
Before every native living tick, the core validates the child's reservation and
loaded root, then calls `restoreSummoner(minion, root)`. The default restores Vex
owner/idle origin; override it to repair native owner references before AI can
search for a different master or self-destruct. Keep it cheap and idempotent,
write only changed state and do not load chunks. When the root is valid but
unloaded, the child's LivingUpdateEvent is canceled, pausing the base living tick
and its AI/movement. Subclass code outside `super.tick()` is not paused: vanilla
Vex lifespan still decreases there. The external server tick counter also advances.
The root link survives disk reload even though vanilla Vex does not
save its own owner reference. Target inheritance, boss-bar rediscovery and the
existing `tickMinion` callback retain their five-tick cadence.
An old, unmarked Vex loaded from a save that already lost its native owner cannot
be safely adopted retroactively; check the feature with newly created minions.

Root recall, death or permanent removal invalidates its reservation and dissolves
loaded descendants. Unloaded descendants fail validation on their next load.
Chunk unload/dimension-transfer removal reasons do not end a root reservation.
Permanent root removal without a living-death event (for example a creeper's own
explosion) now ends the reservation with the normal defeat cooldown and no refund.
Minion death never changes the root cooldown or active identity.

For a custom child type, extend its adapter's creator resolver:

```java
@Override
public Entity summoner(MobEntity mob) {
    return ((TheirMinion) mob).getCaster(); // Integration owns this dependency.
}
```

Alternatively call `SoulMinions.attach(child, creator)` on the server thread,
before insertion or after the creator becomes known. Existing companions/minions
are not reassigned. Unknown/cyclic/cross-level creators return false unchanged;
a recognized but invalidated root marks and discards its stale child and returns
false. Do not load chunks or mutate AI in `summoner`. Override `configureMinion`
for nonstandard target selection, `restoreSummoner(minion, root)` for native owner
restoration before each AI tick, and `tickMinion(minion, root)` for other integration
work after common target inheritance every five ticks. Ownerless spawning outside
the creator's native tick, such as scheduled tasks or independent effect entities,
still needs an explicit link/attach call. Automatic compatibility with every mod
is not promised.

For a known synchronous child type, opt in on the **creator's** adapter:

```java
@Override
public boolean inheritsTickSpawn(MobEntity summoner, EntityType<?> childType) {
    return childType == THEIR_SUMMONED_TYPE.get();
}
```

The optional Twilight Forest 4.0.870 bridge enables this for Snow Queen's
`ice_crystal` only. Lich shadow clones expose `getMasterLich`/`setMaster`, while
`lich_minion` exposes a package-private `master` field (cached reflective access). These members are discovered once
at common setup, without a compile-time Twilight dependency. The adapters restore
the saved root into those native links before AI runs after reload. Older unmarked
ownerless crystals have no reliable provenance and are not adopted retroactively.
The integration was inspected against the installed artifact. It compiles and the
development client has started; actual gameplay verification is pending. Performance analysis is in
[MINION_PERFORMANCE.md](MINION_PERFORMANCE.md).

## Departure effects

Companion death and recall send a tracking-client departure message before normal
entity removal. Clients keep a short-lived visual echo of the original mob and
render a bottom-to-top soul-fire dissolve for 18 unpaused client ticks. The server
does not keep a second combat entity alive for this animation. Echoes and particles
pause with the local singleplayer world and are discarded when the client changes
levels. If the original client entity is unavailable, the packet's position and
dimensions still allow a particle effect.

The effect reuses the existing renderer and spectral render-type mapping, with
an additional per-vertex fade edge. Custom renderers or opaque render layers can
need their own spectral mapping to dissolve as completely as a standard body.
No new entity class, persistent save schema or mandatory third-party dependency is
introduced for the visual effect.

## Events

`SoulEvent.Capture` and `SoulEvent.Summon` are cancelable Forge events posted on
`MinecraftForge.EVENT_BUS`. Both expose `player` and `mob`. Summon fires before
the entity is inserted and before energy is consumed. Capture fires before a remnant
is created; ordinary kill energy is a separate reward.

## Health economy and scythe limits

`SoulBalance` uses maximum health in health points: summon cost is `ceil(maxHP * 2)`
and credited kill energy is `floor(maxHP / 2)`, both at least one. Invalid maximum
health is treated as one. Kill rewards never use current dying health. Companion deaths
give no energy; collecting an already owned form gives no second kill reward.

Recall returns `floor(currentHP / 2)` energy from a living, loaded companion after
checking its active UUID, owner and entry identity. Unlike kill rewards, this has
no minimum: HP below two returns zero; invalid/nonfinite health also returns zero.
Current HP is read before adapter cleanup; the active reservation is invalidated
before callbacks to prevent duplicate refunds. Credits fill the owner's offhand
cage first, then hotbar cages from left to right. Without an available owner inventory or free cage space,
the refund cannot be stored. `recallAll(player)` supplies its player directly for
logout and death handling. The shared recall
path covers single, group and automatic recalls; companion death gives no refund.
Unloaded/missing entities are invalidated without a refund or chunk loading because
their current health is unknown.

`SoulEntry` now takes maximum health as its final constructor argument, replacing
the old cost/slots pair. It stores `MaxHealth` and derives `cost` from that value.
Ledger schema 3 loads older entries using their saved maximum-health attribute
(including saved modifiers); without it, old cost / 2 is a best-effort fallback.
Transient modifiers absent from NBT cannot be reconstructed. Old ledger energy
is intentionally not transferred; ownership, selection, active identity, snapshots
and cooldowns are retained.
`PlayerSouls.activeCount()` replaces weighted slot accounting.

`SoulScythe.summonLimit()` is 1 / 2 / 3 for iron / diamond / netherite.
`SoulScythe.heldBy(player)` chooses the main-hand scythe first, then the offhand.
`SummonRules` supplies one availability decision to server summoning and UI state.
Existing companions remain when the held limit decreases or no scythe is held;
only new summons check the current limit. Every companion counts as one, including
unloaded active ledger reservations. The collection belongs to the player's UUID
in overworld saved data. Collection entries are not stored in item stacks; soul
energy is stored exclusively in cage item stacks.

The legacy `soulcatcherplus:soul_scythe` registry ID and `SCYTHE` field alias refer
to the iron tier. New registry IDs are `diamond_soul_scythe` and `netherite_soul_scythe`.

## Soul cage storage

`SoulCage` is a non-stackable item with capacities 150 / 400 / 1000 / 2000 for
`iron_soul_cage`, `diamond_soul_cage`, `netherite_soul_cage` and `supreme_soul_cage`.
`SoulCage.energy(stack)`, `capacity(stack)` and `setEnergy(stack, amount)` access
the bounded `SoulEnergy` integer in the item tag. Reads never create NBT. Vanilla
smithing transformation copies the diamond cage's tag into the netherite result,
preserving stored energy and custom names. Netherite cages are fire-resistant.

`SoulCages.active(player)` scans `Inventory.SLOT_OFFHAND` (40), then inventory slots
0–8, returning the first cage's slot and actual stack, or slot -1/EMPTY. It never
skips an empty/full/undersized offhand cage for a hotbar cage, or a hotbar cage for a later one. Armor,
other inventory slots and modded accessory slots do not participate. Harvest marking and remnant collection require a cage;
merely viewing menus or commanding existing companions does not. Soul capture
does not spend cage energy or require spare capacity.

`SoulCages.spend(player, amount)` uses only that active cage. The whole summon
cost must fit in it; energy from several cages is not combined. The summon path
charges after adapters and the cancelable event approve, before entity insertion,
and restores the exact charged stack if spawning fails. The shared
`SummonRules.context(collection, limit, energy, cageCapacity, gameTime)` provides
both the server decision and menu state, including NO_CAGE and CAGE_TOO_SMALL.

`SoulCages.credit(player, amount)` fills the offhand first, then all hotbar cages
from left to right, and returns the accepted amount. Selection and crediting share one
ordered slot list. Kill rewards and recall refunds use this same path;
any remainder after all cages are full is lost. `set(player, amount)` changes
only the active cage and clamps to its capacity. Server callers mutate inventory
and let the normal inventory synchronization carry NBT changes to clients.
The admin `/soulcatcher energy <amount>` command sets that active cage, reports
the actual bounded result and requires a hotbar or offhand cage.
`SoulCages.location(slot)` supplies the translated location to both menu headers
and the command reply, so offhand slot 40 is never presented as hotbar slot 41.
The packet fields remain unchanged; the protocol stays at 9.

All four item models inherit `item/soul_cage`, based on vanilla `item/handheld`.
The sprite's plane follows the sword orientation along the hand. Third-person
Z rotation accounts for upright cage artwork, with the top chain texel anchored
at the fist. First-person rotations and scale match vanilla swords while retaining
the lowered grip. GUI, ground and fixed transforms inherit the normal item defaults.

`PlayerSouls` no longer exposes or saves an energy balance. Schema 3 deliberately
ignores old `Energy` fields: the user requested no legacy resource transfer.
Existing entries, favorites, selection, active identities and cooldowns are retained.
There is no migration escrow, automatic cage grant or hidden player energy balance.

## Capture availability through datapacks

Create `data/<your_namespace>/soul_profiles/<name>.json`:

```json
{"entity":"yourmod:your_mob","enabled":true}
```

Set `enabled` to false to disable marking and capture. Ordinary credited kill
energy still uses the maximum-health formula. Legacy `cost`, `slots` and `energy`
fields are ignored; profiles cannot override the common economy or scythe limits.
The Wroughtnaut profile is provided in the base mod even when Mowzie's is absent.
Use the same resource path as an existing profile to override it. Reload with `/reload`.
Existing collection entries retain their captured maximum health and cost; disabling
capture does not delete collected souls. Avoid multiple profile files targeting one entity.

## Development bestiary population

The dev-only `soulcatcherqa` command (permission level 2, player source) accepts
`--all`. The bare command keeps its sample species and two sheep colors; both
forms grant the existing scythe loadout and put a supreme cage with 1000 energy
in the offhand if a cage is already there, otherwise before other hotbar cages.
Displaced items return to inventory or drop
if it is full. The command does not silently delete an existing cage.

Bulk population takes a sorted snapshot of registered entity types and creates
samples on the server thread in small tick batches. Only returned `MobEntity` instances
passing profile/adapter capture checks are added. No world insertion, ticking or
`finalizeSpawn` is requested. Constructor-default forms are used, with a bow for
skeleton QA samples; this does not enumerate every variant or spawn-dependent state.
The common capture helper sanitizes snapshots and uses `SoulVariants.same` to
preserve existing entries and selection. `PlayerSouls.MAX_ENTRIES` is shared with
normal remnant capture and packet bounds.

Errors in one factory/adapter are logged and counted; returned temporary entities
are discarded in a guarded cleanup. A failed constructor that never returns an
object cannot be cleaned up generically. Bulk population is a QA tool, not a
guarantee that arbitrary mod constructors/adapters are free of world/global effects.
Jobs stop when the player leaves or the server stops. A full collection stops
further creation and reports remaining unprocessed entity types rather than
claiming that they were all capturable. Partial additions remain saved.

## Client boss music (1.16.5)

The client-only `SoulMusic` service tracks the native client tick source in a
`ThreadLocal`, restored in `finally` for normal entities and passengers. Calls to
the vanilla `MusicTicker.startPlaying` and `stopPlaying` are rejected only when
that source has the synchronized companion owner marker. Other music continues.

The Forge `PlaySoundEvent` filter applies only to `SoundCategory.MUSIC`. An explicit
source resolver takes precedence, followed by a single direct `Entity` field on
the sound instance, then the current entity tick source. Field discovery is cached
per sound class; ambiguous or inaccessible fields are not guessed. During client
setup, use `SoulMusic.registerSource(Function<ISound, Entity>)` for other sound
layouts; return null for unsupported sounds. These hooks must stay on `Dist.CLIENT`.

Only active, attributed music instances are retained, with weak entity references.
The end-of-tick check stops music if a late ownership packet identifies its source
as a companion. World changes clear the tracking map. There is no world entity
scan, server polling, extra packet, or sound-ID blacklist. Soundtracks without a
recoverable source, async playback, and non-MUSIC categories need an integration.

An optional Aquamirae 5.4.0 hook cancels `AquamiraeAmbient.playCorneliaMusic` for a
companion tick before its shared cooldown is updated. It also tracks the last
successful Cornelia music request weakly and clears that request's cooldown if
its source is identified as a companion later. Another successful boss request
replaces the tracked source; simply hitting the cooldown does not. No Aquamirae classes are
required when that mod is absent. This protects the regular Cornelia encounter's
music timer as well as the vanilla music manager.

## Client rendering

On `Dist.CLIENT` only, call `SoulVisuals.register(entityId, provider)` during client
setup. `SoulVisualProvider.remap(originalRenderType, bodyTexture)` lets an integration
replace render types; the shared vertex wrapper applies spectral color and alpha.
The client-only `EntityRenderDispatcher` hook wraps the native renderer's buffer
argument once, including renderers that bypass `RenderLivingEvent` (such as GeckoLib
entity renderers). It does not rerun render events or wrap dispatcher fire, shadows
or debug hitboxes. Ordinary entities keep their original buffers; dissolve echoes
keep their separately supplied fade. Nested dispatcher calls unwrap our parent
source before applying the child's own texture/provider, preventing repeated tint.
No GeckoLib or Mowzie dependency is required.
The default mapping makes standard solid/cutout body materials translucent. A
renderer must use the supplied `IRenderTypeBuffer` for this hook to affect it.
Custom shaders, independently acquired buffers and extra textures/layers may still
need an integration through `SoulVisualProvider` and visual testing. The hook does
not guarantee transparency for every third-party rendering pipeline.

The built-in Wroughtnaut adapter uses the creature's public activation API. An optional
`@Pseudo` Mixin filters Mowzie's area-query results before native attacks apply
damage, shield wear, knockback or other effects. No Mowzie code or assets are bundled.
