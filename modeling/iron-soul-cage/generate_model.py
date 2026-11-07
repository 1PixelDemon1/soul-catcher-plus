"""Original cuboid cage, using the existing iron-cage palette as flat materials.

No item icons or game resources are changed. Requires the installed model-bridge
skill only to write the editable Blockbench project.
"""
from pathlib import Path
import base64
import importlib.util
import json
import math
import struct
import uuid
import zlib

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SKILL = Path.home() / '.codex/skills/blockbench-model-bridge'
NAMESPACE = uuid.UUID('b185806c-01ef-4ee7-9f37-c29667a35035')
PALETTE = json.loads((ROOT / 'art/soul-cages/animated/sprites.json').read_text(encoding='utf-8'))
MATERIALS = [
    ('iron', PALETTE['materials']['iron']['m']),
    ('iron_shadow', PALETTE['materials']['iron']['o']),
    ('iron_highlight', PALETTE['materials']['iron']['h']),
    ('chain', '252E36FF'),
    ('chain_highlight', '52606AFF'),
    ('glass', 'B6D9E020'),
    ('glass_edge', 'B6D9E060'),
    ('soul_pale', PALETTE['shared']['A']),
    ('soul_cyan', PALETTE['shared']['B']),
    ('soul_blue', PALETTE['shared']['C']),
    ('soul_tail', PALETTE['shared']['t']),
]


def uid(name):
    return str(uuid.uuid5(NAMESPACE, name))


def dump(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def swatch(color):
    """Uniform material parameters, encoded as tiny PNGs for Blockbench portability."""
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    rgba = bytes.fromhex(color)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 8, 8, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress((b'\x00' + rgba * 8) * 8)) + chunk(b'IEND', b''))


cubes, members = [], {}


def cube(name, lo, hi, bone, material, top=None):
    index = next(i for i, pair in enumerate(MATERIALS) if pair[0] == material)
    faces = {side: {'texture': index, 'uv': [0, 0, 8, 8]}
             for side in ('north', 'south', 'east', 'west', 'up', 'down')}
    if top:
        faces['up']['texture'] = next(i for i, pair in enumerate(MATERIALS) if pair[0] == top)
    element = {'name': name, 'uuid': uid('cube/' + name), 'from': lo, 'to': hi,
               'origin': [(a + b) / 2 for a, b in zip(lo, hi)], 'faces': faces}
    cubes.append(element)
    members.setdefault(bone, []).append(name)


# Stepped iron caps, with the visual weight kept at the top and bottom.
cube('lower_foot', [-2.55, .4, -2.55], [2.55, .85, 2.55], 'cage', 'iron_shadow')
cube('lower_rim', [-2.8, .85, -2.8], [2.8, 1.4, 2.8], 'cage', 'iron', 'iron_highlight')
cube('lower_collar', [-2.45, 1.4, -2.45], [2.45, 2.0, 2.45], 'cage', 'iron')
cube('upper_rim', [-2.8, 10.15, -2.8], [2.8, 10.8, 2.8], 'cage', 'iron', 'iron_highlight')
cube('upper_collar', [-2.5, 10.8, -2.5], [2.5, 11.65, 2.5], 'cage', 'iron')
cube('upper_socket', [-2.3, 11.65, -2.1], [2.3, 12.15, 2.1], 'cage', 'iron_shadow', 'iron_highlight')

# Square sections and simple right-angle steps, with a wider central belly.
# All glass parts are axis-aligned boxes, without diagonal facets.
glass_sections = [(1.95, 2.7, 2.3), (2.7, 3.6, 3.05), (3.6, 8.9, 3.65),
                  (8.9, 9.65, 3.05), (9.65, 10.2, 2.3)]
for section, (bottom, top, radius) in enumerate(glass_sections):
    prefix = f'glass_section_{section+1}'
    cube(prefix+'_front', [-radius, bottom, -radius-.04], [radius, top, -radius+.04], 'glass', 'glass')
    cube(prefix+'_back', [-radius, bottom, radius-.04], [radius, top, radius+.04], 'glass', 'glass')
    cube(prefix+'_left', [-radius-.04, bottom, -radius], [-radius+.04, top, radius], 'glass', 'glass')
    cube(prefix+'_right', [radius-.04, bottom, -radius], [radius+.04, top, radius], 'glass', 'glass')

# Four non-overlapping strips close each square ledge while leaving its centre hollow.
for section, ((_, top, first), (_, _, second)) in enumerate(zip(glass_sections, glass_sections[1:])):
    inner, outer = sorted((first, second))
    prefix = f'glass_step_{section+1}'
    cube(prefix+'_front', [-outer, top-.025, -outer], [outer, top+.025, -inner], 'glass', 'glass')
    cube(prefix+'_back', [-outer, top-.025, inner], [outer, top+.025, outer], 'glass', 'glass')
    cube(prefix+'_left', [-outer, top-.025, -inner], [-inner, top+.025, inner], 'glass', 'glass')
    cube(prefix+'_right', [inner, top-.025, -inner], [outer, top+.025, inner], 'glass', 'glass')

# Restrained highlights follow the widest front face; no tall iron bars hide the belly.
cube('glass_glint_low', [-.9, 4.0, -3.71], [-.72, 5.3, -3.69], 'glass', 'glass_edge')
cube('glass_glint_high', [-.9, 7.2, -3.71], [-.72, 8.2, -3.69], 'glass', 'glass_edge')

# Basket handle formed by three interlocking rectangular chain links.
# The end links lie in YZ; the long upper link lies in XY.
for side, x in (('left', -2.05), ('right', 2.05)):
    cube(f'{side}_link_front', [x-.22, 12.0, -.8], [x+.22, 14.65, -.45], 'handle', 'chain')
    cube(f'{side}_link_back', [x-.22, 12.0, .45], [x+.22, 14.65, .8], 'handle', 'chain_highlight')
    cube(f'{side}_link_bottom', [x-.22, 12.0, -.45], [x+.22, 12.35, .45], 'handle', 'chain')
    cube(f'{side}_link_top', [x-.22, 14.3, -.45], [x+.22, 14.65, .45], 'handle', 'chain_highlight')
cube('handle_upper_bar', [-2.55, 15.05, -.22], [2.55, 15.4, .22], 'handle', 'chain_highlight')
cube('handle_lower_bar', [-2.55, 13.95, -.22], [2.55, 14.3, .22], 'handle', 'chain')
cube('handle_left_return', [-2.55, 14.3, -.22], [-2.2, 15.05, .22], 'handle', 'chain')
cube('handle_right_return', [2.2, 14.3, -.22], [2.55, 15.05, .22], 'handle', 'chain_highlight')

pivots = {'root': [0, 0, 0], 'handle': [0, 14.65, 0], 'cage': [0, 12.1, 0], 'glass': [0, 6.1, 0]}
hierarchy = {'root': ['handle', 'cage'], 'cage': ['glass']}
for index, (height, material) in enumerate(((6.25, 'soul_pale'), (7.15, 'soul_cyan'), (5.25, 'soul_blue'))):
    orbit, body = f'orbit_{index+1}', f'soul_{index+1}'
    phase = math.radians(index * 120)
    x, y, z = 1.25 * math.cos(phase), height, -1.25 * math.sin(phase)
    pivots[orbit] = [0, 6.25, 0]
    pivots[body] = [x, y, z]
    hierarchy['cage'].append(orbit)
    hierarchy[orbit] = [body]
    cube(f'{body}_head', [x-.8, y+.2, z-.7], [x+.8, y+1.6, z+.7], body, material)
    cube(f'{body}_body', [x-.5, y-.65, z-.4], [x+.5, y+.3, z+.4], body, material)
    cube(f'{body}_tail', [x-.8, y-1.0, z-.25], [x-.15, y-.55, z+.25], body, 'soul_tail')
    cube(f'{body}_core', [x-.3, y+.65, z-.72], [x+.3, y+1.2, z-.7], body, 'soul_pale')


def animator(bone, tracks):
    frames = []
    for channel, keys in tracks.items():
        for time, vector in keys:
            frames.append({'channel': channel, 'data_points': [dict(zip('xyz', map(str, vector)))],
                           'uuid': uid(f'key/{bone}/{channel}/{time}'), 'time': time, 'interpolation': 'linear'})
    return {'name': bone, 'type': 'bone', 'keyframes': frames}


animators = {
    uid('bone/cage'): animator('cage', {'rotation': [(0,[0,0,0]), (1,[1.0,0,.8]),
        (2,[0,0,0]), (3,[-1.0,0,-.8]), (4,[0,0,0])]})
}
for index in range(3):
    orbit, body = f'orbit_{index+1}', f'soul_{index+1}'
    animators[uid('bone/' + orbit)] = animator(orbit, {'rotation': [(t,[0,t*90,0]) for t in range(5)]})
    # Different phases preserve a lively spiral without the souls touching the glass.
    keys = [(t/2, [0, round(1.0 * math.sin(t/8*2*math.pi + index*2*math.pi/3), 4), 0]) for t in range(9)]
    animators[uid('bone/' + body)] = animator(body, {'position': keys,
        'rotation': [(0,[0,0,-5]), (2,[0,0,5]), (4,[0,0,-5])]})
animation = {'name':'soul_orbit', 'uuid':uid('animation/soul_orbit'), 'loop':'loop',
             'length':4.0, 'snapping':20, 'override':False, 'animators':animators}

textures = []
(HERE / 'textures').mkdir(parents=True, exist_ok=True)
for name, color in MATERIALS:
    path = f'textures/{name}.png'
    (HERE / path).write_bytes(swatch(color))
    textures.append({'name':name, 'path':path, 'width':8, 'height':8, 'uuid':uid('material/' + name)})
groups = [{'name':name, 'uuid':uid('bone/' + name), 'origin':pivot, 'children':members.get(name, [])}
          for name, pivot in pivots.items()]
plan = {'name':'Iron Soul Cage - Hand Model', 'model_identifier':'soulcatcherplus:iron_soul_cage_hand',
        'model_format':'free', 'format_version':'5.0',
        'target_size': {'minecraft_blocks':[.5,1,.5], 'blockbench_units':[8,16,8],
                        'fit_policy':'Entire model and animation within X/Z [-4,4], Y [0,16].'},
        'texture_width':8, 'texture_height':8, 'textures':textures, 'cubes':cubes,
        'groups':groups, 'hierarchy':hierarchy, 'animations':[animation]}
dump(HERE / 'plan.json', plan)
spec = importlib.util.spec_from_file_location('model_bridge', SKILL / 'scripts/plan_to_bbmodel.py')
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)
model, warnings = bridge.convert(plan)
by_group = {g['name']:g for g in model['groups']}
by_cube = {c['name']:c['uuid'] for c in model['elements']}


def node(name):
    return {'uuid':by_group[name]['uuid'], 'isOpen':name=='root',
            'children':[by_cube[c] for c in members.get(name, [])] + [node(g) for g in hierarchy.get(name, [])]}


model['outliner'] = [node('root')]
for group in model['groups']:
    group.update({'rotation':[0,0,0], 'export':True, 'visibility':True})
for texture in model['textures']:
    texture.update({'source':'data:image/png;base64,' + base64.b64encode((HERE/texture['relative_path']).read_bytes()).decode(),
                    'saved':True})
dump(HERE / 'iron_soul_cage.bbmodel', model)
print(f'Created {len(cubes)} cuboids, {len(groups)} bones, a 4-second looping animation.')
for warning in warnings:
    print('Warning:', warning)
