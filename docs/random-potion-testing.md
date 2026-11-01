# random_potion

## Ревью 2026-11-01

На исходном HEAD `cbe05c9` свежий предметный запуск прошёл: 2 JUnit и
1/1 GameTest. Старые результаты реализации (66 JUnit, 12 GameTest) не
используются как доказательство этого ревью.

После расширения тестов: `build runGametest --offline -I
build/random-potion-review-final.init.gradle` — exit 0, свежие **149 JUnit**
и **5/5 предметных GameTest**. Production и общие контракты не менялись;
подтверждённых дефектов механики не выявлено.
Лог: `build/random-potion-review-final.log`, XML:
`build/reports/random-potion-review-final.xml`, `build/test-results/test/`.
Оценка расходов статическая: время кадра/MSPT и профиль памяти не измерялись.

Проверено в `RandomPotionGameTest`:

- `everyResultAndInstantCompatibility`: все 19 результатов через seeded
  vanilla RNG и реальную активацию, amplifier 0/duration 600; instant
  health 10→14 и damage 10→4 один раз, дубли dispatch не повторяют
  результат, stop не откатывает HP; лечение блокируется no_healing,
  max health 2 при one_heart. Для урона истекает login protection.
- `realServerIntervalsMilkAndLatePlayer`: 645 настоящих приращений
  `MinecraftServer.getTicks()` в ускоренном GameTest, обычный END_SERVER_TICK
  плюс явный повтор `CycleManager.onServerTick`. Выбор при старте и на 600,
  повтор разрешён; молоко на 300 не выдаёт результат до 600; третий игрок
  начинает на 40 и меняет результат на 640. Nether roundtrip и изменение
  timeOfDay не сдвигают deadline. OP2 исполняет list/activate/active.
  Это не 30 секунд wall-clock на настоящем клиенте; JOIN позднего игрока
  дополнен seeded callback после создания для воспроизводимого выбора.
- `externalClocksOwnersAndRepeatedCleanup`: внешняя speed III 1000→900,
  добавленная после выбора speed IV 800→750; cleanup не возвращает устаревшее
  время. Slowness/all_debuffs очищается в обе стороны, no_block_breaking
  сохраняет fatigue X; 32 start/stop с no_sprint не накапливают записи ledger.
- `lifecycleModesAndPersistedStaleEffects`: два ServerPlayerEntity с
  EmbeddedChannel, независимые результаты, режимы, disabled cleanup,
  death/respawn и alive replace, Nether teleport; active player-NBT load,
  новый выбор при входе, DISCONNECT/reconnect, PERSONAL; настоящее сохранение
  PersistentState на диск с activationSerial и активным ID. Stale player NBT
  после завершения очищает только своё, внешняя speed остаётся с duration 899.
  Это не полный restart JVM и не реальная сеть.
- Исходный `milkInstantAndForeignContributions` снова выполнен: молоко,
  живой респаун без сброса таймера, instant damage один раз, внешний вклад.

`RandomPotionChallengeTest` проверяет точный пул 19 ID, границы Clock,
одинаковый tick и большой скачок без догоняющей серии выдач. Равновероятность
следует из `nextInt(19)` с одним уникальным ID на индекс; статистический
профиль распределения не измерялся.

## Воспроизводимый предметный запуск

Из корня создать `build/random-potion-review-final.init.gradle`:

```groovy
allprojects {
    afterEvaluate {
        def isolatedResources = project.file('build/random-potion-review-final-resources')
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.RandomPotionGameTest']
        isolatedResources.mkdirs()
        new File(isolatedResources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([isolatedResources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        tasks.named('test') { outputs.upToDateWhen { false } }
        loom.runs.gametest.runDir = 'build/gametest-random-potion-review-final-20261101'
        loom.runs.gametest.vmArg '-Dfabric-api.gametest.report-file=../reports/random-potion-review-final.xml'
    }
}
```

```powershell
.\gradlew.bat build runGametest --offline -I build/random-potion-review-final.init.gradle
.\gradlew.bat processGametestResources --rerun-tasks --offline
git diff --check
```

При повторе выбрать новый runDir/report, сохранив первый лог/XML. Init меняет
только gametest resources/runDir, не main или classpath. Вторая команда
возвращает стандартные 43 entrypoints. Изолированный запуск не является общим
прогоном GameTest. Локальный `build/random-potion-review-verify.py` сравнил
шесть metadata полей с JSON, единственную регистрацию, XML counts и чистоту
binary/sources JAR от GameTest/Probe; exit 0.

## Ограничения

Настоящий HUD/menu, обычный клиент/порталы/дроп/keepInventory/тотем,
мировые последствия weaving/oozing/infested/wind_charged, настоящие два клиента
и поздний третий, full JVM restart active/ended и длительный нагрузочный
профиль не выполнены. Они остаются ручными/нагрузочными проверками.

## Механика

Каждые 30 секунд серверного времени каждый игрок независимо получает случайный эффект зелья I уровня. Первый выбор происходит сразу. Обычный эффект длится до следующего выбора, мгновенный применяется один раз. Список эффектов фиксирован в правилах; дополнительные штрафы не назначаются.

Равновероятный пул: minecraft:speed, slowness, strength, instant_health, instant_damage, jump_boost, regeneration, resistance, fire_resistance, water_breathing, invisibility, night_vision, weakness, poison, slow_falling, wind_charged, weaving, oozing, infested. Все перечисленные короткие ID имеют пространство minecraft.

Живой респаун переносит происхождение эффектов; новый хук респауна нужен
для сохранения таймера при возвращении из End и нового выбора после смерти.
Таймеры не сериализуются. Вход удаляет прошлый собственный результат.

## Ручные сценарии (ещё не выполнены)

Исходные условия: Survival, активировать испытание и наблюдать серверные тики. Действия: Записать первый эффект, дождаться 600 тиков, затем выпить молоко посередине интервала. Ожидаемый результат: Выбор при старте и на границе интервала; после молока собственный эффект снят до очередной границы.

Исходные условия: Задан более сильный долгий эффект скорости обычным зельем/OP; отладочно контролируется RNG выбора без добавления новой команды. Действия: Получить случайную скорость I, затем сменить испытание; отдельно принудить выбор instant_damage. Ожидаемый результат: Чужая скорость не сокращена и не удалена; мгновенный урон обработан один раз, без непрерывного нанесения.

Исходные условия: Оператор OP 2, игрок Survival, испытание реализовано и включено. Действия: Выполнить /fdc list, /fdc activate random_potion, /fdc active. Ожидаемый результат: ID random_potion зарегистрирован ровно один раз, активен; русские название и описания совпадают с ТЗ.

Исходные условия: Два игрока Survival, испытание активно; третий подключается позже. Действия: Повторить основной сценарий обоими игроками и поздно вошедшим; затем последовательно проверить смерть/респаун, выход/вход и портал. Ожидаемый результат: Правила применяются к каждому подходящему игроку согласно player_lifecycle; нет зависимости от игрока, первым активировавшего испытание, и повторных срабатываний на один результат.

Исходные условия: Есть контрольное поведение без испытания; испытание активно. Действия: Повторить основной сценарий в Survival, затем Creative, Spectator и снова Survival; Adventure проверять с ванильно разрешёнными действиями. Ожидаемый результат: На игроков Survival/Adventure действует описанная механика, в Creative/Spectator собственное вмешательство в игрока отключено. Возврат режима не удваивает эффект. Уникальные мировые последствия проверяются по исключениям данного ТЗ.

Исходные условия: Новое испытание и существующее no_sprint включены; два игрока Survival. Действия: Выполнить /fdc activate "double_challenge:random_potion,no_sprint"; проверить основную механику и попытку спринта, затем /fdc activate no_sprint. Ожидаемый результат: Во время пары работают обе механики. После замены снимаются только временные изменения нового испытания, no_sprint продолжает блокировать спринт; здоровье, предметы и описанные мировые последствия не возвращаются.

Исходные условия: Испытание активно; один игрок отключился. Действия: Сохранить и перезапустить мир с активным испытанием, проверить основной эффект; затем завершить его через /fdc activate no_sprint, сохранить и перезапустить, вернуть офлайн-игрока. Ожидаемый результат: После первого запуска выполняется on_server_restart данного ТЗ. После второго новое испытание не действует; устаревшие собственные изменения очищены, чужие состояния сохранены. Не использовать несуществующую команду /fdc stop.
