# Soul Pylon model study

Approved reference: the short stepped dark-stone column and visible front scroll
socket. Cyan pedestal insets were removed following the user's design revision.
Back and sides are inferred symmetrical stone surfaces;
only the north face has a scroll socket. All geometry is cuboids.

Size: pedestal fits one Minecraft block (16 x 16 x 16 units), with an actual
14.08 x 16 x 14.08 silhouette. The complete preview is about 1.6 blocks tall.

- `soul_pylon.bbmodel`: pedestal and independently hideable inserted-scroll group.
- `soul_pylon_with_cage.bbmodel`: presentation scene using the **existing** iron
  cage geometry, materials and soul animation. An outer preview group adds slow
  rotation and levitation; the cage is not baked into the pedestal model.
- `plan.json`: editable cuboid plan; `generate_model.py` regenerates both projects.

Stone uses vanilla Minecraft 1.16.5 polished blackstone; corner fittings use the
vanilla gold block texture. UVs retain block-scale stone texels on each cuboid.
Other materials reuse existing authored PNGs without altering the originals.
Textures are embedded in both projects for portable Blockbench viewing.

Implementation remains separate: render the installed cage ItemStack above the
pedestal (including its material and animation), hide it when empty, and render
the inserted scroll only when present. The animated preview is a design study,
not gameplay or a Java block export. A spirit tether belongs to a runtime effect,
not permanent cuboids in this model. No game resources or mod code are changed.
