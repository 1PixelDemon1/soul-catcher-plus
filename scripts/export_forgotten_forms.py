# SPDX-License-Identifier: MIT
"""Bake the authored alternate forms and their flat-color texture atlases for Forge 1.16.5."""
import json
import struct
import zlib

from export_soul_raven import ROOT, TEXTURES, material_color
from model_surfaces import bake, java_geometry

OUTPUT = ROOT / 'src/main/java/dev/soulcatcherplus/client/model'
PACKAGE = 'dev.soulcatcherplus.client.model.'
TILE_SIZE = 8
TILES_PER_ROW = 8


def write_atlas(path, colors):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))

    rows = []
    for start in range(0, len(colors), TILES_PER_ROW):
        tiles = colors[start:start + TILES_PER_ROW]
        pixels = b''.join(color * TILE_SIZE for color in tiles)
        rows.extend([b'\0' + pixels.ljust(TILE_SIZE * TILES_PER_ROW * 3, b'\0')] * TILE_SIZE)
    header = struct.pack('>IIBBBBB', TILE_SIZE * TILES_PER_ROW, len(rows), 8, 2, 0, 0, 0)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header)
                     + chunk(b'IDAT', zlib.compress(b''.join(rows))) + chunk(b'IEND', b''))


def export(folder, stem, geometry, model_type, animated, body, glow, glow_names):
    project = ROOT / 'modeling' / folder / (stem + '.bbmodel')
    model = json.loads(project.read_text(encoding='utf-8'))
    textures = model['textures']
    atlas_rows = (len(textures) + TILES_PER_ROW - 1) // TILES_PER_ROW
    indices = {texture['uuid']: i for i, texture in enumerate(textures)}

    def material(texture, polygon):
        index = indices[texture['uuid']]
        u = (index % TILES_PER_ROW + 0.5) / TILES_PER_ROW
        v = (index // TILES_PER_ROW + 0.5) / atlas_rows
        return (glow if texture['name'] in glow_names else body, [vertex[:3] + (u, v) for vertex in polygon])

    bones, surfaces, report = bake(model, animated, material)
    source = java_geometry(geometry, 'CuboidModelPart<Material>', PACKAGE + model_type + '.Material', bones, surfaces)
    (OUTPUT / (geometry + '.java')).write_text(source, encoding='utf-8')
    write_atlas(TEXTURES / (stem + '.png'), [material_color(texture) for texture in textures])
    print(stem, report)


def main():
    export('soul-long-tailed-tit', 'soul_long_tailed_tit', 'SoulLongTailedTitGeometry', 'SoulRavenModel',
           {'raven', 'body', 'head', 'wing_left', 'wing_right', 'tail', 'leg_left', 'leg_right'},
           'BODY', 'EMISSIVE', {'soul_amulet'})
    export('soul-shaman-miku', 'soul_shaman_miku', 'SoulShamanMikuGeometry', 'SoulShamanModel',
           {'root', 'head', 'arm_left', 'arm_right', 'staff', 'wisp'},
           'CLOTH', 'SOUL_LIGHT', {'soul_light'})


if __name__ == '__main__':
    main()
