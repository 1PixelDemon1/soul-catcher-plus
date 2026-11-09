# Soul Shaman — cuboid-only version

This version follows the user's request to use only cubes and rectangular blocks
in the style of the other mobs. It is separate from `../soul-shaman-reference`,
which preserves the approved mesh-based silhouette for comparison.

- **111 cuboids, zero meshes**, 666 quads / 1332 triangulated faces, 17 bones.
  Every element is a native Blockbench `cube`; rotations provide the sloping
  cloth shapes. No arbitrary vertex faces, smooth surfaces or modifiers.
- The approved mask, eyes, clothes, sleeves, amulet, staff, grip and lower flame
  derive from the frozen `base-cube-plan.json`. The figure is scaled to 88%
  and the staff to 106% about the hand, retaining the grip. The model is raised
  1.2 units so the longer staff remains above the ground in its rest pose.
- The hood uses broad overlapping rectangular blocks for its occiput, nape,
  back fold, visor and crown, plus two angled side walls and short brow/lower
  folds. The upper visor still projects forward; the lower rim is retracted.
- Two dark cheek linings close the gaps between the mask and hood side walls.
- Each sleeve consists of two cuboids: a narrow upper section and a slightly
  wider cuff, forming a modest stepped flare toward the hand. The cuff is about
  29% narrower and 8% shallower than the first two-section iteration, with
  unchanged length, rotations, pivots and hand/staff attachment. UV extents
  follow each section's dimensions.
  Cyan hood markings are thin rectangular blocks transformed with their walls.
  The cloth under the chin, neck lining and skirt lining remain closed.
- The rest pose fits within 17x26x8 Blockbench units; the staff measures
  25.44 units (1.59 Minecraft blocks) including its flame. Casting can extend
  beyond the rest envelope. The figure's collision box is 0.62x1.22 blocks.
- Existing cloth/material textures are reused unchanged, embedded in the model,
  and copied into `textures/`. Cloth provenance and its imagegen prompt are
  documented in `../soul-shaman-reference/notes.md` and `cloth-prompt.txt` there.

`generate_model.py` uses the local frozen base plan and local textures. It does
not read neighboring model projects to regenerate. It emits the current
`plan.json`, `bounds-report.json` and portable `soul_shaman.bbmodel`, checks all
generated element types and validates the actual rotated size envelope.

The installed bridge validator passes without mesh warnings. The earlier
geometry was inspected in Blockbench from the front three-quarter, rear and
bottom views. `preview-three-quarter.png` depicts that earlier iteration.

## Animation refinement

Eight independent hem bones carry their cloth strips and embroidery together.
They rise and fall on the same sine cycle as the figure's hover, with a small
outward fold. The lower flame gently twists. The right arm carries the hand,
staff, flame and charm through a smooth raising, sweeping and lowering gesture;
the left sleeve follows the gesture. All runtime transforms are assigned anew
for each rendered entity, including the resting pose.

`idle_hover` (loop, pi seconds) and `cast_preview` (one-shot, 1.8 seconds) are
editable clips in the Blockbench file. Their sampled curves correspond to the
procedural Java animation; the runtime exporter exports geometry, not clips.
The server starts eligible support casts every seven seconds. Cast start time
and active state are synced to observers, including players joining mid-cast.
At tick 18 the server applies the aura and plays the vanilla enchantment-table
sound once, at volume 0.35 and pitch 1.25. Rendering and menu previews never
emit sounds or grant effects. Silent shamans still buff but emit no sound.
The updated file opens in Blockbench with both clips and all 17 bones; the rest
pose and raised-staff pose were visually inspected. The mod build succeeds and
the development client's startup is confirmed from its fresh log. Gameplay
and animation appearance inside the world remain the user's manual checks.

## Runtime prototype

`python scripts/export_soul_shaman.py` exports this exact cuboid project to
`SoulShamanGeometry.java` and its 13 embedded texture materials to the core mod.
The shared cuboid renderer preserves each face's UVs, material and pivot; mixed
materials use disjoint face masks. Soul-colored materials receive full light.

The prototype is registered as `soulcatcherplus:soul_shaman` and as a Creative
variant of `forgotten_soul_scroll`. Right-clicking the scroll unlocks its soul
through the existing collection workflow. It has provisional 8 HP, is passive,
uses normal companion orders and has no natural spawns. Its scroll shares the
existing Forgotten Souls chest-loot pool with Raven and Courier, with equal
selection weight. The overall scroll drop chance and eligible chests are unchanged.

Its support aura grants vanilla Strength I and Regeneration I for eight seconds
on each seven-second cast. The caster must be an active soul within six blocks
of its living, online owner in the same dimension. At impact the owner and
their active souls (including bonded minions and the shaman itself) within a
six-block sphere around the owner receive effects. Other players, pets and
unowned mobs are excluded. Native effect immunities and stronger potion effects
are respected; undead do not gain regeneration. Moving away or recalling the
shaman stops future refreshes; existing effects expire naturally.

Only loaded nearby mobs are queried, once per successful cast. Eligibility is
retried once per second while idle. There are no global entity scans or forced
chunk loads, and no synchronization updates on each animation frame.

The mod builds on Forge 36.2.42 with Java 8 bytecode. Source review is separate
from the user's manual gameplay checks; no automated game tests were requested.
