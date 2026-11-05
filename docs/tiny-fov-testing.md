# tiny_fov — «Тесновато»

EASY/GLOBAL; итоговый вертикальный FOV сцены — 15 градусов.
TinyFovGameRendererMixin находится только в client-разделе и перехватывает
getFov на RETURN. renderWorld передаёт true, renderHand — false; panorama
сохраняет 90. Не меняются настройки, perspective, camera distance, aspect ratio,
GUI scale, item use, raycast и projection API. TinyFovCamera каждый раз читает
текущие synced effective IDs и требует мир, живого неудалённого игрока,
interactionManager и Survival/Adventure. Собственного persist/snapshot нет.

## Ревью 2026-11-05

Исходный production HEAD: `24f6699e37cdb10487c86c889a8e71fe5b82d947`.
Дефектов production-кода не найдено; расширены только предметные стенды.
Результаты реализации от 2026-10-29 — история, не доказательство нового ревью.

- `.\gradlew.bat build runGametest --offline -I build/tiny-fov-review-baseline.init.gradle`
  — exit 0 / 28 s, 149 JUnit / 54 suites, 2/2 GameTest, normal shutdown.
- Та же команда с `build/tiny-fov-review-final.init.gradle` — exit 0 / 30 s,
  149 JUnit / 54 suites, 3/3 GameTest, normal shutdown. failures/errors/skipped=0;
  в JUnit входят три TinyFovCameraTest.
- T1: actual S2C для двух игроков и late join, modes, смерть/новый объект,
  Nether/End/Overworld, same UUID, double/no_sprint и запрет после окончания,
  PersistentState.save/read/set active/ended/offline end, OP2-команды list/activate/active.
  T2: PERSONAL owner/other, GLOBAL, dedup, disabled/enabled. T3: 32 serial
  activation/end, cargo17/HP17/XP7 неизменны, нет settings NBT, actual Fabric
  DISCONNECT invoker, старт/окончание на пустом сервере и same-UUID join.
  Каждый assertIds дополнен настоящим CurseCycleSyncPayload.CODEC encode/decode.
- `.\gradlew.bat runClient --offline -I src/clientprobe/tiny_fov/init.gradle`
  — baseline exit 0 / 19 s; expanded exit 0 / 19 s,
  `TINY_FOV_CLIENT_PROBE_PASSED: 2700`, `checks=12548`.
  Настоящий transformed getFov и production gate: base30/70/80/90/110,
  все perspectives, multiplier .1/.85/1/1.3/1.5, delta0/.5/1,
  все CameraSubmersionType, effect scale0/.5/1; vanilla control/scene15/
  independent hand/options unchanged/end→control в каждой комбинации.
  Дополнительно latest90→80/modes/dead/removed/new player/null world-player-
  manager-mode/replacement world/reset/offline end/panorama/actual forced setter.
- Расширение CLIENT: reflection вызывает actual merged rollProjection и
  offsetCrosshair handlers. Каждая пара проверена в четырёх состояниях:
  оба активны, каждый отдельно, оба выключены. FOV15/80 сохраняется в projection,
  roll добавляет ровно ожидаемый поворот; offset даёт X80/Y40, один recording
  callback и восстановленную HUD matrix. 32 reset/start/end × 50 duplicate sync
  не накапливают эффект, после окончания возвращается последняя настройка.
- Свежий `javap -p -c` mapped GameRenderer подтверждает scene=true/hand=false
  и RETURN после lerp/death/submersion. Vanilla culling использует отдельную
  консервативную projection; она не определяет видимый FOV сцены.
- `.\gradlew.bat processGametestResources --offline` — exit 0 / 1 s,
  восстановлены все 43 entrypoints; source == built.
- `python build/tiny-fov-review-verify.py` — exit 0 / TINY_FOV_REVIEW_VERIFIED:
  SHA5/metadata6/registration1/clientMixin1/fresh XML-JUnit-CLIENT/43entries/
  чистые binary и sources JAR. Attempt1 exit1: UTF-16 PowerShell-лог прочитан
  как UTF-8, исправлен только reader; сохранён verifier-attempt1.log.

Логи: `build/tiny-fov-review-{baseline,final}.log`,
`build/tiny-fov-review-client-{baseline,final}.log`,
`build/tiny-fov-review-{restore,verifier}.log`.
XML: `build/reports/tiny-fov-review-{baseline,final}.xml`;
JUnit: `build/reports/tiny-fov-review-{baseline,final}-junit/`.
Байткод: `build/tiny-fov-review-GameRenderer.javap.txt`.

Изолирующий init меняет только metadata/runDir/reports. Воспроизводимый пример
(для повтора задать новое phase, чтобы не использовать старый мир):

```groovy
allprojects {
    afterEvaluate {
        def phase = 'final'
        def resources = project.file("build/tiny-fov-review-${phase}-resources")
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.TinyFovGameTest']
        resources.mkdirs()
        new File(resources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([resources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        tasks.named('test') {
            outputs.upToDateWhen { false }
            reports.junitXml.outputLocation = layout.buildDirectory.dir("reports/tiny-fov-review-${phase}-junit")
        }
        loom.runs.gametest.runDir = "build/gametest-tiny-fov-review-${phase}-20261105"
        loom.runs.gametest.vmArg "-Dfabric-api.gametest.report-file=../reports/tiny-fov-review-${phase}.xml"
    }
}
```

После запуска восстановить ресурсы без init. CLIENT jar из src/clientprobe/tiny_fov
отделён от production binary/sources. Unsafe client/options/player/world/manager/
renderer, stub alive/mode/camera и seeded multiplier не запускают реальный sprint,
атрибуты, bow/spyglass, окно или network. Roll проверен на merged handler;
offset — на merged wrapper с recording Operation, без vanilla draw/GL/full frame.
PersistentState disk и same-UUID join одной JVM не равны полному перезапуску.

## Накладные расходы — static

Vanilla FOV вычисляется и при активном override; RETURN injector имеет цену
callback/boxing до пользовательского gate, в том числе inactive. Gate O(K),
K — число effective client IDs; null/mode/alive O(1). Hand/panorama выходят
до gate. Собственных tick/maps/timers/packets/NBT и player/world retention нет.
Общие dispatch, sync каждые 20 server ticks, history и HUD имеют свою цену.
Culling остаётся консервативным vanilla; FOV15 не обещает ускорения сцены.
FPS/MSPT/allocations/retained memory не измерялись; counters не профиль.

## Ручные проверки — не выполнены

1. manual_tests[0,2,4]: Survival/FOV90/одинаковая точка; контроль, start, sprint,
   Speed/Slowness, bow, spyglass, вода/лава, F5/forced perspectives, UI/hand/frame
   трубы/aspect/GUI scale. Scene15, остальное штатно. OP2 list/active/activate,
   точные русские тексты/одно уведомление. Creative/Spectator выключают только FOV;
   Adventure/Survival включают. Смерть/keepInventory оба/respawn/порталы/natural cycle.
2. manual_tests[1]: при активном tiny_fov изменить FOV90→80 и GUI scale,
   завершить через no_sprint, выйти в меню/открыть мир. Последнее80 используется
   с vanilla modifiers; options.txt не содержит временный15.
3. manual_tests[3,5]: dedicated + два клиента Survival и поздний третий,
   разные настройки/измерения/scopes/enabled/double, RTT/low TPS/pause,
   один lifecycle не влияет на остальных. Start/end/rejoin/server switch:
   только актуальное состояние. Double tiny_fov/no_sprint → no_sprint:
   FOV вернулся, запрет спринта сохранён. Для roll/offset/forced пар проверить
   обе очередности окончания и сохранение второго эффекта в обычном кадре.
4. manual_tests[6]: новый JVM с active tiny_fov, затем end через no_sprint
   при offline игроке, save/new JVM/rejoin; также empty server. Current IDs
   определяют15/vanilla, foreign items/HP/XP/effects/options сохранены.
   Команды /fdc stop нет.
5. Одинаковые versions/world/P/K/history/warmup/duration, inactive/active/double/
   PERSONAL и повторные reconnect: измерить FPS/MSPT/allocations/retained memory,
   отдельно mod gate/callback и shared sync/HUD/vanilla render/network.

Проверка взаимодействий в этом документе не заменяет самостоятельное ревью
upside_down_camera и offset_crosshair на этапах 26/27.
