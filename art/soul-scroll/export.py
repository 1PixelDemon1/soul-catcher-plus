# SPDX-License-Identifier: MIT
"""Export the Soul Scroll pixel map, without changing any runtime resources."""
from pathlib import Path
import json
import argparse
import struct
import zlib

HERE = Path(__file__).resolve().parent


def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xFFFFFFFF)


def export(spec, zoom, name):
    size = spec['size']
    assert len(spec['pixels']) == size and all(len(row) == size for row in spec['pixels'])
    palette = {key: bytes.fromhex(value) for key, value in spec['palette'].items()}
    scanlines = []
    for row in spec['pixels']:
        scanline = b'\0' + b''.join(palette[pixel] * zoom for pixel in row)
        scanlines.extend([scanline] * zoom)
    edge = size * zoom
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>2I5B', edge, edge, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(b''.join(scanlines))) + chunk(b'IEND', b'')
    (HERE / name).write_bytes(png)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--variant', default='')
    args = parser.parse_args()
    suffix = '-' + args.variant if args.variant else ''
    spec = json.loads((HERE / f'sprite{suffix}.json').read_text(encoding='utf-8'))
    export(spec, 1, f'soul_scroll{suffix}.png')
    export(spec, 32, f'preview{suffix}.png')
    print('Exported 16x16 RGBA Soul Scroll and 512x512 nearest-neighbor preview. Game assets unchanged.')
