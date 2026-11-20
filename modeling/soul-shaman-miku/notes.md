# Soul Shaman alternate form

The model follows the approved silver costume concept. The sleeveless shirt has two flared panels, with a pleated dark skirt, detached sleeves and high boots. Each ponytail begins with a narrow strand through a tilted four-sided cuff. Two adjacent edges of each cuff carry the red accent. The hair shape on the back follows the silhouette in the side view.

The face, sleeves, pleated skirt, boots and large hair sections use flat-faced loft meshes. The eye blocks, cuffs, leek and other small features remain editable cuboids. The associated plan stores the lofts in its `meshes` field. The existing cuboid-only runtime exporter would need mesh support before this project could be imported into Minecraft.

The green onion and grip use the anatomical right arm. The cast motion uses the Soul Shaman tracks mirrored to that arm. The boots stand on a small cyan-edged platform that follows the hover animation. A cyan glow hangs just beneath its hull. The target resting envelope matches the Soul Shaman at 17 × 26 × 8 Blockbench units. The cast gesture can extend past that envelope.

Run `python modeling/soul-shaman-miku/generate_model.py` to rebuild the editable project, plan and material swatches. The `.bbmodel` embeds the swatches.
