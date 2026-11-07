# Soul Scroll icon — design preview

`soul_scroll.png` is the native **16×16 RGBA** item sprite. `preview.png` is the
same pixel map enlarged 32 times without filtering. Both use full transparency,
ten flat opaque colors and a one-pixel transparent margin.

A dark, aged parchment roll sits diagonally with curled ends, a narrow black
cord and a cyan soul seal. The second variant below is the approved in-game sprite.
`sprite.json` uses the same editable pixel-map approach as the existing inventory
icons. Run `python art/soul-scroll/export.py` to regenerate these preview assets.

The built-in image generation tool supplied `concept-v1.png`; the native icon is
a deliberate adaptation to the established 16×16 item grid and limited palette.
Concept prompt: closed darkened parchment scroll, thin black thread, bright cyan
wax seal with a simple soul impression; diagonal vanilla Minecraft item sprite,
coarse logical 16×16 pixels, few flat colors, transparent background, no text,
anti-aliasing, blur, dithering or glow.

## Second variant

`soul_scroll-v2.png` / `preview-v2.png` use a shorter, broader silhouette, rolled
top/bottom lips and a larger central cyan wax seal. The black cord is one pixel
thick. `sprite-v2.json` is the editable native pixel map; the first draft remains
available. Export with `python art/soul-scroll/export.py --variant v2`.

The built-in image tool generated `concept-v2.png` with this prompt:

> Create ONE Minecraft item inventory icon: SOUL SCROLL. New silhouette: a SHORT BROAD parchment scroll, almost upright with a very slight diagonal tilt. This must read as folded/rolled PAPER, not as a long wooden stick, staff, log, bottle or sack. Broad flat aged parchment center, clearly recognizable curled rolled lips at top AND bottom, small visible dark spiral/curl on each end. Darkened warm ochre/taupe paper, lighter broad face and dark brown edges, compact clean clusters. A thin BLACK thread wraps horizontally across the middle, holding the parchment shut. A large bright CYAN BLUE wax seal sits ON TOP of that thread, centered slightly below the middle; about 4x4 logical pixels with a simple pale-cyan soul impression. This is actual Minecraft-resolution art: exactly 16 by16 logical pixels, occupied footprint around 11 wide x14 high, transparent margins. Draw with a consistent uniform square-pixel grid. All details must survive at 16x16. The seal must be legible and the parchment silhouette must remain broad and chunky. At most 10 opaque flat colors plus full transparency. Four warm parchment tones, near-black cord, three cyan seal tones. Absolutely no soft gradients, antialiasing, grain, dithering, subpixels, glow, shadow, background, grid, checkerboard, letters, text or border. If enlarged for delivery, show only a nearest-neighbor enlargement of the 16x16 sprite, no added detail.

The approved `soul_scroll-v2.png` is copied unchanged to the in-game soul scroll
texture. The ordinary scroll defaults to a pig; other forms can be stored in item NBT.
Soul Raven and Soul Courier have separate Forgotten Soul Scroll variants.

## Boss variant

`sprite-boss.json` preserves the approved second variant's pixel map, parchment
and black thread. Its four seal colors use a dark violet to pale lilac palette.
Export with `python art/soul-scroll/export.py --variant boss`. The native
`soul_scroll-boss.png` is copied unchanged to `textures/item/boss_soul_scroll.png`;
`preview-boss.png` enlarges it without filtering. This is a palette edit of the
editable source map, with no new image generation or resampling.
