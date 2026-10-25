# SPDX-License-Identifier: MIT
"""Author the original pixel scythe and a small, empty GameTest arena."""
from pathlib import Path
import gzip
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
pixels = [[(0, 0, 0, 0) for _ in range(32)] for _ in range(32)]


def rect(x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            pixels[y][x] = color


dark = (20, 28, 40, 255)
rect(20, 5, 23, 30, dark)
rect(21, 5, 22, 29, (74, 54, 67, 255))
for y in (10, 17, 24, 28):
    rect(20, y, 23, y, (62, 201, 201, 255))
for x in range(3, 28):
    y = 3 + int(((x - 15) / 7) ** 2)
    for dy in range(4):
        if y + dy < 32:
            pixels[y + dy][x] = dark if dy in (0, 3) else (102, 224, 225, 255)
for x in range(2, 10):
    pixels[6 + (10 - x) // 2][x] = (198, 255, 248, 255)
rect(19, 5, 24, 8, (36, 100, 121, 255))
rect(21, 6, 22, 7, (175, 255, 249, 255))


def chunk(name, data):
    return struct.pack('>I', len(data)) + name + data + struct.pack('>I', zlib.crc32(name + data))


raw = b''.join(b'\x00' + bytes(v for pixel in row for v in pixel) for row in pixels)
png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>2I5B', 32, 32, 8, 6, 0, 0, 0))
png += chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
texture = ROOT / 'src/main/resources/assets/soulcatcherplus/textures/item/soul_scythe.png'
texture.parent.mkdir(parents=True, exist_ok=True)
texture.write_bytes(png)


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
print('Generated scythe texture and GameTest arena')
