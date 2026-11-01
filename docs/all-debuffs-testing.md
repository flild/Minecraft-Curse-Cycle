# all_debuffs

Ревью 2026-11-01: build, свежие 149 JUnit и 5/5 предметных GameTest прошли.
Игровой код не менялся. Настоящий клиент, реальные сетевые клиенты и полный
перезапуск JVM не проверены. Итог предварительный: pass_with_limits.

В JSON сохранились противоречивые упоминания мгновенного урона (в том числе
acceptance_criteria[0..1], parameters[1], multiplayer.randomness_and_timing,
manual_tests[0], assumptions[4]). Пользователь в этом ревью явно уточнил:
«Исключить WITHER и INSTANT_DAMAGE; импульсов урона нет». Результаты ниже
применяют это уточнение; сам JSON не изменён.

В Survival и Adventure постоянно действуют все ванильные статус-эффекты категории HARMFUL из Minecraft 1.21.1 на I уровне, кроме Мгновенного урона и Иссушения. Эти два эффекта не выдаются ни при активации, ни во время испытания. Молоко не даёт постоянного освобождения: разрешённые эффекты возвращаются на следующем серверном тике. Отдельных импульсов прямого урона испытание не создаёт.

## Проверки

GameTest проверяет фактические 14 minecraft HARMFUL-эффектов: blindness,
darkness, hunger, infested, levitation, mining_fatigue, nausea, oozing,
poison, slowness, unluck, weakness, weaving, wind_charged. NEUTRAL,
BENEFICIAL, wither и instant_damage не выдаются. Это результат реестра 1.21.1,
а не вручную заданный production-список.

Пять сценариев в AllDebuffsGameTest:

- Полный набор/уровень I, отсутствие стартового урона, внешний мгновенный
  урон 20→14 HP и сохранение повреждения после очистки, внешние Wither/Speed.
- 240 вызовов vanilla playerTick + CycleManager: каждый эффект непрерывен,
  собственные durations точно 40/20, повторный dispatch не продлевает их раньше
  времени; сильное внешнее Slowness III и Speed II/Wither имеют остаток
  300→60. Реальный MilkBucketItem.finishUsing, восстановление на следующем
  вызове tick, 32 повторные активации: 14 entries → 0. Эти 240 вызовов
  не являются 240 настоящими серверными тиками; invulnerable исключает
  ванильный урон и infested-спавны из этого теста длительностей.
- Настоящие JOIN/DISCONNECT на EmbeddedChannel; независимость двух игроков,
  режимы, disabled/dead cleanup без onStop, death respawn, Nether teleport,
  живое замещение объекта, PERSONAL, reconnect после окончания. NBT active/stale
  и serial в реальном PersistentState-файле проверены; это не restart JVM.
- no_sprint, no_block_breaking и random_potion с перекрывающимся Slowness:
  снятие одного owner сохраняет другой. no_healing блокирует heal и после
  снятия all_debuffs; контроль без no_healing позволяет heal.
- 45 настоящих серверных тиков: реальные OP2 list/activate/active, возврат
  полного набора после молока через зарегистрированный END_SERVER_TICK,
  очистка. Invulnerable изолирует dispatch от урона/спавнов; отсутствие
  импульсов на 200/400/600 этим сценарием не измерялось. Production-код
  вообще не вызывает instant effect/damage и не содержит такого таймера.

Итоговая команда: `./gradlew.bat build runGametest --offline -I build/all-debuffs-review-final.init.gradle`.
Лог `build/all-debuffs-review-closeout-retry.log`, XML
`build/reports/all-debuffs-review-final.xml`, JUnit `build/test-results/test/`.
Предварительные попытки и ограничения сохранены в локальном отчёте
`Промпты/reviews/02-all_debuffs.md`. Стенд ограничивает только GameTest
entrypoints, production source/classpath не подменяются. Для восстановления
стандартного списка выполнить `./gradlew.bat processGametestResources --offline`.

Воспроизводимое содержимое init-script (перед запуском выбрать новый runDir,
если мир загрязнён; не запускать одновременно с другими shared-state тестами):

```groovy
allprojects {
    afterEvaluate {
        def isolatedResources = project.file('build/all-debuffs-review-final-resources')
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.AllDebuffsGameTest']
        isolatedResources.mkdirs()
        new File(isolatedResources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([isolatedResources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        tasks.named('test') { outputs.upToDateWhen { false } }
        loom.runs.gametest.runDir = 'build/gametest-all-debuffs-review-final-20261101'
        loom.runs.gametest.vmArg '-Dfabric-api.gametest.report-file=../reports/all-debuffs-review-final.xml'
    }
}
```

Накладные расходы проверены статически: активный tick сканирует реестр R и
поддерживает K=14 типов на игрока; каждый вклад обновляется раз в 20 effect
ticks, после молока — немедленно на следующем dispatch. Ledger обходит только
затронутые типы/owner/hidden chains, stop удаляет entries. Нет scan мира,
новых регистраций или собственного сетевого протокола. Поток/список реестра
создаётся каждый активный tick; это ограниченная работа, не нулевая стоимость.
MSPT/FPS/allocations не измерялись; общий профиль остаётся foundation-L02.

Автоматические проверки не заменяют следующие ручные сценарии.

Исходные условия: Survival без внешних эффектов и источников урона, полный запас здоровья и питания; зафиксирован фактический HARMFUL-набор реестра 1.21.1. Действия: Активировать, сопоставить выдачу с набором без instant_damage и wither, выпить молоко и дождаться следующего тика; наблюдать механику более 600 серверных тиков. Ожидаемый результат: Все разрешённые продолжительные эффекты поддерживаются, оба исключённых не выдаются; ни при старте, ни через 200/400/600 тиков отдельного мгновенного урона нет. Ванильный урон от разрешённых эффектов не отменяется.

Исходные условия: До активации выданы долгое Иссушение I и более сильное Замедление; есть положительное Сопротивление. Действия: Активировать, получить Мгновенный урон от отдельного внешнего источника, затем завершить испытание. Ожидаемый результат: Собственные Мгновенный урон и Иссушение не выдаются, внешние источники не блокируются; все чужие ещё действующие эффекты сохраняют корректное оставшееся время и силу.

Исходные условия: Оператор OP 2, игрок Survival, испытание реализовано и включено. Действия: Выполнить /fdc list, /fdc activate all_debuffs, /fdc active. Ожидаемый результат: ID all_debuffs зарегистрирован ровно один раз, активен; русские название и описания совпадают с ТЗ.

Исходные условия: Два игрока Survival, испытание активно; третий подключается позже. Действия: Повторить основной сценарий обоими игроками и поздно вошедшим; затем последовательно проверить смерть/респаун, выход/вход и портал. Ожидаемый результат: Правила применяются к каждому подходящему игроку согласно player_lifecycle; нет зависимости от игрока, первым активировавшего испытание, и повторных срабатываний на один результат.

Исходные условия: Есть контрольное поведение без испытания; испытание активно. Действия: Повторить основной сценарий в Survival, затем Creative, Spectator и снова Survival; Adventure проверять с ванильно разрешёнными действиями. Ожидаемый результат: На игроков Survival/Adventure действует описанная механика, в Creative/Spectator собственное вмешательство в игрока отключено. Возврат режима не удваивает эффект. Уникальные мировые последствия проверяются по исключениям данного ТЗ.

Исходные условия: Новое испытание и существующее no_sprint включены; два игрока Survival. Действия: Выполнить /fdc activate "double_challenge:all_debuffs,no_sprint"; проверить основную механику и попытку спринта, затем /fdc activate no_sprint. Ожидаемый результат: Во время пары работают обе механики. После замены снимаются только временные изменения нового испытания, no_sprint продолжает блокировать спринт; здоровье, предметы и описанные мировые последствия не возвращаются.

Исходные условия: Испытание активно; один игрок отключился. Действия: Сохранить и перезапустить мир с активным испытанием, проверить основной эффект; затем завершить его через /fdc activate no_sprint, сохранить и перезапустить, вернуть офлайн-игрока. Ожидаемый результат: После первого запуска выполняется on_server_restart данного ТЗ. После второго новое испытание не действует; устаревшие собственные изменения очищены, чужие состояния сохранены. Не использовать несуществующую команду /fdc stop.
