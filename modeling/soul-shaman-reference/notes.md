# Soul Shaman — reference rebuild

Rebuilt independently from the user-supplied three-view `reference.png`.
The previous work remains in `../soul-shaman`; no runtime mod assets changed.

## Scale and interpretation

The previously approved staff height is 1.5 Minecraft blocks (24 Blockbench
units including flame). The planning envelope remains 16x25x8 units. The
reference front and back views determine the major silhouette, face markings,
teal motifs, split robe, shoulder cape, pendant and crooked wooden staff.
Depth, hidden surfaces and cloth thickness are inferred: the reference is
concept art rather than an orthographic dimensioned mesh drawing.

## Construction

- 84 editable cuboids, a 56-face hood shell (40 quads and 16 triangles), and
  20 surface-following rune quads. Total 1144 triangulated faces, nine named
  bones. No subdivision or bevel modifiers; all surfaces are coarse flat planes.
- Hood: the front outline is retained, but four depth sections now shape a
  crown, projecting occiput and narrowing nape. Its depth is 7.78 units rather
  than the former flat rear plate. The rim now projects 0.74 units in front of
  the eyes while the mask stays in its approved position. The interior has its
  own dark cup, rim, cloth chin closure and neck lining.
  The lower front rim has returned to Z -2.7; the projection grows toward
  the crown at Z -3.8. The chin closure retracts with that lower rim, while
  the underside remains closed. Front cyan marks follow the inclined surface.
  Cyan motifs follow the actual side and rear bevels.
- Mask: broad ivory upper face, narrow chin, rectangular dark eye recesses with
  cyan lower portions, stepped forehead/chin cuts and quiet ivory patches.
- Robe: three hanging front panels and matching rear panels, flared sides,
  loose sleeves, a two-layer shoulder cape, leather belt and square cyan amulet.
  A solid cloth lining behind the hanging panels closes the underside around
  the emerging soul flame.
- Staff: long wood shaft, angular double bend, ivory fork, one bound hanging
  bone and stepped soul flame. No extra heel cap or decorative shaft wraps.
- The lower ghost flame has a curved stepped silhouette and a bright core.
- Cyan hood/hem/cape motifs are thin cuboids positioned against the corresponding
  planes. Cloth UVs retain a consistent coarse texel density.
- Soul-colored materials are flagged emissive in Blockbench. Full-bright
  rendering in Minecraft would still require renderer integration.

## Texture provenance

`cloth-source.png` was created with the built-in imagegen tool using the
supplied character art as a fabric-color reference. Its 16x16 pixel pattern
was sampled at texel centers into `textures/cloth.png`, without adding painted
details. Other materials are native solid color swatches and per-face bindings.
The exact generation prompt is in `cloth-prompt.txt`.

## Validation and previews

`generate_model.py` regenerates the cube plan, native hood meshes and portable
`.bbmodel` with embedded textures. It checks the size envelope (including hood
vertices), hood UV range, nonzero face areas and closed-manifold edge counts.
The installed bridge validator passes the cubes; it reports that mesh geometry
is outside its deep-validation scope. The hood has additional generator checks
and was visually inspected in Blockbench from the front, side and rear.

Current native Blockbench screenshot: `preview-hood-taper.png`. The earlier
`preview-hood-overhang.png` shows the rim before retracting its lower part. The
earlier
`preview-volume-front.png`, `preview-volume-side.png` and `preview-volume-back.png`
show the previous volume pass before the rim extension and underside lining.
Older previews and
`soul_shaman-flat-back.bbmodel` preserve the previous silhouette plate for
comparison. The current model is `soul_shaman.bbmodel`, open in Blockbench.
No gameplay code, entity registration, animation or game build was part of
this refinement.
