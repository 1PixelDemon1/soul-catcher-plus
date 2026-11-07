"""Bone reliquary variant of the approved cage; flat palette materials only."""
from pathlib import Path
import base64
import copy
import importlib.util
import json
import struct
import uuid
import zlib

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SKILL = Path.home() / '.codex/skills/blockbench-model-bridge'
NAMESPACE = uuid.UUID('0f152005-8d58-4630-81a7-94344c2f716a')


def uid(name):
    return str(uuid.uuid5(NAMESPACE, name))


def dump(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def swatch(color):
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 8, 8, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress((b'\0' + bytes.fromhex(color) * 8) * 8)) + chunk(b'IEND', b''))


def main():
    plan = copy.deepcopy(json.loads((HERE.parent / 'iron-soul-cage/plan.json').read_text(encoding='utf-8')))
    palette = json.loads((ROOT / 'art/soul-cages/animated/sprites.json').read_text(encoding='utf-8'))['materials']['supreme']
    # Retain the exact approved vessel, handle, soul sizes, pivots and four-second animation.
    replaced = {'lower_foot', 'lower_rim', 'lower_collar', 'upper_rim', 'upper_collar', 'upper_socket'}
    plan['cubes'] = [cube for cube in plan['cubes'] if cube['name'] not in replaced]
    for group in plan['groups']:
        group['children'] = [name for name in group['children'] if name not in replaced]
    source = json.loads((HERE.parent / 'iron-soul-cage/iron_soul_cage.bbmodel').read_text(encoding='utf-8'))
    source_textures = {texture['name']: texture for texture in source['textures']}
    recolors = {'iron': ('bone', palette['m']), 'iron_shadow': ('bone_shadow', palette['o']),
                'iron_highlight': ('bone_highlight', palette['h'])}
    (HERE / 'textures').mkdir(parents=True, exist_ok=True)
    for texture in plan['textures']:
        old_name = texture['name']
        if old_name in recolors:
            texture['name'], color = recolors[old_name]
            data = swatch(color)
        else:
            data = base64.b64decode(source_textures[old_name]['source'].split(',', 1)[1])
        texture['path'] = 'textures/' + texture['name'] + '.png'
        (HERE / texture['path']).write_bytes(data)
    for name, color in [('star_gold', palette['n']), ('star_white', palette['s'])]:
        texture = {'name': name, 'path': f'textures/{name}.png', 'width': 8, 'height': 8,
                   'uuid': uid('material/' + name)}
        plan['textures'].append(texture)
        (HERE / texture['path']).write_bytes(swatch(color))
    materials = {texture['name']: index for index, texture in enumerate(plan['textures'])}
    groups = {group['name']: group for group in plan['groups']}

    def cube(name, lo, hi, material='bone', top=None, bone='cage'):
        faces = {side: {'texture': materials[material], 'uv': [0, 0, 8, 8]}
                 for side in ('north', 'south', 'east', 'west', 'up', 'down')}
        if top:
            faces['up']['texture'] = materials[top]
        plan['cubes'].append({'name': name, 'uuid': uid('cube/' + name), 'from': lo, 'to': hi,
                             'origin': [(a+b)/2 for a, b in zip(lo, hi)], 'faces': faces})
        groups[bone]['children'].append(name)

    # Wider crown and inset lower band; no opaque backing across the soul chamber.
    cube('bone_lower_band', [-2.8, 1.3, -2.8], [2.8, 2.0, 2.8], top='bone_highlight')
    cube('bone_lower_seat', [-2.45, 1.0, -2.45], [2.45, 1.3, 2.45], 'bone_shadow')
    cube('bone_crown_rim', [-3.25, 10.15, -3.0], [3.25, 10.7, 3.0], top='bone_highlight')
    cube('bone_crown_step', [-2.9, 10.7, -2.6], [2.9, 11.35, 2.6])
    cube('bone_crown_socket', [-2.3, 11.35, -2.1], [2.3, 12.15, 2.1], 'bone_shadow', 'bone_highlight')
    for side in (-1, 1):
        x = side * 2.9
        cube(f'crown_end_{side}', [x-.35, 10.7, -2.6], [x+.35, 11.55, 2.6], 'bone_highlight')
        for front in (-1, 1):
            z = front * 2.4
            name = f'bone_support_{side}_{front}'
            # Distinct splayed feet, rather than a single flat slab.
            x = side * 2.55
            cube(f'{name}_ankle', [x-.38, .65, z-.4], [x+.38, 1.4, z+.4])
            x = side * 2.95
            cube(f'{name}_foot', [x-.55, .4, z-.55], [x+.55, .8, z+.55], 'bone_highlight')

    # Four vertical bones follow the square vessel's stepped corner contour.
    sections = [(1.95, 2.7, 2.3), (2.7, 3.6, 3.05), (3.6, 8.9, 3.65),
                (8.9, 9.65, 3.05), (9.65, 10.2, 2.3)]
    half_width = .30
    corner_offset = -.20
    for side in (-1, 1):
        for front in (-1, 1):
            prefix = f'corner_rib_{side}_{front}'
            for section, (bottom, top, radius) in enumerate(sections):
                x, z = side * (radius + corner_offset), front * (radius + corner_offset)
                cube(f'{prefix}_vertical_{section}',
                     [x-half_width, bottom, z-half_width], [x+half_width, top, z+half_width],
                     top='bone_highlight')
            for step, ((_, top, inner), (_, _, outer)) in enumerate(zip(sections, sections[1:])):
                x0, x1 = sorted((side * (inner+corner_offset), side * (outer+corner_offset)))
                z0, z1 = sorted((front * (inner+corner_offset), front * (outer+corner_offset)))
                cube(f'{prefix}_elbow_{step}',
                     [x0-half_width, top-half_width, z0-half_width],
                     [x1+half_width, top+half_width, z1+half_width], top='bone_highlight')
            # Paired, vertically elongated condyles stay within the 8-unit footprint.
            for end, y in (('lower', 3.94), ('upper', 8.56)):
                for lobe in (-1, 1):
                    x = side * (3.46 + lobe * .15)
                    z = front * (3.46 - lobe * .15)
                    cube(f'{prefix}_{end}_condyle_{lobe}',
                         [x-.21, y-.55, z-.21], [x+.21, y+.55, z+.21],
                         top='bone_highlight')

    # Matching crown ornaments on the front and back: dark inset, pale-gold cross and white core.
    star_group = {'name': 'star', 'uuid': uid('bone/star'), 'origin': [0, 11.2, 0], 'children': []}
    plan['groups'].append(star_group)
    groups['star'] = star_group
    plan['hierarchy']['cage'].append('star')
    for front in (-1, 1):
        # Mount the entire ornament outside the crown rim (Z +/-3.0), including its lower ray.
        z = front * 3.1
        cube(f'star_inset_{front}', [-1.0, 10.2, z-.12], [1.0, 12.2, z+.12], 'bone_shadow')
        z = front * 3.28
        cube(f'star_horizontal_{front}', [-1.15, 10.95, z-.07], [1.15, 11.45, z+.07], 'star_gold', bone='star')
        cube(f'star_vertical_{front}', [-.25, 10.05, z-.075], [.25, 12.35, z+.075], 'star_gold', bone='star')
        z = front * 3.38
        cube(f'star_core_{front}', [-.4, 10.8, z-.06], [.4, 11.6, z+.06], 'star_white', bone='star')
        cube(f'star_white_vertical_{front}', [-.12, 10.55, z-.065], [.12, 11.85, z+.065], 'star_white', bone='star')
        cube(f'star_white_horizontal_{front}', [-.65, 11.08, z-.065], [.65, 11.32, z+.065], 'star_white', bone='star')

    plan.update(name='Supreme Soul Cage - Bone Reliquary',
                model_identifier='soulcatcherplus:supreme_soul_cage_hand')
    dump(HERE / 'plan.json', plan)
    spec = importlib.util.spec_from_file_location('model_bridge', SKILL / 'scripts/plan_to_bbmodel.py')
    bridge = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(bridge)
    model, warnings = bridge.convert(plan)
    by_name = {group['name']: group for group in model['groups']}
    by_cube = {cube['name']: cube['uuid'] for cube in model['elements']}

    def node(name):
        return {'uuid': by_name[name]['uuid'], 'isOpen': name == 'root',
                'children': [by_cube[c] for c in groups[name]['children']]
                            + [node(child) for child in plan['hierarchy'].get(name, [])]}

    model['outliner'] = [node('root')]
    for group in model['groups']:
        group.update(rotation=[0, 0, 0], export=True, visibility=True)
    for texture in model['textures']:
        data = (HERE / texture['relative_path']).read_bytes()
        texture.update(source='data:image/png;base64,' + base64.b64encode(data).decode(), saved=True)
    dump(HERE / 'supreme_soul_cage.bbmodel', model)
    print(f'Created supreme cage: {len(model["elements"])} cuboids, {len(model["groups"])} bones.')
    for warning in warnings:
        print('Warning:', warning)


if __name__ == '__main__':
    main()
