# Да как ходить то! — проверка инверсии движения

`inverted_keyboard`: HARD / GLOBAL. Мод нужен на сервере и клиентах.
Сервер выбирает активные включённые ID, клиент преобразует собственный ввод.
Survival/Adventure затрагиваются; Creative/Spectator, мёртвый и удалённый игрок — нет.

## Реализация

- Единственный клиентский Mixin — RETURN `KeyboardInput.tick(boolean, float)`.
  В Yarn 1.21.1 этот метод заново читает текущие KeyBinding и рассчитывает оси
  при каждом вызове. Инверсия выполняется после штатного замедления и до
  потребителей ввода, без изменения длины вектора и ванильной нормализации.
- Обе компоненты умножаются на -1, пары `pressingForward/Back` и
  `pressingLeft/Right` меняются местами: `ClientPlayerEntity.tickRiding`
  передаёт лодке именно флаги. Ходьба, воздух, плавание и прочий транспорт
  используют тот же ввод. Прыжок, приседание и назначения других действий
  остаются штатными. Нового запрета спринта нет.
- Обрабатывается только `client.player.input` живого текущего игрока при наличии
  мира, interactionManager и синхронизированного ID. Дубликаты ID не умножают
  эффект. Нет ссылок на игрока, снимков настроек, таймеров или дополнительных NBT.
  Существующие JOIN/DISCONNECT очищают ClientDataHolder; следующая выборка ввода
  рассчитывается по актуальному состоянию, включая смену режима и объекта игрока.
- `NetworkHandler.getEffectiveActiveIds` теперь фильтрует GLOBAL ID по серверной
  включённости так же, как PERSONAL, и объединяет их без дубликатов. Это нужно,
  чтобы отключённое на сервере испытание не продолжало менять ввод на клиентах.
  Клиент не принимает решение по своим локальным настройкам включённости.
- Назначения клавиш и options.txt реализация не записывает. Инверсия не
  перехватывает текст, события клавиатуры GUI, мышь, скорость или позицию.
  Существующая система сохраняет активность и отправляет её клиентам.
- Текущий порядок общего input Mixin: `InvertedKeyboardInput.apply`, затем
  `MovementMemoryInput.apply`, затем `BlockedDirectionInput.apply`. Ограничение
  направления получает уже преобразованный ввод. У транспорта остаются
  собственные исключения соответствующих испытаний.

## Исторические результаты реализации — 27.10.2026

- `.\gradlew.bat build runGametest --offline`: BUILD SUCCESSFUL, 1m 6s.
  XML: 100 JUnit и 41/41 GameTest, ошибок, провалов и пропусков нет.
  Добавлено 5 JUnit и 2 GameTest. Лог: `build/inverted-keyboard-verification.log`.
- JUnit: точные метаданные и double_challenge; все 16 комбинаций направлений
  при масштабах 1, 0.3 и 0.2; знаки и длина вектора, флаги транспорта,
  неизменность jumping/sneaking. Четыре режима, смерть, отсутствие режима/синка,
  завершение, сброс подключения и свежая синхронизация. Серверные enabled-ID,
  PERSONAL-получатель и дедупликация проверены отдельно.
- GameTest на выделенном сервере: реальные S2C-пакеты двум игрокам и позднему
  вошедшему; режимы, замена объекта при респауне, телепортация между Overworld,
  Nether и End; double_challenge с no_sprint; реальные сохранение в файл,
  чтение PersistentStateManager и повторная синхронизация в том же JVM.
  Возврат того же UUID при активном испытании и после завершения офлайн;
  PERSONAL/GLOBAL ID и отключение/включение на сервере.
  Выполнены `fdc list`, `fdc activate inverted_keyboard`, `fdc active` через
  настоящий dispatcher. После завершения запрет спринта сохранён.
- Клиентский probe:
  `.\gradlew.bat -I build/inverted-keyboard-client-probe/init.gradle runClient --offline`
  — BUILD SUCCESSFUL, 17s, `INVERTED_KEYBOARD_CLIENT_PROBE_PASSED`.
  Fabric CLIENT применил настоящий Mixin, выполнены 1920 вызовов
  `KeyboardInput.tick`: все 64 комбинации направлений/прыжка/приседания,
  замедление, три повторных тика, включение/выключение, затем переназначение
  вперёд на стрелку вверх через KeyBinding и сохранение назначения после stop.
  Только gate `shouldInvert` временно подменялся тестовым Mixin; реальный
  клиентский мир/игрок не создавался, применимость покрыта JUnit и требует
  проверки с живым клиентом. Стенд и лог находятся в игнорируемом
  `build/inverted-keyboard-client-probe/`, в игровой JAR не включены.
- Первый build в песочнице: отказ доступа к общему wrapper zip.lck.
  Проверки прошли с разрешённым доступом, lock не удалялся. Первый запуск
  временного probe получил неверный относительный путь JAR и продолжился
  обычным клиентом; этот процесс остановлен, путь исправлен. Повторный probe
  успешно завершился в preLaunch. Ошибок игровой реализации не обнаружено.
- `git diff --check` и `git diff --cached --check` — без ошибок.

## Свежее ревью — 04.11.2026

Игровой HEAD `048cee6136171e02a8e61320bc9fb3914ccc8440`, Java 21,
Minecraft 1.21.1 / Yarn 1.21.1+build.3, Loader 0.16.0, Fabric API 0.102.0+1.21.1.
Подтверждённых production-дефектов не найдено; gameplay/shared contracts не менялись.
Изменены только предметный GameTest, этот документ и сохранённый CLIENT probe.
Итог `pass_with_limits`, механика `pass`, расходы `no_issue_found / static`.

- `./gradlew.bat build runGametest --offline -I build/inverted-keyboard-review-baseline.init.gradle`
  — exit 0, 44s: 149 JUnit / 54 suites и 2/2 InvertedKeyboard GameTest.
- `./gradlew.bat build runGametest --offline -I build/inverted-keyboard-review-expanded.init.gradle`
  — exit 0, 61s: 149 JUnit / 54 suites и 3/3 InvertedKeyboard GameTest.
  Это итоговый серверный прогон; повтор ради другого названия не выполнялся.
  Оба сервера штатно завершились. Каждый init ограничивает entrypoints этим
  классом и задаёт отдельный свежий runDir внутри build; исходные resources не меняет.
  Логи `build/inverted-keyboard-review-{baseline,expanded}.log`, XML
  `build/reports/inverted-keyboard-review-{baseline,expanded}.xml`, JUnit в
  соседних каталогах `inverted-keyboard-review-{baseline,expanded}-junit`.
- Старые два сценария заново проверили actual S2C для двух игроков и late join,
  четыре режима, смерть/respawn, server teleport в Nether/End/Overworld,
  GLOBAL/PERSONAL/dedup/enabled, double/no_sprint/end, disk state и команды
  настоящего dispatcher теперь с OP 2. Это серверные действия, без живых клиентов.
- Новый третий сценарий: 32 цикла с новым serial, duplicate-ID/double/start/end;
  настоящий Fabric DISCONNECT и PlayerManager remove/save/load того же UUID;
  PersistentState save/read/set active, ended и offline end с пустым сервером.
  Новый JOIN получает текущие ID, no_sprint остаётся. Named diamonds17,
  XP7+.625, HP17 и FireResistance сохраняются. Нет собственного input/keybinding
  snapshot в challengeData. Это disk reload одной JVM, без полного рестарта процесса.
- Сохранённый CLIENT probe запускается командой
  `./gradlew.bat runClient --offline -I src/clientprobe/inverted_keyboard/init.gradle`.
  После исправления fixture: exit 0, 33s,
  `INVERTED_KEYBOARD_REVIEW_CLIENT_PASSED checks=23173 ticks=5890`.
  Лог `build/inverted-keyboard-review-client-attempt2.log`.
  Fabric CLIENT реально трансформирует KeyboardInput; **gate не подменён**.
  Выполняются 128 комбинаций direction/jump/sneak/sprint, масштабы 1/.3/.2,
  повторные ticks, start/end, duplicate IDs, режимы, смерть/removed/new player,
  чужой Input, null world/player/interactionManager и смена мира. Проверяются
  оси, boat flags, длина вектора и неизменность jump/sneak/sprint binding.
  32 start/end/reset, два переназначения forward (Up, PageUp), сохранение
  назначения; actual порядок inversion→block и inversion→memory/release/end,
  сохранение второй механики после снятия первой.
- CLIENT fixture использует Unsafe MinecraftClient/GameOptions/ClientWorld/Player/Manager
  и синтетические player accessors. Sync задаётся через ClientDataHolder, memory
  priming через production receiver. Нет окна, GLFW input events, options.txt I/O,
  обычного client initialization, сети, физического travel/лодки/плавания или HUD.
  Проверка boat flags и чтение tickRiding не означают настоящую поездку в лодке.
- Все неуспешные попытки: первоначальный sandbox exec не создал процесс
  (sandbox-bin ACL access 5); первый helper с PowerShell stdin испортил Unicode
  paths, поэтому Gradle exit 1/1s не нашёл ещё не созданный init (лог
  `build/inverted-keyboard-review-init-failed.log`), исправлен UTF-8 stdin.
  CLIENT attempt1 exit 1/34s прошёл основной input matrix, затем вызов setRemoved
  на Unsafe player упал из-за неинициализированных passengers. Fixture теперь
  меняет только removalReason, реальный isRemoved gate сохранён; production
  не менялся. Лог `build/inverted-keyboard-review-client-attempt1.log` сохранён.

Verifier `python build/inverted-keyboard-review-verify.py` проверяет входные пять
SHA, metadata6/registration1/client-only Mixin, свежие XML/JUnit/CLIENT marker,
восстановленные 43 entrypoints и отсутствие probe/GameTest в двух production JAR.
Mapped vanilla input/tickRiding/normalization сверены через javap:
`build/inverted-keyboard-review-vanilla.txt`. Исторические результаты выше
не засчитываются как новые проверки.

Собственная горячая работа: один gate и `List.contains` O(A) за sample ввода,
два отрицания и четыре присваивания flags при активности. A — число эффективных
ID. Нет собственного tick timer, UUID cache, player/world references, NBT,
options writes, world scans и custom packets. Неактивный hook имеет стоимость
gate; общий Mixin также вызывает helpers других механик. Shared dispatch,
20-tick sync и vanilla movement/network имеют собственную стоимость.
FPS/MSPT/allocations/retained memory не измерялись.

Открыты `inverted_keyboard-L01` (обычный клиент/input/GUI/travel/пары),
`-L02` (настоящие multiplayer/network), `-L03` (full new-JVM restart),
`-L04` (профиль нагрузки). Точные шаги ниже и в локальном отчёте этапа18.
Предыдущие ограничения других испытаний не закрывались; recheck_queue не нужна,
поскольку production-контракты не менялись.

## Ручные сценарии — не выполнены

1. Survival, ровная площадка: `/fdc list`, `/fdc activate inverted_keyboard`,
   `/fdc active`. Проверить русские название, описания HUD/меню и единственное
   стандартное уведомление. По отдельности W/S/A/D, обе диагонали,
   W+S, A+D; скорость диагоналей должна быть ванильной.
2. Проверить воздух, воду и вертикальное плавание, лодку, лошадь и другой
   управляемый транспорт; мышь, элитры, пробел, Shift и клавиша спринта
   сохраняют назначение. Вперёд для спринта определяется итоговым направлением.
3. Переназначить движение на стрелки, затем ещё раз во время испытания.
   Проверить текущие назначения пешком, в воде и лодке; после
   `/fdc activate no_sprint` последнее назначение должно сохраниться.
   Проверить чат с текстом WASD, поиск инвентаря/книги рецептов, GUI-навигацию
   и редактирование настроек. Сравнить options.txt после обычного сохранения:
   собственных переписанных назначений или снимков быть не должно.
4. Два реальных клиента и поздний вход: Survival → Creative → Spectator →
   Adventure → Survival, смерть/респаун с обоими keepInventory, настоящие
   порталы и возврат из End. В отсутствие живого игрока инверсия не работает;
   после его замены возобновляется один раз. Повторить выход/вход и другой сервер.
5. `/fdc activate "double_challenge:inverted_keyboard,no_sprint"`, затем
   `/fdc activate no_sprint`: инверсия снята, запрет спринта сохраняется.
   Отдельно проверить пары с no_jumping и slippery_hands. С blocked_direction
   итоговое направление ограничивается после инверсии; memory хранит уже
   инвертированное направление. inverted_mouse меняет только взгляд.
6. Сохранить и полностью перезапустить JVM сервера с активным испытанием.
   Войти обоими клиентами; повторить с завершением, пока один игрок офлайн,
   вторым сохранением/рестартом и возвратом этого UUID. Проверить отключение
   испытания в серверных настройках и очередную штатную синхронизацию.
7. Проверить естественное завершение по циклу и начало новой активации.
   Команды `/fdc stop` нет; для ручного завершения активировать другое испытание.

Автоматические тесты не доказывают фактическое перемещение/отрисовку живых
клиентов, полёт и транспорт, меню, полноценный рестарт JVM или работу других
модов ввода. Эти ограничения остаются ручными проверками, а не пройденными тестами.
