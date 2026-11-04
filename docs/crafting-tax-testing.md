# crafting_tax — Крафт по талонам

Minecraft 1.21.1 / Yarn 1.21.1+build.3 / Fabric API 0.102.0+1.21.1,
Fabric Loader 0.16.0, Java 21. EASY/GLOBAL. Цена — один уровень за
фактическое исполнение рецепта 2×2/3×3, независимо от размера результата.

## Ревью 2026-11-04 — завершение этапа13

Входной и проверенный игровой HEAD `f1be58466cfd22cd4e6410ac244e092717b614ff`,
main, чистое рабочее дерево. Он уже содержит подготовленное исправление
feedback-кэша и расширение восьми предметных GameTest. Это обнаружено через
`git diff bb10630 HEAD`, а не принято из старого описания состояния.
Новых дефектов в текущем запросе не выявлено; игровой код повторно не менялся.
Итог **pass_with_limits**, механика **fixed**, расходы **fixed / static**:
подтверждено ранее внесённое исправление. Полная матрица и все ограничения —
[отчёт13](../Промпты/reviews/13-crafting_tax.md). Отдельный локальный коммит
текущего запроса обновляет этот документ; итоговый hash записан в relay-state.

Свежие команды этого запроса:

```powershell
.\gradlew.bat build runGametest --offline -I build/crafting-tax-review-resume.init.gradle
.\gradlew.bat processGametestResources --offline --rerun-tasks
python build/crafting-tax-review-resume-verify.py
git diff --check
```

- `build runGametest`: exit0,49s; 149/149 JUnit,54 suites; 11/11 GameTest
  (8 crafting_tax +3 hotbar_shuffle), failures/errors/skipped0, normal shutdown.
  Лог `build/crafting-tax-review-resume.log`, XML
  `build/reports/crafting-tax-review-resume.xml`, JUnit
  `build/reports/crafting-tax-review-resume-junit/`.
- Init скопирован из приведённого ниже final с заменой `review-final` на
  `review-resume` и даты runDir на `20261104`; новый мир
  `build/gametest-crafting-tax-review-resume-20261104`, отдельные XML/JUnit/resources.
- Restore exit0; исходные43 entrypoints восстановлены. Verifier exit0:
  SHA4 постоянных документов+SHA ТЗ, metadata6/6/registration1, свежие XML/JUnit,
  normal shutdown, source/built metadata equal, два production JAR без тестов/probes.
  Логи `build/crafting-tax-review-resume-restore.log` и
  `build/crafting-tax-review-resume-verifier.log`.
- Свежий `javap -c -p` текущего mapped JAR подтвердил whole-result removal,
  canTakePartial/capacity guard, отдельные quickMove iterations и C2S checks.
  `build/crafting-tax-review-resume-vanilla.txt`, `build/crafting-tax-review-resume-network.txt`.

crafting_tax-F01 уже исправлен во входном f1be584: `setEnabled(false)` очищает
NOTICES; последний `onStop` удаляет пустой server bucket. Свежие T5/T6
подтвердили отсутствие удержания после disable,32циклов и независимость UUID.
Нового red-прогона старого кода в этом запросе не было. Общие contracts,
Mixin, оплата, рецепты и зависимости не менялись; прежние limits сохраняются.

Обычный клиент/HUD/menu/физический ввод, настоящая сеть/кооператив, полный
restart JVM и MSPT/FPS/allocations/retained-memory profile не выполнялись.
Disk round trip и EmbeddedChannel подтверждают только описанный серверный путь.

## Реальные пути

- `CraftingTaxResultSlotMixin.canTakeItems` проверяет право до выдачи;
  HEAD `CraftingResultSlot.onTakeItem` вызывает `addExperienceLevels(-1)`
  один раз на расходуемый комплект. `onCrafted/onQuickTransfer` — статистика,
  дополнительной оплаты там нет.
- `CraftingResultInventory.removeStack(index, amount)` в этой версии удаляет
  весь результат. Поэтому правый клик/Q тоже получают полный выход.
  `Slot.tryTakeStackRange` отказывает при недостаточном месте курсора:
  результат не допускает partial insert. SWAP допускается ванилью только
  при совместимых условиях; PICKUP_ALL исключает result inventory,
  QUICK_CRAFT не вставляет в result slot, CLONE доступен лишь Creative.
- В цикле shift-click `ScreenHandler.internalOnSlotClick` проверяет
  `canTakeItems` перед циклом, затем вызывает `quickMove` повторно.
  `CraftingTaxQuickMoveMixin` проверяет XP и полную вместимость на каждой
  итерации обоих handlers. До 36 слотов, с учётом компонентов и lockdown;
  один результат может размещаться в нескольких неполных стеках.
- `ServerPlayNetworkHandler.onClickSlot` исполняет действия на серверном
  потоке, проверяет syncId, Spectator, canUse и диапазон. Несовпавшая revision
  вызывает полную синхронизацию, сама по себе не является replay token.
  Повтор после исчерпания результата не создаёт выдачи/оплаты; новый реально
  расходуемый комплект оплачивается как новое исполнение. Собственного
  античита/дедупликации всех одинаковых кликов не добавлено.
- Ванильный `onTakeItem` обрабатывает расход и возвратную тару синхронно.
  Нет отложенной выдачи, кредита крафта или rollback снимка XP/предметов.
  Это атомарность штатного последовательного пути, не транзакционное
  восстановление после исключения произвольного стороннего мода.
- Server/alive/Survival/Adventure/enabled/effective GLOBAL+PERSONAL — через
  `ChallengeActivity`. Creative/Spectator не облагаются. XP сохраняет Minecraft,
  активность — общий overworld PersistentState. Собственного persisted state нет.
- Feedback: UUID + activationSerial + server ticks, actionbar «Нужен 1 уровень
  опыта» не чаще раза в40 тиков в текущей активации. Stop/leave/respawn удаляют
  UUID и пустой bucket; disable очищает карту. Weak server key, без ссылок
  на Player/World в values. Start не сбрасывает throttle повторного вызова.
- Другие рабочие станции/Crafter не используют эти result/quickMove paths.
  Runtime отдельно проверена печь; остальные исключения подтверждены областью
  хуков и чтением кода, а их полные игровые процессы остаются ручной проверкой.

Свежий bytecode сохранён в `build/crafting-tax-review-resume-vanilla.txt` и
`build/crafting-tax-review-resume-network.txt`. Получен через `javap -c -p` из
текущего mapped Minecraft JAR в `.gradle/loom-cache/minecraftMaven`.
Клиентских классов/кастомных сетевых пакетов налог не добавляет.

## Предыдущая подготовка стенда — исторический журнал

Команды из корня репозитория:

```powershell
.\gradlew.bat build runGametest --offline -I build/crafting-tax-review-final.init.gradle
.\gradlew.bat processGametestResources --offline --rerun-tasks
python build/crafting-tax-review-verify.py
git diff --check
```

Сохранённый final init (UTF-8 без BOM); для повторного запуска заменить
`final` и дату уникальными именами runDir/XML/JUnit/resources, не использовать
старый мир как новое доказательство:

```groovy
allprojects {
    afterEvaluate {
        def resources = project.file('build/crafting-tax-review-final-resources')
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.CraftingTaxGameTest', 'com.cursecycle.HotbarShuffleGameTest']
        resources.mkdirs()
        new File(resources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([resources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        tasks.named('test') { outputs.upToDateWhen { false }; reports.junitXml.outputLocation = layout.buildDirectory.dir('reports/crafting-tax-review-final-junit') }
        loom.runs.gametest.runDir = 'build/gametest-crafting-tax-review-final-20261103'
        loom.runs.gametest.vmArg '-Dfabric-api.gametest.report-file=../reports/crafting-tax-review-final.xml'
    }
}
```

Ниже сохранены результаты предыдущей подготовки, не новые запуски текущего запроса. Общий префикс файлов:
`build/crafting-tax-review-`; XML/JUnit находятся в `build/reports/`.

| Попытка | Команда / изменённый snapshot | Exit | GameTest | JUnit | Лог / XML |
|---|---|---|---|---|---|
| baseline | build runGametest --offline -I build/crafting-tax-review-baseline.init.gradle; исходный HEAD | 0,38s | 4/4 | 149/149,54 suites | baseline.log / baseline.xml |
| red | runGametest --offline -I build/crafting-tax-review-red.init.gradle; новые cache-тесты, старый production | 1,31s | 4/6, две ожидаемые cache-регрессии | не запускались | red.log / red.xml |
| fixed | build runGametest --offline -I build/crafting-tax-review-fixed.init.gradle; cache fix | 0,45s | 6/6 | 149/149,54 suites | fixed.log / fixed.xml |
| expanded | build runGametest --offline -I build/crafting-tax-review-expanded.init.gradle; disk/packets/pairs, feedback через click | 0,35s | 8/8 | 149/149,54 suites | expanded.log / expanded.xml |
| final | build runGametest --offline -I build/crafting-tax-review-final.init.gradle; дополнительно exact THROW/drop cleanup + HS pair suite | 0,48s | 11/11:8 CT+3 HS | 149/149,54 suites | final.log / final.xml |

Во всех успешных XML failures/errors/skipped=0. Red содержит ровно
`Disable must clear feedback before lifecycle dispatch is skipped` и
`Last stop releases empty server bucket`. Ошибок fixture/GameTest сверх
этих воспроизведённых дефектов не было. Restore exit0:
`build/crafting-tax-review-restore.log`; 43 исходных entrypoints восстановлены.
Verifier проверяет SHA5, неизменность восьми защищённых документов, metadata6,
одну регистрацию, counts всех пяти XML/четырёх JUnit-наборов, normal shutdown,
равенство source/built GameTest metadata, чистоту binary/sources JAR и Git scope.
Его результат — `build/crafting-tax-review-verifier.log`.

Первый shell call не создал процесс: sandbox-bin ACL access5. Последующие
разрешённые вызовы вне неисправной песочницы работали; lock не удалялся.
Два read-only чтения ошиблись путями JUnit/LockedContainers Mixin;
`rg --files` нашёл реальные файлы. Это не попытки игровых тестов.
`.harness/` не менялся. Никаких staging/commit/изменений указателей эстафеты.

## Что исполняют восемь предметных GameTest

1. `individualRoutesAndRepeatedPackets`: ноль + дробная полоска, PICKUP
   left/right, shift, number/F, THROW, отрицательные collect/clone;
   4доски/1уровень, дробный XP/totalExperience, пустой повторный C2S,
   точная/недостаточная ёмкость курсора, occupied swap.
   THROW проверяет ровно4 выпавших доски и удаляет только созданные этим
   действием ItemEntity.
2. `bulkCapacityRecipeBookAndRemainders`: обе сетки, shift до лимита
   XP/места, полный/частичный inventory, split merge; настоящий торт с тремя
   вёдрами без расхода при отказе и с одним платежом при успехе;
   книга рецептов бесплатна, печь без налога, inventory_lockdown capacity.
3. `activityLifecycleAndDiskState`: команды с server source, modes,
   PERSONAL/overlap/late player, disable, dead gate, реальный respawn,
   server teleport Nether/Overworld, общий disk round trip, Fabric DISCONNECT,
   same UUID reconnect, no_sprint и bitter_experience без урона от платежа.
4. `feedbackIsLimitedToFortyServerTicks`: реальные slot clicks и actionbar
   packets, повтор в том же tick, тики39/40. Настоящее серверное время,
   без подмены deadline; это не видимое клиентом отображение.
5. `disabledFeedbackCannotRetainDepartedPlayers`: две записи→disable→0,
   no reallocate, отключение ушедшего игрока, включение без смены serial
   даёт первую подсказку немедленно.
6. `feedbackOwnershipAndRepeatedCleanup`: 32start/stop double-same-ID цикла,
   повторный start без spam, две независимые записи, последний stop→нет bucket,
   Fabric DISCONNECT сохраняет чужой throttle, reconnect и replacement cleanup.
   Reflection только читает map, production state не подменяется.
7. `paidOutputAndExperienceSurviveActualDiskLoad`: настоящий PlayerManager
   save/load и PersistentState disk, XP7→6→5, progress.625, total321,
   4→8досок, diamonds7/HP17. Активный вход платит за следующий craft;
   конец офлайн/пустой сервер сохраняют оплаченный XP и дают бесплатный
   следующий результат12досок. Никакой повторной инициализации инвентаря/XP.
8. `actualWorkbenchPacketsAndContainerPair`: реальное открытие верстака,
   C2S PICKUP/SWAP/QUICK_MOVE, wrong syncId и следующий unpaid craft,
   несовместимые компоненты стеков, реальный сундук заблокирован;
   снятие налога сохраняет locked_containers. blind_hud не меняет server gate;
   визуальное скрытие HUD этим тестом не проверено.

Исторический final запускал все3; текущий resume повторно выполнил все3 `HotbarShuffleGameTest`: сохранение cursor/input,
крафт до/после shuffle по1уровню, отсутствие потери результата; ранее проверенные
limits HS сохраняются. Это адресная совместимость, не новый полный аудит HS/LC.

## Накладные расходы

Оценка static: gate O(G+A) по effective IDs, canPay/pay несколько раз на действие;
shift preflight максимум36слотов с компонентным сравнением и lockdown gate
на слот. Для N исполняемых рецептов стоимость preflight O(N×36×(G+A+C)),
C — стоимость сравнения компонентов. Дополнительно остаются ванильные recipe
lookup/insert/remainders/sync, которые не приписываются налогу.

Кэш O(P) для UUID с отказом текущего сервера; stop/leave/respawn O(1) в среднем,
disable освобождает buckets всех серверов. Нет своего tick scan/render hook,
world scan, force-load, повторной регистрации или сохранения NBT на отказ.
Сообщения ограничены1/40ticks/UUID/activation; успешные крафты не спамят.
Исправление устраняет удержание после disable. MSPT/FPS, allocations и retained
memory под нагрузкой не измерялись; счётчик размера map не заменяет профиль.

## Невыполненные ручные проверки

- L01: обычный клиент/OP2, точные русские HUD/menu/actionbar, все физические
  жесты и recipe book, остальные станции/Crafter, keepInventory false/true,
  физические порталы, blind_hud. Повторить manual_tests[0..6]: четыре доски
  за1уровень; ноль/недостаточное место без расхода; торт с возвратом3ведер;
  выключение без refund и со вторым испытанием.
- L02: dedicated +2Survival +late third, задержка/быстрые клики/XP sync,
  независимость платежей/подсказок при уходе одного. Нет ghost/loss/duplicate;
  EmbeddedChannel и server C2S не доказывают доставку/применение клиентом.
- L03: полностью закрыть JVM и открыть active/ended/offline-end/empty-server.
  Сохранённые output/XP/foreign items остаются; следующий craft стоит1либо0,
  без replay/refund. Текущий disk test выполняется в одной JVM.
- L04: одинаковые версии/мир/P/G/A/N/components/прогрев/длительность;
  inactive/active/double, полный inventory, длительные cycles/reconnect.
  Профилировать отдельно mod gates/capacity/cache и vanilla recipes/network.
  Не объявлять нулевой overhead.

Все предыдущие ограничения эстафеты сохраняются; all_debuffs-Q01:
WITHER и INSTANT_DAMAGE исключены, импульсов нет.

## Историческая реализация — не свежие доказательства

Ранее записаны build36s, итоговый build+GameTest69s,86JUnit/28GameTest,
из них4 CT. Тогда исправлялись fixture верстака без world context и ожидание
OverlayMessage вместо GameMessage overlay=true; sandbox отказал в доступе
к wrapper zip.lck до разрешённого запуска. Эти цифры не засчитаны в ревью.
