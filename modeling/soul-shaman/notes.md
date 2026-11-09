# Soul Shaman — Blockbench model

Third model pass from the approved `art/soul-shaman/concept-v1.png` design.

The user requested simple forms with few polygons and a staff approximately
1.5 blocks tall. The staff, including the soul flame, spans exactly **24 units**
from Y 0.5 to Y 24.5. One Minecraft block is 16 model units. The full planning
envelope is **16x25x8 units**; the extra vertical unit leaves room for a gentle
idle float. Body width/depth and the unseen fabric construction are inferred
from the approved concept rather than additional user constraints.

## Construction

- **66 cuboids / 396 quads / 792 triangles**, with nine named bones. No meshes,
  rounded bevels, sculpted surfaces or layered feather-like micro-details.
- An open square hood made from four boxes and a three-step ivory mask that
  narrows at the chin. Dark rectangular sockets contain cyan lower eye panels;
  stepped forehead and chin marks follow the concept art.
- Hood depth reduced from 6.6 to **3.6 units**, with 0.4-unit cloth walls and
  a seven-unit width. Its pivot now follows the shallower head volume.
- A slimmer robe (2.6-unit torso depth), two overlapping open collar layers,
  longer sleeves and three separate hanging front panels with a rear panel.
  Cyan edging uses UV crops rather than solid blue panels.
- Sleeves extend to Y 7.3 (5.6 units long rather than 3.3–3.5). The free hand
  emerges below its cuff, while the staff hand and staff pivot move to Y 8.95.
- A V-shaped leather necklace, ivory bezel, raised cyan gemstone and bright
  center give the amulet a readable pendant silhouette.
- A six-box spirit wisp replaces the feet: a broad crown, a zig-zag tapered tail,
  a side tongue and a two-part bright core.
- A wooden staff with three straight box segments, three leather wraps, a bone
  heel, a bound bone fork with broad end joints, and a shaped hanging bone charm.
  Its five-box flame has a stepped tongue and bright center.
- The same approved **16x16 coarse cloth atlas** as Soul Courier, copied
  unchanged. Patterned broad faces use proportional UV crops to avoid stretching;
  quiet cut edges use a plain patch. Other materials are solid color swatches.
- Cyan eyes, amulet and soul-flame materials are marked emissive in Blockbench.
  Future in-game full-bright passes still need a renderer; this is a model asset.

The `idle_float` clip raises the body, arms and staff together, so the grip is
preserved. Only the head and hanging charm have gentle independent turns.

## Files

- `soul_shaman.bbmodel`: portable editable project with embedded textures.
- `plan.json`: cube geometry, pivots, UVs, dimensions and hierarchy.
- `generate_model.py`: regenerate using the installed Blockbench bridge skill.
- `textures/`: separate cloth/material PNGs.
- `bounds-report.json`: geometry counts, static rotated-cube bounds and staff size.
- `preview-v3-front.png` and `preview-v3-three-quarter.png`: current previews
  exported with Blockbench's native model screenshot tool. Older previews
  remain available for comparison.

Open the `.bbmodel` in Blockbench. The face looks toward negative Z; positive Y
is up. The staff has its own child bone beneath the right arm for later animation.
This task does not register an entity, a scroll, an aura, or any gameplay behavior.
