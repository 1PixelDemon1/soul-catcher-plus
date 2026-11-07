# Forgotten Souls scroll

The built-in `image_gen` tool supplied `concept-forgotten.png`. The final native
16x16 RGBA sprite is a deliberate adaptation to the project's editable pixel map,
`sprite-forgotten.json`: faded grey-beige paper, dark teal edging, coarse faded
cyan marks and a broken-diamond memory seal. Full transparency and flat colors
match the other item icons. Geometry and creature textures are unaffected.

Regenerate art with `python art/soul-scroll/export.py --variant forgotten`.
The resulting `soul_scroll-forgotten.png` is copied unchanged to
`src/main/resources/assets/soulcatcherplus/textures/item/forgotten_soul_scroll.png`.
`preview-forgotten.png` enlarges the native pixels without filtering.

## Item behavior

`forgotten_soul_scroll` is one item family. The creative tab supplies two typed
variants, Soul Raven and Soul Courier. Each scroll unlocks only the soul named
in its tooltip and is consumed after a successful unlock. Existing duplicate,
full-collection and invalid-payload safeguards are shared with ordinary scrolls.
`SoulScroll.containing` chooses the Forgotten family for these two companions.

The earlier `courier_soul_scroll` registry ID remains readable and usable, with
the new icon and name; its creative entry is superseded by the typed variant.
Ordinary and boss scrolls remain registered with their existing icons.
Chest loot tables with a root path beginning `chests/` roll a 2% chance for one
Forgotten Soul Scroll, with equal Raven/Courier weights. Already-generated chest
contents are unchanged. The additive Forge loot modifier's `chance` is data-pack
configurable in `data/soulcatcherplus/loot_modifiers/forgotten_souls.json`.
Nonstandard third-party loot paths are not automatically included. No recipe is assigned.

Example commands:

```mcfunction
/give @s soulcatcherplus:forgotten_soul_scroll
/give @s soulcatcherplus:forgotten_soul_scroll{Soul:{Type:"soulcatcherplus:soul_courier"}}
```

## Generation prompt

Create ONE new Minecraft inventory item icon, the FORGOTTEN SOUL SCROLL, using the attached ordinary soul scroll as a style/silhouette reference. Keep a short broad upright rolled parchment scroll, a slight diagonal tilt, clearly curled top and bottom lips, black binding thread. This is a special ancient relic shared by a red-eyed raven soul and a tiny skeleton porter soul; do NOT draw either creature and do NOT add their faces. Visually express forgotten memory and faded magic: weathered desaturated GREY-BEIGE parchment, dark charcoal-teal edging, a few missing/torn corner pixels, only two or three large faded SOUL-BLUE angular ink marks on the visible paper; a prominent bright CYAN broken-diamond wax seal on the black thread, with a pale cyan core like a memory shard. Same cyan accent palette #147EAA #2DBCD7 #8DE3EB as Minecraft soul-themed companions. Broad clean color clusters, darker ancient feel, strong clear silhouette. Exactly 16 by16 LOGICAL pixels, about 11 logical pixels wide and14 tall with a one-pixel transparent margin. Every logical pixel is a square; if enlarged, nearest-neighbor enlargement only. At most12 flat opaque colors plus transparency, no antialiasing, gradients, grain, soft glow, shadows or tiny decoration. No lettering, no text, no labels, no background/checkerboard, no border, no extra items. Full real transparent background.
