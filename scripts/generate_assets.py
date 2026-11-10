# SPDX-License-Identifier: MIT
"""Generate exact 16x16 scythe icons from their editable pixel map."""
from pathlib import Path
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


if __name__ == '__main__':
    scythe_icons()
    print('Generated 16x16 scythe icons')
