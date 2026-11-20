"""Export the altar's Minecraft model, loot table, and crafting recipe."""
import json
from pathlib import Path
import shutil

SOURCE = Path(__file__).resolve().parent
RESOURCES = SOURCE.parents[1] / 'src/main/resources'
ASSETS = RESOURCES / 'assets/soulcatcherplus'
DATA = RESOURCES / 'data/soulcatcherplus'
NAME = 'soul_transfer_altar'
ITEM_ID = f'soulcatcherplus:{NAME}'
BLOCK_MODEL = f'soulcatcherplus:block/{NAME}'


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')


def main():
    plan = json.loads((SOURCE / 'plan.json').read_text(encoding='utf-8'))
    textures = {'blackstone': 'minecraft:block/polished_blackstone', 'gold': 'minecraft:block/gold_block',
                'particle': 'minecraft:block/polished_blackstone'}
    elements = []
    for cube in plan['cubes']:
        faces = {}
        for side, face in cube['faces'].items():
            source = plan['textures'][face['texture']]
            name = source['name']
            if name not in textures:
                path = ASSETS / f'textures/block/{NAME}/{name}.png'
                path.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(SOURCE / source['path'], path)
                textures[name] = f'{BLOCK_MODEL}/{name}'
            dimensions = (source['width'], source['height'])
            faces[side] = {'texture': '#' + name,
                           'uv': [v * 16 / dimensions[i % 2] for i, v in enumerate(face['uv'])]}
        elements.append({key: [v + offset for v, offset in zip(cube[key], (8, 0, 8))]
                         for key in ('from', 'to')} | {'faces': faces})
    write(ASSETS / f'models/block/{NAME}.json',
          {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements})
    write(ASSETS / f'models/item/{NAME}.json', {'parent': BLOCK_MODEL})
    write(ASSETS / f'blockstates/{NAME}.json',
          {'variants': {f'facing={facing}': {'model': BLOCK_MODEL, 'y': angle}
                        for facing, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]}})
    write(DATA / f'loot_tables/blocks/{NAME}.json', {'type': 'minecraft:block',
          'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': ITEM_ID}],
                     'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    write(DATA / f'recipes/{NAME}.json', {
        'type': 'minecraft:crafting_shaped',
        'pattern': [' P ', 'BSB', 'GBG'],
        'key': {
            'P': {'item': 'minecraft:paper'},
            'B': {'item': 'minecraft:polished_blackstone'},
            'S': {'item': 'minecraft:soul_sand'},
            'G': {'item': 'minecraft:gold_ingot'},
        },
        'result': {'item': ITEM_ID, 'count': 1},
    })
    write(DATA / f'advancements/recipes/{NAME}.json', {
        'parent': 'minecraft:recipes/root',
        'criteria': {
            'has_blackstone': {'trigger': 'minecraft:inventory_changed',
                'conditions': {'items': [{'item': 'minecraft:polished_blackstone'}]}},
            'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': ITEM_ID}},
        },
        'requirements': [['has_blackstone', 'has_the_recipe']],
        'rewards': {'recipes': [ITEM_ID]},
    })


if __name__ == '__main__':
    main()
