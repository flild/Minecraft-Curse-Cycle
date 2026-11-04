# locked_containers — Сундуки не для тебя

Minecraft 1.21.1 / Yarn 1.21.1+build.3 / Fabric API 0.102.0+1.21.1.
MEDIUM/GLOBAL. Запрещены обычные, двойные и ловушка-сундуки, все шалкеры,
эндер-сундуки, сундуки в лодках и вагонетках.

## Реализация

- `LockedContainersServerPlayerMixin` проверяет созданный серверный обработчик
  в `ServerPlayerEntity.openHandledScreen` до отправки OpenScreenS2CPacket
  и назначения currentScreenHandler. Запрет определяется по Inventory слотов,
  включая ванильный DoubleInventory двух половин сундука. Тип экрана 9×3
  сам по себе не запрещён: его использует и разрешённая бочка.
- При отказе `handler.onClosed(player)` балансирует вызванный конструктором
  Inventory.onOpen. Ссылки на игрока/хранилище не сохраняются. Ванильные
  взаимодействия блока/транспорта, включая их статистику, события и генерацию
  ещё не созданного лута, не отменяются целиком; запрещён доступ к инвентарю.
  Посадка в лодку остаётся доступна, открытие её инвентаря пассажиром запрещено.
- `LockedContainersScreenHandlerMixin` отменяет любой onSlotClick запрещённого
  обработчика до выполнения операции. Текущий экран закрывается штатно;
  устаревшая ссылка не закрывает новый разрешённый экран. Сетевой syncId
  дополнительно проверяется самой ванилью. Клиентских классов, снимков
  содержимого, откатов вещей и собственных пакетов нет.
- Активация, вход, респаун и серверный тик согласуют текущий экран через
  `closeHandledScreen`. Возврат/выбрасывание стека курсора выполняет ваниль.
  Проверка ChallengeActivity учитывает жизнь, режим, enabled, GLOBAL/PERSONAL
  и двойные испытания при каждом открытии/клике. После завершения ограничение
  исчезает; интерфейс автоматически не открывается. Сохранённого ID общей
  системы достаточно, собственные NBT или таймеры не нужны.
- Бочки, печи, воронки, другие станции, торговля, обычные инвентари существ,
  вагонетки с воронкой, предметы-шалкеры и автоматизация не входят в запрет.
  Другие испытания продолжают действовать независимо.

Сигнатуры сверены с установленным байткодом и документацией
[Yarn GenericContainerScreenHandler](https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/screen/GenericContainerScreenHandler.html)
и [ServerPlayerEntity](https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/server/network/ServerPlayerEntity.html).

## Ревью этапа 14 — 2026-11-04

Игровой код и общие контракты не менялись. Подтверждённых дефектов не найдено.
Свежие проверки на входном HEAD `1508c0b285ada63038da51751ce2b4d10e03d4db`
с расширенным `LockedContainersGameTest`:

| Запуск | Команда | Результат | Лог и отчёты под build |
|---|---|---|---|
| baseline | `build runGametest --offline -I build/locked-containers-review-baseline.init.gradle` | exit 0, 27 s; 149 JUnit / 54 suites; 4/4 LC | `locked-containers-review-baseline.log`, `reports/locked-containers-review-baseline.xml`, `reports/locked-containers-review-baseline-junit` |
| expanded | `build runGametest --offline -I build/locked-containers-review-expanded.init.gradle` | exit 0, 30 s; 149 JUnit / 54 suites; 7/7 LC | те же пути с `expanded` |
| final | `build runGametest --offline -I build/locked-containers-review-final.init.gradle` | exit 0, 37 s; 149 JUnit / 54 suites; 19/19 GT: 8 LC + 8 CT + 3 HS | те же пути с `final` |

Команды начинаются с `.\gradlew.bat`. Каждый запуск использует отдельный новый
`build/gametest-locked-containers-review-{baseline,expanded,final}-20261104`.
В init-script принудительно выполняется JUnit и меняется только список
GameTest entrypoints, runDir и выходные отчёты. Пример итогового script:

```groovy
allprojects {
    afterEvaluate {
        def resources = project.file('build/locked-containers-review-final-resources')
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.LockedContainersGameTest',
            'com.cursecycle.CraftingTaxGameTest', 'com.cursecycle.HotbarShuffleGameTest']
        resources.mkdirs()
        new File(resources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([resources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        tasks.named('test') {
            outputs.upToDateWhen { false }
            reports.junitXml.outputLocation = layout.buildDirectory.dir('reports/locked-containers-review-final-junit')
        }
        loom.runs.gametest.runDir = 'build/gametest-locked-containers-review-final-20261104'
        loom.runs.gametest.vmArg '-Dfabric-api.gametest.report-file=../reports/locked-containers-review-final.xml'
    }
}
```

Все три свежих прогона завершили сервер штатно; XML не содержат failure/error/skipped.
T1–T4 — четыре исходных сценария ниже, выполненные заново. Добавлены:

- T5 `repeatedDenialsBalanceOpenCloseAndPreserveOtherViewer`: 32 отказа через
  реальный `openHandledScreen` и считающий subclass EnderChestInventory;
  constructor onOpen и denied onClose строго 1:1. Настоящий сундук сохраняет
  второго разрешённого viewer при PERSONAL-запрете; затем счётчик возвращается
  к 0. У отказов нет OpenScreen. Повторные start/tick идемпотентны.
- T6 `currentScreenPacketsAndStaleScreenCannotAffectAllowedCursor`: каждый
  SlotActionType × кнопки 0/1/8/40 через настоящий server C2S handler с текущим
  syncId до start/tick. Даже поддельные cursor/changed-slots из пакета не меняют
  вещи. Именованный стек 23 алмаза возвращён с компонентами ровно раз.
  Старый прямой вызов и старый пакет не закрывают новую бочку и не трогают
  её курсор с пятью золотыми слитками.
- T7 `repeatedActivationsAndActualPlayerdataKeepCurrentRules`: 32 цикла с новым
  serial, одинаковым double ID, disable/enable/end/no_sprint. Фактический
  playerdata + PersistentState disk save/load в одной JVM: active, offline end,
  start/end при пустом сервере. Именованный шалкер, 17 алмазов эндер-инвентаря,
  XP 7 + .625 и HP17 сохранены; active блокирует, ended разрешает. Нет своего
  ключа/снимка в challengeData. DISCONNECT вызывается через Fabric invoker.
- T8 `shufflePairPreservesAllowedScreenAndCursorAfterEnd`: реальные серверные
  тики пары LC/HS, наблюдение после 101 тика. Запрет действует, бочка остаётся
  открытой, именованный курсор и selectedSlot3 сохранены, 45 камней действительно
  переставлены без потерь. После снятия LC повторное открытие разрешено,
  через следующие 101 тик HS продолжает работать; 13 слитков курсора возвращены
  один раз. CT T8 дополнительно проверяет реальный верстак, оплату и LC/end.

Vanilla-порядок проверен свежим `javap -c -p` установленного mapped 1.21.1 JAR:
`build/locked-containers-review-vanilla.txt`; сигнатуры — `...-api.txt`.
Для `openHandledScreen` createMenu предшествует packet/install; constructors
открывают inventory, onClosed закрывает его. C2S проверяет syncId/canUse;
ограничение ScreenHandler действует до операции. Packet claims обновляют
remote tracking, не становятся серверными предметами. Клиентских Mixin у LC нет.

Восстановление стандартного стенда:
`.\gradlew.bat processGametestResources --offline --rerun-tasks`.
Проверка артефактов/отпечатков/XML:
`python build/locked-containers-review-verify.py`.
Их свежие результаты записаны в отчёт этапа 14; прежние результаты ниже
не используются как доказательство нового ревью.

До Gradle первый PowerShell не запустился из-за sandbox-bin access 5; разрешённые
escalated вызовы работают. В служебном Python снимке кириллический stdin был
заменён на `?`; снимок пересоздан PowerShell без изменения исходных файлов.
Неудачных игровых проверок в этом ревью не было. Locks/зависимости не менялись.

Расходы оценены **static**. `blocks` сначала проверяет применимость O(G+A),
затем читает до S слотов; stream выделяется только после gate. Активный tick
стоит O(P×(G+A+S)); неактивные постоянно зарегистрированные open/click hooks
всё равно имеют цену проверки. S=число слотов текущего handler, G/A=число
GLOBAL/PERSONAL ID, P=подходящие онлайн-игроки. Нет своего UUID map, NBT,
world scan, принудительной загрузки чанков, custom packets или feedback spam.
При отказе остаётся ванильное создание/закрытие handler и его обычные события.
MSPT/FPS/allocations/retained memory не измерялись; функциональные счётчики
баланса и пакетов не являются профилем производительности.

Итог ревью: **pass_with_limits**, механика **pass**, расходы
**no_issue_found / static**. Ограничения `locked_containers-L01..L04`:
обычный клиент/HUD/menu/физический ввод и lifecycle/торговля/инвентарь существ;
настоящий dedicated co-op/late join/latency; полностью новые JVM active/ended;
нагрузочный профиль. Подробные шаги — в локальном отчёте этапа 14 и ниже.

## Исторические проверки реализации — не результат ревью 2026-11-04

- `.\gradlew.bat build --offline` — BUILD SUCCESSFUL, 16 s, после реализации.
- Итоговая `.\gradlew.bat build runGametest --offline` — BUILD SUCCESSFUL, 45 s.
  88 JUnit, failures/errors/skipped = 0; 32/32 GameTest без failure/error/skipped,
  включая четыре новых теста locked_containers. XML: `build/test-results/test`
  и `build/reports/gametest.xml`.
- Первый запуск в песочнице получил отказ доступа к общему wrapper zip.lck;
  запуск с разрешённым доступом к существующему GradleCache прошёл. Кэш,
  зависимости и lock-файлы не менялись для обхода ошибки.
- При подготовке стенда исправлены ошибки сигнатуры teleport и доступа к
  каналу. ChallengeScenario теперь отдаёт собственный EmbeddedChannel тесту
  и освобождает каналы при завершении сценария. Первый игровой прогон выявил
  две ошибки условий теста: открытие бочки проверялось только OFF_HAND
  (ваниль вызывает обычное действие блока с MAIN_HAND), а при установке
  шалкера игрок после лодки пересекал место установки. После исправления
  финальный полный прогон прошёл; код механики менять не потребовалось.

JUnit подтверждают русские метаданные, единственную регистрацию, MEDIUM-пул,
enabled и NBT для GLOBAL/PERSONAL/двойной активации без снимков хранилищ.

GameTest с реально применёнными Mixin подтверждают:

- Открытие без испытания и отказ при активности для обычного/ловушка-сундука,
  эндер-сундука, неокрашенного и всех 16 окрашенных шалкеров. Отдельно обе
  половины настоящих двойных обычного/ловушка-сундуков (контроль: шесть рядов).
  Отправлены взаимодействия обеими руками; запрещённый OpenScreen отсутствует.
  Обычное ванильное действие блока выполняется только с MAIN_HAND.
- Содержимое эндер-сундука сохранено. Бочка, печь, воронка, раздатчик,
  выбрасыватель и верстак открываются. Лодка/вагонетка с сундуком закрыты
  для обеих рук и пассажирского openInventory, вагонетка с воронкой открывается;
  посадка в лодку разрешена.
- Штатная воронка извлекает вещь из закрытого сундука. Сундук можно сломать,
  шалкер можно поставить и переместить как предмет. В паре с inventory_lockdown
  Shift из бочки работает, закрытый слот игрока остаётся закрытым.
- Активация при 23 алмазах на курсоре немедленно закрывает экран и возвращает
  ровно 23 алмаза. При полном инвентаре девять изумрудов выпадают ровно один раз.
  Все SlotActionType и кнопки 0/1/8/40 через старый обработчик и повторные
  ClickSlotC2SPacket не меняют хранилище. Отдельно проверен Shift до первого
  согласующего тика, когда активный ID уже изменён, но экран ещё открыт.
- Два игрока и поздний вход, GLOBAL/PERSONAL и их дубликаты, отключённый enabled,
  Creative/Spectator и возврат через Adventure/Survival, мёртвый объект/респаун,
  телепортация Nether/Overworld, повторное подключение того же UUID.
- Активность записана на диск PersistentStateManager, прочитана и установлена
  обратно в том же JVM; запрет действует. После завершения офлайн и входа
  хранилище доступно. Пара с no_sprint и замена на no_sprint сохраняют запрет
  спринта. `/fdc list`, `/fdc activate locked_containers`, `/fdc active` выполнены.

## Ручные проверки, не выполненные в этой среде

1. Настоящий клиент в одиночной игре и два клиента выделенного сервера:
   стандартные HUD/меню/уведомление без дополнительного спама; обе руки,
   все хранилища и транспорт, быстрые Shift/цифры/F/drag/double-click,
   задержка сети и отсутствие визуального предварительного открытия.
2. Активация с курсором, включая полный инвентарь, и играющая воронка под
   сундуком. Проверить сохранность вещей, доступ после замены на no_sprint,
   бочку/печь, торговлю и обычные инвентари существ. Последние два маршрута
   отдельно не запускались GameTest.
3. Настоящая смерть с keepInventory=false/true, дроп, повторный вход с загрузкой
   инвентаря и эндер-сундука, переход через реальные порталы. Автотест использует
   замену игрока и серверную телепортацию; это не полный клиентский сценарий.
4. Полностью перезапустить процесс с активным locked_containers, затем заменить
   его через `/fdc activate no_sprint`, сохранить и перезапустить снова; вернуть
   офлайн-игрока. Чтение NBT с диска в том же JVM не подтверждает этот сценарий.

Сборка и GameTest не заменяют эти ручные проверки. Следующее испытание
не реализовывалось в рамках этой работы.
