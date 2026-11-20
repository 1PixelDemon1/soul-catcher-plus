"""Editable pylon study; existing cage geometry is used only in the preview scene."""
from pathlib import Path
import base64
import copy
import importlib.util
import json
import shutil
import uuid
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
MODELING = HERE.parent
SKILL = Path.home() / '.codex/skills/blockbench-model-bridge'
NS = uuid.UUID('be265c49-05d7-43d2-a6bd-802a6d0bd158')
FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def uid(name):
    return str(uuid.uuid5(NS, name))


def dump(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


# Reuse vanilla block textures and authored materials without editing their pixels.
MATERIALS = {
    'stone': ('minecraft', 'polished_blackstone'),
    'stone_shadow': ('soul-shaman-cuboid', 'cavity'),
    'stone_edge': ('minecraft', 'polished_blackstone'),
    'stone_mid': ('minecraft', 'polished_blackstone'),
    'gold': ('minecraft', 'gold_block'),
    'gold_shadow': ('minecraft', 'gold_block'),
    'parchment': ('soul-shaman-cuboid', 'bone_shade'),
    'parchment_end': ('soul-shaman-cuboid', 'wood_light'),
    'thread': ('soul-shaman-cuboid', 'cord'),
    'rune': ('iron-soul-cage', 'soul_cyan'),
    'rune_light': ('iron-soul-cage', 'soul_pale'),
}
textures = []
(HERE / 'textures').mkdir(parents=True, exist_ok=True)
for name, (folder, source) in MATERIALS.items():
    relative = f'textures/{name}.png'
    size = 16 if folder == 'minecraft' else 8
    if folder == 'minecraft':
        jar = MODELING.parent / '.gradle-user-home/caches/forge_gradle/minecraft_repo/versions/1.16.5/client.jar'
        if jar.exists():
            with ZipFile(jar) as archive:
                (HERE / relative).write_bytes(archive.read(f'assets/minecraft/textures/block/{source}.png'))
        elif not (HERE / relative).exists():
            raise FileNotFoundError(f'Missing material {relative}; supply the vanilla 1.16.5 client JAR or retain the exported texture.')
    else:
        shutil.copyfile(MODELING / folder / 'textures' / f'{source}.png', HERE / relative)
    textures.append({'name': name, 'path': relative, 'width': size, 'height': size,
                     'uuid': uid('texture/' + name)})

cubes, members = [], {}


def cube(name, lo, hi, material, group='pedestal', top=None):
    index = list(MATERIALS).index(material)
    faces = {side: {'uv': [0, 0, 8, 8], 'texture': index} for side in FACES}
    if top:
        faces['up']['texture'] = list(MATERIALS).index(top)
    # Stone texels keep block-scale proportions even on thin ledges and piers.
    dx, dy, dz = (b-a for a, b in zip(lo, hi))
    for side, face in faces.items():
        tex_name = list(MATERIALS)[face['texture']]
        if tex_name in ('stone', 'stone_mid', 'stone_edge'):
            width, height = (dx, dz) if side in ('up', 'down') else ((dz, dy) if side in ('east', 'west') else (dx, dy))
            face['uv'] = [0, 0, width, height]
        elif tex_name.startswith('gold'):
            face['uv'] = [5, 5, 11, 11]
    cubes.append({'name': name, 'uuid': uid('cube/' + name), 'from': lo, 'to': hi,
                  'faces': faces})
    members.setdefault(group, []).append(name)


# Block-centred footprint; the front is north / negative Z.
cube('foundation', [-7, 0, -7], [7, 1.5, 7], 'stone', top='stone_mid')
cube('base_step', [-6, 1.5, -6], [6, 2.5, 6], 'stone_mid', top='stone_edge')
cube('plinth', [-5, 2.5, -5], [5, 4, 5], 'stone', top='stone_edge')

# A real shallow recess, not a black rectangle covering a full solid column.
cube('column_back', [-4.5, 4, -2.25], [4.5, 13.5, 4.5], 'stone')
cube('left_front_pier', [-4.5, 4, -4.5], [-3, 13.5, -2.25], 'stone_mid', top='stone_edge')
cube('right_front_pier', [3, 4, -4.5], [4.5, 13.5, -2.25], 'stone_mid', top='stone_edge')
cube('recess_floor', [-3, 4, -4.5], [3, 5.25, -2.25], 'stone', top='stone_edge')
cube('recess_lintel', [-3, 11.25, -4.5], [3, 13.5, -2.25], 'stone_mid')
cube('socket_back', [-3, 5.25, -2.31], [3, 11.25, -2.25], 'stone_shadow')
cube('scroll_shelf', [-3, 5.25, -4.75], [3, 5.65, -2.25], 'stone_edge')

cube('capital_neck', [-5, 13.5, -5], [5, 14, 5], 'stone_mid')
cube('capital_shadow', [-6, 14, -6], [6, 14.5, 6], 'stone_shadow')
cube('capital', [-6.5, 14.5, -6.5], [6.5, 15.5, 6.5], 'stone_mid', top='stone_edge')
cube('top_inset', [-4.5, 15.5, -4.5], [4.5, 15.75, 4.5], 'stone')

# Raised outer rim leaves a recessed, visible socket below the levitating cage.
for name, lo, hi in (
    ('north', [-5.5, 15.5, -5.5], [5.5, 16, -4.5]),
    ('south', [-5.5, 15.5, 4.5], [5.5, 16, 5.5]),
    ('west', [-5.5, 15.5, -4.5], [-4.5, 16, 4.5]),
    ('east', [4.5, 15.5, -4.5], [5.5, 16, 4.5]),
):
    cube('socket_rim_' + name, lo, hi, 'stone_mid', top='stone_edge')

# Sparse square corner fittings and deliberately quiet stone relief.
for x in (-1, 1):
    for z in (-1, 1):
        lo_x, hi_x = sorted((x * 7.04, x * 5.75))
        lo_z, hi_z = sorted((z * 7.04, z * 5.75))
        cube(f'base_corner_{x}_{z}', [lo_x, .25, lo_z], [hi_x, 1.55, hi_z], 'gold_shadow', top='gold')
        lo_x, hi_x = sorted((x * 6.54, x * 5.25))
        lo_z, hi_z = sorted((z * 6.54, z * 5.25))
        cube(f'cap_corner_{x}_{z}', [lo_x, 14.6, lo_z], [hi_x, 15.6, hi_z], 'gold_shadow', top='gold')
for z in (-1, 1):
    for x, y, width in ((-2.9, 2.7, 1.5), (1.6, 3.4, 1.2), (-2, 12.5, 1.2)):
        face_z = z * (5.01 if y < 4 else 4.51)
        cube(f'stone_relief_{z}_{x}_{y}', [x, y, face_z-.035], [x+width, y+.25, face_z+.035], 'stone_mid')


# Scroll is a removable, separate group in the study, with a black tie and seal.
cube('scroll_roll', [-2.75, 7, -4.18], [2.75, 8.9, -2.65], 'parchment', 'inserted_scroll')
for side in (-1, 1):
    x0, x1 = sorted((side*2.75, side*2.35))
    cube(f'rolled_end_{side}', [x0, 7.2, -4.32], [x1, 8.7, -2.51], 'parchment_end', 'inserted_scroll')
    cube(f'roll_opening_{side}', [x0-.025, 7.65, -3.83], [x1+.025, 8.25, -3.01], 'parchment_end', 'inserted_scroll')
cube('black_scroll_tie', [-.3, 6.95, -4.24], [.3, 8.95, -2.6], 'thread', 'inserted_scroll')
cube('cyan_wax_seal', [-.5, 7.5, -4.43], [.5, 8.5, -4.24], 'rune', 'inserted_scroll')

groups = [{'name': name, 'uuid': uid('group/' + name), 'origin': [0, 0, 0], 'children': names}
          for name, names in members.items()]
plan = {'name': 'Soul Pylon - Pedestal', 'model_identifier': 'soulcatcherplus:soul_pylon',
        'model_format': 'free', 'target_size': {'minecraft_blocks': [1, 1, 1],
        'blockbench_units': [16, 16, 16],
        'fit_policy': 'Pedestal within X/Z [-7.04,7.04], Y [0,16]; inserted scroll is optional. Cage is external.'},
        'texture_width': 8, 'texture_height': 8, 'textures': textures, 'cubes': cubes, 'groups': groups}
dump(HERE / 'plan.json', plan)
spec = importlib.util.spec_from_file_location('bridge', SKILL / 'scripts/plan_to_bbmodel.py')
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)
model, warnings = bridge.convert(plan)
for texture in model['textures']:
    texture['source'] = 'data:image/png;base64,' + base64.b64encode((HERE / texture['relative_path']).read_bytes()).decode()
dump(HERE / 'soul_pylon.bbmodel', model)

# Preview: import the actual authored cage, never a newly approximated lantern.
scene = copy.deepcopy(model)
scene['name'] = 'Soul Pylon - Existing Iron Cage Preview'
cage = json.loads((MODELING / 'iron-soul-cage/iron_soul_cage.bbmodel').read_text(encoding='utf-8'))
scale, lift = .55, 17.1


def transform(point):
    return [round(point[0]*scale, 5), round(point[1]*scale+lift, 5), round(point[2]*scale, 5)]


for element in cage['elements']:
    for field in ('from', 'to', 'origin'):
        element[field] = transform(element[field])
for group in cage['groups']:
    group['origin'] = transform(group['origin'])
preview_root = uid('group/installed_cage_preview')
scene['groups'].append({'name': 'installed_cage_preview', 'uuid': preview_root, 'origin': [0, lift, 0]})
scene['groups'].extend(cage['groups'])
scene['elements'].extend(cage['elements'])
scene['outliner'].append({'uuid': preview_root, 'isOpen': False, 'children': cage['outliner']})
# Embedded textures make the preview portable; prevent dangling relative paths.
for texture in cage['textures']:
    texture['path'] = str((MODELING / 'iron-soul-cage' / texture['relative_path']).resolve())
    texture.pop('relative_path', None)
scene['textures'].extend(cage['textures'])


def keys(channel, values):
    return [{'channel': channel, 'data_points': [dict(zip('xyz', map(str, vector)))],
             'uuid': uid(f'preview/{channel}/{time}'), 'time': time, 'interpolation': 'linear'}
            for time, vector in values]


animators = {preview_root: {'name': 'installed_cage_preview', 'type': 'bone', 'keyframes':
    keys('rotation', [(0, [0,0,0]), (6, [0,180,0]), (12, [0,360,0])]) +
    keys('position', [(t, [0, .17 if t % 4 == 2 else 0, 0]) for t in range(0,13,2)])}}
for bone_id, source in cage['animations'][0]['animators'].items():
    target = copy.deepcopy(source)
    target['keyframes'] = []
    for repetition in range(3):
        for frame in source['keyframes']:
            if repetition and frame['time'] == 0:
                continue
            frame = copy.deepcopy(frame)
            frame['time'] += repetition * 4
            frame['uuid'] = uid(f'preview/{bone_id}/{frame["channel"]}/{frame["time"]}')
            if frame['channel'] == 'position':
                for point in frame['data_points']:
                    for axis in 'xyz':
                        point[axis] = str(float(point[axis]) * scale)
            target['keyframes'].append(frame)
    animators[bone_id] = target
scene['animations'] = [{'name': 'pylon_active_preview', 'uuid': uid('animation/active'),
    'loop': 'loop', 'length': 12, 'snapping': 20, 'override': False, 'animators': animators}]
dump(HERE / 'soul_pylon_with_cage.bbmodel', scene)
print(f'Created pedestal: {len(cubes)} cuboids; scene: {len(scene["elements"])} cuboids. Bounds: 14.08 x 16 x 14.08; cage preview top < 26.')
for warning in warnings:
    print('Warning:', warning)
