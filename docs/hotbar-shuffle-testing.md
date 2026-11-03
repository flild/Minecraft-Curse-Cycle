# hotbar_shuffle — проверка

## Ревью 2026-11-03, этап 12

Production-код не менялся: подтверждённых дефектов не найдено. Расширен только
`HotbarShuffleGameTest`, обратная связь и игровые ограничения прежние.
Итог ревью — pass_with_limits, механика pass, расходы no_issue_found / static.

Свежие прогоны (каждый в новом runDir, все exit 0):

| Прогон | JUnit | GameTest | Лог / XML в build |
|---|---|---|---|
| baseline | 149/149, 54 suites | 1/1 | hotbar-shuffle-review-baseline.log; reports/hotbar-shuffle-review-baseline.xml |
| expanded | 149/149, 54 suites | 3/3 | hotbar-shuffle-review-expanded.log; reports/hotbar-shuffle-review-expanded.xml |
| final | 149/149, 54 suites | 3/3 | hotbar-shuffle-review-final.log; reports/hotbar-shuffle-review-final.xml |

JUnit XML каждого прогона: `build/reports/hotbar-shuffle-review-<label>-junit/`.
Исходный production HEAD: `0de3d997e935f4d3c41ac5ef1fcb6096e05dcac7`.
Финальный код отличается только предметным тестом и этим документом.

Команда финального прогона:

```powershell
.\gradlew.bat build runGametest --offline -I build/hotbar-shuffle-review-final.init.gradle
.\gradlew.bat processGametestResources --offline --rerun-tasks
python build/hotbar-shuffle-review-verify.py
git diff --check
git diff --cached --check
```

Воспроизводимое содержимое изолирующего init (не входит в JAR). Для повторного
прогона выбрать новое имя runDir и соответствующие уникальные пути результатов:

```groovy
allprojects {
    afterEvaluate {
        def resources = project.file('build/hotbar-shuffle-review-final-resources')
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.HotbarShuffleGameTest']
        resources.mkdirs()
        new File(resources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([resources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        tasks.named('test') { outputs.upToDateWhen { false }; reports.junitXml.outputLocation = layout.buildDirectory.dir('reports/hotbar-shuffle-review-final-junit') }
        loom.runs.gametest.runDir = 'build/gametest-hotbar-shuffle-review-final-20261103'
        loom.runs.gametest.vmArg '-Dfabric-api.gametest.report-file=../reports/hotbar-shuffle-review-final.xml'
    }
}
```

Три предметных GameTest:

- `intervalsScreensUseAndLifecycle`: фактический END_SERVER_TICK, 99/100,
  поздний вход, modes, GLOBAL/PERSONAL, real respawn и alive replacement,
  целые стеки/count/components/durability/empty, еда/лук/offhand, сундук/cursor,
  настоящие C2S click/stale revision и S2C slot packets; пары с lockdown,
  damage_slot_drop и no_sprint. Контроль дропа из текущего инвентаря; snapshot
  остальных слотов берётся непосредственно перед граничным вызовом.
- `packetBudgetOwnershipDimensionsAndDisk`: 32 start/stop/double-same-ID цикла,
  disable/re-enable, повторный start и новая активация того же ID, overlap scopes;
  99/100 после повторной активации. На чистом playerScreenHandler ровно один
  пакет на изменённый hotbar slot (не более 9), ни одного для пустого хотбара,
  ни одного дополнительного после duplicate. Настоящие server teleport в Nether
  и End сохраняют объект таймера и его остаток. PlayerManager сохраняет playerdata,
  PersistentStateManager.save/readNbt загружает активное/завершённое состояние;
  JOIN без подмены инвентаря восстанавливает фактические стеки и XP123, но
  начинает новый полный интервал. Offline end при пустом сервере не оживляет
  таймер/раскладку; no_sprint остаётся. Reflection только читает размеры map и
  identity таймера; ticks/deadlines не подменяются. DISCONNECT вызывается через
  Fabric event invoker, затем PlayerManager.remove и освобождение канала.
- `foodAndCraftingAroundShuffle`: реальный interactItem начинает яблоко;
  перемешивание отменяет использование без расхода и без записи one_time_menu.
  Последующий finishUsing расходует одно яблоко и фиксирует историю; снятие
  shuffle не стирает её. C2S крафт до/после границы выдаёт по 4 доски, списывает
  по одному уровню, не меняет cursor/input при shuffle; после снятия shuffle
  налог по-прежнему блокирует крафт при XP0. Это только адресная пара для этапа12,
  самостоятельное ревью crafting_tax не начиналось.

Оценка стоимости статическая, функциональный счётчик пакетов не является
профилем latency/MSPT. Для P подходящих UUID — O(P) ephemeral timers на сервер,
WeakHashMap не удерживает сервер через values. Один onTick: applicability O(G+A),
lookup/clock O(1); до границы нет копирования стеков или своей отправки пакетов.
На границе снимок 9 ссылок, O(9) Fisher–Yates на попытку, проверка ItemStack.areEqual
учитывает стоимость компонентов. При неодинаковом хотбаре вероятность принятия
попытки не менее 8/9 (минимум 9 различных раскладок), ожидается не более 9/8
попытки. Детерминированного лимита повторов нет по требованию равномерного
перевыбора; одинаковый/пустой хотбар возвращается без RNG. Затем 9 setStack,
markDirty и один sendContentUpdates со сканированием S слотов текущего handler,
стоимость сравнений зависит от компонентов. Нет world scan/force-load своего
кода, NBT timers/RNG и дополнительного render/C2S hook. Общие dispatch/scheduler/
20-tick sync и vanilla inventory sync сохраняют цену и не считаются нулевыми.

Ограничения `hotbar_shuffle-L01..L04`: настоящий клиент/HUD/меню и ввод всех
типов кликов/использования, реальные сетевые клиенты и задержки, физические
порталы/обычный death drop, полный новый JVM active/ended/offline end и профиль
нагрузки не выполнялись. Телепорт server API и disk roundtrip в одной JVM их
не заменяют. Подробные шаги в локальном `Промпты/reviews/12-hotbar_shuffle.md`.

Первый обычный PowerShell не стартовал (sandbox-bin ACL access5); разрешённый
запуск вне неисправной песочницы работал. В подготовительном Python helper
кодировка stdin испортила кириллический путь backup; повтор с UTF-8 создал
backup, init был создан до ошибки и baseline Gradle прошёл. Lock-файлы не удалялись.


«Курчу верчу запутать хочу», MEDIUM / GLOBAL. Первый и последующие интервалы —
100 серверных тиков. Смена номера выбранного слота не следует за стеком.

## Реализация

`HotbarShuffleChallenge` использует существующий серверный `onTick`.
Снимок ссылок на девять стеков переставляется Фишером—Йетсом; равновероятные
перестановки с полностью неизменным содержимым отбрасываются. `ItemStack.areEqual`
учитывает количество и компоненты. Для одинакового/пустого хотбара RNG не вызывается.
Переставляются исходные целые стеки, без разделения, объединения или копирования
компонентов. Запись всех девяти слотов и последующая штатная синхронизация
`currentScreenHandler.sendContentUpdates()` выполняются в одном серверном вызове.
Клики обрабатываются на том же потоке, включая сверку ревизии ScreenHandler.

Если выбранное содержимое изменилось при использовании основной руки,
`clearActiveItem()` вызывается до записи слотов. `stopUsingItem()` здесь непригоден:
его `onStoppedUsing` может выпустить стрелу. Неизменившееся содержимое выбранного
слота и использование второй руки не отменяются.

Таймеры привязаны к серверу, UUID и serial активации, не держат ссылки на игроков
и не пишутся в NBT. Вход, настоящий респаун и возврат из исключённого режима
начинают полный интервал. Живое пересоздание сущности при возврате из измерения
сохраняет остаток. Выход/завершение удаляют таймер, выключение очищает все таймеры
этого испытания. Раскладка никогда не откатывается. Активность сохраняется штатным
`CurseCycleState`, сами предметы — ванильным инвентарём.

## Исторические проверки реализации (не доказательство ревью 2026-11-03)

Ниже сохранены прежние результаты реализации; они не входят в свежие числа ревью.

Команды после реализации:

```powershell
.\gradlew.bat build
.\gradlew.bat build runGametest
git diff --check
git diff --cached --check
```

Результат: первый `build` — BUILD SUCCESSFUL, 19 s; итоговый
`build runGametest` — BUILD SUCCESSFUL, 46 s. XML: 84 JUnit,
failures/errors/skipped = 0; 24/24 GameTest, failure/error/skipped = 0.
`git diff --check` и `git diff --cached --check` — без ошибок.
Отчёты: `build/test-results/test/TEST-*.xml`, `build/reports/gametest.xml`.
JUnit: `HotbarShuffleChallengeTest` проверяет границу 99/100, повторный вызов
в том же тике, отсутствие серии догоняющих срабатываний, одинаковые/пустые входы,
отбрасывание перестановок равных стеков и исчерпывающий перебор решений RNG
для трёх позиций: все изменившиеся раскладки имеют одинаковый вес, в том числе
при повторяющихся значениях.

GameTest `HotbarShuffleGameTest.intervalsScreensUseAndLifecycle` изолирован
от других испытаний отдельным batch. Использует реальный END_SERVER_TICK,
зарегистрированные сетевые обработчики игроков и дополнительно прямой `onTick`
на точной границе для проверки повторной доставки. Покрытие:

- `/fdc list`, `/fdc activate hotbar_shuffle`, `/fdc active`, единственная
  регистрация и русское название;
- 99/100 тиков, независимый поздний вход, Survival/Adventure и исключения
  Creative/Spectator, полный интервал после возврата режима;
- целые стеки с разными именами/количествами, отдельно различие только
  количества, только компонентов, повреждённый инструмент среди пустых слотов,
  одинаковый и полностью пустой хотбар;
- выбранный индекс, основной инвентарь, броня, offhand, курсор и внешний контейнер;
- открытый контейнер, реальные исходящие пакеты изменившихся слотов, C2S-клики
  до/после перестановки и устаревшая ревизия без потери/дублирования стеков;
- отмена еды и лука без выстрела/расхода, продолжение использования второй
  руки и неизменившегося выбранного содержимого;
- GLOBAL/PERSONAL с повторным вызовом в одном тике и PERSONAL отдельно;
- пары с inventory_lockdown, damage_slot_drop и no_sprint: автоматическое
  перемещение, ручные ограничения, настоящий урон с одним выпадением из
  актуального инвентаря, запрет спринта;
- смерть и настоящий респаун с keepInventory, отсутствие вмешательства в мёртвого
  игрока, живое пересоздание сущности с сохранением остатка, выход/повторный вход,
  вход после завершения офлайн и отсутствие отката раскладки;
- запись/чтение `CurseCycleState` с диска, сохранение ID и отсутствие таймера/RNG
  в challengeData. Это не полный перезапуск процесса.

Первый запуск сборки в песочнице получил отказ доступа к
`F:\Programs\GradleCache\wrapper\dists\gradle-8.10.2-bin\a04bxjujx95o3nb99gddekhwo\gradle-8.10.2-bin.zip.lck`.
Повтор с разрешённым доступом использует тот же кэш и зависимости.
При подготовке новых GameTest исправлены обращения к отсутствующему setter,
обработка IOException и преждевременное чтение исходящих пакетов: EmbeddedChannel
требовал flush внутри серверного пакетного batch. Дополнительно изолировано
новое выпадение от уже существовавших ItemEntity и исправлен PERSONAL-список
стенда на изменяемый: CycleManager штатно вызывает removeIf. Тестовые игроки
перенесены через настоящий teleport, чтобы подтверждение входа не возвращало
их в точку спавна. Все эти исправления находятся в новом тесте.

## Ручные проверки — не выполнены

1. Одиночный клиент и два настоящих сетевых клиента: HUD/меню, точные тексты,
   предмет в руке, применение пакетов и отсутствие рассинхронизации при задержке.
2. Девять подписанных стеков, выбран индекс 3. Проверить несколько интервалов
   в закрытом/открытом инвентаре и сундуке со стеком на курсоре; быстрые клики,
   Shift, цифровые клавиши, перетаскивание и книга рецептов около границы.
3. Еда, лук, арбалет, щит, трезубец: смена выбранного содержимого на границе
   прекращает незавершённое использование, не расходует новый стек. Отдельно
   одинаковые предметы с разными компонентами/количеством и offhand.
4. Настоящие порталы во все измерения, в том числе возврат из Края; остаток
   интервала сохраняется. Автоматический тест живой замены сущности не заменяет
   прохождение портала и загрузку клиентского мира.
5. Смерть с keepInventory=false и обычным дропом, переподключение настоящего
   клиента с восстановлением его инвентаря. Стенд повторного входа проверяет
   таймер; тестовая раскладка при подключении задаётся заново.
6. `/fdc activate "double_challenge:hotbar_shuffle,no_sprint"`, затем
   `/fdc activate no_sprint`: проверить полный интервал в паре, прекращение
   перестановок и сохранение запрета спринта. Дополнительно slippery_hands.
7. Полностью закрыть и открыть мир/выделенный сервер при активном испытании:
   ID/раскладка сохраняются, первый интервал после входа полный. Завершить через
   `/fdc activate no_sprint`, сохранить, перезапустить и вернуть офлайн-игрока:
   перестановок нет. Команды `/fdc stop` нет.
