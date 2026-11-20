"""Build the editable one-block altar and a separate loaded concept preview."""
import base64
import copy
import importlib.util
import json
from pathlib import Path
import shutil
import uuid

HERE = Path(__file__).resolve().parent
MODELING = HERE.parent
SKILL = Path.home() / '.codex/skills/blockbench-model-bridge'
NAMESPACE = uuid.UUID('6e7d6333-a61a-45a9-b279-ad24f45603e1')
FACES = ('north', 'south', 'west', 'east', 'up', 'down')


def uid(name):
    return str(uuid.uuid5(NAMESPACE, name))


def write(name, value):
    (HERE / name).write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def generate():
    materials = {
        'blackstone': ('soul-projector/dark/textures/body.png', 16),
        'gold': ('soul-pylon/textures/gold.png', 16),
        'recess': ('soul-shaman-cuboid/textures/cavity.png', 8),
        'conduit': ('soul-shaman-cuboid/textures/cyan_trim.png', 8),
        'parchment': ('soul-shaman-cuboid/textures/bone_light.png', 8),
        'cyan': ('iron-soul-cage/textures/soul_cyan.png', 8),
        'spark': ('iron-soul-cage/textures/soul_pale.png', 8),
    }
    textures = []
    (HERE / 'textures').mkdir(exist_ok=True)
    for material, (source, size) in materials.items():
        path = f'textures/{material}.png'
        shutil.copyfile(MODELING / source, HERE / path)
        textures.append({'name': material, 'path': path, 'width': size, 'height': size,
                         'uuid': uid('texture/' + material)})

    cubes = []
    members = {}

    def cube(name, lo, hi, material='blackstone', group='table'):
        dx, dy, dz = (end - start for start, end in zip(lo, hi))
        faces = {}
        for face in FACES:
            if material == 'blackstone':
                width, height = (dx, dz) if face in ('up', 'down') else (
                    (dz, dy) if face in ('west', 'east') else (dx, dy))
                uv = [0, 0, width, height]
            elif material == 'gold':
                uv = [3, 3, 13, 13] if face in ('up', 'down') else [2, 8, 14, 14]
            else:
                uv = [0, 0, 8, 8]
            faces[face] = {'texture': list(materials).index(material), 'uv': uv}
        cubes.append({'name': name, 'uuid': uid('cube/' + name), 'from': lo, 'to': hi, 'faces': faces})
        members.setdefault(group, []).append(name)

    def rim(name, x0, z0, x1, z1, bottom, top, width, group):
        cube(name + '_north', [x0, bottom, z0], [x1, top, z0 + width], group=group)
        cube(name + '_south', [x0, bottom, z1 - width], [x1, top, z1], group=group)
        cube(name + '_west', [x0, bottom, z0 + width], [x0 + width, top, z1 - width], group=group)
        cube(name + '_east', [x1 - width, bottom, z0 + width], [x1, top, z1 - width], group=group)

    # Leave the front pixels just proud of the stone without exceeding the block.
    cube('tabletop', [-8, 9, -7.97], [8, 12, 8])
    # Front is north. The gold plinths use two stepped shapes like cast ingots.
    for x in (-1, 1):
        for z in (-1, 1):
            label = ('left' if x < 0 else 'right') + ('_front' if z < 0 else '_back')
            cx, cz = x * 5.5, z * 5.5
            cube(label + '_leg', [cx-1.25, 2.5, cz-1.15], [cx+1.25, 9, cz+1.15], group='legs')
            cube(label + '_ingot_base', [cx-2, 0, cz-1.65], [cx+2, 1.45, cz+1.65], 'gold', 'legs')
            cube(label + '_ingot_shoulder', [cx-1.65, 1.45, cz-1.3], [cx+1.65, 2.5, cz+1.3], 'gold', 'legs')
    # The two upper pixels and lowered middle pixel echo the concept's front mark.
    for label, x, y in (('left', -1.5, 10.55), ('right', 0.5, 10.55), ('center', -0.5, 9.55)):
        cube('front_pixel_' + label, [x, y, -8], [x+1, y+1, -7.94], 'conduit')

    cube('socket_plinth', [-7.5, 12, 0.5], [-0.5, 13, 7.5], group='cage_socket')
    rim('socket', -7, 1, -1, 7, 13, 16, 0.75, 'cage_socket')
    cube('socket_floor', [-6.25, 13, 1.75], [-1.75, 13.25, 6.25], 'recess', 'cage_socket')
    tray_center = 1.5
    rim('tray', tray_center - 4, -7.5, tray_center + 4, -1, 12, 12.6, 0.55, 'writing_tray')
    cube('tray_floor', [tray_center - 3.45, 12, -6.95], [tray_center + 3.45, 12.1, -1.55], 'recess', 'writing_tray')
    # Both branches start at the cage plinth and terminate against the writing tray.
    for name, x0, x1, z0, z1 in (
        ('west_from_socket', -4.35, -3.65, -4.3, 0.5),
        ('west_to_tray', -3.65, tray_center - 4, -4.3, -3.6),
        ('east_from_socket', -0.5, tray_center + 0.35, 1.9, 2.6),
        ('east_to_tray', tray_center - 0.35, tray_center + 0.35, -1, 1.9),
    ):
        cube('conduit_' + name, [x0, 12, z0], [x1, 12.045, z1], 'conduit', 'conduit')

    def plan(name):
        return {'name': name, 'model_format': 'free', 'model_identifier': 'soulcatcherplus:soul_transfer_altar',
                'target_size': {'blockbench_units': [16, 16, 16], 'minecraft_blocks': [1, 1, 1],
                                'fit_policy': 'Table and socket fit one block. The inserted cage can extend above it.'},
                'texture_width': 16, 'texture_height': 16, 'textures': textures, 'cubes': cubes,
                'groups': [{'name': name, 'origin': [0, 0, 0], 'uuid': uid('group/' + name), 'children': children}
                           for name, children in members.items()]}

    spec = importlib.util.spec_from_file_location('bridge', SKILL / 'scripts/plan_to_bbmodel.py')
    bridge = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(bridge)

    def convert(source):
        model, warnings = bridge.convert(source)
        if warnings:
            raise ValueError(warnings)
        for texture in model['textures']:
            png = (HERE / texture['relative_path']).read_bytes()
            texture['source'] = 'data:image/png;base64,' + base64.b64encode(png).decode()
        return model

    empty = plan('Soul Transfer Altar')
    write('plan.json', empty)
    write('soul_transfer_altar.bbmodel', convert(empty))

    cube('paper', [tray_center - 2.85, 12.1, -6.4], [tray_center + 2.85, 12.23, -2.1], 'parchment', 'transfer_preview')
    cube('soul_seal', [tray_center - .375, 12.23, -4.7], [tray_center + .375, 12.38, -3.95], 'cyan', 'transfer_preview')
    for i, (x, y, z) in enumerate(((-4.2, 12.8, 0), (-4.2, 12.4, -1.8), (-4.2, 12.2, -3.95),
                                    (-3.35, 12.18, -4.12), (-2.85, 12.25, -4.12))):
        cube(f'spark_{i}', [x, y, z], [x+0.36, y+0.36, z+0.36], 'spark', 'transfer_preview')
    preview = convert(plan('Soul Transfer Altar - Loaded'))
    cage = json.loads((MODELING / 'iron-soul-cage/iron_soul_cage.bbmodel').read_text(encoding='utf-8'))

    def socket_position(point):
        return [round(value * 0.60 + offset, 5) for value, offset in zip(point, (-4, 13.01, 4))]

    for element in cage['elements']:
        for field in ('from', 'to', 'origin'):
            element[field] = socket_position(element[field])
    for group in cage['groups']:
        group['origin'] = socket_position(group['origin'])
    cage_group = uid('group/inserted_iron_cage')
    preview['groups'].append({'name': 'inserted_iron_cage', 'uuid': cage_group, 'origin': [-4, 13.01, 4]})
    preview['groups'].extend(cage['groups'])
    preview['elements'].extend(cage['elements'])
    preview['outliner'].append({'uuid': cage_group, 'isOpen': False, 'children': copy.deepcopy(cage['outliner'])})
    for texture in cage['textures']:
        texture.pop('relative_path', None)
        texture['path'] = ''
    preview['textures'].extend(cage['textures'])
    write('soul_transfer_altar_loaded.bbmodel', preview)


if __name__ == '__main__':
    generate()
