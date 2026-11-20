"""Export the original Blockbench raven to the native Forge 1.16.5 renderer.

Run from any directory with Python 3. Uses only the standard library. Materials
are flat swatches, one material per cuboid.
Animation remains hand-authored in SoulRavenModel, independently of geometry.
"""
from pathlib import Path
import base64
import json
import math
import struct
import zlib

ROOT = Path(__file__).resolve().parent.parent
MODEL = ROOT / 'modeling/legendary-raven/legendary_raven.bbmodel'
OUTPUT = ROOT / 'src/main/java/dev/soulcatcherplus/client/model/SoulRavenGeometry.java'
TEXTURES = ROOT / 'src/main/resources/assets/soulcatcherplus/textures/entity'

def material_color(texture):
    """Read the first RGB pixel of the model's flat swatch instead of duplicating its palette."""
    data = base64.b64decode(texture['source'].split(',', 1)[1])
    if data[:8] != b'\x89PNG\r\n\x1a\n' or data[24] != 8 or data[25] not in (2, 6):
        raise ValueError('Raven materials must be flat 8-bit RGB/RGBA PNG swatches')
    compressed = bytearray()
    offset = 8
    while offset < len(data):
        size = struct.unpack_from('>I', data, offset)[0]
        if data[offset + 4:offset + 8] == b'IDAT':
            compressed.extend(data[offset + 8:offset + 8 + size])
        offset += size + 12
    # At the first pixel all PNG filter predictors are zero; these are the original RGB bytes.
    return zlib.decompress(compressed)[1:4]


def write_png(path, colors, eye_tiles, eyes_only):
    def chunk(tag, data):
        return (struct.pack('>I', len(data)) + tag + data
                + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff))
    rows = []
    for y in range(64):
        row = bytearray([0])
        for x in range(128):
            tile = y // 32 * 4 + x // 32
            alpha = 255 if tile < len(colors) and (not eyes_only or tile in eye_tiles) else 0
            # The native eyes layer uses additive blending: transparent texels
            # must also have black RGB, otherwise the entire bird would glow.
            rgb = colors[tile] if alpha else bytes(3)
            row.extend(rgb + bytes([alpha]))
        rows.append(row)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'
                     + chunk(b'IHDR', struct.pack('>IIBBBBB', 128, 64, 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(b''.join(rows))) + chunk(b'IEND', b''))


def main():
    from model_surfaces import bake, java_geometry, write_preview
    model = json.loads(MODEL.read_text(encoding='utf-8'))
    materials = {t['uuid']: i for i,t in enumerate(model['textures'])}
    colors = [material_color(t) for t in model['textures']]
    emissive = {i for i,t in enumerate(model['textures']) if t['name'] in ('eye', 'amulet')}
    if len(materials)>8: raise ValueError('Raven material atlas supports eight swatches')
    def material(texture, poly):
        tile = materials[texture['uuid']]
        u, v = (tile%4*32+16)/128, (tile//4*32+16)/64
        return ('EMISSIVE' if tile in emissive else 'BODY', [p[:3]+(u,v) for p in poly])
    animated = {'raven', 'body', 'head', 'wing_left', 'wing_right', 'tail', 'leg_left', 'leg_right'}
    ordered, surfaces, report = bake(model, animated, material)
    OUTPUT.write_text(java_geometry('SoulRavenGeometry', 'CuboidModelPart<Material>',
        'dev.soulcatcherplus.client.model.SoulRavenModel.Material', ordered, surfaces), encoding='utf-8')
    TEXTURES.mkdir(parents=True, exist_ok=True)
    write_png(TEXTURES/'soul_raven.png', colors, emissive, False)
    # Keep the old material atlas available; emissive faces are now excluded from the body pass.
    write_png(TEXTURES/'soul_raven_eyes.png', colors, emissive, True)
    preview_surfaces = {name:[(mat,[p[:3]+(.5,.5) for p in poly],normal,texture)
        for mat,poly,normal,texture in faces] for name,faces in surfaces.items()}
    write_preview(MODEL, model, ordered, preview_surfaces, report)
    print('Soul Raven:', report)


if __name__ == '__main__':
    main()
