# Asset authoring

Gradle builds use the Java geometry and resources already committed under
`src/main`. Asset generation is a separate authoring step. Keep the approved
sources below; previews, animation-frame sheets and bounds reports are generated
locally and ignored by Git.

## Branding

`art/branding/logo-banner.png` is the user-supplied logo with lettering, used for
the GitHub banner. `logo-icon.png` has no lettering and supplies other branding.
Preserve both originals, their transparency and aspect ratio. Derived pixel-art
copies use nearest-neighbor scaling. The runtime mod logo is `src/main/resources/logo.png`.

## Item artwork

| Asset | Editable source | Export |
| --- | --- | --- |
| Held scythes | `art/scythes/*.png`, `held-palettes.json`, `held-geometry.json` | `scripts/prepare_scythe_materials.ps1`, then `scripts/prepare_scythe_art.ps1` |
| Scythe inventory icons | `art/scythes/inventory.json` | `python scripts/generate_assets.py` |
| Animated cage icons | `art/soul-cages/animated/sprites.json` | `python scripts/prepare_cage_art.py` |
| Soul scrolls | `art/soul-scroll/sprite-v2.json`, `sprite-boss.json`, `sprite-forgotten.json` | `python art/soul-scroll/export.py --variant v2`, `boss` or `forgotten` |
| Guidebook icon | `art/soul-guide/guidebook-source-v3.png` | Approved runtime icon: `textures/item/soul_guide.png` |
| Soul remnants | Runtime `textures/entity/soul_remnant/{small,medium,large,skull}.png` | Edit the existing PNGs directly |

Runtime texture paths in this document are relative to
`src/main/resources/assets/soulcatcherplus`. Scythe and cage exporters update
runtime files. The scroll exporter creates local 16x16 PNGs and enlarged previews;
after reviewing them, copy the selected icon to the corresponding runtime item
texture (`soul_scroll.png`, `boss_soul_scroll.png` or `forgotten_soul_scroll.png`).
The default scroll export is the approved second variant.

The iron held-scythe master was created with the image-generation tool; material
variants use its silhouette and the checked-in palettes. Cage and scroll pixel
maps retain the approved small-pixel designs. The guide icon follows the vanilla
book silhouette with a teal cover and cyan seal; only the final source is kept.

## Blockbench models

| Model | Approved source | Runtime exporter |
| --- | --- | --- |
| Standard cages | `modeling/iron-soul-cage/iron_soul_cage.bbmodel` | `python scripts/export_soul_cages.py` |
| Supreme cage | `modeling/supreme-soul-cage/supreme_soul_cage.bbmodel` | Same cage exporter |
| Soul Raven | `modeling/legendary-raven/legendary_raven.bbmodel` | `python scripts/export_soul_raven.py` |
| Soul Courier | `modeling/soul-courier/soul_courier.bbmodel` | `python scripts/export_soul_courier.py` |
| Soul Shaman | `modeling/soul-shaman-cuboid/soul_shaman.bbmodel` | `python scripts/export_soul_shaman.py` |

Keep each project's generator, input plans and local textures. The supreme cage
generator also reads the iron cage's plan and model. The Shaman uses its local
`base-cube-plan.json`; it does not depend on an older Shaman project. The Raven's
flight pose is used by its bounds checker, and the Courier's open-chest pose is
an editable inspection view. Rejected model families are not authoring inputs.

Edit approved `.bbmodel` files in Blockbench, then run the corresponding runtime
exporter. The optional `generate_model.py` scripts rebuild authoring projects from
their plans and require the installed Blockbench Model Bridge skill. Runtime
exporters use Python's standard library; Python 3.10 or newer supports the current
scripts. The scythe PowerShell tools additionally use `System.Drawing`.

The Courier's custom cloth uses the native 16x16 `textures/cloak_runic.png` atlas
with six flat colors (`192B33`, `253D43`, `3A5860`, `147EAA`, `2DBCD7`, `8DE3EB`).
Its approved image-generation source is `texture-source-coarse.png`; use
`import-coarse-texture.ps1 -SourcePath <PNG>` to import a replacement. Keep
`vanilla-sources.json` for the provenance of its vanilla skeleton/chest materials.
The Shaman's cloth was generated as a quiet, coarse dark-teal material tile;
its final local texture, mask, trim and flat material swatches remain editable.
The current Shaman reference image is retained with that project.

The supreme cage's approved thick bone ribs follow the stepped glass contour.
Its final generator and model contain that geometry directly. Raven, Courier and
Shaman runtime animations are maintained in their Java model classes; exporting
geometry does not replace those animation rules.

Authored models and legacy exporters are maintained on `develop`. When porting,
translate their generated geometry to the target renderer API; do not run a
legacy Java exporter over a newer branch without reviewing its native renderer.
