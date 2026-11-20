"""Export the approved cuboid pylon, with the scroll separate from the static block."""
from pathlib import Path
import json
import shutil

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / 'modeling/soul-pylon'
ASSETS = ROOT / 'src/main/resources/assets/soulcatcherplus'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def main():
    plan = json.loads((SOURCE / 'plan.json').read_text(encoding='utf-8'))
    texture_map = {}
    for texture in plan['textures']:
        name = texture['name']
        if name in ('stone', 'stone_mid', 'stone_edge'):
            texture_map[name] = 'minecraft:block/polished_blackstone'
        elif name.startswith('gold'):
            texture_map[name] = 'minecraft:block/gold_block'
        else:
            target = ASSETS / 'textures/block/soul_pylon' / f'{name}.png'
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(SOURCE / texture['path'], target)
            texture_map[name] = f'soulcatcherplus:block/soul_pylon/{name}'
    texture_map['particle'] = 'minecraft:block/polished_blackstone'
    scroll_names = set(next(g['children'] for g in plan['groups'] if g['name'] == 'inserted_scroll'))
    parts = {'soul_pylon': [], 'soul_pylon_scroll': []}
    for cube in plan['cubes']:
        element = {'from': [cube['from'][0]+8, cube['from'][1], cube['from'][2]+8],
                   'to': [cube['to'][0]+8, cube['to'][1], cube['to'][2]+8], 'faces': {}}
        for side, face in cube['faces'].items():
            texture = plan['textures'][face['texture']]
            width, height = texture['width'], texture['height']
            uv = [value * 16 / (width if index % 2 == 0 else height)
                  for index, value in enumerate(face['uv'])]
            element['faces'][side] = {'uv': uv, 'texture': '#' + texture['name']}
        parts['soul_pylon_scroll' if cube['name'] in scroll_names else 'soul_pylon'].append(element)
    for name, elements in parts.items():
        write(ASSETS / 'models/block' / f'{name}.json',
              {'parent': 'minecraft:block/block', 'textures': texture_map, 'elements': elements})
    write(ASSETS / 'models/block/soul_pylon_boss_scroll.json', {
        'parent': 'soulcatcherplus:block/soul_pylon_scroll',
        'textures': {'rune': 'minecraft:block/purple_concrete'}})
    write(ASSETS / 'blockstates/soul_pylon.json', {'variants': {
        f'facing={direction}': {'model': 'soulcatcherplus:block/soul_pylon', 'y': angle}
        for direction, angle in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})
    write(ASSETS / 'models/item/soul_pylon.json', {'parent': 'soulcatcherplus:block/soul_pylon'})
    write(ROOT / 'src/main/resources/data/soulcatcherplus/recipes/soul_pylon.json', {
        'type': 'minecraft:crafting_shaped', 'pattern': ['GPG', 'P P', 'GPG'],
        'key': {'G': {'item': 'minecraft:gold_nugget'}, 'P': {'item': 'minecraft:polished_blackstone'}},
        'result': {'item': 'soulcatcherplus:soul_pylon', 'count': 1}})
    write(ROOT / 'src/main/resources/data/soulcatcherplus/advancements/recipes/soul_pylon.json', {
        'parent': 'minecraft:recipes/root',
        'criteria': {
            'has_polished_blackstone': {'trigger': 'minecraft:inventory_changed',
                'conditions': {'items': [{'item': 'minecraft:polished_blackstone'}]}},
            'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked',
                'conditions': {'recipe': 'soulcatcherplus:soul_pylon'}}},
        'requirements': [['has_polished_blackstone', 'has_the_recipe']],
        'rewards': {'recipes': ['soulcatcherplus:soul_pylon']}})
    write(ROOT / 'src/main/resources/data/soulcatcherplus/loot_tables/blocks/soul_pylon.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [
            {'type': 'minecraft:item', 'name': 'soulcatcherplus:soul_pylon'}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    for lang, entries in {
        'en_us': {
            'block.soulcatcherplus.soul_pylon': 'Soul Pylon',
            'message.soulcatcherplus.pylon_unsupported': 'This soul cannot guard a pylon.',
            'screen.soulcatcherplus.pylon_cage': 'Soul cage',
            'screen.soulcatcherplus.pylon_scroll': 'Soul scroll',
            'screen.soulcatcherplus.pylon_insert': 'Place a soul cage and a scroll',
            'screen.soulcatcherplus.pylon_energy': 'Soul energy: %s / %s',
            'screen.soulcatcherplus.pylon_ready': 'Guardians are summoned automatically',
            'screen.soulcatcherplus.pylon_needs_items': 'Both items are needed to summon',
        },
        'ru_ru': {
            'block.soulcatcherplus.soul_pylon': 'Пилон душ',
            'message.soulcatcherplus.pylon_unsupported': 'Эта душа не может охранять пилон.',
            'screen.soulcatcherplus.pylon_cage': 'Клетка душ',
            'screen.soulcatcherplus.pylon_scroll': 'Свиток души',
            'screen.soulcatcherplus.pylon_insert': 'Поместите клетку и свиток',
            'screen.soulcatcherplus.pylon_energy': 'Энергия душ: %s / %s',
            'screen.soulcatcherplus.pylon_ready': 'Защитник призывается автоматически',
            'screen.soulcatcherplus.pylon_needs_items': 'Для призыва нужны оба предмета',
        },
    }.items():
        path = ASSETS / 'lang' / f'{lang}.json'
        content = json.loads(path.read_text(encoding='utf-8'))
        for key in ('pylon_inserted', 'pylon_status', 'pylon_guarding', 'pylon_waiting'):
            content.pop('message.soulcatcherplus.' + key, None)
        content.update(entries)
        write(path, content)
    print('Exported pylon block, regular/boss scroll models, item, recipe, recipe unlock, loot and translations.')


if __name__ == '__main__':
    main()
