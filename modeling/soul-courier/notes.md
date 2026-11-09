# Soul Courier — vanilla-based model concept

Blockbench source for the registered Soul Courier and its Ender Chest interaction.

## Vanilla basis

The earlier stylized skeleton and hand-built chest have been replaced.

- The skeleton uses the six original SkeletonModel/BipedModel boxes, proportions,
  native 64x32 texture and UV offsets. The arms are whole straight cuboids again,
  with shoulder pivots at their upper ends. No extra ribs, skull details, hands
  or feet are added. Limb mirroring follows SkeletonModel.
- Child proportions follow BipedModel/AgeableModel's vanilla young rendering:
  body and limbs scale 0.5 with model-Y offset 24; head scales 0.75 with offset 16.
  Tiny Skeletons 1.16.5 confirms this basis by using vanilla SkeletonModel and
  `minecraft:textures/entity/skeleton/skeleton.png` in its BabySkeletonRenderer.
  Its implementation was inspected as a reference; no mod source is copied.
- The Ender Chest uses the exact three ChestTileEntityRenderer boxes: bottom
  14x10x14 at UV (0,19), lid 14x5x14 at UV (0,0), lock 2x4x1 at UV (0,0).
  The entire approved carrying model is reduced uniformly by 14/16, bringing the
  chest's visible box to vanilla's 14x14x14 units while preserving the grip and
  skeleton-to-chest proportions. The lid and lock retain the separate
  native pivots and both animate through -90 degrees, matching vanilla.
- Both original PNGs are extracted unchanged from the local Minecraft 1.16.5
  client JAR and embedded in the model for a portable preview. Their paths and
  SHA-256 hashes are recorded in `vanilla-sources.json`. These two textures are
  Mojang Studios assets, not original MIT-licensed artwork.
- The cloak, open hood, shoulder straps and hanging cyan amulet are separate
  original cuboids. The cloak uses an original 16x16 coarse runic cloth atlas; straps
  and the emissive amulet keep separate materials. The cloak does not alter the
  underlying skeleton texture or proportions.

The body proportions naturally give an 18-unit baby skeleton. The initial
skeleton-and-cloak fit factor of 16/18.4 is followed by the final whole-model
factor of 14/16, giving a 14-unit unbent height including the hood. Relative
proportions remain unchanged. The face points toward -Z; the backpack faces +Z.
The combined envelope is 14x31.5x28 units including the fully opened lid.

## Carrying pose

The spine bends forward 20 degrees around the hips, while the feet stay on the
floor. The full-size chest now belongs to the body group and follows the same
20-degree lean. Its floor is lowered from the previous upright placement and
aligned with both straight arms: 25 degrees backward relative to the torso.
The arms retain their vanilla length without elbow segmentation. The floor is
positioned 0.02 units above the upper end-cap corner in the torso's local space;
this contact stays aligned when the spine and chest tilt together. A slight
forward neck posture keeps the hood clear of the angled front of the chest.
Walking preserves this carrying grip while moving the legs.

## Loose cloak and emissive amulet

The mantle now has more shoulder volume. Side panels flare outward by 8 degrees,
the open front lapels by 6 degrees, and thicker rear panels drape away from the
body by 6 degrees. A split wider hem and two broad rear folds give the fabric
space around the skeleton. These remain simple cuboids; the skeleton, chest and
carrying pose retain their approved dimensions and transforms.

The hood's front rim now projects 0.7 unscaled units beyond the skull's front.
A brow lip overlaps the upper 0.45 units of the forehead, while the side lips
cover 0.35 units at each temple. The eyes and mouth stay visible. The skull itself
is unchanged. Rim boxes meet at their boundaries to avoid overlapping flat faces.

The amulet's separate `soul` material uses Blockbench's native `render_mode:
emissive`. Only `amulet_stone` uses this material, so the cloak and skeleton keep
normal shading.
The pendant hangs vertically below the neck on two dark cords forming a V
from the sides of the neck to its framed cyan stone. A dedicated `amulet` child bone
counter-rotates the body's fixed 20-degree forward lean. Its attachment follows
the neck position, but the pendant points straight down in world space instead
of following the torso's slope. Head turns and walking legs do not
rotate this bone. The chest, hands and carrying pose are unchanged.
The Minecraft renderer exports this material as a separate full-bright pass.
It remains visible in darkness without casting dynamic world lighting.

## Files and inspection

- `soul_courier.bbmodel`: working model with `idle`, `walk`, `chest_open` and
  `chest_close` clips.
- `soul_courier_open_chest.bbmodel`: separate static open-chest inspection pose.
- `plan.json`: regenerated geometry, materials, pivots and animation plan.
- `generate_model.py`: requires the installed model-bridge skill and local
  1.16.5 game JAR; changes no runtime resources.
- `textures/`: original vanilla textures and separate cloak material parameters.
- `textures/cloak_runic.png`: the pixel-art blue embroidery atlas, generated and
  assigned to cloth UVs; see `texture-authoring.md` for the source and prompt.

Vanilla geometry and UV evidence was checked against the actual 1.16.5 mapped
JAR: SkeletonModel, BipedModel, AgeableModel and ChestTileEntityRenderer. The
chest renderer uses positive Y upward; skeleton model coordinates use positive
Y downward, so their side-face UV orientation differs in Blockbench.

References inspected:
- https://github.com/Fuzss/tiny-skeletons/tree/1.16.5
- https://github.com/Fuzss/tiny-skeletons/blob/1.16.5/src/main/java/fuzs/tinyskeletons/client/renderer/entity/BabySkeletonRenderer.java
- https://github.com/InventivetalentDev/minecraft-entity-models

## Minecraft integration

`python scripts/export_soul_courier.py` exports the working model's hierarchy,
cube rotations and face UVs to native 1.16.5 rendering code. Vanilla skeleton and
Ender Chest textures are referenced directly, including resource-pack overrides.
Custom cloth, strap and amulet textures are copied into the mod resources.

Right-clicking opens the interacting player's own Ender Chest, including when
that player is not the courier's owner. Multiple viewers keep the physical lid
open until the last menu closes. The passive courier stops walking while viewed.
The menu closes if the courier dies, is recalled, changes world or is more than
eight blocks away. It stores no inventory of its own.

The default has 8 HP and uses the normal summon-slot and soul-energy rules.
Unlock with `/give @s soulcatcherplus:courier_soul_scroll`, then use the scroll
and summon from the collection menu. Raw model inspection remains available
with `/summon soulcatcherplus:soul_courier`. No survival loot or recipe is assigned.
