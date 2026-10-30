# SPDX-License-Identifier: MIT
"""Export the editable cage pixel maps to native animated textures and an APNG preview."""
from pathlib import Path
import json
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'art/soul-cages/animated'
TEXTURES = ROOT / 'src/main/resources/assets/soulcatcherplus/textures/item'


def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))


def image_data(rows):
    return zlib.compress(b''.join(b'\0' + bytes(channel for pixel in row for channel in pixel) for row in rows))


def png(width, height, rows):
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>2I5B', width, height, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', image_data(rows)) + chunk(b'IEND', b''))


def export():
    spec = json.loads((ART / 'sprites.json').read_text(encoding='utf-8'))
    size, body, frames = spec['size'], spec['body'], spec['frames']
    if size != 16 or len(body) != size or any(len(row) != size for row in body):
        raise ValueError('Cage frames must be exactly 16x16')
    if len(frames) != 4 or any(len(frame) != 3 for frame in frames):
        raise ValueError('Expected four poses of three souls')

    poses = []
    for frame in frames:
        pose = [list(row) for row in body]
        occupied = set()
        for symbol, soul in zip('ABC', frame):
            for part, color in [('tail', 't'), ('head', symbol)]:
                x, y = soul[part]
                if not (0 <= x < size and 0 <= y < size) or body[y][x] != 'b' or (x, y) in occupied:
                    raise ValueError('Soul pixels must remain separate inside the glass')
                occupied.add((x, y))
                pose[y][x] = color
        poses.append(pose)

    material_frames = []
    for material, colors in spec['materials'].items():
        palette = {key: tuple(bytes.fromhex(value)) for key, value in {**spec['shared'], **colors}.items()}
        rendered = [[[palette[pixel] for pixel in row] for row in pose] for pose in poses]
        material_frames.append(rendered)
        for index, rows in enumerate(rendered):
            (ART / f'{material}-{index}.png').write_bytes(png(size, size, rows))
        texture = TEXTURES / f'{material}_soul_cage.png'
        texture.write_bytes(png(size, size * len(frames), [row for frame in rendered for row in frame]))
        metadata = {'animation': {'frametime': spec['frametime'], 'width': size, 'height': size,
                                  'frames': list(range(len(frames))), 'interpolate': False}}
        texture.with_suffix('.png.mcmeta').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8', newline='\n')

    # Inspectable preview: the same exported pixels, enlarged without resampling or smoothing.
    scale, gap = 12, 16
    width, height = (size * scale + gap) * len(material_frames) + gap, size * scale + 2 * gap
    preview_frames = []
    for pose in range(len(frames)):
        rows = [[(50, 64, 76, 255)] * width for _ in range(height)]
        for tier, rendered in enumerate(material_frames):
            left = gap + tier * (size * scale + gap)
            for y, row in enumerate(rendered[pose]):
                for x, color in enumerate(row):
                    if color[3] == 0:
                        continue
                    for dy in range(scale):
                        rows[gap + y * scale + dy][left + x * scale:left + (x + 1) * scale] = [color] * scale
        preview_frames.append(rows)
    (ART / 'preview.png').write_bytes(png(width, height, preview_frames[0]))
    animated = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>2I5B', width, height, 8, 6, 0, 0, 0))
    animated += chunk(b'acTL', struct.pack('>2I', len(frames), 0))
    sequence = 0
    for index, rows in enumerate(preview_frames):
        animated += chunk(b'fcTL', struct.pack('>5I2H2B', sequence, width, height, 0, 0, spec['frametime'], 20, 0, 0))
        sequence += 1
        compressed = image_data(rows)
        if index == 0:
            animated += chunk(b'IDAT', compressed)
        else:
            animated += chunk(b'fdAT', struct.pack('>I', sequence) + compressed)
            sequence += 1
    (ART / 'animated-preview.png').write_bytes(animated + chunk(b'IEND', b''))
    print(f'Prepared {len(material_frames)} cage strips: 16x64, four frames, {spec["frametime"]} ticks/frame')


if __name__ == '__main__':
    export()
