"""Approved half-block projector skins, built from cuboids and existing material textures."""
from pathlib import Path
import base64
import importlib.util
import json
import shutil
import uuid
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
ASSETS = ROOT / 'src/main/resources/assets/soulcatcherplus'
SKILL = Path.home() / '.codex/skills/blockbench-model-bridge'
FACES = ('north', 'south', 'west', 'east', 'up', 'down')
NS = uuid.UUID('89f1ee57-71c4-451b-af70-3f5082a7b844')


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def generate(skin, vanilla):
    folder = HERE / skin
    (folder / 'textures').mkdir(parents=True, exist_ok=True)
    jar = ROOT / '.gradle-user-home/caches/forge_gradle/minecraft_repo/versions/1.16.5/client.jar'
    textures = []
    for name, source in (('body', vanilla), ('top', vanilla if skin == 'dark' else 'quartz_block_top')):
        path = folder / f'textures/{name}.png'
        if jar.exists():
            with ZipFile(jar) as archive:
                path.write_bytes(archive.read(f'assets/minecraft/textures/block/{source}.png'))
        elif not path.exists():
            raise FileNotFoundError(f'Keep {path} or supply the cached Minecraft client JAR.')
        textures.append({'name': name, 'path': f'textures/{name}.png', 'width': 16, 'height': 16})
    for name, material in (('lens', 'chain'), ('light', 'soul_cyan')):
        shutil.copyfile(HERE.parent / 'iron-soul-cage/textures' / f'{material}.png', folder / f'textures/{name}.png')
        textures.append({'name': name, 'path': f'textures/{name}.png', 'width': 8, 'height': 8})
    cubes = []

    def cube(name, lo, hi, material, top=None):
        sizes = [hi[i] - lo[i] for i in range(3)]
        faces = {}
        for side in FACES:
            index = top if side == 'up' and top is not None else material
            width, height = (sizes[0], sizes[2]) if side in ('up', 'down') else (
                (sizes[2], sizes[1]) if side in ('west', 'east') else (sizes[0], sizes[1]))
            faces[side] = {'texture': index, 'uv': [0, 0, min(width, textures[index]['width']), min(height, textures[index]['height'])]}
        cubes.append({'name': name, 'from': lo, 'to': hi, 'faces': faces})

    cube('inset_foot', [1, 0, 1], [15, 1, 15], 0, 1)
    cube('slab_body', [0, 1, 0], [16, 7, 16], 0, 1)
    cube('north_rim', [0, 7, 0], [16, 8, 3], 0, 1)
    cube('south_rim', [0, 7, 13], [16, 8, 16], 0, 1)
    cube('west_rim', [0, 7, 3], [3, 8, 13], 0, 1)
    cube('east_rim', [13, 7, 3], [16, 8, 13], 0, 1)
    cube('recessed_lens', [3, 7, 3], [13, 7.05, 13], 2)
    for name, lo, hi in (
        ('north', [3.25, 7.05, 3.25], [12.75, 7.1, 3.5]),
        ('south', [3.25, 7.05, 12.5], [12.75, 7.1, 12.75]),
        ('west', [3.25, 7.05, 3.5], [3.5, 7.1, 12.5]),
        ('east', [12.5, 7.05, 3.5], [12.75, 7.1, 12.5]),
    ):
        cube('emitter_' + name, lo, hi, 3)
    plan = {'name': 'Soul Projector - ' + skin.title(), 'model_format': 'free',
        'target_size': {'minecraft_blocks': [1, .5, 1], 'blockbench_units': [16, 8, 16],
            'fit_policy': 'Complete physical block including the inset emitter; projected soul is separate.'},
        'texture_width': 16, 'texture_height': 16, 'textures': textures, 'cubes': cubes,
        'groups': [{'name': 'projector', 'children': [cube['name'] for cube in cubes]}]}
    write(folder / 'plan.json', plan)
    spec = importlib.util.spec_from_file_location('bridge', SKILL / 'scripts/plan_to_bbmodel.py')
    bridge = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(bridge)
    model, warnings = bridge.convert(plan)
    for texture in model['textures']:
        texture['source'] = 'data:image/png;base64,' + base64.b64encode((folder / texture['relative_path']).read_bytes()).decode()
    write(folder / f'soul_projector_{skin}.bbmodel', model)
    if warnings:
        raise ValueError(warnings)


if __name__ == '__main__':
    generate('dark', 'polished_blackstone')
    generate('light', 'quartz_block_side')
    print('Generated two editable 16 x 8 x 16 projector models; 11 cuboids each.')
