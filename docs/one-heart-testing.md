# one_heart

Исторические результаты реализации не являются подтверждением текущего ревью.
Свежая проверка этапа 7 от 2026-11-02 описана ниже; ручные сценарии остаются
невыполненными.

Доступно максимум 2 единицы здоровья — одно сердце. При активации текущее здоровье обрезается до этого предела без исцеления. Дополнительные сердца поглощения недоступны, а увеличение максимального здоровья не поднимает предел. После завершения обычный максимум возвращается, потерянное здоровье не восстанавливается.

Ограничивается итог getMaxHealth до min(ванильный итог,2), getMaxAbsorption
до 0; сам атрибут и его модификаторы не переписываются. Ванильные setters
используют эти пределы. На старте/тиках/респауне текущее здоровье обрезается
сверху без лечения. Конструктор сервера исключён признаком готовности.
Клиент применяет тот же предел по синхронизированной активности. В 1.21.1
renderStatusBars читает raw max_health и максимум текущего/анимированного HP,
обходя getMaxHealth. OneHeartInGameHudMixin ограничивает именно итог этого
выражения и отображаемое поглощение; внешние атрибуты не меняются. Это также
закрывает короткое рассогласование старого HP/absorption и нового пакета ID.
Сохраняемых собственных модификаторов нет: неактивный ID сразу снимает
ограничение, а уже потерянные здоровье/поглощение остаются потерянными.

## Воспроизводимые предметные проверки ревью

Изолированный CLIENT probe сохранён в src/clientprobe/one_heart. Его init
добавляет отдельный test JAR только к runClient. Probe не входит в main,
production binary или sources JAR. Для предметного GameTest создать
build/one-heart-review-final.init.gradle со следующим содержимым:

```groovy
allprojects {
    afterEvaluate {
        def resources = project.file('build/one-heart-review-final-resources')
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.OneHeartGameTest']
        resources.mkdirs()
        new File(resources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([resources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        tasks.named('test') {
            outputs.upToDateWhen { false }
            reports.junitXml.outputLocation = layout.buildDirectory.dir('reports/one-heart-review-final-junit')
        }
        loom.runs.gametest.runDir = 'build/gametest-one-heart-review-verified-20261102'
        loom.runs.gametest.vmArg '-Dfabric-api.gametest.report-file=../reports/one-heart-review-final.xml'
    }
}
```

При повторении выбрать новый runDir, сохранив XML предыдущего запуска.

```powershell
.\gradlew.bat build runClient runGametest --offline -I src/clientprobe/one_heart/init.gradle -I build/one-heart-review-final.init.gradle
.\gradlew.bat processGametestResources --offline --rerun-tasks
git diff --check
```

Вторая команда возвращает стандартные GameTest entrypoints после фильтрации.
Это предметный запуск пяти методов OneHeartGameTest, не общий набор GameTest.

1. `effectiveCapAbsorptionAndNoHealing`: текущий HP, max/absorption setters,
   внешний base/Health Boost, меньший максимум, repeated start без лечения,
   no_healing, реальный lethal damage→тотем и PlayerManager.respawnPlayer.
2. `externalModifiersFoodHealingAndNoDamageOnCut`: base30 + persistent10 +
   Health Boost8; изменение base40 даёт актуальный max58 после окончания.
   Чужие modifier/effect instances/duration сохраняются. Срез не вызывает
   drop/teleport и износ брони, реальные drinking/enchanted apple/milk,
   лечение до2, будущая выдача поглощения после снятия. 100 повторов не лечат,
   не создают challengeData/UUID records и не делают state dirty.
3. `modesPersonalAndActualDiskReconnect`: OP2 dispatcher, два игрока+late,
   Creative/Spectator/Adventure/Survival, Nether teleport roundtrip, enabled,
   PERSONAL UUID, 32 цикла с повторной активацией. Реальный playerdata disk
   save/remove/connect и PersistentStateManager.save/readNbt/load active/ended,
   offline end, external base40/Health Boost700/HP1 сохраняются.
4. `independentSprintHealingAndExternalEffects`: два игрока с no_sprint,
   запрет no_healing ниже2 и независимое снятие обеих сторон, seeded actual
   random_potion instant_health/resistance, all_debuffs и сохранение второй
   механики после окончания one_heart.
5. `realMissedHeartbeatAndIndependentCleanup`: 127 настоящих серверных тиков.
   Пропущенный beat наносит2: игрок без защиты умирает, игрок с тотемом
   спасается при HP1/absorption0. Снятие one_heart оставляет heartbeat/max20
   без лечения. Это проверка пары, не самостоятельное ревью heartbeat.

CLIENT probe исполняет transformed getMaxHealth/getMaxAbsorption и настоящий
InGameHud.renderStatusBars. Проверяет внешние max1/2/20/48/100, Survival и
Adventure, одно полное/половинное сердце, stale HP20/absorption16, no_healing
double, blind_hud hide/restore, Creative/Spectator/dead, stop и disconnect.
ASM проверяет ровно два вызова getter injections и два HUD expression hooks
в экспортированных классах. Подменены world/network/attribute source, HP и
absorption, DrawContext записывает texture calls; GL отключён test Mixin.
Unsafe обходит конструкторы; gates, final getters и vanilla HUD не подменены.

## Доказательства и пределы

Свежие логи: build/one-heart-review-baseline{,-retry}.log,
build/one-heart-review-final-attempt{1,2}.log,
build/one-heart-review-client-attempt{1,2}.log, build/one-heart-review-verified.log.
XML: build/reports/one-heart-review-{baseline,attempt1,attempt2,final}.xml;
JUnit: build/reports/one-heart-review-final-junit.

Baseline: sandbox exit1 wrapper zip.lck до тестов; разрешённый повтор exit0,
1 JUnit +1/1 GameTest. Первый расширенный запуск exit1,4/5: новый late-join
тест проверял HP до deferred Fabric JOIN. EmbeddedChannel не создаёт этот
сетевой тик: fixture теперь вызывает настоящий JOIN invoker; повтор exit0,
149 JUnit +5/5. CLIENT attempt1 exit1 из-за незаполненного submergedFluidTag
Unsafe fixture. Attempt2 exit1 воспроизвёл игровой дефект HUD: raw attribute
обходит getter cap. Исправлен только клиентский расчёт HUD.

Итоговый combined run: exit0, build, свежие 149/149 JUnit в 54 suites,
ONE_HEART_CLIENT_PROBE_PASSED checks=45 и 5/5 предметных GameTest; сервер
штатно остановлен. processGametestResources --rerun-tasks exit0 восстановил
43 стандартных entrypoints. Локальный python build/one-heart-review-verify.py
exit0 проверил SHA5/5, metadata6/6, registration1, XML каждой попытки,
JUnit149, CLIENT45, регистрацию client Mixin и чистоту binary/sources JAR.
Неуспешные попытки не засчитываются как подтверждение итогового кода.

Автоматизация не заменяет обычную игру/HUD/анимацию/menu, физический портал,
полный death/drop/keepInventory цикл, щит/armor damage, настоящие два клиента
и сеть либо два новых JVM active/ended. Disk roundtrip здесь в одном JVM;
JOIN/DISCONNECT вызываются через Fabric invoker. Респаун идёт через
PlayerManager; Nether teleport не физический портал. Единственный тест
реального времени — heartbeat127; другие loops выполняются синхронно.

Оценка расходов static: final getters вызывают type/gate/list lookup,
onTick делает bounded setters для активных игроков, HUD имеет два scoped
expression hooks на renderStatusBars. Собственных maps/NBT/packets/timers нет.
MSPT/FPS/allocations и длительная нагрузка не измерялись; общая синхронизация
и scheduler имеют отдельную стоимость. Нулевой overhead не заявляется.

## Ручные сценарии (ещё не выполнены)

Исходные условия: Survival с 20 здоровья и поглощением от яблока. Действия: Активировать, применить Health Boost, съесть ещё яблоко, получить небольшой урон и выпить лечение. Ожидаемый результат: Максимум 2, поглощение 0; лечение не выше 2, прочие эффекты яблока остаются.

Исходные условия: Внешнее увеличение max_health было до старта и изменено во время испытания; здоровье 1. Действия: Активировать, проверить отсутствие исцеления, сохранить/перезапустить, затем завершить. Ожидаемый результат: Во время испытания здоровье не повышено и максимум ограничен; после снятия актуальный внешний максимум восстановлен без возврата здоровья и поглощения.

Исходные условия: Оператор OP 2, игрок Survival, испытание реализовано и включено. Действия: Выполнить /fdc list, /fdc activate one_heart, /fdc active. Ожидаемый результат: ID one_heart зарегистрирован ровно один раз, активен; русские название и описания совпадают с ТЗ.

Исходные условия: Два игрока Survival, испытание активно; третий подключается позже. Действия: Повторить основной сценарий обоими игроками и поздно вошедшим; затем последовательно проверить смерть/респаун, выход/вход и портал. Ожидаемый результат: Правила применяются к каждому подходящему игроку согласно player_lifecycle; нет зависимости от игрока, первым активировавшего испытание, и повторных срабатываний на один результат.

Исходные условия: Есть контрольное поведение без испытания; испытание активно. Действия: Повторить основной сценарий в Survival, затем Creative, Spectator и снова Survival; Adventure проверять с ванильно разрешёнными действиями. Ожидаемый результат: На игроков Survival/Adventure действует описанная механика, в Creative/Spectator собственное вмешательство в игрока отключено. Возврат режима не удваивает эффект. Уникальные мировые последствия проверяются по исключениям данного ТЗ.

Исходные условия: Новое испытание и существующее no_sprint включены; два игрока Survival. Действия: Выполнить /fdc activate "double_challenge:one_heart,no_sprint"; проверить основную механику и попытку спринта, затем /fdc activate no_sprint. Ожидаемый результат: Во время пары работают обе механики. После замены снимаются только временные изменения нового испытания, no_sprint продолжает блокировать спринт; здоровье, предметы и описанные мировые последствия не возвращаются.

Исходные условия: Испытание активно; один игрок отключился. Действия: Сохранить и перезапустить мир с активным испытанием, проверить основной эффект; затем завершить его через /fdc activate no_sprint, сохранить и перезапустить, вернуть офлайн-игрока. Ожидаемый результат: После первого запуска выполняется on_server_restart данного ТЗ. После второго новое испытание не действует; устаревшие собственные изменения очищены, чужие состояния сохранены. Не использовать несуществующую команду /fdc stop.
