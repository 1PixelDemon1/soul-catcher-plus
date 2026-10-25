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
   the same form; repeated captures of that form become energy. Different forms
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

All callbacks run on the server thread. Do not access client classes here.
Do not call `configureCompanion` repeatedly each tick or retain entity references globally.
Default follow/wait/guard behavior lives in `CompanionBrain`; read `CompanionBrain.mode(mob)`
from a custom controller. A dedicated `CompanionController` interface is not needed in
this initial API: adapters own that lifecycle directly.

Only one form of a species can be active at a time. Defeat cooldowns are shared
across its forms. Selected-species commands target the active form even when the
player is inspecting another captured appearance. Existing saved entries need no
schema migration; their original snapshots supply their initial appearance.

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
requests while the player holds the scythe, strips identity and Forge persistent
data/capabilities, and limits serialized visual data to 16 KiB. Larger/invalid visual
data falls back to default entity appearance. Complex nested custom state should
be reduced to the fields that the renderer actually needs.

Preview mobs are detached client entities: they are never added to the level or
AI-ticked. Their renderer and visible equipment are reused. Third-party renderers
that require live-world ticking or special synchronized data can need further
integration; a failed preview shows a placeholder without removing the soul.
The collection UI and departure effects use protocol **3**, so clients and servers must update together.

The collection screen pauses an unpublished integrated singleplayer server.
Recovery remains authoritative on world game time; cosmetic screen ticks do not
advance its displayed countdown. Packet-rate limits use a session-only monotonic
clock so requests remain usable while world time is frozen. Dedicated servers and
LAN sessions retain Minecraft's normal non-pausing behavior.

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

## Balance through datapacks

Create `data/<your_namespace>/soul_profiles/<name>.json`:

```json
{"entity":"yourmod:your_mob","enabled":true,"cost":100,"slots":2,"energy":20}
```

`cost`: energy spent on summon; `slots`: active capacity used (total capacity is 6);
`energy`: awarded for a credited kill. Set `enabled` to false to disable capture.
The Wroughtnaut profile is provided in the base mod even when Mowzie's is absent.
Use the same resource path as an existing profile to override it. Reload with `/reload`.
Existing collection entries retain their captured cost and capacity; profiles apply
to new captures and kill rewards. Avoid multiple profile files targeting one entity.

## Client rendering

On `Dist.CLIENT` only, call `SoulVisuals.register(entityId, provider)` during client
setup. `SoulVisualProvider.remap(originalRenderType, bodyTexture)` lets an integration
replace render types; the shared vertex wrapper applies spectral color and alpha.
Vanilla `LivingEntityRenderer`/`MobRenderer` bodies are handled by the default provider.
Renderers that bypass `RenderLivingEvent` require their own renderer hook and can
reuse `CompanionBrain.isSoul(entity)`. Custom armor/emissive layers and shaders need
visual testing; their blend modes may differ from the main body.

The built-in Wroughtnaut adapter uses the creature's public activation API. An optional
`@Pseudo` Mixin filters Mowzie's area-query results before native attacks apply
damage, shield wear, knockback or other effects. No Mowzie code or assets are bundled.
