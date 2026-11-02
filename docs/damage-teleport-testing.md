# damage_teleport — Почти как эндермен

MEDIUM / GLOBAL. В Survival и Adventure фактически принятый несмертельный
урон вызывает одну попытку переноса на 5 блоков по горизонтали. Потеря
поглощения подходит, спасение тотемом тоже. Мёртвые игроки, пассажиры,
Creative и Spectator исключены. Активация сама не переносит игрока.

## Реализация

Используется существующий `onPlayerDamageApplied` с `DamageReceipt`.
Уменьшение HP/поглощения подтверждается до восстановления тотемом,
реакции выполняются после завершения ванильного урона. Дополнительные
Mixin, события, сетевые payload и таймеры не добавлены.

Восемь направлений 0°, 45°, …, 315° перемешиваются один раз на событие.
Каждое проверяется на высотах исходная Y, Y+1, Y−1, Y+2, Y−2.
Выбирается первый безопасный кандидат; максимум 40 проверок. Без кандидата
положение и физика не меняются, отложенных повторов нет.

Проверяется весь текущий хитбокс игрока: высотные границы мира,
world border, загруженность всех пересекаемых чанков, отсутствие коллизий,
жидкости, огня (включая горящие обычный и душевный костры), лавы, магмы,
кактуса, ягодных кустов, рыхлого снега и
ванильных порталов. Опора определяется реальным пересечением блоковой
коллизии с тонким слоем под стопами. Проверяются все опоры, включая
выступающие формы заборов/стен и воду в waterlogged-блоке опоры.
Поиск не вызывает загрузку или генерацию чанков.

Перенос сохраняет измерение и yaw/pitch, обнуляет скорость и fallDistance.
Используются ванильные телепортация и пакет скорости. В паре с
`damage_slot_drop` существующий DamageHooks выполняет дроп первым;
стек остаётся у исходной точки независимо от порядка ID в активации.

Собственных сохраняемых данных, модификаторов и клиентских настроек нет.
После входа, респауна, смены измерения или загрузки сохранённого мира
обработчик читает актуальные эффективные GLOBAL/PERSONAL ID. После
завершения перестаёт реагировать, не возвращая игрока в прежнюю позицию.

## Ревью 02.11.2026

Подтверждён и исправлен дефект безопасности: горящий костёр имел твёрдую
коллизию и проходил `isSafe`. На высоте его поверхности (7/16 блока)
настоящий `player.damage` переносил игрока на одну из горящих опор.
`CampfireBlock.isLitCampfire` исключает обычный и душевный горящие костры;
потушенные остаются допустимыми. Общий damage receipt contract не менялся.

Свежие предметные проверки `DamageTeleportGameTest`:

- `safeTeleportActualDamageAndLifecycle`: исходный настоящий damage/Mixin,
  щит, i-frames, setter, absorption, лодка, hazards, незагруженный чанк,
  физика/взгляд, drop, тотем/смерть/два respawn path, disk и завершение.
- `litCampfireSupportCannotStartDamageTeleportChain`: оба костра, контроль
  без огня и реальный vanilla contact damage; восемь горящих опор,
  отсутствие безопасной точки на всех 40 позициях и лишнего урона/переноса.
- `everyDirectionAndHeightThroughActualDamageAndNoRetry`: каждая из 40 точек
  поочерёдно единственная безопасная; одна фактическая телепортация на hit.
  При полном отказе сохраняются скорость/fallDistance; 100 вызовов onTick
  после восстановления пола не повторяют поиск (это не 100 реальных тиков).
- `cancelledZeroLossSourcesAndNestedResults`: inactive, настоящий cancel,
  Resistance V/invulnerable, пять источников; nested same/different source
  дают два переноса на два принятых результата, absorption-only — один.
- `modesPersonalRepeatedActivationAndActualDiskReconnect`: OP2-команды,
  три игрока, JOIN, четыре режима, enabled, PERSONAL/overlap, 32 цикла
  повторной активации/окончания, no_sprint; actual playerdata+PersistentState
  disk reload active/ended и offline end, сохранение чужих предметов/Speed.
- `dimensionsBoundsAndPartialCollisionSupports`: Nether/End, сухая верхняя
  плита, выступающий забор/его waterlogged-вариант, height/border по хитбоксу.
  Чанки арены заранее загружает fixture; поиск этого не делает.
- `actualOrbDamageHealthCapAndBothDropOrders`: настоящий XP orb вызывает
  один перенос после XP/damage; после снятия teleport XP-механика работает;
  оба порядка drop/teleport оставляют полный стек у начала; one_heart cap
  сам не переносит, настоящий несмертельный damage под cap переносит.

Пакеты считаются в EmbeddedChannel. Это доказательство серверной отправки
`PlayerPositionLookS2CPacket`, а не настоящей доставки/подтверждения клиентом.
Базовый запуск: 149 JUnit + 1/1 GameTest. Red: 4/5 (safety gate), затем
6/7 (фактический перенос на костёр). После исправления 7/7 предметных;
финальные build + 149/149 JUnit (54 suites) + 19/19 GameTest
(7 DamageTeleport, 8 DamageSlotDrop, 4 Foundation), штатный shutdown.
Verifier подтвердил чистоту обоих production JAR и возврат 43 entrypoints.
Логи/XML всех попыток и итоговые числа —
в локальном `Промпты/reviews/09-damage_teleport.md`; старые числа ниже не
засчитываются как новое ревью. Все миры отдельные под build.

Предметный стенд воспроизводится init-script (для финального прогона также
включены DamageSlotDropGameTest и FoundationGameTest):

```groovy
allprojects {
    afterEvaluate {
        def resources = project.file('build/damage-teleport-review-final-resources')
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.DamageTeleportGameTest', 'com.cursecycle.DamageSlotDropGameTest', 'com.cursecycle.FoundationGameTest']
        resources.mkdirs()
        new File(resources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([resources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        tasks.named('test') { outputs.upToDateWhen { false }; reports.junitXml.outputLocation = layout.buildDirectory.dir('reports/damage-teleport-review-final-junit') }
        loom.runs.gametest.runDir = 'build/gametest-damage-teleport-review-final-20261102'
        loom.runs.gametest.vmArg '-Dfabric-api.gametest.report-file=../reports/damage-teleport-review-final.xml'
    }
}
```

Команда: `./gradlew.bat build runGametest --offline -I build/damage-teleport-review-final.init.gradle`.
После изоляции: `./gradlew.bat processGametestResources --offline --rerun-tasks`
восстанавливает стандартный набор entrypoints (43).

Оценка расходов статическая: до 40 кандидатов/локальных проверок, ранние
activity/vehicle/world gates, нет собственных tick/map/NBT/retry. Общие
receipt/dispatch и vanilla collision/teleport имеют стоимость. MSPT/FPS,
allocations и нагрузочный профиль не измерялись. Настоящий клиент/HUD,
сеть двух клиентов, physical portals/death/keepInventory, full JVM restart
и реальные heartbeat/DoT pulses остаются ручными ограничениями; disk reload
и несколько тестовых ServerPlayerEntity их не заменяют.

## История реализации 26.10.2026 (не новое подтверждение ревью)

Команды: `./gradlew.bat build runGametest --offline`, `git diff --check`.

- Исходный `build --offline` прошёл до изменений.
- Четыре новых JUnit проверяют 40 различных кандидатов, расстояние 5,
  порядок высот/направлений, остановку на первом успехе, конечность
  неудачного поиска, регистрацию и двойную активацию.
- Новый серверный GameTest проходит настоящий `player.damage` с Mixin:
  обычный урон, щит с подтверждённой блокировкой, i-frames, прямое
  уменьшение здоровья, поглощение, пассажир, отсутствие опоры,
  отсутствие отложенного повтора, поправки +1/−2 по Y.
- Проверены опасные блоки, waterlogged-опора, потолок, граница мира,
  незагруженный чанк и отсутствие его загрузки проверкой безопасности.
- Проверены yaw/pitch, скорость и fallDistance, обратный порядок ID
  в паре с damage_slot_drop, полный стек у исходной точки, тотем,
  смерть без тотема, обычный и живой респаун, поздний игрок, Adventure,
  исключения режимов, PERSONAL ID и независимость второго игрока.
- Активность сохранена и повторно прочитана с диска через настоящий
  PersistentStateManager. После завершения перенос не происходит,
  no_sprint продолжает действовать.
- Полный набор: 76 JUnit и 18/18 GameTest без ошибок, сервер штатно
  сохранил мир и завершился. Первый запуск нового теста выявил ошибку
  тестовой опоры для soul_fire: исправлена на soul_soil, добавлена
  проверка фактического наличия каждого опасного блока перед запросом.

Это серверные проверки с тестовыми подключениями, не проверка доставки
пакетов настоящему клиенту или перезапуска процесса JVM.

## Оставшиеся ручные сценарии

1. OP 2: `/fdc list`, `/fdc activate damage_teleport`, `/fdc active`.
   Проверить русские название, описание HUD и полные правила меню H.
2. На просторной площадке получить урон: горизонтальное смещение 5,
   взгляд сохранён, прежняя скорость погашена. Повторить с настоящим
   щитом, поглощением и тотемом; без принятого урона переноса нет.
3. Проверить тесную комнату, жидкость, опасные опоры и лодку:
   нет переноса в опасность, загрузки новых чанков и спешивания.
4. Два клиента и поздний вход: каждый реагирует на собственный урон.
   Проверить переподключение, смерть/респаун, Nether/End и возврат,
   Survival/Adventure/Creative/Spectator.
5. `/fdc activate "double_challenge:damage_teleport,damage_slot_drop"`:
   после одного урона один перенос, один стек лежит у исходной точки.
   Проверить отдельно пару с no_sprint и завершить через
   `/fdc activate no_sprint`: переносы прекращаются, спринт запрещён.
6. Полностью перезапустить сервер с активным испытанием. Затем закончить
   его во время отсутствия игрока, перезапустить и вернуть игрока:
   устаревших переносов нет. Команды `/fdc stop` не существует.
