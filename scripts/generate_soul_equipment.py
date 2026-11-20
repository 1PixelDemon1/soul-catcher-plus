# SPDX-License-Identifier: MIT
"""Generate original pixel sprites and the recipes/model assets for soul equipment."""
from pathlib import Path
import json
import re
import struct
import zlib

RES = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = RES / 'assets/soulcatcherplus'
DATA = RES / 'data/soulcatcherplus'
FURNACE = 'soul_fueled_furnace'
BOW = 'soul_energy_bow'
HOE = 'soulbound_hoe'
TOTEM = 'soul_sacrifice_totem'
COLORS = {
    '.': '00000000', '#': '18232AFF', 's': '3E5059FF', 'm': '627881FF', 'l': 'A3B5B9FF',
    'w': '60483DFF', 't': '94725AFF',
    'n': '1E354EFF', 'u': '2B5372FF', 'v': '437EA0FF',
    'k': '82766AFF', 'i': 'BDB09BFF', 'j': 'F1DFC0FF',
    'o': '554B41FF', 'e': 'D8C7AAFF', 'Y': 'F4E79CFF',
    'x': '110F14FF', 'q': '211E25FF', 'd': '29262CFF',
    'p': '353139FF', 'f': '403B44FF', 'h': '4C4650FF',
    'b': '106479FF', 'c': '26ACBBFF', 'a': '66E0DFFF', '+': 'C6FAEFFF',
}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def png(path, rows):
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    assert len(rows) == 16 and all(len(row) == 16 for row in rows)
    raw = b''.join(b'\0' + b''.join(bytes.fromhex(COLORS[pixel]) for pixel in row) for row in rows)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16,16,8,6,0,0,0))
                     + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))


# Keep the vanilla limbs and string. The drop narrows as its tip follows the nock.
BOW_FRAMES = (
    (
        '................', '...........####.', '........###ikij#', '......##ivj####.',
        '.....#uv###..v..', '....#vnu....v...', '...#vnu....u....', '...#un....u.....',
        '..#v#....u......', '..#u#...u.......', '..#v#..u........', '.#k#..u.........',
        '.#j#.u..........', '.#iku...........', '.#j#............', '..#.............',
    ),
    (
        '................', '...........####.', '........###ikij#', '......##ivj####.',
        '....bcvu###...v.', '....#cac.....v..', '...#vnaac....u..', '...#uc++ac..u...',
        '..#v#..cac.u....', '..#u#....acu....', '..#v#.....c.....', '.#k#....uu......',
        '.#j#...u........', '.#ik.uu.........', '.#j#u...........', '..#.............',
    ),
    (
        '................', '............###.', '........####kij#', '......##ivji###.',
        '....bcvu####..v.', '....#cac......v.', '...#vnac.....v..', '...#una+ac...u..',
        '..#v#..cac..u...', '..#u#...ac..u...', '..#v#....cc.....', '..#u#.....c.....',
        '.#j#....uu......', '.#ik..uu........', '.#j#uu..........', '..#.............',
    ),
    (
        '................', '................', '........#######.', '......##ivjvkij#',
        '....bcvu#######.', '....#ca.......v.', '...#vnac......v.', '...#una+c....v..',
        '..#v#..cac...u..', '..#u#...ac...u..', '..#v#....cc..u..', '..#u#.....cc....',
        '..#j#......c....', '..#ik..uuuu.....', '..#j#uu.........', '...#............',
    ),
)
BOLT_PIXELS = (
    '................', '................', '................', '................',
    '..........bb....', '.......bbbccbb..', '....bbbcca+aacb.', '.bbbccaa+++++acb',
    '.bbbcccaa++++acb', '.....bbbccaaacb.', '........bbccbb..', '..........bb....',
    '................', '................', '................', '................',
)

ITEM_DISPLAY = {
    'ground': {'translation': [0, 2, 0], 'scale': [0.5] * 3},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7]},
    'fixed': {'rotation': [0, 180, 0]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68] * 3},
    'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68] * 3},
}
TOTEM_DISPLAY = {
    **ITEM_DISPLAY,
    'firstperson_lefthand': ITEM_DISPLAY['firstperson_righthand'],
    'thirdperson_righthand': {'translation': [0, 3, 1], 'scale': [0.55] * 3},
}
HOE_DISPLAY = {
    **ITEM_DISPLAY,
    'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 4, 0.5], 'scale': [0.85] * 3},
    'thirdperson_lefthand': {'rotation': [0, 90, -55], 'translation': [0, 4, 0.5], 'scale': [0.85] * 3},
}
BOW_DISPLAY = {
    **ITEM_DISPLAY,
    'thirdperson_righthand': {'rotation': [-80, 260, -40], 'translation': [-1, -2, 2.5], 'scale': [0.9] * 3},
    'thirdperson_lefthand': {'rotation': [-80, -280, 40], 'translation': [-1, -2, 2.5], 'scale': [0.9] * 3},
}


def sprite_mesh(rows):
    # Match the generated item's one-texel thickness. Keep face UVs inside opaque texels.
    elements = []
    for row, pixels in enumerate(rows):
        v = row + 0.5
        for span in re.finditer(r'[^.]+', pixels):
            left, right = span.span()
            u0, u1 = left + 0.0625, right - 0.0625
            faces = {
                'north': [u1, v, u0, v],
                'south': [u0, v, u1, v],
                'west': [left + 0.5, v, left + 0.5, v],
                'east': [right - 0.5, v, right - 0.5, v],
                'up': [u0, v, u1, v],
                'down': [u0, v, u1, v],
            }
            elements.append({
                'from': [left, 15 - row, 7.5],
                'to': [right, 16 - row, 8.5],
                'faces': {side: {'uv': uv, 'texture': '#layer0'} for side, uv in faces.items()},
            })
    return elements


def item_models():
    designs = {
        TOTEM: [
            '................', '.....ookkoo.....', '....oijjeiio....', '....ojejjeio....',
            '....oieiieio....', '....okbnnbko....', '....okaooako....', '....okieeiko....',
            '.oieiknccnkieio.', '.oikinbjjbnikio.', '..ookcjYYjckoo..', '....onbjjbno....',
            '....oknccnko....', '.....onkkno.....', '.....okieko.....', '......oooo......',
        ],
        HOE: [
            '................', '.......ooo......', '......ojeeo.....', '.......##ieonu..',
            '.........#iiv#..', '..........nei#..', '.........nu##...', '........nv#.....',
            '.......nu#......', '......nv#.......', '.....nu#........', '....nv#.........',
            '...nu#..........', '..nv#...........', '..##............', '................',
        ],
    }
    for name, display in ((TOTEM, TOTEM_DISPLAY), (HOE, HOE_DISPLAY)):
        pixels = designs[name]
        png(ASSETS / f'textures/item/{name}.png', pixels)
        texture = f'soulcatcherplus:item/{name}'
        write(ASSETS / f'models/item/{name}.json', {
            'gui_light': 'front', 'display': display,
            'textures': {'layer0': texture, 'particle': texture},
            'elements': sprite_mesh(pixels),
        })
    png(ASSETS / 'textures/entity/soul_bolt.png', BOLT_PIXELS)
    for frame, pixels in enumerate(BOW_FRAMES):
        name = BOW if frame == 0 else f'{BOW}_pull_{frame}'
        png(ASSETS / f'textures/item/{name}.png', pixels)
        texture = f'soulcatcherplus:item/{name}'
        model = {'textures': {'layer0': texture, 'particle': texture}}
        if frame == 0:
            model.update({'gui_light': 'front', 'display': BOW_DISPLAY})
            model['overrides'] = [
                {'predicate': {'pulling': 1, 'pull': threshold}, 'model': f'soulcatcherplus:item/{BOW}_pull_{i}'}
                for i, threshold in enumerate((0, .65, .9), 1)]
        else:
            model['parent'] = f'soulcatcherplus:item/{BOW}'
        model['elements'] = sprite_mesh(pixels)
        write(ASSETS / f'models/item/{name}.json', model)


def furnace_model():
    front = (
        'fhhfhffhhffhfhhp', 'phpppffppffpphqd', 'pfphhhhhhhhhhfqd', 'pfhhqxxxxxxqhfqd',
        'pfhqxxxxxxxxqhqd', 'pfqxxxxxxxxxxhqd', 'pfqqdqdqdqdqdhqd', 'pfhhhhhhhhhhhhqd',
        'pfhpxxxxxxxxqhqd', 'pffqxxxxxxxxqhqd', 'pfhqxxxxxxxxqfqd', 'pfhqxxxxxxxxqhqd',
        'pfhqxxxxxxxxqfqd', 'pfhqxxxxxxxxqhqd', 'pfhhhhhhhhhhhhqd', 'pqqdqpqddqqddqqq',
    )
    inside = ['x' * 16 for _ in range(16)]
    inside[13] = 'xxxxqdqdqdqdxxxx'
    fire = list(front)
    fire[3:7] = (
        'pfhhqxxxcxxqhfqd', 'pfhqxxcxacxxqhqd', 'pfqxxcb+aaccxhqd', 'pfqqbca+accqbhqd',
    )
    png(ASSETS / f'textures/block/{FURNACE}_front.png', front)
    png(ASSETS / f'textures/block/{FURNACE}_shadow.png', inside)
    png(ASSETS / f'textures/block/{FURNACE}_fire.png', fire)

    # Only the lower cage slot is recessed. The upper firebox is painted onto the face.
    walls = []

    def cladding(lo, hi, face, axis, plane, base_texture):
        # Split the face into non-overlapping patches so trim sits flush with the stone.
        across, along = [dimension for dimension in range(3) if dimension != axis]
        for bottom, top, corner_width in ((0, 1, 3), (1, 3, 1), (3, 13, 0), (13, 15, 1), (15, 16, 3)):
            strips = ((0, corner_width, '#gold'), (corner_width, 16 - corner_width, base_texture),
                      (16 - corner_width, 16, '#gold'))
            for left, right, texture in strips:
                start, end = list(lo), list(hi)
                start[across], end[across] = max(lo[across], left), min(hi[across], right)
                start[along], end[along] = max(lo[along], bottom), min(hi[along], top)
                if start[across] >= end[across] or start[along] >= end[along]:
                    continue
                start[axis] = end[axis] = plane
                patch = {'texture': texture}
                if texture == '#gold':
                    patch['uv'] = [5, 5, 11, 11]  # Same part of the gold texture as the pylon.
                walls.append({'from': start, 'to': end, 'faces': {face: patch}})

    def cuboid(lo, hi):
        faces = {}
        for face, axis, positive in (('north', 2, False), ('south', 2, True), ('west', 0, False), ('east', 0, True),
                                     ('up', 1, True), ('down', 1, False)):
            plane = (hi if positive else lo)[axis]
            if plane == (16 if positive else 0):
                cladding(lo, hi, face, axis, plane, '#front' if face == 'north' else '#stone')
            else:
                faces[face] = {'texture': '#inside' if face == 'north' else '#stone'}
        walls.append({'from': lo, 'to': hi, 'faces': faces})

    cuboid([0, 0, 5], [16, 16, 16])
    cuboid([0, 0, 0], [16, 2, 5])
    cuboid([0, 2, 0], [4, 8, 5])
    cuboid([12, 2, 0], [16, 8, 5])
    cuboid([0, 8, 0], [16, 16, 5])

    stone = 'minecraft:block/polished_blackstone'
    for lit in (False, True):
        name = FURNACE + ('_lit' if lit else '')
        facade = 'fire' if lit else 'front'
        write(ASSETS / f'models/block/{name}.json', {
            'parent': 'minecraft:block/block', 'ambientocclusion': False,
            'textures': {'stone': stone, 'particle': stone,
                         'gold': 'minecraft:block/gold_block',
                         'front': f'soulcatcherplus:block/{FURNACE}_{facade}',
                         'inside': f'soulcatcherplus:block/{FURNACE}_shadow'},
            'elements': walls,
        })
    write(ASSETS / f'blockstates/{FURNACE}.json', {'variants': {
        f'facing={side},lit={str(lit).lower()}': {'model': f'soulcatcherplus:block/{FURNACE}' + ('_lit' if lit else ''), 'y': angle}
        for side,angle in (('north',0),('east',90),('south',180),('west',270)) for lit in (False,True)}})
    write(ASSETS / f'models/item/{FURNACE}.json', {'parent': f'soulcatcherplus:block/{FURNACE}'})
    write(DATA / f'loot_tables/blocks/{FURNACE}.json', {'type': 'minecraft:block', 'pools': [
        {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'soulcatcherplus:{FURNACE}'}],
         'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})


def recipes():
    for name, pattern, ingredients in (
        (FURNACE, ['GPG', 'PFP', 'GSG'],
         {'F': 'furnace', 'G': 'gold_nugget', 'P': 'polished_blackstone', 'S': 'soul_sand'}),
        (TOTEM, [' N ', 'BTB'], {'T': 'totem_of_undying', 'N': 'nether_star', 'B': 'bone'}),
    ):
        write(DATA / f'recipes/{name}.json', {
            'type': 'minecraft:crafting_shaped', 'pattern': pattern,
            'key': {symbol: {'item': f'minecraft:{item}'} for symbol, item in ingredients.items()},
            'result': {'item': f'soulcatcherplus:{name}', 'count': 1},
        })
    for name, base, addition in ((HOE, 'iron_hoe', 'bone_block'), (BOW, 'bow', 'soul_torch')):
        write(DATA / f'recipes/{name}.json', {
            'type': 'minecraft:smithing',
            'base': {'item': f'minecraft:{base}'}, 'addition': {'item': f'minecraft:{addition}'},
            'result': {'item': f'soulcatcherplus:{name}'},
        })
    for name, vanilla in (
        (FURNACE,'furnace'), (TOTEM,'totem_of_undying'), (HOE,'iron_hoe'), (BOW,'bow'),
    ):
        recipe = f'soulcatcherplus:{name}'
        write(DATA / f'advancements/recipes/{name}.json', {'parent':'minecraft:recipes/root',
            'criteria': {'has_tool': {'trigger':'minecraft:inventory_changed',
                'conditions': {'items': [{'item':'minecraft:'+vanilla}]}},
                'has_the_recipe': {'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':recipe}}},
            'requirements': [['has_tool','has_the_recipe']], 'rewards': {'recipes':[recipe]}})


def localize():
    localized = {
        'en_us': ('Soul-Fueled Furnace', 'Soul Sacrifice Totem of Undying', 'Soulbound Hoe', 'Soul Energy Bow',
                  'On revival: %s souls', 'To fertilize: %s souls', 'Per shot: %s souls', 'Soul energy: %s / %s'),
        'ru_ru': ('Печь душ', 'Тотем жертвенной души', 'Мотыга душ', 'Лук душ',
                  'За спасение: %s душ', 'За удобрение: %s душ', 'За выстрел: %s душ', 'Энергия душ: %s / %s'),
    }
    keys = (f'block.soulcatcherplus.{FURNACE}', f'item.soulcatcherplus.{TOTEM}',
            f'item.soulcatcherplus.{HOE}', f'item.soulcatcherplus.{BOW}',
            f'item.soulcatcherplus.{TOTEM}.cost', f'item.soulcatcherplus.{HOE}.cost',
            f'item.soulcatcherplus.{BOW}.cost', 'screen.soulcatcherplus.soul_furnace_energy')
    for locale, values in localized.items():
        path = ASSETS / f'lang/{locale}.json'
        entries = json.loads(path.read_text(encoding='utf-8'))
        entries.update(zip(keys, values))
        write(path, entries)


def main():
    item_models()
    furnace_model()
    recipes()
    localize()


if __name__ == '__main__':
    main()
