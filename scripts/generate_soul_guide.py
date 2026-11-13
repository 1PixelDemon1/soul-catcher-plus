# SPDX-License-Identifier: MIT
"""Author the optional Patchouli guide and its two localizations.

Edit this catalog, then regenerate. Recipe pages reference real recipe IDs.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / 'src/main/resources'
MINECRAFT = re.search(r'^minecraft_version=(.+)$', (ROOT / 'gradle.properties').read_text(), re.M).group(1).strip()
LEGACY_BOOK = MINECRAFT == '1.16.5'
BOOK_META = RES / 'data/soulcatcherplus/patchouli_books/soul_guide'
BOOK = BOOK_META if LEGACY_BOOK else RES / 'assets/soulcatcherplus/patchouli_books/soul_guide'
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
    if not 1 <= len(recipes) <= 2:
        raise ValueError('A recipe page must contain one or two recipes')
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
    metadata = {
        'name': tr('name', "Soul Catcher's Notes", 'Записки ловца душ'),
        'landing_text': tr('landing',
            'Death leaves a trace that few can see. A scythe holds it, a cage gathers its energy, and a bestiary remembers the creatures you have caught.',
            'Смерть оставляет след, который умеют различать немногие. Коса позволяет удержать его, клетка собирает энергию, а бестиарий хранит память о пойманных существах.'),
        'subtitle': tr('subtitle', 'Soul Catcher Plus', 'Soul Catcher Plus'),
        'version': '0', 'i18n': True, 'show_progress': False, 'show_toasts': False,
        'pause_game': True, 'use_blocky_font': True,
        'creative_tab': 'soulcatcherplus' if MINECRAFT in ('1.16.5', '1.19.2') else 'soulcatcherplus:soulcatcherplus',
        'model': 'soulcatcherplus:soul_guide',
        'book_texture': 'patchouli:textures/gui/book_cyan.png',
        'header_color': '164F59', 'link_color': '006B83', 'link_hover_color': '159CA8'}
    if not LEGACY_BOOK:
        metadata['use_resource_pack'] = True
    write(BOOK_META / 'book.json', metadata)

    category('basics', ('The first bond', 'Первая связь'),
             ('Learn to catch a soul, summon a companion and lead your party.',
              'Узнай, как поймать душу, призвать спутника и вести свой отряд.'),
             'soulcatcherplus:soul_icon', 0)
    category('equipment', ("A catcher's tools", 'Снаряжение ловца'),
             ('Scythes, vessels and the drawings needed to make them.',
              'Косы, сосуды и чертежи для их создания.'), 'soulcatcherplus:soul_scythe', 1)
    category('enchantments', ('Enchantments', 'Зачарования'),
             ('Charms that strengthen your tools and the bonds with your companions.',
              'Чары, укрепляющие снаряжение и связь со спутниками.'), 'minecraft:enchanted_book', 2)
    category('forgotten', ('Forgotten Souls', 'Забытые души'),
             ('Old seals sometimes hold companions with unusual gifts.',
              'Старые печати порой хранят спутников с необычными дарами.'),
             'soulcatcherplus:forgotten_soul_scroll', 3)
    category('commands', ('Commands & debugging', 'Команды и отладка'),
             ('Administrative commands. Requires cheats or operator permission level 2.',
              'Команды администратора. Нужны включённые читы или права оператора уровня 2.'),
             'minecraft:experience_bottle', 4)

    entry('basics/capture', ('Your first soul', 'Первая душа'), 'soulcatcherplus:textures/entity/soul_remnant/small.png', 0, [
        ('Begin with simple equipment and your first soul. A good catcher learns to care for companions as well as summon them.$(br2)To catch a soul, you will need a scythe and a soul cage. Keep the cage in your offhand or on your hotbar.$(br2)Switch the scythe to Harvest mode and use it on a creature — right mouse button by default. A mark will appear. It fades quickly: defeat the creature before the bond breaks.$(br2)A glowing soul will remain after your victory. Use the scythe on it to preserve the captured form in your bestiary. Only you can claim a soul bearing your mark.$(br2)Do not linger: an unattended trace will soon fade away.',
         'Начни с простого снаряжения и первой души. Хороший ловец умеет не только призывать спутников, но и беречь их.$(br2)Для ловли понадобятся коса и клетка душ. Держи клетку во второй руке или на панели быстрого доступа.$(br2)Переключи косу в режим «Сбор» и примени её к существу — по умолчанию правой кнопкой мыши. На нём появится метка. Она недолговечна: успей одержать победу, пока связь не оборвалась.$(br2)После победы останется светящаяся душа. Примени к ней косу, чтобы сохранить пойманный облик в бестиарии. Забрать отмеченную тобой душу сможешь только ты.$(br2)Не медли: оставленный без внимания след вскоре рассеется.')])
    entry('basics/energy', ('Soul energy', 'Энергия душ'), 'soulcatcherplus:iron_soul_cage', 1, [
        ('Victories fill your cages with energy. Both you and your summoned companions can gather it.$(br2)This power sustains a summon. The mightier the creature, the more energy it takes to bring it back into the world.$(br2)A captured form remains in your bestiary. Once a bond is made, you can call upon that soul again, as long as you have the strength to summon it.',
         'Победы наполняют клетки энергией. Её собираешь и ты, и твои призванные спутники.$(br2)Эта сила поддерживает призыв. Чем могущественнее существо, тем больше энергии потребуется, чтобы вернуть его в мир.$(br2)Пойманный облик остаётся в бестиарии. Однажды установив связь, ты сможешь вновь обращаться к этой душе, пока хватает сил для призыва.')])
    entry('basics/collection', ('The bestiary', 'Бестиарий'), 'soulcatcherplus:soul_icon', 2, [
        ('Hold a scythe in either hand and open the bestiary with its assigned key — O by default.$(br2)Choose a captured soul and summon it. Make sure your party has room and your cage holds enough energy.$(br2)Different creature forms can be found and preserved separately. Especially mighty souls are marked in purple.$(br2)Mark your favorite companions with a star to find them more easily. Here you can also choose the tint of their spectral forms or release an unwanted soul forever.',
         'Возьми косу в любую руку и открой бестиарий назначенной клавишей — по умолчанию O.$(br2)Выбери пойманную душу и призови её. Убедись, что в отряде есть свободное место, а в клетке достаточно энергии.$(br2)Разные облики существ можно находить и сохранять отдельно. Особенно могучие души отмечены фиолетовым.$(br2)Отметь любимых спутников звёздочкой, чтобы быстрее находить их среди остальных. Здесь же можно подобрать оттенок их призрачных обликов или навсегда отпустить ненужную душу.')])
    entry('basics/orders', ('Orders', 'Приказы'), 'soulcatcherplus:diamond_soul_scythe', 3, [
        ('With a scythe in hand, hold the command wheel key — R by default. Point at a companion, then at an order, and release the key.$(br2)Returning the pointer to the center clears your choice. All creatures lets you address the entire party.',
         'С косой в руке удерживай клавишу кольца приказов — по умолчанию R. Наведи указатель на спутника, затем на приказ и отпусти клавишу.$(br2)Возврат указателя в центр снимает выбор. «Все существа» позволяет обратиться ко всему отряду.'),
        ('$(thing)Follow$() — accompany you, defend you and help against those you strike. A companion that strays too far will leave the fight to return.',
         '$(thing)Следовать$() — сопровождать тебя, защищать и помогать против тех, кого ты ударил. Слишком отдалившись, спутник оставит бой и вернётся.'),
        ('$(thing)Aggressive$() — follow you and seek nearby hostile creatures. Once fighting, the soul keeps its chosen enemy instead of following your every step.$(br2)Only when you are beyond teleport range will it abandon the fight and try to return. Without a target, it follows normally.',
         '$(thing)Агрессия$() — следовать за тобой и искать врагов поблизости. Вступив в бой, душа продолжит сражаться с выбранной целью, не отвлекаясь на твои перемещения.$(br2)Лишь за пределом телепортации она оставит бой и попытается вернуться. Без цели спутник следует за тобой как обычно.'),
        ('$(thing)Wait$() — stay in place and stop fighting.$(br2)$(thing)Guard$() — defend the place where you stood when giving the order. The companion watches for nearby monsters and pursues them around that post.',
         '$(thing)Ждать$() — оставаться на месте и прекратить бой.$(br2)$(thing)Охранять$() — защищать место, где ты стоял при отдаче приказа. Спутник ищет поблизости монстров и преследует их в пределах своего поста.'),
        ('$(thing)Teleport$() — bring a companion close, keeping its health and orders. A guard takes up a new post beside you.',
         '$(thing)Телепорт$() — вернуть спутника поближе, сохранив его здоровье и приказ. Страж займёт новый пост рядом с тобой.'),
        ('$(thing)Recall$() — end the summon without losing the captured form. Part of the companion\'s remaining strength returns as soul energy to your cages.',
         '$(thing)Отозвать$() — завершить призыв, сохранив пойманный облик. Часть оставшихся сил спутника вернётся в клетки энергией душ.'),
        ('$(thing)Attack a target$() — switch the scythe to Command mode and use it on an enemy — right mouse button by default. The addressed companions take that target and switch to Follow.$(br2)The other orders are also available in the bestiary — O by default.',
         '$(thing)Атаковать цель$() — переключи косу в командный режим и примени её к врагу — по умолчанию правой кнопкой мыши. Выбранные спутники возьмут эту цель и перейдут в режим «Следовать».$(br2)Остальные приказы доступны и в бестиарии — по умолчанию O.')])
    entry('basics/lifecycle', ("A soul's return", 'Возвращение души'), 'minecraft:clock', 4, [
        ('The death of a spectral body does not destroy its bond. The soul will need time to recover before answering your call again.$(br2)You can recall a companion before that happens, returning some energy to your cages. Leave room for it: any excess power will disperse.$(br2)If a companion is delayed on the road, use the Teleport order.',
         'Гибель призрачного тела не уничтожает установленную связь. Душе потребуется время, чтобы восстановиться и вновь ответить на твой зов.$(br2)Спутника можно отозвать заранее. Это позволит вернуть часть энергии в клетки. Позаботься, чтобы в них оставалось свободное место: лишняя сила рассеется.$(br2)Если спутник задержался в дороге, воспользуйся приказом «Телепорт».')])

    entry('equipment/scythes', ("A catcher's scythes", 'Косы ловца'), 'soulcatcherplus:supreme_soul_scythe', 0, [
        ('A scythe is both a weapon and a bond with your party. A finer tool lets you lead more companions.$(br2)Begin with an iron scythe. In time, diamond and netherite may take its place. A netherite scythe requires a smithing table and a diamond base.$(br2)The supreme scythe is made from bones and a Nether Star. It is a tool for a catcher ready to gather a powerful party.$(br2)Scythes accept sword enchantments. Do not neglect these charms when seeking dangerous prey.',
         'Коса служит оружием и удерживает связь с отрядом. Более совершенное орудие позволяет вести за собой больше спутников.$(br2)Начни с железной косы. Позже её место смогут занять алмазная и незеритовая. Для незеритовой понадобится кузнечный стол и алмазная основа.$(br2)Высшую косу создают из костей и звезды Незера. Это орудие для ловца, готового собрать вокруг себя сильный отряд.$(br2)Косы принимают зачарования мечей. Не пренебрегай чарами, отправляясь за опасной добычей.')])
    entry('equipment/cages', ('Soul cages', 'Клетки душ'), 'soulcatcherplus:supreme_soul_cage', 1, [
        ('The blue light inside a cage reveals the energy held within. A finer vessel can hold a greater reserve.$(br2)An iron cage will serve you at first. Diamond and netherite will prepare you for more demanding summons. The supreme cage is made from bones and a Nether Star.$(br2)Summoning uses the cage in your offhand. If there is none, it uses the leftmost cage on your hotbar.$(br2)The energy for a single summon must be held in one cage. When gathering your spoils, a full vessel passes the excess to the next available cages.',
         'Голубое свечение внутри клетки выдаёт заключённую в ней энергию. Чем лучше сосуд, тем больший запас он способен удержать.$(br2)Железная клетка пригодится в начале пути. Алмазная и незеритовая позволят готовиться к более требовательным призывам. Высшую создают из костей и звезды Незера.$(br2)Для призыва используется клетка во второй руке. Если её там нет — самая левая клетка на панели быстрого доступа.$(br2)Энергия для одного призыва должна находиться в одной клетке. При сборе добычи заполненный сосуд передаёт излишек следующим доступным клеткам.')])
    # Recipe-only entry: each pair of pages is a full spread, independent of prose pagination.
    entry('equipment/blueprints', ('Drawings', 'Чертежи'), 'minecraft:crafting_table', 2, [
        crafting('soul_scythe', 'diamond_soul_scythe'),
        crafting('iron_soul_cage', 'diamond_soul_cage'),
        crafting('netherite_soul_scythe', 'netherite_soul_cage', smithing=True),
        crafting('supreme_soul_scythe', 'supreme_soul_cage')])
    entry('equipment/guide', ('A new copy', 'Новая копия'), 'patchouli:guide_book{"patchouli:book":"soulcatcherplus:soul_guide"}', 3, [
        ('If these notes are lost, combine an ordinary book with soul sand.$(br2)May the new book serve you on your travels — or reach someone just beginning to hear the souls.',
         'Если эти записи будут потеряны, соедини обычную книгу с песком душ.$(br2)Пусть новая книга послужит тебе в дороге — или достанется тому, кто только начинает слышать души.'), crafting('soul_guide')])

    entry('enchantments/harvester', ('Soul Harvester', 'Жнец душ'), 'soulcatcherplus:soul_scythe', 0, [
        ('An enchanted scythe helps draw more energy from your victories.$(br2)Keep it in your hand when going into battle, and your cage will fill more generously.',
         'Зачарованная коса помогает извлекать больше энергии из одержанных побед.$(br2)Держи её в руке, отправляясь в бой, — и клетка будет наполняться щедрее.')])
    entry('enchantments/capacity', ('Soul Capacity', 'Вместимость душ'), 'soulcatcherplus:diamond_soul_cage', 1, [
        ('These charms let a cage hold more energy.$(br2)They are especially useful to a catcher who travels far from home and prepares a reserve for the summons ahead.',
         'Эти чары позволяют клетке удерживать больше энергии.$(br2)Они особенно полезны ловцу, который привык уходить далеко от дома и заранее готовит запас для будущих призывов.')])
    entry('enchantments/slots', ('Soul Slot', 'Узы душ'), 'minecraft:diamond_chestplate', 2, [
        ('These charms are woven into armor to strengthen the bond between a catcher and their companions.$(br2)Wearing enchanted armor lets you sustain a larger party. A good scythe alone may not be enough for someone who wishes to lead many.',
         'Эти чары вплетают в броню, чтобы укрепить связь между ловцом и его спутниками.$(br2)Надетые зачарованные доспехи позволяют поддерживать больший отряд. Одной хорошей косы не всегда достаточно тому, кто желает вести за собой многих.')])

    entry('forgotten/scrolls', ('Soul scrolls', 'Свитки душ'), 'soulcatcherplus:forgotten_soul_scroll', 0, [
        ('Some souls have survived in old scrolls. Before breaking a seal, read which creature is bound to it.$(br2)Take the scroll in hand and use it. The seal will break, and the form held within will appear in your bestiary. You will still need energy and room in your party to summon it.$(br2)Scrolls of Forgotten Souls are occasionally found in chests. Their companions are reached through surviving seals, rather than caught with a scythe.',
         'Некоторые души сохранились в старых свитках. Прежде чем разрушить печать, прочитай, какое существо связано с ней.$(br2)Возьми свиток в руку и используй его. Печать разрушится, а заключённый в ней облик появится в бестиарии. Для призыва всё ещё понадобятся энергия и свободное место в отряде.$(br2)Изредка в сундуках встречаются свитки Забытых душ. Их спутников получают через сохранившиеся печати, а не обычной ловлей косой.')])
    entry('forgotten/raven', ('Soul Raven', 'Призрачный ворон'), 'soulcatcherplus:forgotten_soul_scroll', 1, [
        ('A small raven rests on its owner’s shoulder and watches for nearby spoils.$(br2)It seeks items and souls that you can claim, gathers several finds and brings them back. If your inventory is full, the spoils will be left at your feet.$(br2)You will need a cage to receive the souls it brings.$(br2)The raven is no fighter. Take care of your collector.',
         'Небольшой ворон отдыхает на плече своего хозяина и следит за оставленной поблизости добычей.$(br2)Он разыскивает предметы и доступные тебе души, подбирает несколько находок и приносит их обратно. Если в твоём инвентаре нет места, добыча останется у ног.$(br2)Для принятия принесённых душ нужна клетка.$(br2)Ворон не создан для сражений. Береги своего собирателя.')])
    entry('forgotten/courier', ('Soul Courier', 'Призрачный курьер'), 'minecraft:ender_chest', 2, [
        ('A little skeleton carries an Ender Chest much heavier than itself. Use the courier to reach your storage far from home.$(br2)Other travelers can call upon it too. Each will see their own belongings.$(br2)The courier waits patiently while its chest is open. Even if its summon ends, the contents of your storage will remain safe.',
         'Маленький скелет несёт эндер-сундук, который заметно тяжелее его самого. Воспользуйся курьером, чтобы добраться до своего хранилища вдали от дома.$(br2)Другие путешественники тоже могут обращаться к нему. Каждый увидит собственные вещи.$(br2)Пока сундук открыт, курьер терпеливо ждёт. Даже если его призыв оборвётся, содержимое хранилища останется в сохранности.')])
    entry('forgotten/shaman', ('Soul Shaman', 'Призрачный шаман'), 'minecraft:soul_torch', 3, [
        ('The shaman accompanies your party with a staff and an amulet. Its rites strengthen you and your allied souls, helping them fight and recover from wounds.$(br2)Stay nearby to keep its aid close at hand.$(br2)The shaman itself avoids combat. See to its protection, and it will care for those you lead.',
         'Шаман сопровождает отряд с посохом и амулетом. Своими обрядами он укрепляет тебя и союзные души, помогая им сражаться и оправляться от ран.$(br2)Держись поблизости, чтобы его помощь не прерывалась.$(br2)Сам шаман избегает боя. Позаботься о его защите — и он позаботится о тех, кого ты ведёшь за собой.')])

    entry('commands/energy', ('Set soul energy', 'Запас энергии'), 'soulcatcherplus:iron_soul_cage', 0, [
        ('$(thing)/soulcatcher energy <amount>$()$(br2)Sets the energy stored in your active cage. It replaces the current amount rather than adding to it.$(br2)Example:$(br)/soulcatcher energy 500',
         '$(thing)/soulcatcher energy <число>$()$(br2)Устанавливает запас энергии в активной клетке. Заменяет текущее значение, а не прибавляет к нему.$(br2)Пример:$(br)/soulcatcher energy 500'),
        ('Keep a cage in your offhand or hotbar. The offhand takes priority; otherwise the leftmost hotbar cage is used.$(br2)The input range is 0 to 1000000. The stored amount is capped at the cage’s actual capacity. Zero empties it.',
         'Держи клетку во второй руке или на панели быстрого доступа. Приоритет у второй руки, затем у самой левой клетки панели.$(br2)Допустимы числа от 0 до 1000000. Запас ограничен фактической вместимостью клетки. Ноль опустошает её.'),
        ('This command acts on the player running it.$(br2)From the server console, run it as a player:$(br)/execute as Steve run soulcatcher energy 500',
         'Команда работает с клеткой игрока, от имени которого выполняется.$(br2)Из консоли сервера можно выполнить её от имени игрока:$(br)/execute as Steve run soulcatcher energy 500')])
    entry('commands/scroll', ('Give a soul scroll', 'Выдача свитков'), 'soulcatcherplus:soul_scroll', 1, [
        ('$(thing)/soulcatcher scroll <mob> [player]$()$(br2)Gives one scroll containing the default form of that mob. Use the scroll to add its soul to the bestiary.$(br2)Without a recipient, the scroll goes to you. From the console, specify a player name or a selector for one player.',
         '$(thing)/soulcatcher scroll <моб> [игрок]$()$(br2)Выдаёт один свиток с обычным обликом указанного моба. Чтобы добавить душу в бестиарий, используй свиток.$(br2)Без получателя свиток выдаётся тебе. В консоли укажи ник или селектор одного игрока.'),
        ('Use an entity ID from Minecraft or an installed mod. Use Tab (default) to complete IDs and recipients.$(br2)Examples:$(br)/soulcatcher scroll minecraft:creeper$(br2)/soulcatcher scroll twilightforest:naga Steve',
         'Укажи ID моба из Minecraft или установленного мода. Для автодополнения ID и получателей по умолчанию используется Tab.$(br2)Примеры:$(br)/soulcatcher scroll minecraft:creeper$(br2)/soulcatcher scroll twilightforest:naga Steve'),
        ('The namespace@mob form is also accepted:$(br)minecraft@creeper$(br2)The command chooses a normal, boss or Forgotten Soul scroll automatically. If the recipient has no room for it, free inventory space and try again.$(br2)The ID must refer to a mob, not an item or projectile.',
         'Вместо двоеточия можно использовать @:$(br)minecraft@creeper$(br2)Обычный, боссовый или забытый свиток выбирается автоматически. Если места нет, освободи инвентарь получателя и повтори команду.$(br2)ID должен указывать на моба, а не на предмет или снаряд.')])

    russian_names = {
        'entity.soulcatcherplus.soul_raven': 'Призрачный ворон',
        'entity.soulcatcherplus.soul_courier': 'Призрачный курьер',
        'entity.soulcatcherplus.soul_shaman': 'Призрачный шаман',
        'enchantment.soulcatcherplus.soul_capacity': 'Вместимость душ',
        'enchantment.soulcatcherplus.soul_harvester': 'Жнец душ',
        'enchantment.soulcatcherplus.soul_slot': 'Узы душ',
        'command.soulcatcherplus.qa.raven': 'Призрачный ворон добавлен в бестиарий.',
        'screen.soulcatcherplus.appearance_hex': 'Код цвета'}
    for locale, new in translations.items():
        path = LANG / f'{locale}.json'
        current = {key: value for key, value in json.loads(path.read_text(encoding='utf-8')).items()
                   if not key.startswith('guide.soulcatcherplus.')}
        if locale == 'ru_ru':
            current.update({key: value for key, value in russian_names.items() if key in current})
        current.update(new)
        write(path, current)
    print(f'Generated {len(list((BOOK/"en_us/entries").rglob("*.json")))} entries, '
          f'{len(list((BOOK/"en_us/categories").glob("*.json")))} categories and two localizations for {MINECRAFT}.')


if __name__ == '__main__':
    main()
