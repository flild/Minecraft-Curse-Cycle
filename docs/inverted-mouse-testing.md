# inverted_mouse — проверка

ТЗ: «Лучше бы вечная тошнота», HARD/GLOBAL. Инвертируются обе компоненты
мышиного взгляда после текущей sensitivity, smoothing/spyglass и invertY.
Настройки пользователя и текущий угол при завершении сохраняются.

## Пути реализации

- `InvertedMouseChallenge` задаёт метаданные, зарегистрирован один раз.
- Client-only `InvertedMouseMixin` меняет аргументы единственного
  `ClientPlayerEntity.changeLookDirection(DD)` в `Mouse.updateMouse`.
- `InvertedMouseInput.shouldInvert` проверяет текущие world/player/manager,
  alive/removed, Survival/Adventure и синхронизированные эффективные ID.
  Перспектива, транспорт и жидкости не исключают мышиный ввод.
- Enabled/GLOBAL/PERSONAL/double фильтруются общим `NetworkHandler`.
  JOIN/DISCONNECT сбрасывают `ClientDataHolder`; собственных пакетов,
  таймеров, коллекций, NBT и ссылок на игрока у испытания нет.
- GUI, кнопки, колесо и абсолютные повороты проходят отдельными путями.
  Пары с keyboard/roll/forced perspective сохраняют независимость.

## Свежее ревью 2026-11-05

Проверенный production HEAD: `74ab54f7c16fed0561db6f277923ac56609123e2`.
Production/shared код не менялся; подтверждённых дефектов нет.
Предварительный итог: `pass_with_limits`; расходы `no_issue_found / static`.
Исторические результаты реализации не засчитывались как новое подтверждение.

| Команда | Результат | Доказательства |
|---|---|---|
| `.\gradlew.bat build runGametest --offline -I build/inverted-mouse-review-baseline.init.gradle` | exit 0, 28 s; 149 JUnit / 54 suites, 2/2 GameTest | `build/inverted-mouse-review-baseline.log`, `build/reports/inverted-mouse-review-baseline.xml`, `build/reports/inverted-mouse-review-baseline-junit/` |
| `.\gradlew.bat build runGametest --offline -I build/inverted-mouse-review-final.init.gradle` | exit 0, 30 s; 149 JUnit / 54 suites, 3/3 GameTest | Аналогичные пути `inverted-mouse-review-final`; штатный shutdown |
| `.\gradlew.bat runClient --offline -I src/clientprobe/inverted_mouse/init.gradle` | attempt4 exit 0, 20 s; `INVERTED_MOUSE_REVIEW_CLIENT_PASSED checks=4803 pairs=384` | `build/inverted-mouse-review-client-attempt4.log` |
| `.\gradlew.bat processGametestResources --offline` | exit 0, 1 s; восстановлены 43 entrypoints | `build/inverted-mouse-review-restore.log` |
| `python build/inverted-mouse-review-verify.py` | сверка XML, метаданных, SHA, Mixin и JAR | `build/inverted-mouse-review-verifier.log` |

GameTest init меняет только тестовую metadata, отчёты и свежий runDir
`build/gametest-inverted-mouse-review-<baseline/final>-20261105`.
Для воспроизведения его содержимое:

```groovy
allprojects {
    afterEvaluate {
        def resources = project.file('build/inverted-mouse-review-final-resources')
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.InvertedMouseGameTest']
        resources.mkdirs()
        new File(resources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([resources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        tasks.named('test') {
            outputs.upToDateWhen { false }
            reports.junitXml.outputLocation = layout.buildDirectory.dir('reports/inverted-mouse-review-final-junit')
        }
        loom.runs.gametest.runDir = 'build/gametest-inverted-mouse-review-final-20261105'
        loom.runs.gametest.vmArg '-Dfabric-api.gametest.report-file=../reports/inverted-mouse-review-final.xml'
    }
}
```

`InvertedMouseGameTest` выполняет реальные S2C в EmbeddedChannel и codec,
проверяет двух игроков и поздний вход, режимы, respawn, Nether/End/Overworld,
GLOBAL/PERSONAL/дубли/disabled и OP2 команды. Новый тест выполняет 32 serial /
duplicate / start-join-leave-stop / double-no_sprint / end цикла, сохраняет
именованные 17 diamonds, HP17, XP7+.625 и Fire Resistance. Реальный Fabric
DISCONNECT + playerdata и PersistentState.save/read/set подтверждают active,
offline-ended и empty-server-ended состояния. Это диск одной JVM.

Сохранённый CLIENT-стенд компилируется отдельно и не входит в production JAR.
Он исполняет transformed `Mouse.updateMouse` с production gate: 384 пары,
sensitivity 0/.1/.5/1, invertY on/off, smoothing/spyglass, 3 перспективы,
4 последовательных кадра с положительными/отрицательными/нулевыми дельтами.
Дополнительно: настоящий `Entity.changeLookDirection` (yaw, pitch clamp ±90,
сохранение угла при end, абсолютные setYaw/setPitch), 512 настоящих KeyboardInput
тиков для всех 128 masks и обоих окончаний пары; настоящий injected roll-handler
с проекцией для обоих окончаний; forced perspective setter; 32 environment masks;
32 reset/start/end × 50 повторных sync, актуальные настройки сохраняются.
ASM проверяет единственный mouse-hook только в updateMouse; текущий байткод
сохранён в `build/inverted-mouse-review-Mouse.javap.txt`.

Подмены: Unsafe client/world/options/players/manager/renderer, alive/mode/
spyglass/environment accessors, recording sink для точных приращений; в отдельном
RealLookPlayer приёмник углов не переопределён. Sync доставляется вручную.
Roll-handler вызван отражением; renderWorld/OpenGL не исполняются. Нет окна,
настоящей сети, GUI dispatch, options.txt I/O и физического движения мыши.

Неудачные CLIENT попытки сохранены: attempt1 exit1/22s — слишком точный допуск
float pitch; attempt2 exit1/20s — отсутствующий fluidHeight в Unsafe fixture при
KeyboardInput→MovementMemory gate; attempt3 exit1/20s — ожидаемая округлённая
константа sensitivity вместо vanilla float-коэффициентов. Исправлены только
fixture/assertions; сравнения raw mouse остаются с допуском 1e-10 относительно
vanilla контроля, float Entity angles с допуском 1e-5.

## Накладные расходы

На каждый mouse-look вызов `@ModifyArgs` создаёт Args/Object[]/boxing до gate,
даже при выключенном испытании. Gate O(K) по числу синхронизированных ID,
активная обработка O(1), два отрицания/обновления args. K — общий список,
своего растущего состояния нет. Нет own tick/render/world scan/C2S/S2C/save;
есть общие dispatch, синхронизация каждые 20 server ticks и HUD. Время кадра,
MSPT, allocations и retained memory не измерялись; нулевая цена не заявляется.

## Ручные сценарии — не выполнены

1. `inverted_mouse-L01`: OP2 `/fdc list`, `/fdc activate inverted_mouse`,
   `/fdc active`; проверить точные тексты/одну нотификацию/HUD/меню. Сравнить
   физический сдвиг мыши до/после вдали от ±90, live invertY/sensitivity/smooth/
   spyglass, GUI курсор/клики/колесо/атака/use, три перспективы, boat/horse/swim/
   elytra; оба окончания пар keyboard/roll/no_sprint/forced front-rpg. Modes,
   death/respawn keepInventory оба, реальные порталы и естественный цикл.
2. `inverted_mouse-L02`: dedicated + два Survival и поздний третий; разные
   options, GLOBAL/PERSONAL/double/enabled, lifecycle одного игрока, latency/
   TPS/pause/start/end/rejoin/смена сервера. После доставки актуального S2C
   инверсия ровно одна; нет переноса старого эффекта, no_sprint сохранён.
3. `inverted_mouse-L03`: полностью новые JVM с active, ended, offline-ended,
   empty-server-ended сохранениями; вернуть тот же UUID. Активность актуальна,
   settings последнего выбора и чужие предметы/HP/XP/effects сохранены.
4. `inverted_mouse-L04`: одинаковые версии/мир/P/K/H/частота мыши/прогрев/
   длительность; inactive/active/double/PERSONAL и 32 reconnect/start/end.
   Измерить FPS/MSPT/allocations/retained memory; отделить Args/gate от shared
   sync/HUD и vanilla input/render/network. Функциональные counters не профиль.

Полная матрица и реестр ограничений: локальный отчёт
`Промпты/reviews/24-inverted_mouse.md`. Прежние ограничения других этапов
сохраняются; самостоятельное ревью следующего испытания не начиналось.
