# SPDX-License-Identifier: MIT
"""Soul Courier: vanilla baby Skeleton + vanilla Ender Chest, with a separate cloak.

No runtime exports. Vanilla bitmaps are read unchanged from the local 1.16.5 game.
"""
from pathlib import Path
from zipfile import ZipFile
import base64
import hashlib
import importlib.util
import itertools
import json
import math
import struct
import uuid
import zlib

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SKILL = Path.home() / '.codex/skills/blockbench-model-bridge'
CLIENT = ROOT / '.gradle-user-home/caches/forge_gradle/minecraft_repo/versions/1.16.5/client.jar'
NAMESPACE = uuid.UUID('6bd7dc67-c977-459d-a752-25e5c4827315')
MATERIALS = [('skeleton', None), ('ender_chest', None), ('cloak', '253D43FF'),
             ('cloak_shadow', '192B33FF'), ('cloak_edge', '3A5860FF'),
             ('strap', '504839FF'), ('soul', '72D8D1FF')]
VANILLA = {'skeleton': 'assets/minecraft/textures/entity/skeleton/skeleton.png',
           'ender_chest': 'assets/minecraft/textures/entity/chest/ender.png'}
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
CLOTH_MATERIALS = {'cloak', 'cloak_shadow', 'cloak_edge'}
CLOTH_ATLAS = HERE / 'textures/cloak_runic.png'
# UV layout below uses the original 64-unit authoring grid; each new atlas texel spans four units.
CLOTH_UV_SCALE = 16 / 64
# Vanilla baby model is 18 units tall; retain all its proportions and fit the hood too.
FIT = 16 / 18.4
# Uniform final reduction: the authored 16-unit chest becomes vanilla's 14 units.
MODEL_SCALE = 14 / 16
# Keep the skeleton small; the chest's visible 14-unit box now spans one block.
CHEST_WORLD_SCALE = 16 / 14
CHEST_SCALE = CHEST_WORLD_SCALE / FIT
LEAN = -20
ARM_ANGLE = -25
HEAD_FORWARD = 1.1
# Straight arms set the height of the tilted floor in the torso's local space.
CHEST_BASE = (12*FIT - 6*FIT*math.cos(math.radians(ARM_ANGLE))
              + FIT/2*abs(math.sin(math.radians(ARM_ANGLE))) + .02) / FIT
CHEST_BACK = (2.05 - CHEST_WORLD_SCALE) / FIT


def uid(name):
    return str(uuid.uuid5(NAMESPACE, name))


def dump(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def swatch(color):
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 8, 8, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress((b'\0' + bytes.fromhex(color) * 8) * 8)) + chunk(b'IEND', b''))


cubes, members = [], {}


def cloth_uv(name, face, size):
    """Place the atlas's embroidery on broad surfaces; keep thin cut edges quiet."""
    plain = [42, 40, 50, 48]
    hood = [32, 0, 64, 32]
    horizontal_trim, vertical_trim = [32, 60, 64, 64], [60, 32, 64, 64]
    if name == 'hood_brow':
        return horizontal_trim if face == 'north' else plain
    if name.endswith('_hood_edge'):
        return vertical_trim if face == 'north' else plain
    if name == 'hood_top':
        return hood if face == 'up' else plain
    if name == 'hood_back':
        return hood if face == 'south' else plain
    if name.endswith('_hood'):
        return hood if face in ('east', 'west') else plain
    if name.endswith('_mantle'):
        return hood if face == 'up' else horizontal_trim if face == 'north' else plain
    if name.endswith('_loose_lapel'):
        panel = [0, 0, 12, 32] if name.startswith('left') else [20, 0, 32, 32]
        return panel if face == 'north' else plain
    if name.endswith('_cloak_side'):
        if face not in ('east', 'west'):
            return plain
        # Crop a vertical embroidered strip instead of squeezing a square panel.
        # East/west faces span depth by height; match that ratio in texture pixels.
        scale = min(32 / size[2], 32 / size[1])
        width, height = size[2]*scale, size[1]*scale
        start = 0 if name.startswith('left') else 32-width
        return [start, 0, start+width, height]
    if name.endswith('_cloak_back'):
        panel = [16, 32, 32, 64] if name.startswith('left') else [0, 32, 16, 64]
        return panel if face == 'south' else plain
    if name.endswith('_cloak_hem'):
        return horizontal_trim if face == 'south' else plain
    if name.endswith('_back_fold'):
        return vertical_trim if face == 'south' else plain
    return plain


def cube(name, lo, hi, bone, material, faces=None, rotation=None, origin=None):
    index = next(i for i, pair in enumerate(MATERIALS) if pair[0] == material)
    if faces is None:
        size = [b-a for a, b in zip(lo, hi)]
        faces = {face: {'texture': index, 'uv': cloth_uv(name, face, size) if material in CLOTH_MATERIALS else [0, 0, 8, 8]}
                 for face in FACES}
        if material in CLOTH_MATERIALS:
            for face in faces.values():
                face['uv'] = [value*CLOTH_UV_SCALE for value in face['uv']]
    cubes.append({'name': name, 'uuid': uid('cube/' + name), 'from': lo, 'to': hi,
                  'origin': origin or [(a + b) / 2 for a, b in zip(lo, hi)],
                  'rotation': rotation or [0, 0, 0], 'faces': faces})
    members.setdefault(bone, []).append(name)


def box_uv(offset, size, material, mirror=False, y_up=False):
    """Native ModelRenderer UV net, independent of the scaled geometric dimensions.

    The skeleton's model Y points down, while the chest renderer uses positive Y
    upward. Side-face V and the top/bottom nets therefore differ between them.
    """
    u, v = offset
    w, h, d = size
    rects = {'north': [u+d, v+d, u+d+w, v+d+h],
             'east': [u+d+w, v+d, u+2*d+w, v+d+h],
             'south': [u+2*d+w, v+d, u+2*d+2*w, v+d+h],
             'west': [u, v+d, u+d, v+d+h],
             'up': [u+d, v, u+d+w, v+d],
             'down': [u+d+w, v+d, u+d+2*w, v]}
    if y_up:
        rects['up'], rects['down'] = rects['down'], rects['up']
        for side in ('north', 'south', 'east', 'west'):
            rects[side][1], rects[side][3] = rects[side][3], rects[side][1]
    if mirror:
        rects['east'], rects['west'] = rects['west'], rects['east']
        for rect in rects.values():
            rect[0], rect[2] = rect[2], rect[0]
    index = next(i for i, pair in enumerate(MATERIALS) if pair[0] == material)
    return {face: {'texture': index, 'uv': rect} for face, rect in rects.items()}


def skeleton_box(name, origin, size, pivot, uv, head=False, mirror=False):
    # BipedModel young: body scale .5 with Y offset 24; head .75 with Y offset 16.
    scale, offset = (.75, 16) if head else (.5, 24)
    native_lo = [origin[i] + pivot[i] for i in range(3)]
    native_hi = [native_lo[i] + size[i] for i in range(3)]
    lo = [native_lo[0]*scale, 24-(native_hi[1]+offset)*scale, native_lo[2]*scale]
    hi = [native_hi[0]*scale, 24-(native_lo[1]+offset)*scale, native_hi[2]*scale]
    cube('skeleton_' + name, lo, hi, name, 'skeleton', box_uv(uv, size, 'skeleton', mirror))
    return [pivot[0]*scale, 24-(pivot[1]+offset)*scale, pivot[2]*scale]


pivots = {'root': [0, 0, 0]}
# Exact vanilla SkeletonModel/BipedModel boxes and UV offsets. No added 3D bones.
pivots['head'] = skeleton_box('head', [-4, -8, -4], [8, 8, 8], [0, 0, 0], [0, 0], head=True)
pivots['body'] = skeleton_box('body', [-4, 0, -2], [8, 12, 4], [0, 0, 0], [16, 16])
for side, sign in (('left', 1), ('right', -1)):
    pivots[side+'_arm'] = skeleton_box(side+'_arm', [-1, -2, -1], [2, 12, 2], [5*sign, 2, 0], [40, 16], mirror=sign==1)
    pivots[side+'_arm'] = [2.5*sign, 12, 0]
    pivots[side+'_leg'] = skeleton_box(side+'_leg', [-1, 0, -1], [2, 12, 2], [2*sign, 12, 0], [0, 16], mirror=sign==1)
# Bend from the hips rather than tipping the entire character and its feet.
pivots['body'] = [0, 6, 0]

# A loose cuboid cloak: open lapels, a wider shoulder mantle and flared hems.
cube('hood_top', [-3.4, 18, -3.05], [3.4, 18.4, 3.4], 'head', 'cloak_edge')
cube('hood_brow', [-3.4, 17.55, -3.7], [3.4, 18.4, -3.05], 'head', 'cloak_edge')
cube('hood_back', [-3.4, 11.8, 3.05], [3.4, 18, 3.4], 'head', 'cloak_shadow')
for side, x in (('left', 3.05), ('right', -3.4)):
    cube(side+'_hood', [x, 11.8, -3.05], [x+.35, 18, 3.05], 'head', 'cloak')
    edge_lo, edge_hi = (2.65, 3.4) if side == 'left' else (-3.4, -2.65)
    cube(side+'_hood_edge', [edge_lo, 11.8, -3.7], [edge_hi, 17.55, -3.05], 'head', 'cloak_edge')
for side, sign in (('left', 1), ('right', -1)):
    lo, hi = sorted((sign*.45, sign*3.55))
    cube(side+'_mantle', [lo, 11.35, -1.5], [hi, 12.3, 1.8], 'body', 'cloak')
    lo, hi = sorted((sign*1.75, sign*2.4))
    cube(side+'_cloak_side', [lo, 3.7, -1.3], [hi, 11.35, 1.9], side+'_cape', 'cloak',
         rotation=[0, 0, sign*8], origin=[sign*2, 11.35, .3])
    lo, hi = sorted((sign*.65, sign*1.8))
    cube(side+'_loose_lapel', [lo, 6.6, -1.75], [hi, 11.4, -1.3], side+'_cape', 'cloak',
         rotation=[0, 0, sign*6], origin=[sign*1.3, 11.4, -1.3])
    lo, hi = sorted((sign*.2, sign*2.5))
    back_pivot = [0, 11.35, 1.25]
    cube(side+'_cloak_back', [lo, 3.0, 1.25], [hi, 11.35, 1.85], side+'_cape', 'cloak_shadow',
         rotation=[-6, 0, 0], origin=back_pivot)
    cube(side+'_cloak_hem', [lo, 3.0, 1.2], [hi, 3.4, 1.92], side+'_cape', 'cloak_edge',
         rotation=[-6, 0, 0], origin=back_pivot)
    lo, hi = sorted((sign*1.6, sign*2.05))
    cube(side+'_back_fold', [lo, 3.5, 1.85], [hi, 9.5, 2.05], side+'_cape', 'cloak',
         rotation=[-6, 0, 0], origin=back_pivot)
    x = sign*1.45
    cube(side+'_strap_front', [x-.15, 8.5, -1.27], [x+.15, 12.25, -1.18], 'body', 'strap')
    cube(side+'_strap_top', [x-.15, 12.2, -1.2], [x+.15, 12.4, 1.65], 'body', 'strap')
    cube(side+'_strap_back', [x-.15, 7.6, 1.46], [x+.15, 12.2, 1.75], 'body', 'strap')
cube('amulet_cord', [-.07, 11.0, -1.65], [.07, 11.8, -1.5], 'body', 'strap')
cube('cloak_clasp', [-.3, 10.25, -1.8], [.3, 11.0, -1.55], 'body', 'soul')

# A slight forward neck posture keeps the hood clear of the tilted chest.
for element in cubes:
    if element['name'] in members['head']:
        for field in ('from', 'to', 'origin'):
            element[field][2] -= HEAD_FORWARD/FIT
pivots['head'][2] -= HEAD_FORWARD/FIT


def chest_point(point):
    return [(point[0]-8)*CHEST_SCALE, CHEST_BASE+point[1]*CHEST_SCALE,
            CHEST_BACK+point[2]*CHEST_SCALE]


def chest_box(name, origin, size, offset, bone, uv):
    lo = [origin[i]+offset[i] for i in range(3)]
    hi = [lo[i]+size[i] for i in range(3)]
    cube(name, chest_point(lo), chest_point(hi), bone, 'ender_chest', box_uv(uv, size, 'ender_chest', y_up=True))


# The vanilla single chest has exactly three boxes, also used by Ender Chests.
# Preserve their shapes, UVs and native independent lid/lock pivots.
chest_box('chest_bottom', [1, 0, 1], [14, 10, 14], [0, 0, 0], 'backpack', [0, 19])
chest_box('chest_lid', [1, 0, 0], [14, 5, 14], [0, 9, 1], 'chest_lid', [0, 0])
chest_box('chest_lock', [7, -1, 15], [2, 4, 1], [0, 8, 0], 'chest_lock', [0, 0])
pivots.update({'left_cape': [0, 11.5, 1.3], 'right_cape': [0, 11.5, 1.3],
               'backpack': chest_point([8, 0, 0]), 'chest_lid': chest_point([8, 9, 1]),
               'chest_lock': chest_point([8, 8, 0])})
hierarchy = {'root': ['body', 'left_leg', 'right_leg'],
             'body': ['head', 'left_arm', 'right_arm', 'left_cape', 'right_cape', 'backpack'],
             'backpack': ['chest_lid', 'chest_lock']}
# The chest follows the spine's lean; its floor and the straight arms stay aligned.
# Preserve the carrying pose, then shrink the whole model to vanilla chest width.
for element in cubes:
    for field in ('from', 'to', 'origin'):
        element[field] = [round(value*FIT*MODEL_SCALE, 6) for value in element[field]]
pivots = {name: [round(value*FIT*MODEL_SCALE, 6) for value in pivot] for name, pivot in pivots.items()}

pose = {'body': [LEAN, 0, 0]}
for side in ('left', 'right'):
    pose[side+'_arm'] = [ARM_ANGLE, 0, 0]


def animator(bone, tracks, animation):
    frames = []
    for channel, keys in tracks.items():
        for time, vector in keys:
            frames.append({'channel': channel, 'data_points': [dict(zip('xyz', map(str, vector)))],
                           'uuid': uid(f'key/{animation}/{bone}/{channel}/{time}'),
                           'time': time, 'interpolation': 'linear'})
    return {'name': bone, 'type': 'bone', 'keyframes': frames}


def animation(name, length, loop, tracks):
    return {'name': name, 'uuid': uid('animation/'+name), 'loop': loop, 'length': length,
            'snapping': 20, 'override': False, 'animators': {
                uid('bone/'+bone): animator(bone, channels, name) for bone, channels in tracks.items()}}


open_keys = [(0, [0, 0, 0]), (.15, [-35, 0, 0]), (.45, [-90, 0, 0])]
close_keys = [(0, [-90, 0, 0]), (.2, [-20, 0, 0]), (.35, [0, 0, 0])]
animations = [
    animation('idle', 3, 'loop', {'head': {'rotation': [(0, [0, -3, 0]), (1.5, [0, 3, 0]), (3, [0, -3, 0])]}}),
    animation('chest_open', .45, 'hold', {bone: {'rotation': open_keys} for bone in ('chest_lid', 'chest_lock')}),
    animation('chest_close', .35, 'once', {bone: {'rotation': close_keys} for bone in ('chest_lid', 'chest_lock')}),
    animation('walk', 1, 'loop', {
        bone: {'rotation': [(0, [angle, 0, 0]), (.5, [-angle, 0, 0]), (1, [angle, 0, 0])]}
        for bone, angle in [('left_leg', 22), ('right_leg', -22)]}),
]

(HERE/'textures').mkdir(parents=True, exist_ok=True)
textures, provenance = [], {}
with ZipFile(CLIENT) as jar:
    for name, color in MATERIALS:
        path = 'textures/'+name+'.png'
        if name in VANILLA:
            data = jar.read(VANILLA[name])
        elif name in CLOTH_MATERIALS:
            data = CLOTH_ATLAS.read_bytes()
        else:
            data = swatch(color)
        (HERE/path).write_bytes(data)
        width, height = struct.unpack('>II', data[16:24])
        textures.append({'name': name, 'path': path, 'width': width, 'height': height,
                         'render_mode': 'emissive' if name == 'soul' else 'default',
                         'uuid': uid('material/'+name)})
        if name in VANILLA:
            provenance[name] = {'minecraft_version': '1.16.5', 'source': VANILLA[name],
                                'sha256': hashlib.sha256(data).hexdigest(), 'copyright': 'Mojang Studios'}

plan = {'name': 'Soul Courier', 'model_identifier': 'soulcatcherplus:soul_courier',
        'model_format': 'free', 'format_version': '5.0',
        'target_size': {'minecraft_blocks': [.875, 1.96875, 1.75], 'blockbench_units': [14, 31.5, 28],
                        'fit_policy': 'Uniformly reduced carrying pose with a vanilla-sized 14-unit chest; envelope includes the fully open lid.'},
        'texture_width': 64, 'texture_height': 64, 'textures': textures, 'cubes': cubes,
        'groups': [{'name': name, 'uuid': uid('bone/'+name), 'origin': pivot, 'children': members.get(name, [])}
                   for name, pivot in pivots.items()], 'hierarchy': hierarchy, 'pose': pose, 'animations': animations}
dump(HERE/'plan.json', plan)
dump(HERE/'vanilla-sources.json', provenance)
spec = importlib.util.spec_from_file_location('model_bridge', SKILL/'scripts/plan_to_bbmodel.py')
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)
model, warnings = bridge.convert(plan)
by_group = {group['name']: group for group in model['groups']}
by_cube = {element['name']: element['uuid'] for element in model['elements']}


def node(name):
    return {'uuid': by_group[name]['uuid'], 'isOpen': name=='root',
            'children': [by_cube[part] for part in members.get(name, [])]
            + [node(child) for child in hierarchy.get(name, [])]}


model['outliner'] = [node('root')]
for group in model['groups']:
    group.update({'rotation': pose.get(group['name'], [0, 0, 0]), 'export': True, 'visibility': True})
for texture in model['textures']:
    texture['source'] = 'data:image/png;base64,' + base64.b64encode((HERE/texture['relative_path']).read_bytes()).decode()


def bounds_report():
    """Measure the posed cubes, including cloth rotations and the carrying lean."""
    groups = {group['name']: group for group in model['groups']}
    parents = {child: parent for parent, children in hierarchy.items() for child in children}
    owner = {part: bone for bone, parts in members.items() for part in parts}

    def rotate(point, part):
        point = [a-b for a, b in zip(point, part['origin'])]
        for axis, degrees in enumerate(part.get('rotation', [0, 0, 0])):
            a, b = ((1, 2), (2, 0), (0, 1))[axis]
            cosine, sine = math.cos(math.radians(degrees)), math.sin(math.radians(degrees))
            point[a], point[b] = cosine*point[a]-sine*point[b], sine*point[a]+cosine*point[b]
        return [a+b for a, b in zip(point, part['origin'])]

    vertices = []
    for element in model['elements']:
        for point in itertools.product(*zip(element['from'], element['to'])):
            point = rotate(point, element)
            bone = owner[element['name']]
            while bone:
                point = rotate(point, groups[bone])
                bone = parents.get(bone)
            vertices.append(point)
    low = [min(point[axis] for point in vertices) for axis in range(3)]
    high = [max(point[axis] for point in vertices) for axis in range(3)]
    assert abs(low[1]) < 1e-5 and high[1] <= 36*MODEL_SCALE, 'Pose must remain above the ground and inside its height envelope'
    assert high[0]-low[0] <= 16*MODEL_SCALE+.0001 and high[2]-low[2] <= 32*MODEL_SCALE, 'Pose exceeds its footprint envelope'
    return {'min': low, 'max': high, 'emissive_cubes': ['cloak_clasp']}


reports = {'soul_courier.bbmodel': bounds_report()}
dump(HERE/'soul_courier.bbmodel', model)
for name in ('chest_lid', 'chest_lock'):
    by_group[name]['rotation'] = [-90, 0, 0]
model['name'], model['animations'] = 'Soul Courier - Open Chest Preview', []
dump(HERE/'soul_courier_open_chest.bbmodel', model)
reports['soul_courier_open_chest.bbmodel'] = bounds_report()
dump(HERE/'bounds-report.json', reports)
print(f'Created {len(cubes)} cuboids; lowered chest follows {LEAN}-degree spine lean; straight carrying arms.')
for warning in warnings:
    print('Warning:', warning)
