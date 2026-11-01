# Общие механизмы — ревью foundation

Этап проверяет общие контракты, регистрацию и стенд. Он не заменяет
предметное ревью каждого испытания и полный игровой клиент.

## Исправления

- GLOBAL/PERSONAL и повторные сохранённые ID больше не вызывают один
  onTick/onPlayerJoin/onPlayerLeave несколько раз. Активация одинаковых ID
  и reset также вызывают start/stop однократно.
- `CurseCycleState.findPersonalActiveChallengeIds(UUID)` читает существующее
  назначение без создания записи. Все production-проверки используют его.
  Старый `getPersonalActiveChallengeIds` оставлен для изменения назначения;
  после изменения нужен `markDirty`. Scheduler удаляет пустые записи,
  оставшиеся от прежнего чтения или удаления отключённых назначений.
- Описания в 10 классах (11 полей) приведены к актуальным локальным JSON.
  Названия, сложности, scope и игровое поведение при этой правке не менялись.

## Постоянные регрессии

`CurseCycleStateTest`: 10 000 чтений отсутствующих UUID не создают записей
и не помечают состояние изменённым; настоящие назначения доступны и сохраняются.
`CycleManagerTest`: очистка пустых и отключённых персональных назначений.

`FoundationGameTest` (отдельный batch):

1. Повторный ID одновременно в GLOBAL/PERSONAL: настоящий join, tick,
   dispatch события, vanilla respawn и DISCONNECT callback вызываются один раз.
   Spy временно заменяет служебный test_easy и восстанавливает его в finally.
2. Повторный ID в encoded double и reset: однократные start/stop.
3. 100 проверок gates/tick не создают PERSONAL-записей или пустого NBT.
4. Настоящие PersistentStateManager.save/readNbt: serial, GLOBAL, PERSONAL
   офлайн-владельца и challengeData переживают запись/чтение; новая активация
   меняет serial и очищает только данные активации.

DISCONNECT проверяется через Fabric invoker, без физического клиента.
Disk reload выполняется в той же JVM, а не полным перезапуском процесса.

## Воспроизведение

```powershell
.\gradlew.bat build --offline
.\gradlew.bat runGametest --offline
```

Для свежего мира создать локальный init-script (runDir должен ещё не существовать):

```groovy
allprojects {
    afterEvaluate {
        loom.runs.gametest.runDir = 'build/gametest-foundation-fresh'
        loom.runs.gametest.vmArg '-Dfabric-api.gametest.report-file=../reports/foundation-fresh.xml'
        tasks.named('test') { outputs.upToDateWhen { false } }
    }
}
```

Передать его через `-I build/<имя>.init.gradle`. Не удалять lock-файлы
Gradle и не выдавать старые UP-TO-DATE XML за новый запуск тестов.

Для изоляции четырёх FoundationGameTest использован локальный script:

```groovy
allprojects {
    afterEvaluate {
        def isolatedResources = project.file('build/foundation-isolated-resources')
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.FoundationGameTest']
        isolatedResources.mkdirs()
        new File(isolatedResources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([isolatedResources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        loom.runs.gametest.runDir = 'build/gametest-foundation-isolated-20261101'
        loom.runs.gametest.vmArg '-Dfabric-api.gametest.report-file=../reports/foundation-isolated-gametest.xml'
    }
}
```

Он меняет только производные тестовые ресурсы. Чтобы вернуть полный список,
выполнить обычный `processGametestResources` без этого script.

## Результаты 2026-11-01

- Исходный код: 146/146 JUnit, 79/79 GameTest в новом мире.
- До production-исправлений новые регрессии воспроизвели повторный join/start
  и создание пустого UUID. В том же прогоне отдельно упал старый
  `NetheriteMobsGameTest.birthsIncludingPostSpawnInitialization` на damage=1
  у новой брони; этот сбой не является доказательством ошибки foundation.
- После исправлений: 150/150 JUnit с локальной проверкой 198 полей метаданных
  33 JSON (149 постоянных тестов и один локальный inventory-тест).
- Полный последний GameTest: 82/83, все четыре foundation прошли. Снова
  упал только старый NetheriteMobsGameTest на начальной прочности. Между ними
  был полный прогон, где NetheriteMobs прошёл, а новый reset-тест ошибочно
  учитывал stop предыдущей активации; исправлен именно счётчик fixture.
- Изолированный foundation: 4/4, exit 0; свежий мир, штатный shutdown/save.
- Финальный closeout после сохранения раннего no-player gate:
  build + свежие 149/149 JUnit + 4/4 foundation, exit 0.
- Обычная сборка без inventory init-script: 149/149 JUnit, exit 0.
  `build processGametestResources runClient --offline -I src/clientprobe/tiny_fov/init.gradle`
  восстановил полный список GameTest и дал `TINY_FOV_CLIENT_PROBE_PASSED`:
  2700 сравнений transformed vanilla getFov. Это smoke-проверка клиентского
  стенда с Unsafe fixtures и stub accessors, без настоящего окна/сети.
  Она не засчитывается как предметное ревью tiny_fov или остальных испытаний.
- Проверены binary/sources JAR: GameTest, fixtures и validation/probe-классов нет.
- Дальнейшая проверка этой нестабильности относится к ревью netherite_mobs;
  полный набор не объявляется зелёным. Production-код и старый тест этого
  испытания ради результата не менялись.

Последующая адресная правка по запросу пользователя закрыла эту нестабильность:
изолированы взаимодействия мобов и дневной износ шлемов в fixture.
Проверки сохранены. Прогон 30 повторов + три netherite GameTest дал 33/33;
новая полная сборка — 149 JUnit; два полных запуска в разных свежих мирах —
83/83 GameTest каждый, failures/errors=0.
Причины, повторные проверки и логи: [netherite-mobs-testing.md](netherite-mobs-testing.md).
Числа 82/83 выше относятся к первоначальному ревью и сохранены как история.

Логи/отчёты: `build/foundation-{baseline,red,final,verified,isolated}-tests.log`,
`build/reports/foundation-{baseline,red,final,verified,isolated}-gametest.xml`,
копии JUnit `build/foundation-{baseline,focused,verified}-junit/`.
Обычная сборка/CLIENT: `build/foundation-client-build.log`,
актуальные 149 JUnit XML в `build/test-results/test/`.
Последний код: `build/foundation-closeout-tests.log` и
`build/reports/foundation-closeout-gametest.xml`.
Первичный отказ доступа к Gradle-кэшу: `build/foundation-baseline-build.log`;
успешный повтор: `build/foundation-baseline-build-elevated.log`.

## Пределы вывода

Общие callbacks зарегистрированы один раз при initialization. Состояние
загружается из overworld PersistentState, effect ledger — из NBT игрока.
Выход игрока сам по себе не завершает активацию; start/stop зависят от online
игроков, поэтому постоянное мировое состояние должно сверять текущую активацию
самостоятельно. GameTest проверяет реально исполнившиеся серверные пути.

Стоимость оценена статически и счётчиками поведения. MSPT/FPS, профиль памяти,
длительный multiplayer и полный restart JVM не измерялись. Пересылка полной
истории раз в 20 тиков стоит O(P×H); история растёт по активациям. Это явное
ограничение оценки, а не обещание постоянной стоимости или нулевого оверхеда.
