"""Build the Miku variant from the approved right-handed concept."""
import base64
import importlib.util
import json
import math
from pathlib import Path
import struct
import uuid
import zlib


HERE = Path(__file__).resolve().parent
SHAMAN = HERE.parent / 'soul-shaman-cuboid'
BRIDGE = Path.home() / '.codex/skills/blockbench-model-bridge/scripts/plan_to_bbmodel.py'
NAMESPACE = uuid.UUID('efa10c29-393d-441d-b699-e66c3ce782d0')
FACES = ('north', 'south', 'west', 'east', 'up', 'down')
CHIN_HEIGHT = 13.65
HEAD_HEIGHT_SCALE = .81
PALETTE = {
    'skin': 'FFE1C4', 'white': 'C5C9CC', 'silver': 'A4A8B0',
    'hair': '20C5CA', 'hair_light': '32D4D7', 'hair_dark': '179DA6', 'cyan': '20D3D0',
    'ink': '28292F', 'shadow': '393B43', 'magenta': 'BE2345',
    'iris': '29E1D9', 'eyelid': '1A9591', 'onion_white': 'F1ECD5',
    'onion_pale': 'ACDC63', 'onion_green': '66A728', 'onion_dark': '497D20',
    'soul_light': '61F2F3', 'silver_light': 'D4D4D8', 'fold': '202128',
    'skin_shadow': 'E7C5A8', 'nameplate': 'D6BE86', 'platform_deck': '3B4653',
}


def identifier(name):
    return str(uuid.uuid5(NAMESPACE, name))


def compact_head(point):
    x, y, z = point
    return [x, CHIN_HEIGHT + (y - CHIN_HEIGHT) * HEAD_HEIGHT_SCALE, z]


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def swatch(rgb):
    def chunk(tag, data):
        return (struct.pack('>I', len(data)) + tag + data
                + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff))

    rows = (b'\0' + bytes.fromhex(rgb) * 8) * 8
    return (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', struct.pack('>IIBBBBB', 8, 8, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b''))


def main():
    original = json.loads((SHAMAN / 'soul_shaman.bbmodel').read_text(encoding='utf-8'))
    names = ('root', 'body', 'head', 'arm_left', 'arm_right', 'staff')
    pivots = {group['name']: group['origin'] for group in original['groups'] if group['name'] in names}
    pivots['head'] = compact_head(pivots['head'])
    for bone in ('arm_left', 'arm_right'):
        pivots[bone][0] *= -1
    pivots.update(staff=[-4.37, 10.1, -1.61],
                  hair_left=[3.12, 17.86, .74], hair_right=[-3.12, 17.86, .74],
                  cuff_left=[3.12, 17.86, 1.30], cuff_right=[-3.12, 17.86, 1.30],
                  platform=[0, 1.35, 0], wisp=[0, .47, -.10])
    hierarchy = {'root': ['body'], 'body': ['head', 'arm_left', 'arm_right', 'platform', 'wisp'],
                 'head': ['hair_left', 'hair_right'], 'arm_right': ['staff'],
                 'hair_left': ['cuff_left'], 'hair_right': ['cuff_right']}
    group_rotation = {'cuff_left': [0, 28, 0], 'cuff_right': [0, -28, 0]}
    members = {name: [] for name in pivots}
    cubes = []
    meshes = []
    material_index = {material: index for index, material in enumerate(PALETTE)}

    def cube(name, bone, lo, hi, color, rotation=None, pivot=None, shades=None):
        if bone == 'head':
            lo, hi = compact_head(lo), compact_head(hi)
        shades = shades or {}
        part = {'name': name, 'uuid': identifier('cube/' + name), 'from': lo, 'to': hi,
                'faces': {face: {'texture': material_index[shades.get(face, color)], 'uv': [0, 0, 8, 8]}
                          for face in FACES}}
        if rotation is not None:
            part.update(rotation=rotation, origin=pivot or [(a+b)/2 for a,b in zip(lo,hi)])
        cubes.append(part)
        members[bone].append(name)

    def beam(name, bone, start, end, width, z0, z1, color, shades=None):
        x, y = [(a+b)/2 for a,b in zip(start,end)]
        length = math.dist(start,end)
        angle = -math.degrees(math.atan2(end[0]-start[0],end[1]-start[1]))
        cube(name, bone, [x-width/2, y-length/2, z0], [x+width/2, y+length/2, z1],
             color, [0,0,angle], [x,y,(z0+z1)/2], shades)

    def loft(name, bone, rings, bands, top, bottom):
        """Join equal-sized rings in top-to-bottom order with flat quad faces."""
        bias = pivots[bone]
        vertices, faces = {}, {}
        keys = []
        for layer, ring in enumerate(rings):
            ids = [f'v{layer}_{index}' for index in range(len(ring))]
            keys.append(ids)
            for key, point in zip(ids, ring):
                if bone == 'head':
                    point = compact_head(point)
                vertices[key] = [round(a-b, 6) for a,b in zip(point, bias)]

        def add_face(corners, material):
            uv_points = ((2,2), (6,2), (6,6), (2,6))
            faces[f'f{len(faces)}'] = {
                'vertices': corners,
                'uv': dict(zip(corners, uv_points)),
                'texture': material_index[material],
            }

        count = len(keys[0])
        for upper, lower, materials in zip(keys, keys[1:], bands):
            for index in range(count):
                successor = (index + 1) % count
                add_face([upper[index], upper[successor], lower[successor], lower[index]], materials[index])
        for name_cap, ring, material, reverse in (
            ('top', keys[0], top, True), ('bottom', keys[-1], bottom, False),
        ):
            vertices[name_cap] = [sum(vertices[key][axis] for key in ring)/count for axis in range(3)]
            for index in range(count):
                pair = [ring[index], ring[(index + 1) % count]]
                add_face([name_cap] + (pair[::-1] if reverse else pair), material)
        meshes.append({'name': name, 'uuid': identifier('mesh/' + name), 'type': 'mesh',
                       'origin': bias, 'rotation': [0,0,0], 'vertices': vertices, 'faces': faces,
                       'visibility': True, 'export': True})
        members[bone].append(name)

    def rectangular(y, width, front, back, center=0):
        return [(center-width, y, front), (center+width, y, front),
                (center+width, y, back), (center-width, y, back)]

    def tube(name, bone, sections, bands, top, bottom):
        rings = []
        for index, (point, width, depth) in enumerate(sections):
            earlier = sections[max(0, index-1)][0]
            later = sections[min(len(sections)-1, index+1)][0]
            nx, ny = earlier[1]-later[1], later[0]-earlier[0]
            length = math.hypot(nx, ny)
            ux, uy = nx/length, ny/length
            rings.append([(point[0] + a*ux*width/2, point[1] + a*uy*width/2, point[2] + b*depth/2)
                          for a,b in ((-1,-1), (1,-1), (1,1), (-1,1))])
        loft(name, bone, rings, bands, top, bottom)

    # The front panels share the tapered shirt's waist and separate below the tie.
    loft('fitted_silver_shirt', 'body', [
        rectangular(13.40, 1.31, -1.04, .92),
        rectangular(12.78, 1.71, -1.27, 1.00),
        rectangular(11.27, 1.65, -1.37, 1.08),
        rectangular(9.49, 1.40, -1.27, .98),
    ], [('silver_light', 'silver', 'white', 'silver')] * 3, 'white', 'silver')
    cube('neck', 'body', [-.63, 13.29, -.84], [.63, 14.12, .54], 'skin')
    fold_count = 22

    def pleats(y, width, depth, inset):
        ring = []
        for index in range(fold_count):
            angle = -math.pi/fold_count + 2*math.pi*index/fold_count
            radius = 1 - inset * (index % 2)
            ring.append((math.sin(angle)*width*radius, y, -math.cos(angle)*depth*radius))
        return ring

    cloth = tuple('shadow' if index % 2 == 0 else 'fold' for index in range(fold_count))
    trim = tuple('cyan' if index % 2 == 0 else 'hair_dark' for index in range(fold_count))
    loft('pleated_skirt_and_hem', 'body', [
        pleats(9.58, 1.48, 1.16, .015), pleats(8.88, 1.83, 1.37, .07),
        pleats(6.54, 2.80, 1.88, .155), pleats(6.23, 2.91, 1.94, .155),
    ], [cloth, cloth, trim], 'ink', 'ink')
    cube('tie_knot', 'body', [-.37, 12.46, -1.66], [.37, 13.02, -1.16], 'cyan')
    loft('long_teal_tie', 'body', [
        rectangular(12.52, .27, -1.63, -1.28),
        rectangular(11.44, .29, -1.72, -1.36),
        rectangular(10.30, .32, -1.73, -1.30),
        rectangular(9.15, .37, -1.83, -1.53),
        rectangular(8.80, .036, -1.82, -1.55),
    ], [('cyan', 'hair_dark', 'hair_dark', 'hair')] * 4, 'cyan', 'hair_dark')
    cube('tie_clip', 'body', [-.37, 10.20, -1.79], [.37, 10.41, -1.71], 'ink')
    cube('nameplate', 'body', [0.77, 12.14, -1.395], [1.20, 12.32, -1.27], 'nameplate')

    loft('face_and_chin', 'head', [
        rectangular(18.87, 2.18, -2.22, 1.61),
        rectangular(18.19, 2.27, -2.31, 1.70),
        rectangular(14.10, 2.27, -2.31, 1.70),
        rectangular(13.80, 2.00, -2.11, 1.48),
    ], [('skin', 'skin_shadow', 'skin_shadow', 'skin_shadow')] * 3, 'skin', 'skin_shadow')
    loft('beveled_teal_crown', 'head', [
        rectangular(20.03, 2.42, -2.11, 1.89),
        rectangular(19.62, 2.82, -2.54, 2.16),
        rectangular(17.77, 2.82, -2.54, 2.16),
    ], [('hair_light', 'hair', 'hair_dark', 'hair'), ('hair', 'hair', 'hair_dark', 'hair')],
         'hair_light', 'hair_dark')
    cube('nape_hair_upper', 'head', [-2.62, 15.41, 1.43], [2.62, 18.24, 2.20], 'hair_dark')
    cube('nape_hair_lower', 'head', [-2.16, 14.19, 1.57], [2.16, 15.43, 2.13], 'hair_dark')
    loft('long_center_bang', 'head', [
        rectangular(18.04, .61, -2.57, -2.21, .14),
        rectangular(15.78, .58, -2.56, -2.23, .12),
        rectangular(15.54, .48, -2.49, -2.24, .12),
    ], [('hair', 'hair_dark', 'hair_dark', 'hair')] * 2, 'hair', 'hair_dark')
    cube('bang_right', 'head', [-1.93, 16.47, -2.54], [-.46, 17.78, -2.23], 'hair')
    cube('bang_left', 'head', [.67, 16.78, -2.53], [1.94, 17.78, -2.23], 'hair')

    for side, sign in (('right', -1), ('left', 1)):
        hair = 'hair_' + side

        def pair(part, bone, lo, hi, color, tilt=0, shades=None):
            if sign == -1:
                lo, hi = [-hi[0], lo[1], lo[2]], [-lo[0], hi[1], hi[2]]
            cube(side + '_' + part, bone, lo, hi, color,
                 [0,0,sign*tilt] if tilt else None, pivots[bone], shades)

        def accent(part, bone, start, end, width, z0, z1, color, shades=None):
            beam(side + '_' + part, bone, [sign*start[0], start[1]], [sign*end[0], end[1]],
                 width, z0, z1, color, shades)

        loft(side + '_cheek_lock', 'head', [
            rectangular(18.14, .48, -2.63, -1.22, sign*2.25),
            rectangular(17.61, .50, -2.65, -1.21, sign*2.25),
            rectangular(13.64 if side == 'right' else 13.83, .44, -2.57, -1.30, sign*2.22),
        ], [('hair', 'hair', 'hair_dark', 'hair')] * 2,
             'hair_light', 'hair_dark')
        pair('side_hair_upper', 'head', [2.03, 15.22, -1.28], [2.63, 18.15, 1.68], 'hair')
        pair('side_hair_lower', 'head', [2.08, 14.49, -.84], [2.53, 15.27, 1.53], 'hair_dark')
        pair('eye', 'head', [.69, 14.56, -2.34], [1.83, 15.20, -2.30], 'iris')
        pair('eyelid', 'head', [.69, 15.20, -2.34], [1.83, 15.57, -2.30], 'eyelid')

        # The frame and the strand jointly tilt at the root. Their attachment stays coaxial.
        cuff = 'cuff_' + side
        pair('threaded_ponytail_root', cuff, [2.60, 17.33, .85], [4.45, 18.39, 1.75], 'hair')
        center_x, center_y, center_z = pivots[cuff]
        # The frame sits in the YZ plane around a 1.62 unit opening.
        inner, outer = .81, 1.18
        for edge, y0, y1, z0, z1, red in (
            ('upper', inner, outer, -outer, outer, False),
            ('lower', -outer, -inner, -outer, outer, True),
            ('near', -inner, inner, -outer, -inner, True),
            ('far', -inner, inner, inner, outer, False),
        ):
            # The two red edges use a colored outer face on the same bars.
            shades = {'east' if sign > 0 else 'west': 'magenta', 'north': 'magenta'} if red else None
            cube(f'{side}_hair_cuff_{edge}', cuff,
                 [center_x-.15, center_y+y0, center_z+z0], [center_x+.15, center_y+y1, center_z+z1],
                 'ink', [45,0,0], pivots[cuff], shades)
        pair('tail_elbow', hair, [3.85, 17.36, .77], [4.68, 18.32, 2.35], 'hair')
        tail_bands = [('hair', 'hair', 'hair_dark', 'hair')] * 2
        tube(side + '_tail_upper', hair, [
            ((sign*4.11, 18.80, 3.12), 1.65, 1.82),
            ((sign*4.38, 18.39, 3.20), 1.99, 2.00),
            ((sign*5.36, 13.26, 3.20), 1.99, 1.99),
        ], tail_bands, 'hair_light', 'hair_dark')
        tube(side + '_tail_lower', hair, [
            ((sign*5.33, 13.27, 3.18), 1.64, 1.66),
            ((sign*6.06, 8.63, 3.17), 1.65, 1.73),
            ((sign*6.05, 8.40, 3.16), 1.54, 1.58),
        ], tail_bands, 'hair_light', 'hair_dark')

        accent('collar', 'body', [.18, 12.72], [1.19, 13.34], .48, -1.43, -1.20, 'silver')
        accent('collar_edge', 'body', [.15, 12.66], [1.16, 13.28], .12, -1.452, -1.40, 'cyan')
        accent('front_panel', 'body', [.73, 10.04], [1.34, 7.71], 1.48, -1.65, -1.13, 'white',
               {'west': 'silver', 'east': 'silver'})
        pair('bare_thigh', 'body', [.13, 5.37, -1.04], [1.40, 6.51, .87], 'skin',
             shades={'down': 'skin_shadow'})
        loft(side + '_boot', 'body', [
            rectangular(5.51, .69, -1.07, .94, sign*.79),
            rectangular(2.07, .65, -1.07, .91, sign*.81),
            rectangular(1.61, .66, -1.23, .96, sign*.81),
            rectangular(1.35, .66, -1.23, .96, sign*.81),
        ], [('shadow', 'ink', 'fold', 'ink')] * 2 + [('cyan', 'hair_dark', 'hair_dark', 'cyan')],
             'ink', 'hair_dark')
        pair('boot_mark', 'body', [.70, 4.14, -1.098], [.91, 5.16, -1.061], 'cyan')

    # Build each bare shoulder, detached sleeve and edge from shared cross sections.
    skin = ('skin', 'skin_shadow', 'skin_shadow', 'skin_shadow')
    band = ('cyan', 'hair_dark', 'hair_dark', 'cyan')
    sleeve_color = ('shadow', 'ink', 'fold', 'ink')
    tube('right_upper_arm', 'arm_right', [
        ((-1.87, 12.92, -.06), 1.38, 1.51), ((-2.59, 11.54, -.10), 1.37, 1.52),
        ((-2.70, 11.33, -.14), 1.54, 1.69), ((-2.93, 10.30, -.44), 1.65, 1.79),
        ((-3.19, 9.48, -.53), 1.71, 1.81),
    ], [skin, band, sleeve_color, sleeve_color], 'skin', 'fold')

    def right_cuff_ring(x, y, z, width, height):
        return [(x+a*width/2, y+b*height/2, z) for a,b in ((-1,1), (1,1), (1,-1), (-1,-1))]

    loft('right_forward_forearm', 'arm_right', [
        right_cuff_ring(-3.02, 9.49, .24, 1.39, 1.28),
        right_cuff_ring(-3.55, 9.29, -.73, 1.70, 1.76),
        right_cuff_ring(-3.92, 9.33, -1.48, 1.96, 2.04),
        right_cuff_ring(-3.99, 9.35, -1.67, 2.01, 2.10),
        right_cuff_ring(-3.99, 9.35, -1.69, 1.72, 1.80),
    ], [sleeve_color, sleeve_color, band, ('cyan',)*4], 'ink', 'fold')
    tube('left_arm_and_flared_sleeve', 'arm_left', [
        ((1.91, 12.92, -.03), 1.39, 1.50), ((2.50, 11.58, -.07), 1.39, 1.53),
        ((2.61, 11.34, -.08), 1.54, 1.75), ((3.37, 9.46, -.09), 1.81, 1.93),
        ((4.36, 7.65, -.10), 2.15, 2.22), ((4.47, 7.41, -.11), 2.18, 2.25),
    ], [skin, band, sleeve_color, sleeve_color, band], 'skin', 'fold')
    cube('onion_hand', 'arm_right', [-5.00, 9.48, -2.13], [-3.84, 10.62, -1.04], 'skin')
    stalk_points = [(-3.94 - 1.06 * (height - 6.99) / 7.46, height)
                    for height in (6.99, 10.91, 13.72, 14.45)]
    for name, color, start, end in zip(('pale_stalk', 'transition', 'neck'),
                                       ('onion_white', 'onion_pale', 'onion_green'),
                                       stalk_points, stalk_points[1:]):
        beam('onion_' + name, 'staff', start, end, .85, -2.03, -1.17, color)
    beam('onion_tall_leaf', 'staff', [-4.97, 14.34], [-5.32, 19.35], .91, -2.06, -1.15, 'onion_dark',
         {'up': 'onion_green'})
    beam('onion_side_leaf', 'staff', [-4.93, 14.30], [-6.53, 18.13], .99, -2.02, -1.16, 'onion_green',
         {'south': 'onion_dark', 'east': 'onion_dark'})

    def platform_ring(y, width, depth, trim):
        return [(-width+trim,y,-depth), (width-trim,y,-depth),
                (width,y,-depth+trim), (width,y,depth-trim),
                (width-trim,y,depth), (-width+trim,y,depth),
                (-width,y,depth-trim), (-width,y,-depth+trim)]

    loft('hover_platform', 'platform', [
        platform_ring(1.35, 2.35, 1.65, .23),
        platform_ring(1.19, 2.47, 1.76, .25),
        platform_ring(.98, 2.47, 1.76, .25),
        platform_ring(.82, 2.47, 1.76, .25),
        platform_ring(.56, 2.04, 1.42, .21),
    ], [('platform_deck',)*8, ('ink',)*8, ('soul_light',)*8, ('fold',)*8], 'platform_deck', 'ink')
    cube('wisp_core', 'wisp', [-.32, .43, -.40], [.32, .60, .18], 'soul_light')
    cube('wisp_tip', 'wisp', [-.28, .35, -.36], [-.04, .43, -.07], 'cyan')

    textures = []
    (HERE / 'textures').mkdir(exist_ok=True)
    for material, color in PALETTE.items():
        relative = f'textures/{material}.png'
        (HERE / relative).write_bytes(swatch(color))
        textures.append({'name': material, 'path': relative, 'width': 8, 'height': 8,
                         'uuid': identifier('material/' + material)})
    groups = [{'name': name, 'uuid': identifier('bone/' + name), 'origin': pivot,
               'rotation': group_rotation.get(name, [0,0,0]),
               'children': members[name], 'isOpen': name == 'root'} for name, pivot in pivots.items()]
    animations = original['animations']
    for anim in animations:
        anim['uuid'] = identifier('animation/' + anim['name'])
        anim['animators'] = {identifier('bone/' + track['name']): track for track in anim['animators'].values()
                             if track['name'] in pivots}
        for track in anim['animators'].values():
            for key in track['keyframes']:
                key['uuid'] = identifier(f"{anim['name']}/{track['name']}/{key['channel']}/{key['time']}")
                if key['channel'] == 'rotation' and track['name'] in ('arm_left', 'arm_right', 'staff'):
                    # Reflection across X keeps the X angle and reverses Y and Z.
                    data = key['data_points'][0]
                    for axis in ('y', 'z'):
                        data[axis] = str(-float(data[axis]))

    plan = {'name': 'Soul Shaman - Hatsune Miku', 'model_identifier': 'soulcatcherplus:soul_shaman_miku',
            'model_format': 'free', 'format_version': '5.0',
            'target_size': {'blockbench_units': [17, 26, 8], 'minecraft_blocks': [1.0625, 1.625, .5],
                            'fit_policy': 'Match the resting Soul Shaman envelope including the leek and platform.'},
            'texture_width': 8, 'texture_height': 8, 'textures': textures, 'cubes': cubes, 'meshes': meshes,
            'groups': groups, 'hierarchy': hierarchy, 'animations': animations}
    write_json(HERE / 'plan.json', plan)
    converter = importlib.util.spec_from_file_location('blockbench_bridge', BRIDGE)
    bridge = importlib.util.module_from_spec(converter)
    converter.loader.exec_module(bridge)
    # The bridge converts cuboids; add the native loft meshes to its output before writing the hierarchy.
    cube_names = {part['name'] for part in cubes}
    cube_groups = [dict(group, children=[part for part in group['children'] if part in cube_names])
                   for group in groups]
    model, warnings = bridge.convert(dict(plan, groups=cube_groups), texture_ref_mode='index')
    if warnings:
        raise ValueError(warnings)
    model['elements'].extend(meshes)

    by_group = {group['name']: group for group in model['groups']}
    by_cube = {part['name']: part['uuid'] for part in model['elements']}

    def outliner(name):
        return {'uuid': by_group[name]['uuid'], 'isOpen': name == 'root',
                'children': [by_cube[part] for part in members[name]]
                + [outliner(child) for child in hierarchy.get(name, [])]}

    model['outliner'] = [outliner('root')]
    for group in model['groups']:
        group.update(rotation=group_rotation.get(group['name'], [0,0,0]), export=True, visibility=True)
    for texture in model['textures']:
        png = (HERE / texture['relative_path']).read_bytes()
        texture['source'] = 'data:image/png;base64,' + base64.b64encode(png).decode('ascii')
    write_json(HERE / 'soul_shaman_miku.bbmodel', model)


if __name__ == '__main__':
    main()
