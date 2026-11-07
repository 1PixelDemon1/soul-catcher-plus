# Runic cloak texture

The current atlas was edited with the built-in image generation tool, then
sampled at logical texel centres into a native 16x16 PNG and restricted to six
flat palette colors. This removes generated grain and fine color variations.
The current generated source is `texture-source-coarse.png`; the earlier
detailed source remains `texture-source-runic.png`. The model uses
`textures/cloak_runic.png`. Reimport with `import-coarse-texture.ps1 -SourcePath
<generated PNG>`, then regenerate the model and run the runtime exporter.

Four 8x8 atlas regions provide lapel embroidery, hood ornament, a rear rune
and quiet cloth with edge trim. `cloth_uv()` in the generator assigns these
regions to broad cloth faces and selects plain cloth for thin cut edges. The
three named cloak materials use identical atlas pixels with their own UVs.
Side panels crop an embroidered vertical strip from the left/right edge of the
front region. Its UV width-to-height ratio follows the cuboid's depth-to-height
ratio, so the pattern's texels remain square rather than stretching a full panel.
The original 64-unit authoring UV layout is multiplied by 16/64 before export,
so the same normalized surface regions remain assigned at the lower resolution.
The blue thread uses normal shading. Only the separate amulet stays emissive.
Vanilla skeleton and Ender Chest textures, geometry and carrying pose are kept.

## Current coarse atlas generation prompt

Edit target: the attached original 64x64 cloak texture atlas. Make a drastically simpler coarse-pixel replacement for a tiny Minecraft skeleton's cloak. Keep the existing 2-by-2 atlas organization, dark desaturated teal cloth and restrained soul-blue decoration. IMPORTANT target is ONLY 16 by 16 LOGICAL PIXELS over the entire square, 8 by 8 pixels per quadrant; render as a clean nearest-neighbor enlarged 16x16 pixel grid, every logical pixel a large equal solid square, no subdivisions. Top left quadrant: two simple cyan edge stripes, one small chunky angular accent, mostly plain dark cloth. Top right quadrant: one large cyan stepped hood chevron near the top, mostly plain dark cloth. Bottom left quadrant: one very simple chunky cyan diamond/soul symbol at center plus one single cyan bottom hem stripe. Bottom right quadrant: plain dark cloth with a cyan stripe on bottom edge and right edge. Use only SIX flat solid colors: dark cloth #192B33, main cloth #253D43, restrained shadow highlight #3A5860, cyan thread #147EAA and #2DBCD7 with rare #8DE3EB. No fine stitching, no tiny filigree, no noise/dither, no gradients, no glow, no antialiasing, no fabric grain, no text/labels, no margins or gaps between quadrants. The goal is the same broad pixel size as vanilla Minecraft skeleton textures, with very simple readable decoration. Output is a flat square 2D texture atlas filling the canvas, NOT an illustration of a cloak or a 3D render.

## Original detailed atlas generation prompt

Use case: stylized-concept. Asset type: original flat pixel-art texture ATLAS for a Minecraft-style dark teal cloak worn by a tiny skeleton courier with an Ender Chest. Produce ONE square atlas, a 2 by 2 arrangement of FOUR square fabric panels, no gaps, no labels, no lines separating panels. It must look like genuine coarse 64 by 64 logical pixel art (each quadrant 32 by 32 logical pixels); if delivered enlarged, use only nearest-neighbor square pixels. Colors: very dark desaturated teal cloth #192B33 and #253D43, restrained cloth highlights #3A5860, embroidered soul-blue #147EAA, #2DBCD7, small pale-cyan #8DE3EB highlights. At least 75 percent quiet dark cloth, NO grain/dithering. TOP LEFT panel: vertical cloak-lapel design, cyan stitched edging and two large simple angular glyphs along each side, quiet middle. TOP RIGHT panel: hood cloth, a sparse symmetrical cyan stepped chevron and broken rune border, with plain dark cloth in its centre. BOTTOM LEFT panel: cloak-back design with a large clean angular soul/rune emblem, and cyan double hem stripe near the bottom. BOTTOM RIGHT panel: trim material, mostly plain dark teal with a repeating two-pixel-wide cyan stepped embroidery stripe along its bottom edge and right edge. Strict flat orthographic 2D texture, fill the whole square; hard square pixel edges, chunky readable geometry, NOT a garment drawing, NOT a model, NOT a perspective render, no backdrop/margins, no text or letters, no borders around the whole image, no antialiasing, no soft glow, no gradients. The blue embroidery itself should be colored thread, not emissive; a separate amulet will glow in the model.
