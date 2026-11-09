# SPDX-License-Identifier: MIT
"""Author the optional Patchouli 1.16.5 guide and its two localizations.

Edit this catalog, then regenerate. Recipe pages reference real recipe IDs.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / 'src/main/resources'
BOOK = RES / 'data/soulcatcherplus/patchouli_books/soul_guide'
LANG = RES / 'assets/soulcatcherplus/lang'
translations = {'en_us': {}, 'ru_ru': {}}
FONT_ADVANCES = json.loads((Path(__file__).with_name('soul_guide_font.json')).read_text(encoding='utf-8'))['advances']
# Patchouli 53.3: 116 px pages, 156 px height, first-page text begins at 22 px.
# Leave room for the title, footer and small differences in key labels/resource packs.
TEXT_WIDTH = 110
MAX_TEXT_LINES = 12


def visible_text(text):
    def markup(match):
        command = match.group(1)
        if command.startswith('k:'):
            return 'Right Shift'  # Reserve more space than the default O/R key labels.
        return {'br2': '\n\n', 'br': '\n'}.get(command, '')
    return re.sub(r'\$\(([^)]*)\)', markup, text).replace('%%', '%')


def text_lines(text):
    lines = 0
    for paragraph in visible_text(text).split('\n'):
        used = 0
        lines += 1
        for word in paragraph.split():
            width = sum(FONT_ADVANCES.get(char, 8) for char in word)
            if width > TEXT_WIDTH:
                raise ValueError(f'Word exceeds the guide page width: {word}')
            if used and used + 4 + width > TEXT_WIDTH:
                lines += 1
                used = width
            else:
                used += (4 if used else 0) + width
    return lines


def paginate(text):
    chunks = []
    for paragraph in text.split('$(br2)'):
        chunk = ''
        for word in paragraph.split():
            candidate = (chunk + ' ' + word).strip()
            if chunk and text_lines(candidate) > MAX_TEXT_LINES:
                chunks.append(chunk)
                chunk = word
            else:
                chunk = candidate
        if chunk:
            chunks.append(chunk)
    pages = []
    current = ''
    for chunk in chunks:
        candidate = current + '$(br2)' + chunk if current else chunk
        if current and text_lines(candidate) > MAX_TEXT_LINES:
            pages.append(current)
            current = chunk
        else:
            current = candidate
    if current:
        pages.append(current)
    return pages


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def tr(key, en, ru):
    key = 'guide.soulcatcherplus.' + key
    # Patchouli passes i18n text through String.format, even without arguments.
    translations['en_us'][key] = en.replace('%', '%%')
    translations['ru_ru'][key] = ru.replace('%', '%%')
    return key


def category(path, name, description, icon, order):
    data = {'name': tr(f'{path}.name', *name), 'description': tr(f'{path}.description', *description),
            'icon': icon, 'sortnum': order}
    for locale in translations:
        write(BOOK / f'{locale}/categories/{path}.json', data)


def crafting(*recipes, smithing=False):
    return {'type': 'smithing' if smithing else 'crafting',
            **{('recipe' if i == 0 else 'recipe2'): 'soulcatcherplus:' + r for i, r in enumerate(recipes)}}


def entry(path, name, icon, order, pages):
    key = path.replace('/', '.')
    title = tr(key + '.name', *name)
    for language_index, locale in enumerate(translations):
        output = []
        for page in pages:
            if isinstance(page, dict):
                output.append(page)
                continue
            for text in paginate(page[language_index]):
                text_key = f'guide.soulcatcherplus.{key}.page{len(output)}'
                translations[locale][text_key] = text.replace('%', '%%')
                output.append({'type': 'text', 'title': title, 'text': text_key})
        write(BOOK / f'{locale}/entries/{path}.json', {
            'name': title, 'category': 'soulcatcherplus:' + path.split('/')[0],
            'icon': icon, 'sortnum': order, 'pages': output})


def main():
    write(BOOK / 'book.json', {
        'name': tr('name', "Soul Catcher's Guide", 'Путеводитель ловца душ'),
        'landing_text': tr('landing',
            'Mark an enemy, claim its soul and build your collection of spectral companions.$(br2)This guide covers equipment, orders, soul energy and the Forgotten Souls.',
            'Пометь врага, поймай его душу и собери отряд призрачных напарников.$(br2)Здесь описаны снаряжение, приказы, энергия душ и Забытые души.'),
        'subtitle': tr('subtitle', 'Soul Catcher Plus', 'Soul Catcher Plus'),
        'version': '0', 'i18n': True, 'show_progress': False, 'show_toasts': False,
        'pause_game': True, 'use_blocky_font': True,
        'creative_tab': 'soulcatcherplus', 'model': 'soulcatcherplus:soul_guide',
        'book_texture': 'patchouli:textures/gui/book_cyan.png',
        'header_color': '164F59', 'link_color': '006B83', 'link_hover_color': '159CA8'})

    category('basics', ('Soul catching', 'Ловля душ'),
             ('Capture, energy, collection and commands.', 'Захват, энергия, коллекция и управление.'),
             'soulcatcherplus:soul_icon', 0)
    category('equipment', ('Equipment & recipes', 'Снаряжение и рецепты'),
             ('Scythes, cages and this guide. All recipes are linked to their in-game definitions.',
              'Косы, клетки и эта книга. Рецепты связаны с их игровыми определениями.'),
             'soulcatcherplus:soul_scythe', 1)
    category('enchantments', ('Enchantments', 'Зачарования'),
             ('Increase energy, cage capacity and companion slots.', 'Больше энергии, вместимости клеток и слотов напарников.'),
             'minecraft:enchanted_book', 2)
    category('forgotten', ('Forgotten Souls', 'Забытые души'),
             ('Rare companions with their own roles.', 'Редкие напарники с особыми умениями.'),
             'soulcatcherplus:forgotten_soul_scroll', 3)

    entry('basics/capture', ('Your first soul', 'Первая душа'), 'soulcatcherplus:soul_scythe', 0, [
        ('First craft a scythe and a soul cage. Carry the cage in your offhand or hotbar.$(br2)In Harvest mode, right-click an enemy with the scythe to mark it. The mark lasts three minutes.',
         'Сначала сделай косу и клетку душ. Держи клетку во второй руке или хотбаре.$(br2)В режиме сбора нажми ПКМ косой по врагу, чтобы пометить его. Метка действует три минуты.'),
        ('Defeat the marked enemy before the mark expires. A soul remnant appears; right-click it with a scythe to add that form to your collection.$(br2)Only the marking player can collect it. The remnant lasts one minute.',
         'Победи помеченного врага до конца действия метки. Появится остаток души: нажми по нему ПКМ косой, чтобы открыть этот облик.$(br2)Забрать его может только владелец метки. Остаток существует одну минуту.'),
        ('Your own kills and kills credited to your souls award energy. A marked kill can also unlock a companion.$(br2)A known form does not award energy a second time when collected. Some entity types or native abilities may need an external compatibility addon.',
         'За свои убийства и убийства твоими душами ты получаешь энергию. Помеченная цель также может открыть напарника.$(br2)Повторный облик не даёт энергию второй раз при подборе. Некоторым существам может потребоваться аддон совместимости.')])

    entry('basics/energy', ('Soul energy', 'Энергия душ'), 'soulcatcherplus:iron_soul_cage', 1, [
        ('Energy is stored in cage items, while your soul collection belongs to your character.$(br2)A kill yields half the enemy\'s maximum HP, rounded down, with a minimum of 1. Summoning costs twice its maximum HP, rounded up.',
         'Энергия хранится в клетках-предметах, а коллекция душ привязана к персонажу.$(br2)Убийство даёт половину максимального HP врага с округлением вниз, минимум 1. Призыв стоит удвоенное максимальное HP с округлением вверх.'),
        ('The offhand cage takes priority. Otherwise the leftmost hotbar cage is active.$(br2)Summoning spends energy from that cage only: several small cages cannot combine their balances to pay for an expensive soul.',
         'Приоритет у клетки во второй руке, затем у самой левой клетки хотбара.$(br2)Призыв расходует энергию только активной клетки: несколько маленьких клеток не объединяют запасы для дорогой души.'),
        ('Incoming energy fills the offhand cage first, then spills through the hotbar from left to right. Excess is lost if every available cage is full.$(br2)Recall refunds half the companion\'s current HP, rounded down.',
         'Полученная энергия сначала заполняет клетку второй руки, затем клетки хотбара слева направо. Если все заполнены, излишек теряется.$(br2)Отзыв возвращает половину текущего HP напарника с округлением вниз.')])

    entry('basics/collection', ('The collection', 'Коллекция'), 'soulcatcherplus:soul_icon', 2, [
        ('Hold any scythe in either hand and press $(k:key.soulcatcherplus.collection) to open the collection. The default key is O.$(br2)Search by name, scroll the list and use the star to pin favorite creatures to the top.',
         'Держи любую косу в одной из рук и нажми $(k:key.soulcatcherplus.collection), чтобы открыть коллекцию. По умолчанию это O.$(br2)Ищи по имени, прокручивай список и отмечай звёздочкой избранных существ.'),
        ('Different captured appearances unlock separate forms. You may summon only one form of a creature type at a time.$(br2)Souls with at least 150 maximum HP are highlighted purple. Delete removes only the selected form and asks for confirmation.',
         'Разные пойманные варианты открывают отдельные облики. Одновременно можно призвать только один облик одного вида.$(br2)Души с максимальным HP от 150 выделены фиолетовым. Удаление выбранного облика требует подтверждения.'),
        ('Select a form and press Summon. You need an available slot and enough energy in the active cage.$(br2)The color-picker button adjusts your souls\' tint and opacity (10–100%) and can reset them. White tint keeps their original colors.',
         'Выбери облик и нажми «Призвать». Нужны свободный слот и энергия в активной клетке.$(br2)Кнопка выбора цвета меняет оттенок и прозрачность душ (10–100%) и позволяет сбросить настройки. Белый сохраняет исходные цвета.')])

    entry('basics/orders', ('Orders', 'Приказы'), 'soulcatcherplus:diamond_soul_scythe', 3, [
        ('Hold a scythe and hold $(k:key.soulcatcherplus.command_wheel) (default R) to open the wheel.$(br2)Hover a soul, then an order; release the key to issue it. Returning the pointer to the center clears the focused soul. All creatures targets the whole group.',
         'С косой в руке удерживай $(k:key.soulcatcherplus.command_wheel) (по умолчанию R), чтобы открыть кольцо.$(br2)Наведи на душу, затем на приказ; отпусти клавишу. Возврат курсора в центр сбрасывает фокус. «Все существа» выбирает весь отряд.'),
        ('$(thing)Follow$(): accompany the owner and respond to combat.$(br2)$(thing)Aggressive$(): follow while actively seeking nearby hostile mobs.$(br2)$(thing)Wait$(): stay in place and stop fighting.',
         '$(thing)Следовать$(): сопровождать владельца и помогать в бою.$(br2)$(thing)Агрессия$(): следовать и самостоятельно искать рядом враждебных мобов.$(br2)$(thing)Ждать$(): оставаться на месте и прекращать бой.'),
        ('$(thing)Guard$(): defend the point where the order was issued.$(br2)$(thing)Teleport$(): try to move the selected soul or group to a safe place near you.$(br2)$(thing)Recall$(): dismiss and refund half of current HP as energy.',
         '$(thing)Охранять$(): защищать точку, где был отдан приказ.$(br2)$(thing)Телепорт$(): попытаться перенести душу или отряд в безопасное место рядом.$(br2)$(thing)Отозвать$(): убрать душу и вернуть энергию в размере половины текущего HP.'),
        ('To attack one target, switch the scythe to Command mode in the collection, then right-click the enemy.$(br2)The collection also has order buttons and a group toggle. Change O and R in Minecraft\'s Controls settings.',
         'Для атаки конкретной цели переключи косу в командный режим в коллекции и нажми ПКМ по врагу.$(br2)В большом меню также есть кнопки приказов и выбор группы. Клавиши O и R меняются в настройках управления Minecraft.')])

    entry('basics/lifecycle', ('Life & recovery', 'Жизнь и восстановление'), 'minecraft:clock', 4, [
        ('A summoned soul starts with full health and keeps supported native abilities. It has an owner-prefixed name for maps; overhead name labels are hidden.$(br2)Its bonded summons inherit ownership when the summoning relationship can be identified.',
         'Призванная душа появляется с полным здоровьем и сохраняет поддерживаемые способности. Для карт имя содержит ник владельца, но над головой не показывается.$(br2)Её помощники наследуют владельца, если связь призыва удаётся определить.'),
        ('When a companion dies, its collection entry survives but needs one minute to recover. Recall is available before death.$(br2)Souls persist across a normal rejoin. Following souls return when too far away; Teleport lets you call them sooner.',
         'После гибели напарника запись остаётся в коллекции, но восстанавливается одну минуту. Живую душу можно отозвать.$(br2)Души сохраняются при обычном перезаходе. При следовании они возвращаются с большой дистанции; команда телепорта позволяет сделать это раньше.')])

    entry('equipment/scythes', ('Soul scythes', 'Косы душ'), 'soulcatcherplus:supreme_soul_scythe', 0, [
        ('Iron / diamond / netherite / supreme scythes provide 1 / 2 / 3 / 4 companion slots.$(br2)Taking a weaker scythe limits new summons; it does not dismiss existing ones. Scythes accept normal sword enchantments.',
         'Железная / алмазная / незеритовая / высшая коса дают 1 / 2 / 3 / 4 слота напарников.$(br2)Более слабая коса ограничивает новый призыв, но не отзывает уже призванных. Косы принимают обычные зачарования мечей.'),
        crafting('soul_scythe', 'diamond_soul_scythe'), crafting('netherite_soul_scythe', smithing=True),
        crafting('supreme_soul_scythe')])
    entry('equipment/cages', ('Soul cages', 'Клетки душ'), 'soulcatcherplus:supreme_soul_cage', 1, [
        ('Base capacities: iron 150, diamond 400, netherite 1,000, supreme 2,000.$(br2)Keep a cage in your offhand or hotbar for collecting and summoning. Netherite equipment is upgraded at a smithing table.',
         'Базовая вместимость: железная — 150, алмазная — 400, незеритовая — 1000, высшая — 2000.$(br2)Для сбора и призыва держи клетку во второй руке или хотбаре. Незеритовое снаряжение улучшается на столе кузнеца.'),
        crafting('iron_soul_cage', 'diamond_soul_cage'), crafting('netherite_soul_cage', smithing=True),
        crafting('supreme_soul_cage')])
    entry('equipment/guide', ('This guide', 'Эта книга'), 'patchouli:guide_book{"patchouli:book":"soulcatcherplus:soul_guide"}', 2, [
        ('With Patchouli installed, you receive this guide once on joining the world. Death and later logins do not grant extra copies.$(br2)Craft a replacement with a book and soul sand in any arrangement. Without Patchouli, the guide integration is inactive.',
         'При установленном Patchouli эта книга выдаётся один раз при входе в мир. Смерть и повторные входы не дают новых копий.$(br2)Запасную можно сделать из книги и песка душ в любом порядке. Без Patchouli интеграция не активна.'),
        crafting('soul_guide')])

    entry('enchantments/harvester', ('Soul Harvester', 'Soul Harvester'), 'soulcatcherplus:soul_scythe', 0, [
        ('Soul Harvester has two levels. Each adds 50% to energy from kills while an enchanted scythe is held.$(br2)Level I: ×1.5. Level II: ×2. Two scythes use the stronger level rather than stacking. The bonus affects energy, not unlocked forms.',
         'Soul Harvester имеет два уровня. Каждый даёт +50% энергии за убийства, пока зачарованная коса в руке.$(br2)I: ×1,5. II: ×2. У двух кос учитывается сильнейшее зачарование, без сложения. Бонус влияет на энергию, а не число обликов.')])
    entry('enchantments/capacity', ('Soul Capacity', 'Soul Capacity'), 'soulcatcherplus:diamond_soul_cage', 1, [
        ('Soul Capacity enchants cages and has three levels. Each adds one third of base capacity.$(br2)Level III doubles the original capacity. For example, a supreme cage increases from 2,000 to 4,000. Fractions are rounded down once.',
         'Soul Capacity накладывается на клетки и имеет три уровня. Каждый добавляет треть базовой вместимости.$(br2)III удваивает исходный объём: например, высшая клетка растёт с 2000 до 4000. Дробная часть округляется вниз один раз.')])
    entry('enchantments/slots', ('Soul Slot', 'Soul Slot'), 'minecraft:diamond_chestplate', 2, [
        ('Soul Slot is a rare, one-level armor enchantment. Each worn enchanted armor piece adds one companion slot, up to +4 for a full set.$(br2)With a supreme scythe, the maximum is eight slots. Our enchantments use normal enchanting/book pools, without an extra chest-book roll.',
         'Soul Slot — редкое зачарование брони с одним уровнем. Каждая надетая зачарованная часть даёт +1 слот, полный комплект — +4.$(br2)С высшей косой максимум равен восьми слотам. Зачарования используют обычные пулы книг и стола, без отдельного шанса в сундуках.')])

    entry('forgotten/scrolls', ('Soul scrolls', 'Свитки душ'), 'soulcatcherplus:forgotten_soul_scroll', 0, [
        ('A scroll contains one soul form. Its tooltip identifies it. Right-click the scroll to add the form to your collection and consume the scroll.$(br2)Normal, boss and Forgotten Soul scrolls have different appearances. Forgotten companions are unlocked by scrolls, not scythe capture.',
         'Свиток содержит один облик души. Он указан в подсказке. Нажми ПКМ со свитком: облик добавится в коллекцию, а свиток исчезнет.$(br2)Обычные, босс-свитки и Забытые души выглядят по-разному. Забытых напарников открывают свитками, а не поимкой косой.'),
        ('Eligible chest loot tables have a 2% chance to add a Forgotten Soul scroll. Raven, Courier and Shaman are equally likely.$(br2)A scroll teaches the form; it does not summon for free. Normal energy costs and slot limits still apply.',
         'В подходящих таблицах лута сундуков есть 2% шанс дополнительного свитка Забытой души. Ворон, курьер и шаман равновероятны.$(br2)Свиток открывает облик, но не даёт бесплатный призыв: нужны энергия и свободный слот.')])
    entry('forgotten/raven', ('Soul Raven', 'Soul Raven'), 'soulcatcherplus:forgotten_soul_scroll', 1, [
        ('A passive 4-HP collector that rests on your shoulder. Within 20 blocks of you, it gathers accessible dropped items and your collectible soul remnants.$(br2)It carries up to eight stacks or remnants per trip and returns them to you.',
         'Пассивный собиратель с 4 HP, который отдыхает на плече. В радиусе 20 блоков от тебя ищет доступные предметы и твои остатки душ.$(br2)За рейс переносит до восьми стопок или остатков и доставляет их владельцу.'),
        ('A full inventory does not stop collection: overflow is left at your feet. Recall or death releases carried items at the raven\'s position.$(br2)Collecting remnants still requires a cage and room in the collection. The raven does not fight.',
         'Полный инвентарь не мешает сбору: излишек остаётся у ног. При отзыве или гибели груз выпадает у ворона.$(br2)Для остатков душ по-прежнему нужны клетка и место в коллекции. Ворон не сражается.')])
    entry('forgotten/courier', ('Soul Courier', 'Soul Courier'), 'minecraft:ender_chest', 2, [
        ('A passive 8-HP skeleton porter with an Ender Chest. Right-click the courier to open your own Ender Chest inventory.$(br2)Other players can use it too, but each sees their own inventory. The courier is an access point, not shared storage.',
         'Пассивный скелет-носильщик с 8 HP и эндер-сундуком. Нажми ПКМ по курьеру, чтобы открыть свой эндер-сундук.$(br2)Другие игроки тоже могут им пользоваться, но каждый видит свой инвентарь. Это точка доступа, а не общее хранилище.'),
        ('The courier supports normal companion orders and stays still while its chest is being used.$(br2)Closing, recalling or losing the courier does not delete the Ender Chest contents: they belong to the accessing player.',
         'Курьер поддерживает обычные приказы напарника и стоит на месте, пока сундук открыт.$(br2)Закрытие, отзыв или гибель курьера не удаляют содержимое эндер-сундука: оно принадлежит открывшему его игроку.')])
    entry('forgotten/shaman', ('Soul Shaman', 'Soul Shaman'), 'minecraft:soul_torch', 3, [
        ('A passive 8-HP support soul. Stay within six blocks of the shaman to receive its aura.$(br2)Every seven seconds it casts Strength I and Regeneration I for eight seconds on you and your active souls within six blocks of you.',
         'Пассивная душа поддержки с 8 HP. Для ауры шаман должен быть не дальше шести блоков от тебя.$(br2)Раз в семь секунд он даёт силу I и регенерацию I на восемь секунд тебе и твоим активным душам в радиусе шести блоков от тебя.'),
        ('The staff gesture and short sound mark the cast. Bonded minions can receive the aura too; other players and pets do not.$(br2)Vanilla immunities remain: undead do not gain regeneration. Leaving or recalling stops refreshes; existing buffs expire naturally.',
         'Каст сопровождают взмах посоха и короткий звук. Аура доступна и помощникам душ, но не чужим игрокам и питомцам.$(br2)Ванильные иммунитеты сохранены: нежить не получает регенерацию. После ухода или отзыва оставшиеся эффекты закончатся сами.')])

    for locale, new in translations.items():
        path = LANG / f'{locale}.json'
        current = {key: value for key, value in json.loads(path.read_text(encoding='utf-8')).items()
                   if not key.startswith('guide.soulcatcherplus.')}
        current.update(new)
        write(path, current)
    print(f'Generated {len(list((BOOK/"en_us/entries").rglob("*.json")))} entries, 4 categories and two localizations.')


if __name__ == '__main__':
    main()
