# Хрупкий пол — проверка fragile_floor

Испытание HARD/GLOBAL. Каждая позиция и тип реально поддерживающего блока
имеют персональный отсчёт в 40 последовательных серверных тиков.
Незавершённые отсчёты не сохраняются; разрушения и предметы остаются в мире.

## Реализация

- `FragileFloorChallenge` использует существующий серверный `onTick` и
  `ChallengeActivity`, без новых Mixin, клиентских классов, пакетов или NBT.
- `BlockCollisionSpliterator` запрашивает только тонкий слой под хитбоксом.
  Учитываются верхние поверхности форм, касающиеся стоп; боковые поверхности,
  жидкость, сущности, пассажиры и поднимающийся игрок не создают опор.
- Отсчёты разделены по серверу, UUID, объекту игрока, активации, измерению,
  режиму и позиции/типу блока. Один серверный тик учитывается один раз даже
  при одновременном GLOBAL/PERSONAL dispatch. Пропуск тика прерывает контакт.
  Свойства того же блока без потери опоры отсчёт не сбрасывают.
- Перед каждым разрушением заново проверяются активность, мир, тип и контакт.
  `World.breakBlock(pos, true, player)` использует пустой инструмент и штатные
  BlockEntity, жидкости, обновления соседей и событие разрушения. Проверки
  твёрдости и прямой добычи игроком не вызываются. Износ инструмента,
  `afterBreak` и дополнительные сферы опыта не добавляются.
- Дроп соответствует именно ванильному `World.breakBlock`, включая правила
  мира: например, камень даёт булыжник, стекло и бедрок предмета не дают.
  Здесь нет отдельной проверки возможности ручной добычи игроком.
- Начало/завершение, выход, респаун и существующий хук прыжка очищают только
  собственные контакты. Сломанные блоки и выпавшие предметы не восстанавливаются.

## Историческая проверка реализации (2026-10-27)

Команда: `.\gradlew.bat build runGametest --offline`.
Итог: BUILD SUCCESSFUL, 38 s; 92 JUnit и 37/37 GameTest,
failures/errors/skipped = 0. XML находятся в
`build/test-results/test/TEST-*.xml` и `build/reports/gametest.xml`.

JUnit `FragileFloorChallengeTest` проверяет границу 39/40, повторный dispatch,
независимые контакты, замену типа, уход, пропуск тиков и скачок времени;
также регистрацию, точные метаданные, двойной выбор и сериализацию активности
без сохранения незавершённых таймеров.

GameTest `FragileFloorGameTest.supportsTimingLootAndLifecycle` использует
подключённых серверных игроков, настоящие серверные тики и штатный
`CycleManager`. Стенд фиксирует положение игроков, чтобы считать контакт
без реального клиента. Проверяет:

- 39/40 тиков для досок, камня, стекла, бедрока, нижней плиты, ступени,
  забора, мёда, сундука, шалкера и эндер-сундука;
- геометрию нижней/верхней ступени, движение внутри опоры, воздух, жидкость
  и боковую коллизию; независимое начало контакта двух соседних блоков;
- реальный прыжок и уход перед порогом, замену типа, waterlogged-свойство
  того же блока и смену Survival → Adventure;
- однократный дроп общей опоры при двух игроках, повторный GLOBAL/PERSONAL
  dispatch, отключённый `doTileDrops`, неизменность износа предмета в руке;
- содержимое сундука один раз, именованный груз в предмете шалкера без
  рассыпания и дублирования, сохранение эндер-инвентаря;
- разрушение узкой верхней коллизии двери с удалением нижней половины,
  обе половины кровати без двойного дропа, выпадение потерявшего опору факела;
- Creative, Spectator и пассажира лодки, PERSONAL без влияния на другого
  игрока, enabled и новую активацию без старых отсчётов;
- респаун после обнуления здоровья, выход/вход того же UUID, переход
  Overworld/Nether, отсутствие разрушения в покинутом измерении;
- завершение на 39 тиках, в том числе офлайн, сохранение no_sprint,
  пары с no_block_placing и no_block_breaking;
- `/fdc list`, `/fdc activate fragile_floor`, `/fdc active`;
- дисковую запись/чтение общей активности в том же JVM, отсутствие таймеров
  в challengeData и новый экземпляр реализации без старых контактов.

## Ручные проверки, которые ещё нужны

Автоматический стенд не подтверждает реальные клиентские HUD/меню и движение,
сетевую задержку, визуальные/звуковые эффекты разрушения, переход через портал,
полную смерть с дропом и keepInventory=false/true или полный рестарт процесса
с повторной загрузкой блоков и предметов. Проверить отдельно:

1. В одиночном Survival выполнить `/fdc list`, `/fdc activate fragile_floor`,
   `/fdc active`; сверить русские название, HUD и правила в меню.
2. На известном первом тике контакта проверить 39/40 тиков, настоящий прыжок,
   ходьбу внутри опоры и уход перед порогом. Проверить точную грань между
   несколькими формами, верхние плиты, открытые/двойные контейнеры, лестницу
   или лиану без опоры, вагонетку, элитры и сущности под ногами.
3. Проверить остальные контейнеры и их содержимое, соседние механизмы,
   поршни, падение песка/гравия, предметы и правила мира. Блоки не откатываются.
4. Проверить Survival → Creative → Survival, Spectator → Adventure,
   полноценную смерть и оба keepInventory, выход/вход, настоящие порталы.
5. Сохранить и полностью перезапустить мир при активном испытании: контакт
   начинается заново, разрушения и предметы сохраняются. Отдельно завершить
   офлайн через `/fdc activate no_sprint` и вернуться.
6. Проверить пары no_jumping/auto_jump, slippery_hands/attack_recoil в реальном
   движении. На выделенном сервере с двумя клиентами проверить независимые
   таймеры, общую опору и выход одного из игроков. Кооператив для работы не нужен.

Сборка из песочницы первоначально не получила доступ к общему Gradle wrapper
`zip.lck`; проверки выполняются с разрешённым доступом к кэшу, lock не удалялся.
Ошибки подготовки нового стенда (обработка IOException, ожидание дропа камня,
положение на узкой форме двери, перемещение между тиками и высота Nether)
исправлены. После успешного собственного сценария оставшийся дроп мешал
следующему bitter_experience: добавлена очистка тестовых блоков и предметов.
Итоговый полный прогон прошёл, чужой код и тесты не менялись.

## Ревью этапа 16 — 2026-11-04

Исходный HEAD: `1e54cca1d70a4f2e7e5d1cc94856c429ebefbf36`.
Числа предыдущего раздела относятся к реализации, не к этому ревью.

Подтверждённый дефект `fragile_floor-F01`: `setEnabled(false)` оставлял
UUID/контакты в `standing`. `CycleManager` исключает disabled ID до tick,
а event dispatch исключает его до DISCONNECT, поэтому эти callbacks уже
не освобождали записи. Weak server key не освобождает записи живого сервера.
Теперь выключение сразу очищает собственную map; общий dispatch не менялся.
Это исправление очистки/удержания памяти; преждевременное разрушение после
повторного включения в тесте не наблюдалось и не объявляется дефектом.

Новые GameTest:

- `disabledAndDepartingPlayersReleaseContacts`: до исправления воспроизводит
  оставшиеся после disable записи; после — 32 цикла double/serial/start/stop,
  повторный tick, удаление одного UUID и последнего server bucket, настоящий
  Fabric DISCONNECT invoker. Для проверки владения onTick вызывается прямо;
  это не измерение времени и не замена основному тесту серверного cadence.
- `diskReloadStartsNewContactAndKeepsWorldConsequences`: настоящие server ticks,
  39/40, PlayerManager playerdata + overworld PersistentState save/read/load,
  active/ended/offline end при пустом сервере; новый вход начинает полные 40
  тиков. Именованные 17 diamonds, XP 7+.625 и HP 17 сохраняются. Предыдущий
  дроп остаётся один раз; никакой таймер не попадает в challengeData.
- `automaticBreakPreservesPlacementAndMiningRestrictions`: реальный BlockItem
  до активации ставит блок и тратит один предмет, с парой no_block_placing
  восемь попыток не меняют мир/стек; опора всё равно ломается. После снятия
  fragile_floor placement остаётся запрещён. С no_block_breaking применяется
  Mining Fatigue X, руда ломается автоматически без износа pickaxe и лишних XP;
  снятие пола оставляет Mining Fatigue X.

Исходный сценарий сохранён; reconnect/offline end и cleanup теперь вызывают
Fabric DISCONNECT перед PlayerManager.remove, чтобы не оставлять контакты
стендовых игроков между batch. EmbeddedChannel и фиксация позиции сохраняются.

Свежие попытки (`.\gradlew.bat build runGametest --offline -I build/fragile-floor-review-<phase>.init.gradle`):

| Phase | Exit / Gradle duration | JUnit | GameTest | Результат |
|---|---|---|---|---|
| baseline init attempt | 1 / 1s | не запускался | не запускался | BOM во временном init; лог `build/fragile-floor-review-baseline-init-error.log` |
| baseline | 0 / 43s | 149 / 54 suites | 1/1 FF | исходный предметный сценарий |
| red | 1 / 49s | 149 / 54 | 1/2 FF | F01: disable не освобождает записи |
| fixed | 0 / 55s | 149 / 54 | 4/4 FF | исправление и новые сценарии |
| final | 1 / 47s | 149 / 54 | 15/16 (3/4 FF + 5 NBB + 7 NBP) | новый disk-fixture потерял падающий дроп из области наблюдения |
| verified | 0 / 45s | 149 / 54 | 16/16 (4 FF + 5 NBB + 7 NBP) | drop-fixture изолирован, адресные пары |

Каждая phase использует отдельные `build/gametest-fragile-floor-review-<phase>-20261104`,
`build/fragile-floor-review-<phase>.log`, `build/reports/fragile-floor-review-<phase>.xml`
и `build/reports/fragile-floor-review-<phase>-junit/TEST-*.xml`.
Начальный exec не создал процесс из-за sandbox ACL/access 5; проверки запущены
с разрешённым доступом. Lock-файлы не удалялись.
В новом disk-fixture после проверки настоящего дропа одна доска фиксируется
над опорой: noGravity, нулевая velocity и infinite pickup delay. Это отделяет
сохранение ItemEntity при reload/stop от обычного падения/подбора в пустой
структуре; физическое падение и запись world chunks в новом JVM не доказаны.

Для воспроизведения изолированного запуска init script меняет только test resources,
JUnit output и runDir, не production. В `afterEvaluate`:

```groovy
def phase = 'verified' // для повторного запуска выбрать новое имя и runDir
def resources = project.file("build/fragile-floor-review-${phase}-resources")
def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.FragileFloorGameTest',
    'com.cursecycle.NoBlockBreakingGameTest', 'com.cursecycle.NoBlockPlacingGameTest']
resources.mkdirs()
new File(resources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
sourceSets.gametest.resources.setSrcDirs([resources])
tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
tasks.named('test') {
    outputs.upToDateWhen { false }
    reports.junitXml.outputLocation = layout.buildDirectory.dir("reports/fragile-floor-review-${phase}-junit")
}
loom.runs.gametest.runDir = "build/gametest-fragile-floor-review-${phase}-20261104"
loom.runs.gametest.vmArg "-Dfabric-api.gametest.report-file=../reports/fragile-floor-review-${phase}.xml"
```

После изоляции восстановить обычные resources:
`.\gradlew.bat processGametestResources --offline --rerun-tasks`.
Сверить 43 entrypoints source/built, свежие XML/JUnit, metadata/registration,
SHA постоянных документов/ТЗ и отсутствие стендов в обоих production JAR.
`build/fragile-floor-review-verify.py` проверяет эти артефакты; итог в отчёте.
Статический разбор текущего mapped 1.21.1 через javap сохранён в
`build/fragile-floor-review-vanilla.txt`: feet query локален, chunk view запрашивается
с create=false, breakBlock использует EMPTY tool/dropStacks/fluid/flags 3/game event.

Расходы оценены статически: P — применимые игроки, G/A — GLOBAL/PERSONAL ID,
B — кандидаты в тонком feet AABB с ванильным padding, V — сложность их форм,
S — реальные опоры. Tick gate O(G+A), контакт O(B×V+S), при D созревших опорах
до D повторных контактных запросов (D≤S) перед разрушением. Для стандартного
игрока область мала и не зависит от числа блоков мира; нет мирового сканирования
и принудительной генерации чанков. Maps/Count/shape boxes/stream выделяются
на активном пути; они имеют стоимость. Retained state O(P×S), не содержит
Player/World; stop/leave/respawn/jump/disable освобождают своё. Выключенное
испытание не исполняет свой tick, общий dispatch/sync по-прежнему имеет цену.
Ванильные loot/BlockEntity/neighbour updates могут быть дороже самого таймера.
MSPT/FPS/allocations/retained-memory profile не измерялись.

Открыты `fragile_floor-L01..L04`: обычный клиент/HUD/OP2/physical movement,
manual_tests[0..7] с настоящими порталами/смертью/keepInventory и остальными
контейнерами/механизмами/парами; dedicated с двумя настоящими клиентами и late join
(manual_tests[8]); полный новый JVM active/ended/offline end с сохранёнными
мировыми блоками/предметами; сопоставимый нагрузочный профиль. Дисковое чтение
в одном JVM не подтверждает полный перезапуск или сохранение world chunks после
нового процесса. Точные шаги и условия закрытия — в отчёте этапа 16.
