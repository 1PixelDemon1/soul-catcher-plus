"""Standalone design preview. Does not change the approved model or game assets."""
from pathlib import Path
import base64
import copy
import importlib.util
import json
import uuid

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent / 'supreme-soul-cage'
SKILL = Path.home() / '.codex/skills/blockbench-model-bridge'
NAMESPACE = uuid.UUID('13fac3e5-b9b0-41c7-a9da-b21c068f9ad3')


def uid(name):
    return str(uuid.uuid5(NAMESPACE, name))


def dump(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def main():
    plan = copy.deepcopy(json.loads((SOURCE / 'plan.json').read_text(encoding='utf-8')))
    source_model = json.loads((SOURCE / 'supreme_soul_cage.bbmodel').read_text(encoding='utf-8'))
    # Preserve the approved crown, exposed stars, feet, glass, chain and soul animation.
    removed = {cube['name'] for cube in plan['cubes']
               if cube['name'].startswith('corner_rib_')
               or (cube['name'].startswith('bone_support_')
                   and ('_shaft_' in cube['name'] or '_joint_' in cube['name']))}
    plan['cubes'] = [cube for cube in plan['cubes'] if cube['name'] not in removed]
    groups = {group['name']: group for group in plan['groups']}
    for group in plan['groups']:
        group['children'] = [name for name in group['children'] if name not in removed]
    materials = {texture['name']: index for index, texture in enumerate(plan['textures'])}

    def cube(name, lo, hi, material='bone', top='bone_highlight'):
        faces = {side: {'texture': materials[material], 'uv': [0, 0, 8, 8]}
                 for side in ('north', 'south', 'east', 'west', 'up', 'down')}
        faces['up']['texture'] = materials[top]
        plan['cubes'].append({'name': name, 'uuid': uid('cube/' + name), 'from': lo, 'to': hi,
                             'origin': [(a+b)/2 for a, b in zip(lo, hi)], 'faces': faces})
        groups['cage']['children'].append(name)

    # Follow the actual square glass contour at each of the four vertical corners.
    # Bone elbows rest on the glass's horizontal steps; no grey bands or fasteners.
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
                     [x-half_width, bottom, z-half_width], [x+half_width, top, z+half_width])
            for step, ((_, top, inner), (_, _, outer)) in enumerate(zip(sections, sections[1:])):
                x0, x1 = sorted((side * (inner+corner_offset), side * (outer+corner_offset)))
                z0, z1 = sorted((front * (inner+corner_offset), front * (outer+corner_offset)))
                cube(f'{prefix}_elbow_{step}',
                     [x0-half_width, top-half_width, z0-half_width],
                     [x1+half_width, top+half_width, z1+half_width])
            # Paired square condyles make the uprights read as sculpted bones.
            # Keep their outer tips inside the existing animated size envelope.
            for end, y in (('lower', 3.94), ('upper', 8.56)):
                for lobe in (-1, 1):
                    x = side * (3.46 + lobe * .15)
                    z = front * (3.46 - lobe * .15)
                    cube(f'{prefix}_{end}_condyle_{lobe}',
                         [x-.21, y-.55, z-.21], [x+.21, y+.55, z+.21],
                         material='bone', top='bone_highlight')

    (HERE / 'textures').mkdir(parents=True, exist_ok=True)
    embedded = {texture['name']: texture['source'] for texture in source_model['textures']}
    for texture in plan['textures']:
        (HERE / texture['path']).write_bytes(base64.b64decode(embedded[texture['name']].split(',', 1)[1]))
    plan.update(name='Supreme Soul Cage - Contour Corner Ribs Preview',
                model_identifier='soulcatcherplus:supreme_cage_corner_preview')
    dump(HERE / 'plan.json', plan)
    spec = importlib.util.spec_from_file_location('model_bridge', SKILL / 'scripts/plan_to_bbmodel.py')
    bridge = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(bridge)
    model, warnings = bridge.convert(plan)
    by_group = {group['name']: group for group in model['groups']}
    by_cube = {cube['name']: cube['uuid'] for cube in model['elements']}

    def node(name):
        return {'uuid': by_group[name]['uuid'], 'isOpen': name == 'root',
                'children': [by_cube[cube] for cube in groups[name]['children']]
                            + [node(child) for child in plan['hierarchy'].get(name, [])]}

    model['outliner'] = [node('root')]
    for group in model['groups']:
        group.update(rotation=[0, 0, 0], export=True, visibility=True)
    for texture in model['textures']:
        texture.update(source=embedded[texture['name']], saved=True)
    dump(HERE / 'supreme_cage_corner_preview.bbmodel', model)
    print(f'Created preview: {len(model["elements"])} cuboids. Approved game model unchanged.')
    for warning in warnings:
        print('Warning:', warning)


if __name__ == '__main__':
    main()
