# Soul Catcher Plus API — 0.1 (experimental)

Target: Minecraft 1.20.1, Forge 47.4.28, Java 17. API stability is not yet guaranteed.
The main mod is required on client and server. Third-party creature mods remain optional.

## Entity adapters

Register exactly one adapter per entity ID during `FMLCommonSetupEvent.enqueueWork`:

```java
SoulAdapters.register(new ResourceLocation("yourmod", "your_mob"), new SoulAdapter() {
    @Override
    public void prepareSummon(Mob mob, ServerPlayer owner) {
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
public Collection<ServerBossEvent> bossBars(Mob mob) {
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
Release 0.2.0 uses protocol **6** for collection, wheel and departure messages;
clients and servers must update together. Released 0.1.0 artifacts remain on protocol 4.

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
(wheel) through Forge's `RegisterKeyMappingsEvent`. Both menus and owned preview
requests work without a held scythe. `SoulService.summon` still applies the held
scythe rules; viewing or issuing orders does not grant summon capacity.

The wheel opens locally and receives update-only `WheelState` packets: late replies
cannot reopen it after release. Hovering the inner ring selects an active entry;
hovering the outer ring arms a command. Releasing the trigger commits only the
currently hovered enabled command. Center, ring gaps, outside, Escape, lost focus,
death, disconnect or a changed client level close without executing an armed action.
It is a non-pausing screen, unlike the collection.

Minecraft clears key states on screen opening and deactivates IN_GAME key contexts.
The wheel therefore handles matching keyboard/mouse release events in the screen
and polls physical keys/buttons as a fallback. Scancodes use matching input events
instead of being treated as GLFW keycodes. Closing with a still-held trigger blocks
wheel reopening until release; it does not block the collection shortcut.

`WheelState` contains energy and active entries only, with current/max HP read from
loaded, canonically owned mobs. Unloaded entries carry explicit unknown health;
menu snapshots never load chunks. Refreshes are throttled to 250 ms per player
using session monotonic time, with a separate 100 ms command throttle.

`SoulService.commandOne(player, entryId, mode)` looks up the entry in that player's
ledger and shares command application with collection orders. Loaded entity UUID,
owner and entry identity are checked. Wheel orders never modify `selected` or
`commandGroup`; a single order cannot silently become a group order. Recall also
works for an unloaded active reservation through the normal ledger invalidation.

## Summoned minions

`SoulMinions` discovers Mob descendants through `SoulAdapter.summoner(mob)`. Its
default follows `TraceableEntity.getOwner()` and `OwnableEntity` ownership. Because
the default `OwnableEntity.getOwner()` resolves players only, a null result also
tries the owner UUID among loaded entities in the same server level. Discovery
requires a chain reaching a marked companion; player-owned pets and unrelated
nearby mobs are not adopted. Traversal stops on cycles, dimension boundaries or
after 32 creators. No reflection, proximity guessing or chunk loading is used.

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
Existing tracking synchronization supplies the spectral tint; the wire protocol
remains 6.

`configureMinion(mob)` defaults to clearing only `targetSelector`; native movement
and attack goals remain. Every five ticks a loaded root's permitted target is
inherited; WAIT clears combat. Independent native Brain/custom attacks can need
an adapter, while the shared target and damage guards still reject allies.
Vex native owner and idle origin are restored to the loaded root without changing
its limited lifespan. A valid but unloaded root leaves the child idle instead of
being mistaken for death. The root link survives disk reload even though vanilla
Vex does not save its own owner reference.
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
public Entity summoner(Mob mob) {
    return ((TheirMinion) mob).getCaster(); // Integration owns this dependency.
}
```

Alternatively call `SoulMinions.attach(child, creator)` on the server thread,
before insertion or after the creator becomes known. Existing companions/minions
are not reassigned. Unknown/cyclic/cross-level creators return false unchanged;
a recognized but invalidated root marks and discards its stale child and returns
false. Do not load chunks or mutate AI in `summoner`. Override `configureMinion`
for nonstandard target selection and `tickMinion(minion, root)` for native owner
restoration or other integration work. The latter runs every five server ticks
only while the root is loaded, after common target inheritance. Fully custom
spawning with no exposed creator still needs an explicit integration; automatic
compatibility with every mod is not promised.

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
before callbacks to prevent duplicate refunds. Credits are capped at `Integer.MAX_VALUE`
and saved to the owner's ledger even if the owner is offline. The shared recall
path covers single, group and automatic recalls; companion death gives no refund.
Unloaded/missing entities are invalidated without a refund or chunk loading because
their current health is unknown. No save-schema or network-protocol change is needed.

`SoulEntry` now takes maximum health as its final constructor argument, replacing
the old cost/slots pair. It stores `MaxHealth` and derives `cost` from that value.
Ledger schema 2 loads older entries using their saved maximum-health attribute
(including saved modifiers); without it, old cost / 2 is a best-effort fallback.
Transient modifiers absent from NBT cannot be reconstructed. Existing energy,
ownership, selection, active identity, snapshots and cooldowns are retained.
`PlayerSouls.activeCount()` replaces weighted slot accounting.

`SoulScythe.summonLimit()` is 1 / 2 / 3 for iron / diamond / netherite.
`SoulScythe.heldBy(player)` chooses the main-hand scythe first, then the offhand.
`SummonRules` supplies one availability decision to server summoning and UI state.
Existing companions remain when the held limit decreases or no scythe is held;
only new summons check the current limit. Every companion counts as one, including
unloaded active ledger reservations. The collection belongs to the player's UUID
in overworld saved data; no collection or energy is stored in item stacks.

The legacy `soulcatcherplus:soul_scythe` registry ID and `SCYTHE` field alias refer
to the iron tier. New registry IDs are `diamond_soul_scythe` and `netherite_soul_scythe`.

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
forms grant the existing scythe loadout and set energy to 1000.

Bulk population takes a sorted snapshot of registered entity types and creates
samples on the server thread in small tick batches. Only returned `Mob` instances
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
renderer must use the supplied `MultiBufferSource` for this hook to affect it.
Custom shaders, independently acquired buffers and extra textures/layers may still
need an integration through `SoulVisualProvider` and visual testing. The hook does
not guarantee transparency for every third-party rendering pipeline.

The built-in Wroughtnaut adapter uses the creature's public activation API. An optional
`@Pseudo` Mixin filters Mowzie's area-query results before native attacks apply
damage, shield wear, knockback or other effects. No Mowzie code or assets are bundled.
