# SPDX-License-Identifier: MIT
"""Export the approved cuboids as a single exposed shell per animated bone."""
import base64
import json
from pathlib import Path
from model_surfaces import bake, java_geometry, write_preview

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT/'modeling/soul-shaman-cuboid/soul_shaman.bbmodel'
OUTPUT = ROOT/'src/main/java/dev/soulcatcherplus/client/model/SoulShamanGeometry.java'
TEXTURES = ROOT/'src/main/resources/assets/soulcatcherplus/textures/entity/soul_shaman'


def main():
    model = json.loads(SOURCE.read_text(encoding='utf-8'))
    animated = {'root', 'head', 'arm_left', 'arm_right', 'staff', 'charm', 'wisp'}
    animated.update(g['name'] for g in model['groups'] if g['name'].startswith('hem_'))
    ordered, surfaces, report = bake(model, animated, lambda texture, poly: (texture['name'].upper(), poly))
    OUTPUT.write_text(java_geometry('SoulShamanGeometry', 'CuboidModelPart<Material>',
        'dev.soulcatcherplus.client.model.SoulShamanModel.Material', ordered, surfaces), encoding='utf-8')
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for texture in model['textures']:
        (TEXTURES/(texture['name']+'.png')).write_bytes(base64.b64decode(texture['source'].split(',', 1)[1], validate=True))
    write_preview(SOURCE, model, ordered, surfaces, report)
    print('Soul Shaman:', report)


if __name__ == '__main__':
    main()
