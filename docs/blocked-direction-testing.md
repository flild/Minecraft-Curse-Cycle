# Туда нельзя — проверка ограничения направления

`blocked_direction`: EASY / GLOBAL. Название и оба описания взяты из ТЗ.
Сервер и клиенты должны использовать эту сборку: в штатный `sync_state`
добавлено поле выбранного направления. Отдельных сообщений и окон нет.

## Реализация

- `BlockedDirectionChallenge.reconcile` выбирает один из трёх enum через
  серверный `random.nextInt(3)`. Выбор и `activationSerial` хранятся в
  `CurseCycleState.challengeData.blocked_direction`; запись помечает состояние
  dirty. Это реальные сохраняемые данные, а не `Challenge.writeNbt` singleton.
- `CycleManager.activateChallenge` создаёт выбор до обхода игроков, в том числе
  на пустом сервере. `updateSchedule` и отправка синхронизации сверяют состояние
  после загрузки, удаления/отключения ID и завершения без `onStop`. Удаляется
  только запись этого испытания. Выход, респаун, режим и измерение выбор не меняют.
- `NetworkHandler` отправляет выбор в том же пакете, что эффективные включённые
  GLOBAL/PERSONAL ID. Посторонним получателям передаётся пустое значение.
  `ClientDataHolder` обновляет ID и выбор вместе, очищает выбор при завершении,
  некорректном значении и штатном JOIN/DISCONNECT reset.
- Использован существующий клиентский `InvertedKeyboardInputMixin` на RETURN
  `KeyboardInput.tick`: сначала `InvertedKeyboardInput.apply`, затем
  `BlockedDirectionInput.apply`. Отдельного конкурирующего Mixin нет, поэтому
  порядок задан прямыми вызовами. [Yarn 1.21.1+build.3](https://maven.fabricmc.net/docs/yarn-1.21.1%2Bbuild.3/net/minecraft/client/input/KeyboardInput.html)
  подтверждает сигнатуру `tick(boolean, float)` и поля ввода; локальный `javap`
  дополнительно проверил фактический путь чтения и потребления этих полей.
- Оси KeyboardInput каждый тик вычисляются заново. Ограничивается знак итоговой
  компоненты: LEFT — положительный sideways, RIGHT — отрицательный sideways,
  FORWARD — положительный forward. Встречные клавиши сохраняют нулевой результат.
  Другая ось, отрицательный forward, jumping и sneaking сохраняются. Преобразование
  идёт до ванильной нормализации движения; нет изменения скорости или позиции.
- Применимость: синхронизированный ID и выбор, живой неудалённый текущий локальный
  игрок/input, мир и interactionManager, Survival/Adventure. При наличии транспорта
  или полёте на элитрах блокировка не применяется; самостоятельная инверсия при
  активном inverted_keyboard продолжает работать по своим правилам.
- Мышь, назначения клавиш, прыжок и вертикальное управление не перехватываются.
  Течение, инерция и отбрасывание не обрезаются: обработчик меняет только ввод.
  Серверных телепортаций и штрафов нет. Против модифицированного клиента,
  игнорирующего согласованный ввод, отдельной защиты это не создаёт.
- В существующем HUD рядом с названием добавлена строка «Нельзя: …» с поправкой
  высоты. Штатное краткое описание сохранено. Актуальное значение добавлено к
  полным правилам существующего меню; после завершения оно исчезает.

## Автоматические результаты — 27.10.2026

- `.\gradlew.bat build runGametest --offline`: BUILD SUCCESSFUL, 49s.
  XML: 106 JUnit, 43/43 GameTest, failures/errors/skipped = 0.
  Добавлены шесть JUnit и два GameTest. Лог: `build/blocked-direction-verification.log`.
- После окончательной правки HUD `.\gradlew.bat build --offline`:
  BUILD SUCCESSFUL, 11s; все 106 JUnit прошли повторно.
  Лог: `build/blocked-direction-final-build.log`.
- JUnit: точные метаданные и double_challenge; все три исхода равновероятного
  выбора, один вызов RNG на активацию, отсутствие нового вызова при NBT-загрузке,
  новая активация, старый serial, некорректный enum, PERSONAL/дубликаты,
  отключение и очистка только своей записи. Ввод: 64 сочетания четырёх направлений,
  прыжка и приседания, три масштаба, все три запрета с инверсией и без неё,
  повторное ограничение. Режимы, смерть, транспорт, элитры, отсутствие синхронизации,
  завершение, reset и динамический текст проверены отдельно.
- GameTest на выделенном сервере: настоящие S2C-пакеты и encode/decode штатного
  codec; одинаковый выбор двум игрокам и позднему вошедшему, четыре режима,
  замена объекта при респауне, телепортации в Nether/End/Overworld, возвращение
  того же UUID и завершение офлайн. Реальный файл PersistentStateManager прочитан
  и восстановлен в том же JVM, enum и serial сохранились без нового RNG.
  Проверены новая активация, пары с inverted_keyboard/no_sprint, сохранение
  запрета спринта после завершения, PERSONAL/GLOBAL и отключение на сервере.
  Пустая онлайн-группа получает выбор до первого входа; устаревшая запись удаляется
  через updateSchedule без callback игрока. Выполнены `fdc list`,
  `fdc activate blocked_direction`, `fdc active` настоящим dispatcher.
- `.\gradlew.bat -I build/blocked-direction-client-probe/init.gradle runClient --offline`:
  BUILD SUCCESSFUL, 18s, `BLOCKED_DIRECTION_CLIENT_PROBE_PASSED`.
  Fabric CLIENT применил настоящий Mixin; 6144 вызова `KeyboardInput.tick`
  проверили все три направления и отсутствие выбора, инверсию и её отсутствие,
  пропуск блокировки, 64 сочетания клавиш, замедление и три последовательных тика.
  Проверены float-компоненты, флаги, jump/sneak, текущие переназначенные клавиши
  и сохранение назначения после завершения. Два world eligibility gate временно
  подменены тестовыми Mixin: живой мир/игрок не создавался. Стенд и его лог
  находятся в игнорируемом `build/blocked-direction-client-probe`, в JAR не входят.
- Первый focused JUnit выявил строгое сравнение -0.0 и 0.0 в тесте; оно исправлено.
  Первый GameTest-прогон выявил игроков, оставленных предыдущим batch, перед
  проверкой пустого сервера; тест теперь очищает предыдущих embedded-игроков.
  Повторный полный прогон прошёл. Начальный запуск в песочнице не смог открыть
  общий Gradle zip.lck; проверки выполнены с разрешённым доступом, lock не удалялся.
- `git diff --check` и `git diff --cached --check` — без ошибок.

## Ручные сценарии — не выполнены

1. Survival на площадке: `/fdc list`, `/fdc activate blocked_direction`,
   `/fdc active`. Проверить одно штатное уведомление, точные название/описания,
   «Нельзя: …» в HUD и полных правилах, включая double_challenge и малый GUI scale.
   По очереди получить все три выбора новыми активациями (повтор одной стороны
   допустим). Проверить отдельные клавиши, диагонали, встречные клавиши и
   разворот камеры на 90°. Назад и разрешённая часть диагонали работают.
2. Повторить на земле, в воздухе, при плавании и вертикальном управлении.
   Течение воды и отбрасывание в запрещённую сторону должны сохраняться.
   Проверить лодку, лошадь, другой транспорт и элитры: запрета собственного ввода
   blocked_direction там нет. Проверить мышь, текст/GUI и переназначение движения.
3. Два живых клиента и поздний вход: общий выбор, Survival → Creative → Spectator →
   Adventure → Survival, настоящая смерть с обоими keepInventory, порталы и возврат
   из End. Проверить мёртвый объект и замену input, выход/вход и другой сервер.
4. `/fdc activate "double_challenge:blocked_direction,inverted_keyboard"`:
   запрещено итоговое направление после инверсии. Повторить с no_sprint,
   no_jumping и slippery_hands. После `/fdc activate no_sprint` блокировка снята,
   запрет спринта сохраняется, пассивное скольжение/отбрасывание не откатывается.
5. Сохранить и полностью перезапустить JVM с активным испытанием; войти обоими
   игроками и проверить тот же выбор. Затем завершить, пока один игрок офлайн,
   сохранить/перезапустить ещё раз и вернуть его UUID: старого запрета нет.
   Повторить отключение в серверных настройках и штатное завершение по циклу.
   Команды `/fdc stop` нет; завершать активацией другого испытания.

Сохранение/загрузка в одном JVM не доказывает полноценный рестарт сервера,
а клиентский стенд без живого мира не доказывает фактическое движение и рендер.
Эти ограничения остаются ручными проверками.

## Ревью этапа 19 — 04.11.2026

Reviewed production HEAD: `1eea302335baf29ac58761ba0f88def8b4d225b0`.
Production/shared contracts не менялись, подтверждённых дефектов не найдено.
Расширены только предметный GameTest и сохранён CLIENT probe под
`src/clientprobe/blocked_direction/`. Результат: `pass_with_limits`, механика
`pass`, расходы `no_issue_found / static`. Результаты 27.10 выше исторические.

Свежие команды и артефакты (все exit 0):

| Команда | Результат | Лог / XML в build |
|---|---|---|
| `.\gradlew.bat build runGametest --offline -I build/blocked-direction-review-baseline.init.gradle` | 44s; 149 JUnit / 54 suites, 2/2 BD GameTests | `blocked-direction-review-baseline.log`, `reports/blocked-direction-review-baseline.xml`, `reports/blocked-direction-review-baseline-junit/` |
| `.\gradlew.bat build runGametest --offline -I build/blocked-direction-review-expanded.init.gradle` | 49s; 149 JUnit / 54 suites, 3/3 BD GameTests; normal shutdown | аналогичные `expanded` пути |
| `.\gradlew.bat runClient --offline -I src/clientprobe/blocked_direction/init.gradle` | 34s; `BLOCKED_DIRECTION_REVIEW_CLIENT_PASSED checks=46253 ticks=11653` | `blocked-direction-review-client.log` |
| `.\gradlew.bat processGametestResources --offline --rerun-tasks` | 2s; 43 стандартных entrypoints, source == built | `blocked-direction-review-restore.log` |
| `python build/blocked-direction-review-verify.py` | SHA5, metadata6/6, registration1, clientMixin1, оба свежих XML/JUnit, CLIENT marker, restore43, два production JAR без test/probe | `blocked-direction-review-verifier.log` |

Expanded является итоговым серверным прогоном: после него Java не менялись.
GameTest T1 сохраняет реальные S2C+codec двум игрокам и позднему третьему,
режимы, death/respawn, Nether/End/Overworld teleport, disk enum/serial,
rejoin/end/no_sprint и команды теперь OP2. Disconnect вызывает Fabric invoker
и PlayerManager.remove. T2 проверяет пустой сервер, GLOBAL/PERSONAL/dedup,
server-enabled recipients и stale cleanup без onStop.

Новый T3 проверяет 32 цикла same-ID/double/end; на каждой активации 32 повторных
reconcile не вызывают RNG и не ставят dirty. Serial растёт один раз; сохраняется
ровно текущая запись выбора. Настоящие playerdata и PersistentStateManager
save/read/set проверены active/ended/offline-end при пустом сервере, с тем же UUID.
При активной загрузке сохранённый enum/serial не перевыбирается, чужая world-data
сохранена. Named diamonds17, XP7+.625, HP17 и FireResistance сохранены после
каждого rejoin; no_sprint работает после завершения BD. Это одна JVM.

CLIENT probe использует настоящий production Mixin и `shouldBlock`, без test
Mixin/gate replacement. Unsafe создаёт MinecraftClient/options/world/player/manager;
accessors игрока и режима задают alive/vehicle/water/lava/climb/elytra. Сеть и
рендер не запускаются, ClientDataHolder синхронизируется вручную. Выполняются:

- 128 masks × 3 scales × 3 repeated ticks × 5 direction strings × inversion on/off;
  три настоящих enum, invalid и empty. Проверяются signed axes/flags/jump/sneak/
  sprint binding и отсутствие запрещённой компоненты после нормализации вектора.
- Real gates: все режимы, dead/removed/replacement, foreign Input, null world/
  player/manager, new world, unsynced/end/reset; riding/elytra пропускают запрет,
  water/lava/climb/flying ability сами по себе не отменяют его.
- 32 start/end/reset, rebind Up/PageUp и сохранение назначения; текущие русские
  feedback-значения. Реальный порядок inversion→memory→block: синтетическое
  удержание memory не возвращает запрещённую ось; endBD сохраняет memory и IK.

Воспроизведение серверной изоляции: создать init ниже для `phase = 'baseline'`
либо `'expanded'` (исходные два теста baseline — исторический снимок до T3;
с текущими исходниками оба init запустят три теста). Новый runDir менять при
каждом повторе, чтобы не использовать мир старой проверки.

```groovy
allprojects {
    afterEvaluate {
        def phase = 'expanded'
        def resources = project.file("build/blocked-direction-review-${phase}-resources")
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.BlockedDirectionGameTest']
        resources.mkdirs()
        new File(resources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([resources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        tasks.named('test') {
            outputs.upToDateWhen { false }
            reports.junitXml.outputLocation = layout.buildDirectory.dir("reports/blocked-direction-review-${phase}-junit")
        }
        loom.runs.gametest.runDir = "build/gametest-blocked-direction-review-${phase}-20261104"
        loom.runs.gametest.vmArg "-Dfabric-api.gametest.report-file=../reports/blocked-direction-review-${phase}.xml"
    }
}
```

Mapped vanilla KeyboardInput/ClientPlayerEntity/Entity/BoatEntity перечитаны
через `javap -c -p` из текущего mapped 1.21.1 JAR:
`build/blocked-direction-review-vanilla.txt`. Это статическое подтверждение
порядка input→travel/normalization и flags→boat, не настоящая поездка/плавание.

Расходы: input gate O(A), clamp O(1) на input sample, без own state/пакетов/
position/velocity writes. Reconcile вызывается каждый server tick и на каждого
получателя общего sync (~раз в 20 ticks, плюс lifecycle/activation). Стоимость
O(G + U×A) в худшем случае при поиске PERSONAL; при GLOBAL short circuit.
Stable choice не вызывает RNG/markDirty; есть NBT lookup, enum values array,
stream/lambda, штатные dispatch/sync и vanilla movement. HUD добавляет строку
и создаёт feedback-string на render, меню — на modal render; это имеет цену.
FPS/MSPT/allocations/retained memory не измерены; функциональные counters
не профиль производительности. Собственных UUID maps/очередей/Player refs нет.

Открытые ограничения ревью:

- `blocked_direction-L01`: выполнить все пять ручных сценариев выше на обычном
  клиенте (JSON manual_tests[0,2,4,5]): HUD/menu/одна нотификация, camera90°,
  земля/воздух/плавание/vertical, течение/knockback/скольжение, boat/horse/elytra,
  GUI/rebind, no_jumping/no_sprint и обе стороны снятия предметных пар;
  actual death с обоими keepInventory, порталы. Синтетический CLIENT не travel.
- `blocked_direction-L02`: dedicated+2 Survival+late third, latency/start-end/
  разные bindings/режимы/порталы/rejoin/другой сервер; одна сторона всем,
  нет stale/desync, no_sprint остаётся. EmbeddedChannel не настоящая сеть.
- `blocked_direction-L03`: новые JVM active/ended/offlineend/emptyserver,
  reconnect тех же UUID; enum/serial тот же при active, после end запрета нет,
  чужие named17/XP7+.625/HP17/effect/no_sprint сохранены. Disk reload одной JVM
  не full restart. Соответствует JSON manual_tests[1,3,6].
- `blocked_direction-L04`: одинаковые версии/world/P/G/U/A/history/inputrate/
  warmup/duration для inactive/active/double/PERSONAL/reconnect/riding; измерить
  FPS/MSPT/allocations/retainedmemory, отделив own gate/reconcile/HUD от shared
  dispatch/20tick sync/vanilla. Foundation-L02 остаётся открыт.

Две начальные попытки обычного exec не создали процесс из-за sandbox ACL
`helper_sandbox_lock_failed` / access5. Разрешённый escalated exec работает;
locks не удалялись. Одна попытка чтения старого probe по неверному пути и
один rg с Windows glob завершились диагностикой; исправлены точными путями,
тесты этим не запускались. Неуспешных Gradle/GameTest/CLIENT попыток нет.
