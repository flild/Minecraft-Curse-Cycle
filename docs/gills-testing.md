# gills

## Ревью 2026-11-02

Свежий `build` и 149 JUnit прошли; предметный серверный прогон — 7/7 GameTest,
exit 0, штатная остановка. Результаты прежней реализации здесь не засчитываются.

Исправлены два воспроизведённых дефекта: tick-кэш удерживал UUID после stop/leave
и disable; полный запас под водой и повторный start без изменений вызывали
ненужный `markDirty`. Red-прогон: 1/3 GT, две ожидаемые ошибки; итоговый: 7/7.
Сохраняемая UUID-запись дыхания при выходе остаётся: это требование ТЗ.

Предметные проверки в `GillsGameTest`:

- `actualAirDamagePersistenceAndRespawn`: старый сценарий, исполненный заново;
  340 синхронных шагов, две потери по 2 HP, вода, disk roundtrip, респаун.
- `eyeWaterExclusionsAndForeignProtection`: реальные WATER у ног/глаз, котёл,
  лава; 50 шагов восстановления из 100 до 300; Водное дыхание/Морская сила/
  Respiration III не отменяют расход, чужие эффекты сохраняются; моб без вмешательства.
- `phaseDiskModesPersonalAndOfflineEnd`: Survival/Adventure, исключённые режимы,
  настоящие JOIN/DISCONNECT callbacks на EmbeddedChannel, тот же UUID после
  PersistentState save/read/load с фазой 19 и импульсом на следующем шаге;
  Nether roundtrip, alive replacement, смерть/новая жизнь, offline end,
  PERSONAL, не затронутый другой игрок, overlapping scopes, disabled.
- `damageProtectionAndIndependentPairs`: ванильное поглощение; no_sprint,
  no_healing, one_heart действуют вместе с gills и после его снятия;
  потерянные HP/поглощение не возвращаются, ванильное land recovery возобновляется.
- `realServerCadenceAndWaterRecovery`: 416 собственных шагов за 415 реальных
  приращений `server.getTicks()`, шаг 1 в начальном тике; сухие шаги 1–340,
  импульсы ровно на 320/340, далее 76 шагов в воде и cap 300. На каждом тике
  исполняются transformed `playerTick` и два `CycleManager.onServerTick`,
  автоматический END_SERVER_TICK также остаётся зарегистрирован: повтор не
  удваивает дыхание. Hunger=17 исключает естественное лечение. Это серверные
  тики GameTest, не wall-clock доказательство 20 TPS.
- `tickCacheReleasedAtLifecycleBoundaries`: 32 start/stop в одном серверном
  тике с новой активацией, повторный tick, DISCONNECT и disable; временный
  кэш пуст, сохранённый запас на выходе не стирается.
- `unchangedFullSupplyDoesNotDirtyPersistence`: 100 обновлений при полном
  запасе в реальной воде плюс повторный start оставляют dirty=false.

Все попытки сохранены под `build/`: `gills-review-baseline-sandbox.log`
(exit 1 до тестов, wrapper lock без доступа), baseline (2 JUnit + 1/1 GT,
exit 0), red (1/3 GT, exit 1), final (149 JUnit + 5/7 GT, exit 1), verified
(149 JUnit + 7/7 GT, exit 0). У final ошибки новых fixtures: absorption без
эффекта/его capacity и естественная регенерация; исправлены только fixtures.
Логи `gills-review-<имя>.log`, XML `build/reports/gills-review-<имя>.xml`;
JUnit baseline/final/verified сохраняются в одноимённых `*-junit/`.

Воспроизвести предметный прогон: сохранить следующий init-script как
`build/gills-review-verified.init.gradle` и выполнить
`.\gradlew.bat build runGametest --offline -I build/gills-review-verified.init.gradle`.
Для повторного запуска выбрать новое уникальное имя `runDir` и XML.
Временная фильтрация entrypoints не меняет tracked `fabric.mod.json`.

```groovy
allprojects {
    afterEvaluate {
        def resources = project.file('build/gills-review-verified-resources')
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.GillsGameTest']
        resources.mkdirs()
        new File(resources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([resources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        tasks.named('test') { outputs.upToDateWhen { false } }
        loom.runs.gametest.runDir = 'build/gametest-gills-review-verified-20261102'
        loom.runs.gametest.vmArg '-Dfabric-api.gametest.report-file=../reports/gills-review-verified.xml'
    }
}
```

После предметного запуска вернуть обычные ресурсы:
`.\gradlew.bat processGametestResources --rerun-tasks --offline`.

CLIENT probe запускается отдельно:
`.\gradlew.bat runClient --offline -I src/clientprobe/gills/init.gradle`.
Он проверяет реальные transformed `getNextAirOnLand/Underwater` и ванильный
`renderStatusBars`, dry-land bubbles и пару с blind_hud. Это preLaunch с
Unsafe-фикстурами мира/игрока, перехваченными draw-вызовами и без OpenGL окна,
обычного игрового цикла, реального ввода или сети. Производственный gate не
подменяется; режим/живость/air/world берутся из fixture, ID из ClientDataHolder.
Probe существует отдельным JAR и не должен входить в binary/sources JAR мода.
Итог: `GILLS_CLIENT_PROBE_PASSED checks=36`, exit 0,
`build/gills-review-client-checked.log`. Предыдущие CLIENT попытки exit 1:
`gills-review-client.log` — неинициализированная ссылка fixture на client;
`gills-review-client-verified.log` — Unsafe-мир не имел isClient=true;
`gills-review-client-final.log` — отсутствовала замена GL-вызовов. Исправлен
только probe: qualified outer reference, флаг стороны и test-only Mixin,
отменяющий enableBlend/disableBlend/defaultBlendFunc. Gills gate/HUD/Mixin
не заменяются. Это ошибки создания стенда, не обнаруженные игровые дефекты.

Оценка затрат статическая: active tick O(G+A) для gate + O(1) на запас,
NBT и временный UUID tick-кэш; сохранение O(U) по UUID текущей активации.
G/A — число effective GLOBAL/PERSONAL IDs, U — участники текущей активации.
Неактивный Mixin всё ещё платит за instanceof/gate, для неигроков выходит
до lookup; сканирования мира, своего HUD, сообщений и пакетов дыхания нет.
Изменения воздуха синхронизируются ванильным DataTracker. Полный запас не
пишет NBT/dirty повторно; MSPT/FPS/allocations и нагрузка не измерялись.

Ограничения: настоящий клиент/анимация/физические portals/death, дождь,
защитные эффекты кроме absorption и дыхания, предметные пары floor_is_water/
no_block_placing/damage_teleport/damage_slot_drop, два настоящих клиента,
full JVM restart active/ended и нагрузочный профиль остаются ручными.
Свежий отчёт с матрицей ТЗ: локальный `Промпты/reviews/05-gills.md`.

Воздух расходуется на суше: полного запаса хватает на 15 секунд, затем каждую секунду наносится 2 единицы урона удушьем. Погружение глаз в воду восстанавливает воздух и позволяет дышать без ограничения. Дождь, мокрые ноги и котёл не заменяют погружение головы. Водное дыхание, Морская сила и зачарование Подводное дыхание не отменяют сухопутную потерю воздуха.

Сохраняются запас и dryTicks в challengeData.gills по UUID.
getNextAirUnderwater/getNextAirOnLand возвращают текущий запас, пока
испытание активно: ваниль не запускает второй отсчёт. Собственный урон
идёт через обычный DamageSources.drown. Респаун после смерти создаёт 300,
живой перенос/вход восстанавливает сохранённый запас без пополнения.
В InGameHud 1.21.1 уже есть показ пузырьков при air < maxAir на суше,
это проверено по фактическому байткоду: отдельный рендер не добавляется.

## Ручные сценарии (ещё не выполнены)

Исходные условия: Одиночный Survival, свежая активация, воды нет, броня/защитные эффекты отсутствуют. Действия: Наблюдать 300 тиков на суше и следующие 40 тиков. Ожидаемый результат: Воздух достигает нуля за 300 тиков, затем два импульса по 2 единицы базового урона с периодом 20 тиков.

Исходные условия: Запас 100, доступны глубокая вода, мелкая вода, дождь и котёл. Действия: Сначала намочить ноги/встать под дождь, затем погрузить глаза на 50 тиков. Ожидаемый результат: Первые способы не восстанавливают запас; 50 тиков под водой восстанавливают 200 единиц, не выше 300, урона от отсутствия воздуха под водой нет.

Исходные условия: Запас частично израсходован, есть Водное дыхание и шлем с Подводным дыханием. Действия: Постоять на суше, выйти/войти и сменить измерение. Ожидаемый результат: Расход остаётся фиксированным, эффекты не удаляются, жизненный цикл не пополняет запас.

Исходные условия: Одиночный мир, Survival, OP 2, испытание реализовано и включено. Действия: Выполнить /fdc list, /fdc activate gills, /fdc active. Ожидаемый результат: gills зарегистрирован один раз, активен; название и описания совпадают с ТЗ. Для работы не нужен второй игрок.

Исходные условия: Одиночный Survival-мир, активное испытание и подходящие условия основного сценария. Действия: Проверить основной эффект, умереть/возродиться, выйти/войти, сменить измерение и перезапустить мир. Ожидаемый результат: Механика и её состояние ведут себя точно по player_lifecycle; нет повторных списаний, событий или обнуления сохраняемых ограничений.

Исходные условия: Известен контрольный результат без испытания. Действия: Повторить основной сценарий в Survival, Creative, Spectator и снова Survival; Adventure проверять там, где контрольное действие разрешено ванилью. Ожидаемый результат: Собственное вмешательство в игрока действует только в Survival/Adventure и корректно возвращается после смены режима. Остальные исключения проверяются по этому ТЗ.

Исходные условия: В одиночном мире включены новое испытание и существующее no_sprint. Действия: Выполнить /fdc activate "double_challenge:gills,no_sprint"; проверить обе механики, затем /fdc activate no_sprint. Ожидаемый результат: Оба испытания работают в одном игроке. После замены временное вмешательство нового прекращается, no_sprint остаётся; описанные постоянные последствия и чужие состояния не откатываются.

Исходные условия: Игрок вышел при активном испытании. Действия: При активном испытании сохранить/перезапустить мир и вернуться; затем выйти, завершить испытание на сервере через /fdc activate no_sprint и вернуться снова. Ожидаемый результат: Первый вход восстанавливает именно предусмотренное состояние, второй не возвращает завершённую активацию. Для проверки окончания офлайн допустим выделенный сервер; команда /fdc stop не предполагается.

Исходные условия: Необязательная проверка сетевой совместимости: два клиента Survival на выделенном сервере. Действия: Активировать испытание, повторить основной сценарий каждым отдельно; один отключается, второй продолжает. Ожидаемый результат: Каждый получает самостоятельную механику без передачи ресурсов, общего персонального счётчика и зависимости от присутствия напарника; исключения мирового состояния описаны отдельно.
