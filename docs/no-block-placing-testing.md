# no_block_placing — проверка

## Ревью этапа 15 — 04.11.2026

Проверен production HEAD `cbc46af9dfc4fabf1a1b37e74520798f7df700ee`, Java21,
MC1.21.1/Yarn1.21.1+build.3/Fabric0.102.0+1.21.1/Loader0.16.0.
Production/shared contracts не менялись; расширены только предметный GameTest
и изолированный CLIENT probe. Подтверждённых production-дефектов не найдено.
Предварительный итог `pass_with_limits`, механика `pass`, расходы
`no_issue_found / static`. Полный отчёт: локальный
`Промпты/reviews/15-no_block_placing.md`.

Свежие проверки (история реализации ниже не засчитывается повторно):

| Запуск | Команда после `.\gradlew.bat` | Результат |
|---|---|---|
| baseline | `build runGametest --offline -I build/no-block-placing-review-baseline.init.gradle` | exit0/31s,149JUnit/54suites,4/4GT |
| expanded | та же с `no-block-placing-review-expanded.init.gradle` | exit1/31s,149JUnit,6/7GT; новый C2S control не ставил блок из-за неподтверждённого teleport |
| verified | та же с `no-block-placing-review-verified.init.gradle` | exit0/32s,149JUnit,7/7GT после vanilla TeleportConfirm |
| CLIENT attempt1 | `runClient --offline -I src/clientprobe/no_block_placing/init.gradle` | exit1/19s; preLaunch без datapacks не имел тега CANDLES |
| CLIENT final | та же команда | exit0/18s,`NO_BLOCK_PLACING_CLIENT_PROBE_PASSED checks=57 vanillaContinuations=6` |
| final | `build runGametest --offline -I build/no-block-placing-review-final.init.gradle` | exit0/32s,149JUnit/54suites,15/15GT:7NBP+8LC; normal shutdown |

Логи `build/no-block-placing-review-{baseline,expanded,verified,final}.log`,
XML `build/reports/no-block-placing-review-{baseline,expanded,verified,final}.xml`,
JUnit в соседних каталогах с `-junit`. Первый CLIENT лог сохранён как
`build/no-block-placing-review-client-attempt1.log`, успешный — `...-client.log`.
Ошибки новых fixtures сохранены; они не объявлены дефектами production.
Первый PowerShell sandbox не создал процесс (`helper_sandbox_lock_failed`,
SetNamedSecurityInfoW access5); разрешённые escalated команды работают.

Новые тесты поверх прежних четырёх:

- `currentPacketsCannotPlaceOrSpendAndStopRestoresPlacement`: настоящий
  `ServerPlayNetworkHandler.onPlayerInteractBlock`, vanilla подтверждение
  телепорта, 9 видов предметов × обе руки × 8 запросов. Никаких блоков,
  многоблочных половин, потери count/custom name/durability/USED.
  Тот же C2S после окончания ставит камень и расходует ровно1; no_sprint остаётся.
  Это EmbeddedChannel на серверном потоке, без реального клиента/latency.
- `repeatActivationAndActualPlayerdataDoNotRestoreOldState`: 32 повтора
  double same ID/new serial, disabled/reenabled, end; фактический
  PlayerManager save/load и PersistentState disk active/ended/offlineend/
  empty server в одной JVM. Именованные17алмазов, XP7+.625, HP17 сохранены;
  Fabric DISCONNECT вызван; своего snapshot/cache/NBT нет.
- `existingContentsEntitiesAndActualDispenserRemainAllowed`: извлечение
  растения из горшка, поедание торта, заполнение существующего котла водой/
  лавой/снегом с ванильной тарой, стойка для брони как сущность;
  настоящий vanilla DispenserBehavior с BlockPointer/DispenserBlockEntity
  устанавливает воду и возвращает пустое ведро. Пара LC запрещает оба
  действия; после окончания NBP установка разрешена, LC продолжает deny.
  Запуск redstone/scheduled tick раздатчика этим не проверяется.

CLIENT probe теперь воспроизводится из `src/clientprobe/no_block_placing`:
init-script отдельно компилирует/упаковывает probe, production sourceSets
не меняются. Проверяет 7 transformed targets и настоящие методы place,
placeFluid, fire useOnBlock, pot/cake onUseWithItem в Survival/Adventure,
обе руки, reset/end/disabled/dead/excluded modes и vanilla continuation.
Unsafe создаёт синтетические Player/ClientWorld, getBlockState возвращает
камень; единственный CANDLES tag задан fixture, поскольку preLaunch идёт
до datapack/sync. canPlace заменён счётчиком с false: шесть проходов
показывают возврат к vanilla, а не успешную клиентскую установку.
Нет подмены production gate/Mixin, но настоящие prediction/network/HUD/
рендер/ghost blocks не проверены. Probe заканчивается до открытия окна.

Воспроизводимая изоляция GameTest; `stage` = baseline/expanded/verified/final,
для первых трёх оставить только NoBlockPlacingGameTest:

```groovy
allprojects {
    afterEvaluate {
        def stage = 'final'
        def stem = "no-block-placing-review-${stage}"
        def resources = project.file("build/${stem}-resources")
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.NoBlockPlacingGameTest', 'com.cursecycle.LockedContainersGameTest']
        resources.mkdirs()
        new File(resources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([resources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        tasks.named('test') { outputs.upToDateWhen { false }; reports.junitXml.outputLocation = layout.buildDirectory.dir("reports/${stem}-junit") }
        loom.runs.gametest.runDir = "build/gametest-${stem}-20261104"
        loom.runs.gametest.vmArg "-Dfabric-api.gametest.report-file=../reports/${stem}.xml"
    }
}
```

После изолированного запуска восстановить43entrypoints:
`.\gradlew.bat processGametestResources --offline --rerun-tasks`.
Проверить `python build/no-block-placing-review-verify.py` и оба production
JAR: GameTest/validation/probe не должны попасть в binary/sources.

Стоимость ограничена статически: server gate O(G+A), client O(C), где
G/A/C — длины GLOBAL/PERSONAL/synced списков; один lookup реестра,
fire читает один blockstate только после active gate. Pot/cake выполняют
ограниченный vanilla выбор перед перехватом. Пустой/null actor выходит
раньше state lookup. Нет своего tick/map/snapshot/rollback/packet/NBT;
общий dispatch и sync по-прежнему стоят времени. MSPT/FPS/allocations и
retained memory не измерены, functional counts не являются профилем.

Открыты NBP-L01..L04: настоящий клиент и ручные предметные края/пары,
dedicated+2клиента+late3/latency/ghost, полный новый JVM active/ended,
сопоставимый нагрузочный профиль. Прежние ограничения других этапов
сохранены. Физические порталы/End/keepInventory, рамка/картина/вагонетка,
дерево из саженца/поршень/моб/реальный redstone и пары с fragile_floor,
no_block_breaking/inventory_lockdown остаются ручными сценариями ниже.

## История реализации — 27.10.2026

«Ну ты же не строитель», EASY/GLOBAL. Дата: 27.10.2026.

## Реализация

Сервер проверяет текущие включённые GLOBAL/PERSONAL ID через
`ChallengeActivity.active`; клиент использует уже синхронизированные эффективные
ID, чтобы не предсказывать запрещённую установку. В Creative/Spectator и на
мёртвом игроке собственное ограничение выключено. Таймеров, снимков инвентаря,
дополнительных пакетов и собственных сохраняемых данных нет. После завершения
перехваты сразу пропускают штатное действие; активность сохраняет общая система.

- `BlockItem.place(ItemPlacementContext)` отменяется до первой записи блока,
  расхода предмета, критериев, звука и статистики. Этот путь включает семена,
  снег, обе половины двери/кровати и кувшинку после её отдельного raycast.
- `BucketItem.placeFluid` и `PowderSnowBucketItem.placeFluid` возвращают false
  до установки, waterlogging, испарения в Nether и опустошения ведра. Пустое
  ведро использует другой путь; раздатчик передаёт null вместо игрока.
  Ведро с рыбой также сохраняется: размещение воды не допускается, поэтому
  `onEmptied` с созданием рыбы не выполняется.
- Огниво/огненный заряд отменяются до звука и расхода, когда создают новый
  огонь. Зажигание существующих костра, свечи и торта со свечой разрешено как
  изменение свойств существующего блока.
- Посадка в пустой горшок и добавление свечи на торт обходят `BlockItem.place`:
  два точечных перехвата отменяют их до первой записи/расхода. Извлечение
  растения и поедание торта используют другие пути и не запрещены.

Сигнатуры сверены с локальным байткодом Minecraft/Yarn 1.21.1+build.3 и
документацией Yarn для
[BlockItem](https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/item/BlockItem.html)
и [BucketItem](https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/item/BucketItem.html).
`UseBlockCallback` не подходит: он отменил бы также открытие существующих блоков.

## Автоматическое покрытие

`NoBlockPlacingChallengeTest` проверяет регистрацию, русские метаданные,
сложность/область, пул выбора, пару с no_sprint и NBT GLOBAL/PERSONAL ID.

Четыре `NoBlockPlacingGameTest` с применёнными серверными Mixin проверяют:

- Реальный ванильный контроль без испытания, затем три повторные попытки
  каждой рукой: камень, сундук, факел, дверь, кровать, семена, саженец, снег,
  рыхлый снег, огниво, огненный заряд. Снимок области подтверждает отсутствие
  частей многоблока, сохранены count/components/прочность и статистика USED.
  Отдельный контроль/запрет посадки в горшок и добавления свечи на торт.
- `interactionManager.interactItem` с настоящим raycast в воздухе: вода,
  лава, ведро с треской, кувшинка, обе руки. Контроль меняет мир; запрет
  сохраняет мир, предмет/components и статистику. Вода не waterlog-ит плиту;
  null-player путь автоматизации разрешён. Прямой fluid-путь рыхлого снега
  запрещён, пустое ведро собирает источник воды.
- Верстак/сундук/бочка и существующая дверь работают даже с блоком в руке.
  Мотыга, топор и лопата меняют землю/бревно; огниво и огненный заряд зажигают
  существующие костёр/свечу/торт со свечой. Костная мука растит пшеницу, лодка
  создаётся как сущность, блок добывается. Пара locked_containers сохраняет
  чужой запрет сундука.
- Несколько игроков и поздний вход, смена режимов, настоящий CanPlaceOn в
  Adventure с контрольной установкой после завершения, enabled=false,
  PERSONAL и дубликат GLOBAL/PERSONAL, мёртвый объект/респаун, Nether/Overworld.
  Активный ID записан и прочитан с диска, состояние заменено загруженным
  экземпляром в том же JVM. Повторный вход того же UUID, двойное испытание
  no_sprint, завершение офлайн и сохранение запрета спринта. Команды
  `/fdc list`, `/fdc activate no_block_placing`, `/fdc active` выполнены.

Временный preLaunch probe в `build/no-block-placing-client-probe` загружает
семь целевых классов через Fabric CLIENT и проверяет наличие всех внедрённых
обработчиков. Он завершает процесс до открытия окна. Это проверка применения
Mixin на клиенте, а не визуальная проверка предсказания/интерфейса.

## Результаты запуска

Итог автоматических запусков: `.\gradlew.bat build runGametest --offline`
— BUILD SUCCESSFUL, 57 s; 89 JUnit и 36/36 GameTest,
failures/errors/skipped = 0. Ранее отдельная сборка прошла за 15 s.
`.\gradlew.bat runClient --offline -I build/no-block-placing-client-probe/init.gradle`
— BUILD SUCCESSFUL, 13 s, маркер
`NO_BLOCK_PLACING_CLIENT_PROBE_PASSED`.

Первый запуск в песочнице не получил доступ к wrapper zip.lck общего кэша;
последующие команды с разрешённым доступом прошли. Lock не удалялся.
До итогового прогона исправлены ошибки стенда: пакет BlockPredicatesChecker,
коллизия игрока с контрольной кувшинкой и случайный рост саженца от костной
муки (заменён гарантированным ростом пшеницы).

## Проверки в игре, которые ещё нужно выполнить

1. Два настоящих клиента: активация, HUD/меню с русским текстом, обе руки,
   Shift и быстрое чередование установок, включая двери/кровати, огонь,
   жидкости, водонаполненные блоки и Nether-портал. При задержке и на границе
   активации не должно оставаться ghost blocks или исчезать ресурсы.
2. Adventure: блок с CanPlaceOn реально ставится до активации и после завершения,
   при активном испытании запрещён. Creative/Spectator, затем снова Survival.
3. Пустые вёдра, станции, контейнеры, двери, инструменты, костная мука на дереве;
   отдельно вагонетки, рамки, картины и стойки для брони. Проверить извлечение
   растения из горшка и поедание торта, взаимодействия котлов и редстоун-блоков.
4. Настоящий раздатчик, рост/течение жидкости, поршень и действия мобов должны
   работать. Автотест автоматизации проверяет её null-player путь установки
   жидкости, а не весь редстоун-механизм раздатчика.
5. Смерть с keepInventory=false/true и реальным дропом, респаун, поздний вход,
   выход/вход, порталы Nether/End, пары no_sprint/no_block_breaking и
   inventory_lockdown. Последние две пары не выполнены отдельными GameTest.
6. Полностью перезапустить процесс с сохранённым активным испытанием; затем
   завершить через `/fdc activate no_sprint`, сохранить/перезапустить и вернуть
   офлайн-игрока. Дисковая проверка в одном JVM не заменяет этот сценарий.

Команды `/fdc stop` нет. Ручные сценарии выше не обозначены как пройденные.
