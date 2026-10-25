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
