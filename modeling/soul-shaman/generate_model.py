# SPDX-License-Identifier: MIT
"""Build the approved Soul Shaman concept from plain cuboids; model assets only."""
from pathlib import Path
import base64
import importlib.util
import itertools
import json
import math
import shutil
import struct
import uuid
import zlib

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SKILL = Path.home() / '.codex/skills/blockbench-model-bridge'
NAMESPACE = uuid.UUID('25813c9b-6575-4980-b4ea-03e6166031eb')
MATERIALS = [('cloth', None, False), ('bone', 'D1CAB0', False),
             ('wood', '755238', False), ('cord', '322B26', False),
             ('mask_cut', '263133', False), ('soul_blue', '2DBCD7', True),
             ('soul_light', '8DE3EB', True)]
FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def uid(name):
    return str(uuid.uuid5(NAMESPACE, name))


def dump(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def swatch(color):
    # Solid material swatches, not painted surface art. Cloth is reused unchanged.
    rgb = bytes.fromhex(color)
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 8, 8, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress((b'\0' + rgb * 8) * 8)) + chunk(b'IEND', b''))


(HERE / 'textures').mkdir(parents=True, exist_ok=True)
textures = []
for name, color, emissive in MATERIALS:
    path = HERE / 'textures' / (name + '.png')
    if name == 'cloth':
        shutil.copyfile(ROOT / 'modeling/soul-courier/textures/cloak_runic.png', path)
        size = 16
    else:
        path.write_bytes(swatch(color))
        size = 8
    textures.append({'name': name, 'path': 'textures/' + path.name,
                     'width': size, 'height': size, 'uuid': uid('material/' + name),
                     'render_mode': 'emissive' if emissive else 'default'})

cubes, members = [], {}


def cloth_uv(name, face, size):
    plain = [10.25, 10.25, 11.25, 11.25]
    width, height, depth = size
    # Crops use one atlas texel per model unit rather than stretching square art.
    if name.startswith('hood'):
        if face == 'up' and name == 'hood_cap':
            return [8, 0, 8 + width, depth]
        if face in ('east', 'west') and name in ('hood_left', 'hood_right'):
            return [8, 0, 8 + depth, height]
        if face == 'south' and name == 'hood_back':
            return [8, 0, 8 + width, height]
        if face == 'north' and name == 'hood_cap':
            return [8, 14.5, 8 + width, 14.5 + height]
    if name.startswith('collar_') and face == 'north':
        return [8, 14.5, 8 + width, 14.5 + height]
    if name == 'robe_lower' and face in ('north', 'south'):
        return [0, 8, width, 8 + height]
    if name.startswith('hem_') and face == 'north':
        return [1, 16 - height, 1 + width, 16]
    if name.startswith('sleeve_') and face == 'north':
        return [0, 0, width, height]
    return plain


def cube(name, lo, hi, bone, material='cloth', rotation=None, origin=None):
    index = next(i for i, entry in enumerate(MATERIALS) if entry[0] == material)
    size = [b - a for a, b in zip(lo, hi)]
    faces = {face: {'texture': index, 'uv': cloth_uv(name, face, size) if material == 'cloth' else [0, 0, 8, 8]}
             for face in FACES}
    cubes.append({'name': name, 'uuid': uid('cube/' + name), 'from': lo, 'to': hi,
                  'origin': origin or [(a + b) / 2 for a, b in zip(lo, hi)],
                  'rotation': rotation or [0, 0, 0], 'faces': faces})
    members.setdefault(bone, []).append(name)


# Slim robe with three distinct hanging front panels and a quiet rear panel.
cube('torso', [-2.5, 8.5, -1.3], [2.5, 13.5, 1.3], 'body')
cube('robe_lower', [-2.6, 5.8, -1.4], [2.6, 8.5, 1.4], 'body')
cube('hem_left', [-3.0, 2.8, -1.55], [-1.0, 6.8, -1.15], 'body',
     rotation=[0, 0, -8], origin=[-1.8, 6.8, -1.35])
cube('hem_center', [-0.85, 2.3, -1.6], [0.85, 6.8, -1.2], 'body')
cube('hem_right', [1.0, 2.8, -1.55], [3.0, 6.8, -1.15], 'body',
     rotation=[0, 0, 8], origin=[1.8, 6.8, -1.35])
cube('hem_back', [-2.7, 3.3, 1.1], [2.7, 6.8, 1.5], 'body')
# Two overlapping shoulder/collar layers, open at the amulet rather than a solid slab.
for layer, y, outer, z, tilt in [('upper', 12.7, 4.2, 1.8, 18),
                                  ('lower', 11.95, 4.45, 1.65, 22)]:
    for side, sign in [('left', -1), ('right', 1)]:
        lo_x, hi_x = (-outer, -0.9) if sign < 0 else (0.9, outer)
        cube('collar_' + layer + '_' + side, [lo_x, y - 0.5, -z], [hi_x, y + 0.25, z],
             'body', rotation=[0, 0, -sign * tilt], origin=[sign * 0.9, y + 0.25, 0])
    cube('collar_' + layer + '_back', [-3.1, y - 0.5, 1.3], [3.1, y + 0.25, z], 'body')
cube('belt', [-2.65, 8.5, -1.45], [2.65, 9.1, 1.45], 'body', 'wood')
cube('sleeve_left', [-4.7, 7.3, -1.2], [-2.5, 12.9, 1.2], 'arm_left',
     rotation=[0, 0, -14], origin=[-2.7, 12.8, 0])
cube('sleeve_right', [2.5, 7.3, -1.2], [5.6, 12.9, 1.2], 'arm_right',
     rotation=[0, 0, 14], origin=[2.7, 12.8, 0])
cube('hand_left', [-6.05, 6.75, -0.9], [-4.65, 7.85, 0.5], 'arm_left', 'bone')
cube('staff_grip', [5.35, 8.4, -2], [6.85, 9.5, -0.8], 'arm_right', 'bone')

# Four plain cuboids form an open hood. The mask narrows in two broad steps.
cube('hood_cap', [-3.5, 19.7, -3], [3.5, 20.1, 0.6], 'head')
cube('hood_back', [-3.1, 12.5, 0.2], [3.1, 19.7, 0.6], 'head')
cube('hood_left', [-3.5, 12.5, -3], [-3.1, 19.7, 0.6], 'head')
cube('hood_right', [3.1, 12.5, -3], [3.5, 19.7, 0.6], 'head')
cube('mask_brow', [-2.8, 17.0, -3.05], [2.8, 19.7, -2.7], 'head', 'bone')
cube('mask_cheeks', [-2.6, 14.4, -3.05], [2.6, 17.0, -2.7], 'head', 'bone')
cube('mask_chin', [-1.85, 13.1, -3.05], [1.85, 14.4, -2.7], 'head', 'bone')
for side, lo_x, hi_x in [('left', -2.25, -0.7), ('right', 0.7, 2.25)]:
    cube('eye_socket_' + side, [lo_x, 15.55, -3.10], [hi_x, 16.85, -3.05], 'head', 'mask_cut')
    cube('eye_' + side, [lo_x + 0.12, 15.65, -3.14], [hi_x - 0.12, 16.30, -3.10], 'head', 'soul_light')
cube('mask_forehead_mark_top', [0.45, 18.1, -3.09], [1.05, 19.7, -3.05], 'head', 'mask_cut')
cube('mask_forehead_mark_step', [-0.15, 17.5, -3.09], [0.65, 18.1, -3.05], 'head', 'mask_cut')
cube('mask_lower_mark_top', [0.05, 14.3, -3.09], [0.65, 15.0, -3.05], 'head', 'mask_cut')
cube('mask_lower_mark_bottom', [-0.5, 13.1, -3.09], [0.1, 14.5, -3.05], 'head', 'mask_cut')
# Visible V-shaped leather suspension, an ivory bezel and a raised luminous stone.
for side, sign in [('left', -1), ('right', 1)]:
    cube('amulet_strap_' + side, [sign * 1.0 - 0.2, 11.45, -1.97],
         [sign * 1.0 + 0.2, 13.3, -1.72], 'body', 'wood',
         rotation=[0, 0, sign * -32], origin=[sign * 1.0, 12.375, -1.845])
cube('amulet_loop', [-0.25, 11.5, -2.03], [0.25, 12.0, -1.7], 'body', 'bone')
cube('amulet_bezel', [-0.8, 9.9, -2.1], [0.8, 11.65, -1.65], 'body', 'bone')
cube('amulet_stone', [-0.56, 10.15, -2.32], [0.56, 11.4, -2.1], 'body', 'soul_blue')
cube('amulet_glint', [-0.18, 10.5, -2.36], [0.3, 11.05, -2.32], 'body', 'soul_light')
# Broad fire crown, tapering zig-zag tail and a raised bright core; six boxes.
cube('spirit_wisp', [-1.1, 1.75, -0.65], [1.05, 3.3, 0.65], 'wisp', 'soul_blue')
cube('spirit_wisp_left_tongue', [-1.1, 3.3, -0.5], [-0.45, 4.0, 0.5], 'wisp', 'soul_blue')
cube('spirit_wisp_bend', [-0.75, 1.05, -0.45], [0.4, 1.75, 0.45], 'wisp', 'soul_blue')
cube('spirit_wisp_tip', [0.05, 0.35, -0.3], [0.7, 1.05, 0.3], 'wisp', 'soul_blue')
cube('spirit_wisp_core', [-0.45, 2.0, -0.72], [0.35, 3.0, 0.72], 'wisp', 'soul_light')
cube('spirit_wisp_core_tail', [-0.3, 1.35, -0.5], [0.15, 2.0, 0.5], 'wisp', 'soul_light')

# The staff including its flame is exactly 24 units tall: Y 0.5 through 24.5.
cube('staff_shaft', [5.8, 0.5, -1.8], [6.6, 16.4, -1], 'staff', 'wood')
cube('staff_heel', [5.65, 0.5, -1.95], [6.75, 1.3, -0.85], 'staff', 'bone')
for index, y in enumerate([11.5, 12.25, 13.0]):
    cube('staff_wrap_' + str(index), [5.68, y, -1.92], [6.72, y + 0.35, -0.88],
         'staff', 'cord', rotation=[0, 0, -8])


def staff_segment(name, start, end):
    cx, cy = [(a + b) / 2 for a, b in zip(start, end)]
    length = math.dist(start, end)
    angle = -math.degrees(math.atan2(end[0] - start[0], end[1] - start[1]))
    cube(name, [cx - 0.4, cy - length / 2, -1.8], [cx + 0.4, cy + length / 2, -1],
         'staff', 'wood', rotation=[0, 0, angle], origin=[cx, cy, -1.4])


staff_segment('staff_crook_lower', [6.2, 16], [8, 17.6])
staff_segment('staff_crook_upper', [8, 17.6], [6.5, 19.3])
cube('bone_fork_base', [4.4, 18.9, -1.9], [8.6, 19.7, -0.9], 'staff', 'bone')
cube('bone_fork_left', [4.4, 19.7, -1.9], [5.2, 22.5, -0.9], 'staff', 'bone')
cube('bone_fork_right', [7.8, 19.7, -1.9], [8.6, 22.5, -0.9], 'staff', 'bone')
cube('fork_binding', [5.65, 18.5, -2.03], [7.35, 19.15, -0.77], 'staff', 'cord')
cube('bone_fork_left_joint', [4.25, 21.65, -2.05], [5.35, 22.5, -0.75], 'staff', 'bone')
cube('bone_fork_right_joint', [7.65, 21.65, -2.05], [8.75, 22.5, -0.75], 'staff', 'bone')
cube('staff_flame', [5.55, 20.0, -1.95], [7.3, 22.1, -0.85], 'staff_flame', 'soul_blue')
cube('staff_flame_tongue_left', [5.55, 22.1, -1.8], [6.15, 22.85, -1], 'staff_flame', 'soul_blue')
cube('staff_flame_bend', [6.45, 22.1, -1.8], [7.3, 23.4, -1], 'staff_flame', 'soul_blue')
cube('staff_flame_tip', [5.95, 23.4, -1.7], [6.75, 24.5, -1.1], 'staff_flame', 'soul_blue')
cube('staff_flame_core', [6.1, 20.35, -2.02], [6.8, 21.8, -0.78], 'staff_flame', 'soul_light')
cube('charm_cord', [8, 16.1, -1.48], [8.16, 17.7, -1.32], 'charm', 'cord')
cube('bone_charm', [7.7, 14.7, -1.6], [8.5, 16.1, -1], 'charm', 'bone')
cube('bone_charm_top', [7.5, 15.75, -1.75], [8.7, 16.25, -0.85], 'charm', 'bone')
cube('bone_charm_bottom', [7.5, 14.55, -1.75], [8.7, 15.05, -0.85], 'charm', 'bone')

pivots = {'root': [0, 0, 0], 'body': [0, 10, 0], 'head': [0, 16, -1.2],
          'arm_left': [-2.7, 12.8, 0], 'arm_right': [2.7, 12.8, 0],
          'staff': [6.2, 8.95, -1.4], 'staff_flame': [6.5, 20.2, -1.4],
          'charm': [8.08, 17.7, -1.4], 'wisp': [0, 3.2, 0]}
hierarchy = {'root': ['body'], 'body': ['head', 'arm_left', 'arm_right', 'wisp'],
             'arm_right': ['staff'], 'staff': ['staff_flame', 'charm']}


def animator(bone, tracks):
    frames = []
    for channel, keys in tracks.items():
        for time, vector in keys:
            frames.append({'channel': channel, 'data_points': [dict(zip('xyz', map(str, vector)))],
                           'uuid': uid(f'idle/{bone}/{channel}/{time}'), 'time': time, 'interpolation': 'linear'})
    return {'name': bone, 'type': 'bone', 'keyframes': frames}


tracks = {'body': {'position': [(0, [0, 0, 0]), (1.5, [0, 0.25, 0]), (3, [0, 0, 0])]},
          'head': {'rotation': [(0, [0, -3, 0]), (1.5, [0, 3, 0]), (3, [0, -3, 0])]},
          'charm': {'rotation': [(0, [0, 0, -5]), (1.5, [0, 0, 5]), (3, [0, 0, -5])]}}
animations = [{'name': 'idle_float', 'uuid': uid('animation/idle_float'), 'length': 3, 'loop': 'loop',
               'snapping': 20, 'override': False,
               'animators': {uid('bone/' + bone): animator(bone, channels) for bone, channels in tracks.items()}}]
groups = [{'name': name, 'uuid': uid('bone/' + name), 'origin': pivot, 'children': members.get(name, [])}
          for name, pivot in pivots.items()]
plan = {'name': 'Soul Shaman', 'model_identifier': 'soulcatcherplus:soul_shaman',
        'model_format': 'free', 'format_version': '5.0', 'texture_width': 16, 'texture_height': 16,
        'target_size': {'minecraft_blocks': [1, 1.5625, 0.5], 'blockbench_units': [16, 25, 8],
                        'staff_height_units': 24, 'fit_policy': '24-unit staff including flame; 25-unit height envelope allows idle hover. Width and depth inferred from approved art.'},
        'textures': textures, 'cubes': cubes, 'groups': groups, 'hierarchy': hierarchy, 'animations': animations}
dump(HERE / 'plan.json', plan)

spec = importlib.util.spec_from_file_location('model_bridge', SKILL / 'scripts/plan_to_bbmodel.py')
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)
model, warnings = bridge.convert(plan)
by_group = {group['name']: group for group in model['groups']}
by_cube = {cube['name']: cube['uuid'] for cube in model['elements']}


def node(name):
    return {'uuid': by_group[name]['uuid'], 'isOpen': name == 'root',
            'children': [by_cube[part] for part in members.get(name, [])]
            + [node(child) for child in hierarchy.get(name, [])]}


model['outliner'] = [node('root')]
for group in model['groups']:
    group.update({'rotation': [0, 0, 0], 'export': True, 'visibility': True})
for texture in model['textures']:
    texture['source'] = 'data:image/png;base64,' + base64.b64encode((HERE / texture['relative_path']).read_bytes()).decode()
dump(HERE / 'soul_shaman.bbmodel', model)


def rotated(point, cube):
    point = [a - b for a, b in zip(point, cube['origin'])]
    for axis, degrees in enumerate(cube.get('rotation', [0, 0, 0])):
        a, b = ((1, 2), (2, 0), (0, 1))[axis]
        cosine, sine = math.cos(math.radians(degrees)), math.sin(math.radians(degrees))
        point[a], point[b] = cosine * point[a] - sine * point[b], sine * point[a] + cosine * point[b]
    return [a + b for a, b in zip(point, cube['origin'])]


vertices = [rotated(point, cube) for cube in cubes for point in itertools.product(*zip(cube['from'], cube['to']))]
low = [min(point[axis] for point in vertices) for axis in range(3)]
high = [max(point[axis] for point in vertices) for axis in range(3)]
assert high[0] - low[0] <= 16 and high[1] <= 25 and high[2] - low[2] <= 8
assert high[1] - 0.5 == 24
dump(HERE / 'bounds-report.json', {'cuboids': len(cubes), 'quads': len(cubes) * 6,
     'triangles': len(cubes) * 12, 'static_min': low, 'static_max': high,
     'staff_height_units': 24, 'staff_height_blocks': 1.5,
     'idle_body_lift_units': 0.25, 'emissive_materials': ['soul_blue', 'soul_light']})
print(f'Created {len(cubes)} cuboids / {len(cubes)*6} quads, {len(groups)} bones; staff height 24 units (1.5 blocks).')
for warning in warnings:
    print('Warning:', warning)
