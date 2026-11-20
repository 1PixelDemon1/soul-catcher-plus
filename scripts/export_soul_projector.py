"""Export the validated projector plans to native block assets and localized UI strings."""
from pathlib import Path
import json
import shutil

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'src/main/resources/assets/soulcatcherplus'
DATA = ROOT / 'src/main/resources/data/soulcatcherplus'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


for skin, name, body, top, ingredient in (
    ('dark', 'soul_projector', 'polished_blackstone', 'polished_blackstone', 'polished_blackstone'),
    ('light', 'quartz_soul_projector', 'quartz_block_side', 'quartz_block_top', 'quartz_block'),
):
    source = ROOT / 'modeling/soul-projector' / skin
    plan = json.loads((source / 'plan.json').read_text(encoding='utf-8'))
    textures = {'body': 'minecraft:block/'+body, 'top': 'minecraft:block/'+top, 'particle': 'minecraft:block/'+body}
    for material in ('lens', 'light'):
        target = ASSETS / 'textures/block/soul_projector' / f'{material}.png'
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source / f'textures/{material}.png', target)
        textures[material] = f'soulcatcherplus:block/soul_projector/{material}'
    elements = []
    for cube in plan['cubes']:
        faces = {}
        for side, face in cube['faces'].items():
            texture = plan['textures'][face['texture']]
            faces[side] = {'texture': '#'+texture['name'], 'uv': [value*16/texture['width'] for value in face['uv']]}
        elements.append({'from': cube['from'], 'to': cube['to'], 'faces': faces})
    write(ASSETS / 'models/block' / f'{name}.json', {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements})
    write(ASSETS / 'blockstates' / f'{name}.json', {'variants': {'': {'model': 'soulcatcherplus:block/'+name}}})
    write(ASSETS / 'models/item' / f'{name}.json', {'parent': 'soulcatcherplus:block/'+name})
    write(DATA / 'loot_tables/blocks' / f'{name}.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'soulcatcherplus:'+name}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    recipe_id = f'soulcatcherplus:{name}'
    casing = f'minecraft:{ingredient}'
    write(DATA / 'recipes' / f'{name}.json', {
        'type': 'minecraft:crafting_shaped',
        'pattern': ['KSK', 'KPK'],
        'key': {'K': {'item': casing}, 'S': {'item': 'minecraft:glass_pane'}, 'P': {'item': 'minecraft:soul_sand'}},
        'result': {'item': recipe_id, 'count': 1},
    })
    write(DATA / 'advancements/recipes' / f'{name}.json', {
        'parent': 'minecraft:recipes/root',
        'criteria': {
            'has_casing': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'item': casing}]}},
            'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': recipe_id}},
        },
        'requirements': [['has_casing', 'has_the_recipe']],
        'rewards': {'recipes': [recipe_id]},
    })

for language, values in {
    'en_us': {'block.soulcatcherplus.soul_projector': 'Soul Projector', 'block.soulcatcherplus.quartz_soul_projector': 'Quartz Soul Projector',
        'scroll': 'Soul scroll', 'scale': 'Size: %s%%', 'angle': 'Angle: %s°', 'opacity': 'Opacity: %s%%',
        'color': 'Color', 'rotate': 'Rotate', 'stop': 'Stop', 'reset': 'Reset'},
    'ru_ru': {'block.soulcatcherplus.soul_projector': 'Проектор души', 'block.soulcatcherplus.quartz_soul_projector': 'Кварцевый проектор души',
        'scroll': 'Свиток', 'scale': 'Размер: %s%%', 'angle': 'Угол: %s°', 'opacity': 'Непрозрачность: %s%%',
        'color': 'Цвет', 'rotate': 'Вращать', 'stop': 'Стоп', 'reset': 'Сброс'},
}.items():
    path = ASSETS / 'lang' / f'{language}.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    data.update({key if key.startswith('block.') else 'screen.soulcatcherplus.projector_'+key: value for key, value in values.items()})
    write(path, data)
print('Exported both projector models, loot tables, recipes, recipe unlocks and UI translations.')
