# SPDX-License-Identifier: MIT
"""Generate exact 16x16 scythe icons from their pixel map and an empty GameTest arena."""
from pathlib import Path
import gzip
import json
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
def chunk(name, data):
    return struct.pack('>I', len(data)) + name + data + struct.pack('>I', zlib.crc32(name + data))


def scythe_icons():
    spec = json.loads((ROOT / 'art/scythes/inventory.json').read_text(encoding='utf-8'))
    for name, material in spec['materials'].items():
        rows = spec.get('pixels_by_material', {}).get(name, spec['pixels'])
        if len(rows) != 16 or any(len(row) != 16 for row in rows):
            raise ValueError('Scythe inventory sprites must be exactly 16x16')
        colors = {key: tuple(bytes.fromhex(value))
                  for key, value in (spec['shared'] | material).items()}
        raw = b''.join(b'\x00' + bytes(channel for key in row for channel in colors[key]) for row in rows)
        png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>2I5B', 16, 16, 8, 6, 0, 0, 0))
        png += chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
        texture = ROOT / f'src/main/resources/assets/soulcatcherplus/textures/item/{name}.png'
        texture.parent.mkdir(parents=True, exist_ok=True)
        texture.write_bytes(png)


scythe_icons()


def string(value):
    data = value.encode('utf-8')
    return struct.pack('>H', len(data)) + data


def payload(kind, value):
    if kind == 3:
        return struct.pack('>i', value)
    if kind == 8:
        return string(value)
    if kind == 9:
        subtype, items = value
        return bytes([subtype]) + struct.pack('>i', len(items)) + b''.join(payload(subtype, item) for item in items)
    if kind == 10:
        return b''.join(bytes([t]) + string(k) + payload(t, v) for k, (t, v) in value.items()) + b'\x00'
    raise ValueError(kind)


floor = [{'pos': (9, (3, [x, 0, z])), 'state': (3, 0)} for x in range(16) for z in range(16)]
arena = {'DataVersion': (3, 3465), 'size': (9, (3, [16, 8, 16])),
         'palette': (9, (10, [{'Name': (8, 'minecraft:stone')}])),'blocks': (9, (10, floor)),
         'entities': (9, (10, []))}
structure = ROOT / 'src/gametest/resources/data/soulcatcherplus/structures/arena.nbt'
structure.parent.mkdir(parents=True, exist_ok=True)
structure.write_bytes(gzip.compress(b'\x0a\x00\x00' + payload(10, arena), mtime=0))
print('Generated 16x16 scythe icons and GameTest arena')
