# netherite_mobs — проверка

«Незеритовая вечеринка», HARD/GLOBAL. Название и оба описания взяты из актуального ТЗ.

## Реализация

- `ServerWorld.spawnEntity` отмечает только успешно добавленную новую `MobEntity`.
  Загрузка чанков/`tryLoadEntity` и `onDimensionChanged` не являются этим путём.
  Существующие мобы не обходятся. GLOBAL ID и включённость проверяются в момент
  рождения, независимо от игроков, их режима и присутствия на сервере.
- Комплект выдаётся один раз перед первым `tickEntity`/`tickPassenger`, либо перед
  первым `LivingEntity.damage`/`MobEntity.convertTo`, если они произошли раньше.
  Это позволяет завершить ванильную инициализацию после `convertTo` (например,
  при заражении жителя). Между добавлением сущности и первым тиком `/data get`
  может ещё видеть старое снаряжение. Никакого таймера или повторной выдачи нет.
- Два поля рождения сохраняются в NBT моба. Незавершённое рождение сохраняет
  право на комплект при загрузке, даже если испытание уже закончилось.
  Уже обработанная сущность не получает новую броню после повторной загрузки.
- Четыре полных незеритовых предмета имеют точный совместимый набор чар из ТЗ,
  `custom_data` с boolean `cursecycle:netherite_mobs`, drop chance 0. Ванильное
  копирование/сохранение компонентов сохраняет происхождение предмета.
- Независимые от активности перехваты запрещают подбор поверх помеченной брони,
  возвращают её drop chance 0 и отменяют только помеченный `MobEntity.dropStack`.
  Обычная броня, оружие и лут сохраняют ванильное поведение.
- `convertTo(..., true)` переносит предметы ванильным путём; для `false` переносятся
  только собственные помеченные предметы через `copyAndEmpty`, без клонирования.
  Это сохраняет последствие при лечении зомби-жителя после завершения испытания.
  Новый моб превращения во время активного испытания получает свой новый комплект.
- В этой версии базовый `LivingEntity.damageArmor` для мобов пуст. Для выданных
  предметов вызван ванильный `damageEquipment` в обычном пути расчёта брони:
  размер износа, Unbreaking, сопротивление источнику, поломка и события ванильные.
  Урон с bypasses_armor этот путь пропускает. Обычные предметы не затрагиваются,
  сломанная броня не восстанавливается. Иммунитеты/специальный урон боссов не меняются.
- Нет клиентского кода, нового HUD, сообщений, ссылок на игроков или глобального
  кэша сущностей. PERSONAL ID сам по себе не включает эту мировую GLOBAL-механику.

## Исторические проверки реализации — 2026-10-31

Команды выполняются из корня проекта с существующим кэшем Gradle, без изменения
зависимостей. Sandbox-запуск компиляции встретил отказ доступа к wrapper
`F:\Programs\GradleCache\wrapper\dists\...\gradle-8.10.2-bin.zip.lck`;
повтор использовал разрешённый доступ. Lock-файлы не удалялись.

- `.\gradlew.bat compileJava compileGametestJava --offline` — успешно.
- `.\gradlew.bat build runGametest --offline` — BUILD SUCCESSFUL: 145 JUnit
  (failures/errors/skipped=0), 74/74 GameTest. Итоговый прогон со всеми
  исправлениями и дополнительными сценариями завершился штатно за 55 секунд.
- `git diff --check`, просмотр staged diff и `git diff --cached --check` — успешно.
- JUnit: регистрация, метаданные, единственность ID, разрешение double_challenge.
- Три GameTest с настоящими серверными Mixin и ванильными сущностями:
  - точные четыре предмета/все уровни и отсутствие лишних чар на зомби, скелете,
    корове, жителе, слизне, Wither, Ender Dragon; неизменность оружия/HP;
    старый моб; повторный тик не обновляет предмет/прочность;
  - `EntityType.spawn` с NATURAL/SPAWN_EGG/SPAWNER, настоящая команда `/summon`
    с NBT, размножение коров, разделение слизня, пассажир; измерения Nether/End;
    превращение с инициализацией экипировки после добавления в мир;
  - первый урон до тика; запрет замены изношенной помеченной брони, обычный подбор
    оружия/брони в свободный слот; реальная атака, уменьшение урона, поломка
    (Unbreaking снят только у тестового шлема для детерминированной проверки),
    отсутствие восстановления; смерть от игрока с Looting III и принудительными
    шансами 2, положительный контроль обычного дропа;
  - сохранение/чтение NBT сущности с тем же UUID, сохранение незавершённого рождения,
    загрузка старого сохранения без меток; превращение после завершения,
    настоящий `ZombieVillagerEntity.finishConversion` (вызов через reflection,
    без ожидания таймера лечения), сохранение комплекта и происхождения;
  - GLOBAL/double, все режимы игрока, поздний вход/respawn/teleport/reconnect,
    запись/чтение `CurseCycleState` с диска в активном и завершённом состоянии,
    disabled и PERSONAL gate; OP 2 `/fdc list`, `/fdc activate netherite_mobs`,
    `/fdc active`.

Промежуточно исправлены ошибка ссылки на `isBaby` в тесте и неверный источник
урона теста износа: `generic` обходит броню, поэтому заменён реальной атакой
игрока. Это не повод менять семантику обходящего броню урона.

## Историческое исправление нестабильности GameTest — 2026-11-01

`birthsIncludingPostSpawnInitialization` сохраняет строгие проверки нулевого
износа, точных чар и отсутствия восстановления. Исправлено окружение теста:

- проверенные мобы удаляются между случаями, а у зомби из `EntityType.spawn`
  отключён ИИ перед первым настоящим тиком;
- сценарий начальной прочности выполняется ночью с пересчётом ambient darkness;
  исходное время и освещение восстанавливаются в `finally`;
- ошибка прочности теперь также указывает тип моба.

Временная диагностика обнаружила реальную атаку зомби на оставшегося жителя
и ответный урон `thorns`, затем вызовы `LivingEntity.damageEquipment`.
Отдельно ванильный `ZombieEntity.tickMovement` случайно увеличивает damage
шлема через `ItemStack.setDamage` на солнце. Поэтому первые исправления
взаимодействий без контроля света ещё дали 4 сбоя в 33 стресс-проверках.

После исправления обоих условий прошли 30 дополнительных запусков сценария
с исходным временем 0/6000/18000 плюс три постоянных netherite GameTest:
33/33, exit 0. Повторы создавались только локальным init-script под build;
постоянное число GameTest не увеличено. Боевой тест износа/поломки не изменён,
ванильные механики и production-код не менялись.

Логи: `build/netherite-diagnostic.log`, `build/netherite-durability.log`,
`build/netherite-stability.log` (первая, ещё неполная правка),
`build/netherite-stability-fixed.log`. Соответствующие XML — под build/reports.
Временный диагностический callback удалён; mixin диагностики и классы повторов
подключались только локальными init-script и не входят в игровой JAR.

Для воспроизводимого полного прогона использовать новый runDir:

```groovy
allprojects {
    afterEvaluate {
        loom.runs.gametest.runDir = 'build/gametest-netherite-full-fresh'
        loom.runs.gametest.vmArg '-Dfabric-api.gametest.report-file=../reports/netherite-full-fresh.xml'
    }
}
```

```powershell
.\gradlew.bat build runGametest --offline -I build/netherite-full-a.init.gradle
.\gradlew.bat runGametest --offline -I build/netherite-full-b.init.gradle
```

Оба локальных init-script используют разные свежие миры. Проверенные XML:
`build/reports/netherite-full-a-gametest.xml` и
`build/reports/netherite-full-b-gametest.xml`: два раза 83/83, failures/errors=0.
Первый запуск также заново выполнил 149 JUnit, failures/errors/skipped=0.
Логи: `build/netherite-full-a.log`, `build/netherite-full-b.log`.

## Ревью этапа 32 — 2026-11-07

Production-код не изменён; добавлены три предметных GameTest. Исторические
результаты выше не засчитываются как доказательство этого ревью.

Изолированный запуск выбирает только `com.cursecycle.NetheriteMobsGameTest`
в копии `fabric.mod.json`, сохраняет production classpath/Mixin и использует
новый runDir, отдельные XML и принудительно свежие JUnit. Пример init-script:

```groovy
allprojects {
    afterEvaluate {
        def resources = project.file('build/netherite-mobs-review-final-resources')
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.NetheriteMobsGameTest']
        resources.mkdirs()
        new File(resources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([resources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        tasks.named('test') {
            outputs.upToDateWhen { false }
            reports.junitXml.outputLocation = layout.buildDirectory.dir('reports/netherite-mobs-review-final-junit')
        }
        loom.runs.gametest.runDir = 'build/gametest-netherite-mobs-review-final-20261107'
        loom.runs.gametest.vmArg '-Dfabric-api.gametest.report-file=../reports/netherite-mobs-review-final.xml'
    }
}
```

При повторе заменить имя phase/runDir на новое. Команды из корня проекта:

```powershell
.\gradlew.bat build runGametest --offline -I build/netherite-mobs-review-baseline.init.gradle
.\gradlew.bat build runGametest --offline -I build/netherite-mobs-review-expanded-attempt1.init.gradle
.\gradlew.bat build runGametest --offline -I build/netherite-mobs-review-final.init.gradle
.\gradlew.bat runClient --offline -I src/clientprobe/invisible_mobs/init.gradle
.\gradlew.bat processGametestResources --offline
python build/netherite-mobs-review-verify.py
```

Baseline: exit 0, 1m28s, 149 JUnit/54 suites и 3/3 target GameTest.
Расширенный первый запуск: exit 0, 50s, 149 JUnit/54 suites и 6/6 target GameTest.
Финальный запуск: exit 0, 47s, 149 JUnit/54 suites и 6/6 target GameTest.
В каждом прогоне failures/errors/skipped=0, сервер завершился штатно.
Клиентская проверка пары: exit 0, 31s,
`INVISIBLE_MOBS_CLIENT_PROBE_PASSED checks=10318 renderCalls=1696`, лог
`build/netherite-mobs-review-invisible-pair.log`. Восстановление ресурсов:
exit 0, 2s, исходные и собранные metadata совпадают, 43 entrypoint.
Verifier: exit 0, SHA5/metadata6/registration1/serverMixins4/freshXML-JUnit/
CLIENT marker/43/два чистых production JAR.
Финальные результаты и пути перечислены в локальном отчёте
`Промпты/reviews/32-netherite_mobs.md`; logs/XML —
`build/netherite-mobs-review-<phase>.log`,
`build/reports/netherite-mobs-review-<phase>.xml` и
`build/reports/netherite-mobs-review-<phase>-junit/`.

Новые сценарии:

- `repeatedActivationsKeepOnlyBirthConsequences`: пустой сервер, фиксация
  eligibility до смены активации; 32 цикла duplicate ID/same ID/double/no_sprint
  для двух EmbeddedChannel игроков; оба окончания пары, сохранение damage=13,
  отсутствующего предмета, HP=17/items=17 и запрета дропа; новые inactive без
  комплекта, собственный challengeData пуст. Это не замер памяти или CPU.
- `dimensionTransferRejectedBirthAndConversionBeforeTick`: настоящий отказ
  дублирующего UUID без birth flags; реальные `Entity.teleportTo` в Nether/End/
  Overworld для старой и выданной брони, без новой выдачи/ремонта; pending birth
  перед `convertTo`, оба keepEquipment, перенос без клонирования и с damage=23;
  стойка для брони/обычный дроп исключены. Ночной fixture восстанавливает время
  и ambient darkness; это не физическое прохождение портала.
- `actualRaidWaveAndIndependentPlayerEffects`: настоящий приватный ванильный
  `Raid.spawnNextWave` вызван через reflection для трёх волн. Новые участники
  получают точный комплект только при активном мировом правиле; оба окончания
  пары с endless_raid сохраняют соседнюю механику, проверен BAD_OMEN игрока.
  Raid создан напрямую: village search/countdown/ownership/repeated raids этим
  тестом не доказаны. Следующее испытание отдельно здесь не ревьюится.
  Дополнительно armor_disabled обнуляет броню игрока, сохраняя защиту моба.

Клиентский existing probe invisible_mobs проверяет actual transformed dispatcher
и production gate, в том числе оба окончания пары с netherite_mobs. Он использует
Unsafe-контекст/recording renderer/подмену terminal GL и ручную синхронизацию;
это не обычный клиент с отрисованной сценой и сетевой передачей экипировки.
Серверные шесть сценариев исполняют реальные production Mixin без подмены gate.

Статически: spawn — O(G), где G число GLOBAL IDs; обычный tick каждого моба —
проверка одного pending boolean, комплект строится однократно (4 предмета,
22 добавления чар). Damage/drop/pickup/conversion — фиксированное число слотов
с проверкой происхождения. `isIssued` копирует CUSTOM_DATA: O(B) по размеру NBT
предмета, а не безусловное O(1); свои новые предметы имеют одну boolean-метку.
Даже inactive остаются callbacks, проверки и два сериализуемых boolean на моба;
это нужно для постоянных последствий. Нет обхода мира, таймера, глобального
UUID map, собственного пакета, player/world refs или повторной регистрации.
Общие sync/HUD/history и ванильные броня/чары/AI имеют отдельную стоимость.
FPS/MSPT/CPU/allocations/retained memory не измерялись.

## Не выполненные ручные проверки

Автоматические тесты используют headless-сервер и EmbeddedChannel-игроков.
Они не подтверждают полный запуск клиента, настоящее сетевое соединение,
выключение/новый запуск JVM, естественный выбор точки спавна или таймер спавнера.

1. В Survival выполнить `/fdc list`, `/fdc activate netherite_mobs`, `/fdc active`.
   Сравнить русские тексты в меню/HUD. Создать старого моба до активации;
   проверить новых естественно, яйцом, спавнером, размножением, `/summon` и
   призывом боссов во всех измерениях. Проверять четыре слота через серверный
   `/data get entity` после первого тика, включая модели без визуальной брони.
2. Сражаться и проверить защиту, Thorns, Unbreaking/Mending, износ и поломку;
   не ожидать универсального отображения брони на всех моделях. Убедиться,
   что оружие, ИИ, здоровье, специальные иммунитеты боссов и частота спавна обычные.
3. Убить мобов с Looting III, проверить обычный лут и отсутствие выданной брони.
   Проверить утопление зомби, заражение и лечение жителя, превращение пиглина,
   замену снаряжения и перенос моба порталом. После завершения старые комплекты
   сохраняются и не выпадают, новые мобы ванильные.
4. Сохранить/выгрузить/загрузить чанк и полностью перезапустить сервер при
   активном испытании. Затем `/fdc activate no_sprint`, сохранить/перезапустить
   ещё раз, вернуть офлайн-игрока: не должно быть новой выдачи старым мобам,
   возврата сломанной брони или потери запрета дропа сохранившихся предметов.
5. Два настоящих клиента и поздний вход: смерть/respawn, reconnect, порталы,
   Survival/Adventure/Creative/Spectator. GLOBAL-спавн действует во всех режимах,
   игроки не получают броню. Проверить `double_challenge:netherite_mobs,no_sprint`,
   затем `no_sprint`; отдельно убедиться, что armor_disabled не снимает броню мобов.

Завершать через активацию другого испытания; команды `/fdc stop` нет.
