# Soul Catcher Plus — сборка для Minecraft 1.16.5

Требуются **Forge 36.2.42** и **Java 8**. Порт находится в отдельной ветке
`codex/forge-1.16.5`; это сборка разработки, не новый релиз.

В папке `mods`:

- Soul Catcher Plus `0.2.0+1.16.5`, с текущими клетками душ, поиском и колесом команд.
- [Advent of Ascension 3.6.11](https://www.curseforge.com/minecraft/mc-mods/advent-of-ascension-nevermine/files/4431558) — Release.
- [GeckoLib 3.0.106](https://modrinth.com/mod/geckolib/version/UQGoJDhn) для Forge 1.16.5.
- [Twilight Forest 4.0.870](https://www.curseforge.com/minecraft/mc-mods/the-twilight-forest/files/3575220) — последняя версия для 1.16.5, Beta.
- [Aquamirae 5.4.0](https://www.curseforge.com/minecraft/mc-mods/aquamirae/files/4420334) — Release.
- [Obscure API 11](https://www.curseforge.com/minecraft/mc-mods/obscure-api/files/4420298), библиотека Aquamirae.

## Установка

1. Создайте отдельный профиль Minecraft **1.16.5** с **Forge 36.2.42** и выберите Java 8.
2. Скопируйте папку `mods` из архива в папку этого профиля.
3. Для совместной игры используйте одинаковые шесть JAR у обоих игроков и на сервере.
4. Создайте новый мир для проверки порта.

Коса помечает врага по ПКМ. Клетка душ должна находиться во второй руке или хотбаре;
вторая рука приоритетнее. **O** открывает коллекцию, удержание **R** — колесо команд.
Для обоих меню коса должна быть в одной из рук. Привязки меняются в настройках клавиш.
Незеритовые предметы улучшаются в кузнечном столе без шаблона: в 1.16.5 шаблонов ещё нет.

В устанавливаемом JAR служебной `/soulcatcherqa` нет. Она доступна в клиенте
разработки с `-PgameTests`; `/soulcatcherqa --all` заполняет коллекцию базовыми
обликами разрешённых мобов. Оператор может настроить энергию активной клетки
командой `/soulcatcher energy <число>`.

Проверены компиляция и запуск клиента. Игровое поведение всех существ AoA, Twilight
Forest и Aquamirae и работа отдельного сервера ещё не проверены. Сторонние моды сохраняют
собственные лицензии и авторство; код Soul Catcher Plus распространяется по MIT.

## Сборка из исходников

```powershell
python scripts/bootstrap.py
.\scripts\gradle.ps1 build -x test
.\scripts\gradle.ps1 playtestZip -PwithAoA -PwithTwilightForest -PwithAquamirae
.\scripts\gradle.ps1 runClient -PgameTests -PwithAoA -PwithTwilightForest -PwithAquamirae
```

Java 17 нужна только Gradle и компилятору. Jabel преобразует синтаксис в байткод
Java 8; дополнительные библиотеки для запуска нашего JAR не требуются.
Bootstrap устанавливает оба JDK локально и проверяет SHA-256 архивов.
