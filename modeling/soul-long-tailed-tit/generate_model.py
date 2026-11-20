"""Build the long-tailed tit concept model with the Soul Raven animation rig."""
from pathlib import Path
import base64
import importlib.util
import json
import struct
import uuid
import zlib


HERE = Path(__file__).resolve().parent
RAVEN_PLAN = HERE.parent / 'legendary-raven/plan.json'
BRIDGE = Path.home() / '.codex/skills/blockbench-model-bridge/scripts/plan_to_bbmodel.py'
NAMESPACE = uuid.UUID('b23e2b45-0de8-4e19-96d0-5bf44b32b2a6')
FACE_NAMES = ('north', 'south', 'west', 'east', 'up', 'down')
PALETTE = {
    'head_white': 'F0EEE6',
    'breast_cream': 'E3DED2',
    'flank_blush': 'D7BDB2',
    'shoulder_rose': 'B7958F',
    'wing_charcoal': '252733',
    'beak_and_eye': '181B22',
    'feet_and_frame': '393C44',
    'soul_amulet': '69DFE5',
}


def uid(key):
    return str(uuid.uuid5(NAMESPACE, key))


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def write_swatch(path, color):
    def chunk(tag, data):
        return (struct.pack('>I', len(data)) + tag + data
                + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff))

    width = height = 8
    rows = (b'\0' + width * bytes.fromhex(color)) * height
    path.write_bytes(b'\x89PNG\r\n\x1a\n'
                     + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b''))


def main():
    raven = json.loads(RAVEN_PLAN.read_text(encoding='utf-8'))
    pivots = {
        'raven': [0, 0, 0],
        'body': [0, 3.35, 0.25],
        'neck': [0, 4.5, -0.95],
        'head': [0, 4.92, -1.18],
        'wing_left': [1.66, 4.04, 0.2],
        'wing_right': [-1.66, 4.04, 0.2],
        'tail': [0, 3.55, 1.55],
        'leg_left': [0.71, 1.67, 0.35],
        'leg_right': [-0.71, 1.67, 0.35],
    }
    cubes = []
    members = {bone: [] for bone in pivots}
    materials = {name: index for index, name in enumerate(PALETTE)}

    def cube(name, lo, hi, bone, material, rotation=None, origin=None):
        texture = materials[material]
        part = {
            'name': name, 'uuid': uid('cube/' + name), 'from': lo, 'to': hi,
            'faces': {face: {'texture': texture, 'uv': [0, 0, 8, 8]} for face in FACE_NAMES},
        }
        if rotation is not None:
            part.update(rotation=rotation, origin=origin)
        cubes.append(part)
        members[bone].append(name)

    # The dark nape and sloping mantle meet above the pink shoulders.
    cube('breast', [-1.68, 2.07, -1.62], [1.68, 4.97, 1.69], 'body', 'breast_cream')
    cube('lower_belly', [-1.49, 1.49, -1.16], [1.49, 2.26, 1.81], 'body', 'flank_blush')
    cube('mantle', [-1.42, 4.52, -0.29], [1.42, 5.25, 1.99], 'body', 'wing_charcoal',
         [27, 0, 0], [0, 4.81, 0.30])
    cube('mantle_coverts', [-1.60, 4.78, 0.30], [1.60, 5.01, 1.76], 'body', 'wing_charcoal')
    cube('rump', [-1.31, 2.81, 1.43], [1.31, 4.90, 2.08], 'body', 'wing_charcoal',
         [15, 0, 0], [0, 4.38, 1.71])
    cube('throat', [-1.26, 4.37, -1.7], [1.26, 5.23, 0.15], 'neck', 'head_white')
    cube('white_head', [-1.46, 5.00, -2.55], [1.46, 6.91, 0.03], 'head', 'head_white')
    cube('white_cheeks', [-1.28, 4.70, -2.41], [1.28, 5.05, -0.06], 'head', 'head_white')
    cube('white_crown', [-1.25, 6.87, -2.34], [1.25, 7.20, -0.11], 'head', 'head_white')
    cube('dark_nape', [-1.35, 5.06, -0.24], [1.35, 6.94, 0.44], 'head', 'wing_charcoal',
         [15, 0, 0], [0, 6.35, 0.12])
    cube('short_beak', [-0.25, 5.32, -2.89], [0.25, 5.63, -2.49], 'head', 'beak_and_eye')

    # The amulet stays on the chest when the head turns.
    cube('amulet_frame', [-0.48, 3.56, -1.79], [0.48, 4.47, -1.6], 'neck', 'feet_and_frame')
    cube('amulet_stone', [-0.33, 3.72, -1.82], [0.33, 4.31, -1.78], 'neck', 'soul_amulet')

    for name, sign in (('left', 1), ('right', -1)):
        def mirrored(part, lo, hi, bone, material, angle=None):
            if sign < 0:
                lo, hi = [-hi[0], lo[1], lo[2]], [-lo[0], hi[1], hi[2]]
            rotation = [angle, 0, 0] if angle is not None else None
            cube(f'{name}_{part}', lo, hi, bone, material, rotation, pivots[bone])

        mirrored('eye', [1.45, 5.87, -2.18], [1.48, 6.17, -1.88], 'head', 'beak_and_eye')
        mirrored('mantle_shoulder', [1.00, 4.57, -0.08], [1.58, 5.00, 1.54], 'body', 'shoulder_rose', 27)
        wing = 'wing_' + name
        mirrored('primaries', [1.55, 2.65, -0.26], [1.92, 3.93, 3.15], wing, 'wing_charcoal', 22)
        mirrored('pale_wing_bar', [1.57, 3.93, -0.25], [1.92, 4.17, 1.83], wing, 'head_white', 22)
        mirrored('rose_coverts', [1.54, 4.17, -0.25], [1.89, 4.65, 1.31], wing, 'shoulder_rose', 22)
        leg = 'leg_' + name
        mirrored('ankle', [0.59, 0.21, 0.21], [0.8, 1.71, 0.49], leg, 'feet_and_frame')
        mirrored('toes', [0.36, 0.04, -0.4], [1.01, 0.25, 0.58], leg, 'feet_and_frame')
        mirrored('hind_toe', [0.59, 0.07, 0.48], [0.8, 0.23, 0.89], leg, 'feet_and_frame')

        x = sign * 0.4
        cord_center = [x, 4.53, -1.68]
        cube(f'{name}_amulet_cord', [x - 0.055, 4.23, -1.735], [x + 0.055, 4.83, -1.625],
             'neck', 'beak_and_eye', [0, 0, -sign * 37], cord_center)

    # A wider dark root tapers into the three long feather bars.
    tail_angle = [-63, 0, 0]
    cube('tail_root', [-0.76, 2.21, 1.38], [0.76, 3.64, 1.73],
         'tail', 'wing_charcoal', tail_angle, pivots['tail'])
    for name, left, right, tip, material in (
        ('tail_center', -0.29, 0.29, -2.59, 'wing_charcoal'),
        ('tail_pale_left', 0.29, 0.59, -2.02, 'head_white'),
        ('tail_pale_right', -0.59, -0.29, -2.02, 'head_white'),
    ):
        cube(name, [left, tip, 1.40], [right, 3.64, 1.68], 'tail', material, tail_angle, pivots['tail'])

    textures = []
    (HERE / 'textures').mkdir(exist_ok=True)
    for material, color in PALETTE.items():
        path = f'textures/{material}.png'
        write_swatch(HERE / path, color)
        textures.append({'name': material, 'path': path, 'width': 8, 'height': 8,
                         'uuid': uid('material/' + material)})

    groups = [{'name': name, 'uuid': uid('bone/' + name), 'origin': pivot,
               'children': members[name], 'isOpen': name == 'raven'}
              for name, pivot in pivots.items()]
    previous_bones = {group['uuid']: group['name'] for group in raven['groups']}
    animations = raven['animations']
    for animation in animations:
        animation['uuid'] = uid('animation/' + animation['name'])
        animation['animators'] = {uid('bone/' + previous_bones[bone]): animator
                                  for bone, animator in animation['animators'].items()}
        for bone, animator in animation['animators'].items():
            for keyframe in animator['keyframes']:
                keyframe['uuid'] = uid(f"{animation['name']}/{bone}/{keyframe['channel']}/{keyframe['time']}")
        if animation['name'] == 'flight':
            # The tit's tail already hangs farther back in the rest pose.
            for keyframe in animation['animators'][uid('bone/tail')]['keyframes']:
                rotation = keyframe['data_points'][0]
                rotation['x'] = str(float(rotation['x']) + 26)

    plan = {
        'name': 'Soul Raven - Long-tailed Tit', 'model_identifier': 'soulcatcherplus:soul_raven_long_tailed_tit',
        'model_format': 'free', 'format_version': '5.0',
        'target_size': {'minecraft_blocks': [1, 1, 1], 'blockbench_units': [16, 16, 16],
                        'fit_policy': 'Include the tail and wing tips in perch and flight.'},
        'texture_width': 8, 'texture_height': 8, 'textures': textures,
        'cubes': cubes, 'groups': groups, 'hierarchy': raven['hierarchy'], 'animations': animations,
    }
    write_json(HERE / 'plan.json', plan)

    bridge_spec = importlib.util.spec_from_file_location('blockbench_bridge', BRIDGE)
    bridge = importlib.util.module_from_spec(bridge_spec)
    bridge_spec.loader.exec_module(bridge)
    model, warnings = bridge.convert(plan, texture_ref_mode='index')
    if warnings:
        raise ValueError(warnings)
    by_group = {group['name']: group for group in model['groups']}
    by_element = {element['name']: element['uuid'] for element in model['elements']}

    def node(name):
        return {'uuid': by_group[name]['uuid'], 'isOpen': name == 'raven',
                'children': [by_element[part] for part in members[name]]
                + [node(child) for child in plan['hierarchy'].get(name, [])]}

    model['outliner'] = [node('raven')]
    for group in model['groups']:
        group.update(rotation=[0, 0, 0], export=True, visibility=True)
    for texture in model['textures']:
        png = (HERE / texture['relative_path']).read_bytes()
        texture['source'] = 'data:image/png;base64,' + base64.b64encode(png).decode('ascii')
    write_json(HERE / 'soul_long_tailed_tit.bbmodel', model)


if __name__ == '__main__':
    main()
