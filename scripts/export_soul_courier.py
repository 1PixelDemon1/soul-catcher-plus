# SPDX-License-Identifier: MIT
"""Export the approved Courier with hidden intersections removed; preserve vanilla UVs."""
import json
import shutil
from pathlib import Path
from model_surfaces import bake, java_geometry, write_preview

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT/'modeling/soul-courier/soul_courier.bbmodel'
OUTPUT = ROOT/'src/main/java/dev/soulcatcherplus/client/model/SoulCourierGeometry.java'
TEXTURES = ROOT/'src/main/resources/assets/soulcatcherplus/textures/entity/soul_courier'
MATERIALS = {'skeleton': 'SKELETON', 'ender_chest': 'CHEST', 'cloak': 'CLOAK',
             'cloak_shadow': 'CLOAK', 'cloak_edge': 'CLOAK', 'strap': 'STRAP', 'soul': 'SOUL'}


def main():
    model = json.loads(SOURCE.read_text(encoding='utf-8'))
    ordered, surfaces, report = bake(model, {'root', 'head', 'left_leg', 'right_leg', 'chest_lid', 'chest_lock'},
        lambda texture, poly: (MATERIALS[texture['name']], poly))
    OUTPUT.write_text(java_geometry('SoulCourierGeometry', 'SoulCourierModel.Part',
        'dev.soulcatcherplus.client.model.SoulCourierModel.Material', ordered, surfaces), encoding='utf-8')
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for name in ('cloak', 'strap', 'soul'):
        shutil.copyfile(SOURCE.parent/'textures'/(name+'.png'), TEXTURES/(name+'.png'))
    write_preview(SOURCE, model, ordered, surfaces, report)
    print('Soul Courier:', report)


if __name__ == '__main__':
    main()
