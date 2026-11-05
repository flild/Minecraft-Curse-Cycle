# gravity_ten — проверка

Испытание «Земля тянет к себе», MEDIUM/GLOBAL, множитель гравитации 3.
Название и оба описания взяты из актуального JSON, включая его формулировки.

## Механика и точки перехвата

В Yarn 1.21.1+build.3 `LivingEntity.travel` получает `getFinalGravity()`,
затем при движении вниз применяет `min(gravity, 0.01)` для Slow Falling.
`GravityTenLivingEntityMixin` изменяет первый double-local один раз перед
`World.getFluidState`, после этого выбора и перед всеми ветками движения.
Начальная скорость, прыжковый импульс, атрибуты и сопротивление не записываются.
Общий Mixin работает на сервере и в клиентском предсказании. Сервер использует
`ChallengeActivity`, клиент — уже отфильтрованные сервером эффективные ID
из `ClientDataHolder`, включая PERSONAL и двойные испытания.

Пути этой версии:

- Воздух вычитает g перед вертикальным drag. Левитация заменяет это вычитание.
- Вода использует `applyFluidMovingSpeed(g, falling, velocity)` и g/16;
  при спринте вычитания нет. Ванильное округление малой скорости до -0.003
  сохранено: это отдельная ветка, поэтому не вся результирующая скорость ×3.
- Глубокая лава вычитает g/4 после drag. Мелкая лава дополнительно использует
  g/16 через `applyFluidMovingSpeed`; оба члена получают один множитель.
- Элитры используют g в выражении `g * (-1 + cos²(pitch) * 0.75)`;
  остальной подъём и преобразование скорости остаются ванильными.
- `computeFallDamage` возвращает ванильно рассчитанное целое значение;
  положительный результат умножается на 3. Сначала сохраняются ванильные
  safe distance, множитель блока/атрибута и округление, затем собственный ×3.
  Последующая защита и обычный путь `damage` сохраняются. Другой урон не меняется.

Гейт исключает мёртвых/удалённых игроков, Creative, Spectator и пассажиров.
Мобы, предметы и транспорт не проходят проверку PlayerEntity.
Нет своего NBT, модификаторов, таймеров и ссылок на игроков: активные ID уже
сохраняются общей системой. Окончание не изменяет скорость и fallDistance.

## Автоматические проверки, 2026-10-27

- `.\gradlew.bat test compileGametestJava --offline` — BUILD SUCCESSFUL, 15s;
  116 JUnit, failures/errors/skipped = 0. Новый `GravityTenTest` проверяет
  единственную регистрацию, название, краткое описание, сложность и область.
- `.\gradlew.bat -I build/gravity-clean.init.gradle build runGametest --offline`
  — BUILD SUCCESSFUL, 50s; все 49 GameTest прошли, включая оба новых.
  Init в игнорируемом build меняет только runDir на build/gravity-clean-gametest.
  Лог: build/gravity-verification.log; XML: build/reports/gametest.xml.
- `GravityTenGameTest.realTravelBranchesAndFallDamage`: реальные travel/jump,
  горизонтальный drag, исходная скорость, Jump Boost, Slow Falling, Levitation,
  внешний gravity-атрибут, noGravity, вода/спринт/округление малой скорости,
  обе лавовые ветки, элитры, остановка в падении, округлённый/неположительный
  fall damage, фактическая потеря 6 HP от контрольных 2 HP, обычный урон,
  исключение пассажира и неизменная физика моба.
- `GravityTenGameTest.activationModesPersistenceAndLifecycle`: команды list,
  activate и active (в первом тесте), повторный lifecycle, Creative/Spectator/
  Adventure/Survival, heavy_body/no_jumping/no_sprint, PERSONAL и дубли ID,
  disabled, поздний вход, повторный UUID, смерть/респаун, Nether/Overworld,
  настоящий save/readNbt через PersistentStateManager и окончание офлайн.
- `.\gradlew.bat -I build/gravity-client-probe/init.gradle runClient --offline`
  — BUILD SUCCESSFUL, 31s; GRAVITY_CLIENT_PROBE_PASSED. Настоящие CLIENT
  travel/computeFallDamage с Mixin: воздух, исходная скорость прыжка,
  Slow Falling, Levitation, вода, лава, элитры, исключения и reset.
  Мир, движение при столкновениях и часть методов игрока подменены.
  Исходники, init.gradle, probe.jar и лог находятся в build/gravity-client-probe;
  игровое окно/мир не запускались, тестового стенда в игровом JAR нет.
- `git diff --check` и `git diff --cached --check` — без ошибок.

При подготовке тестов исправлены ожидания ванильного порога воды, подъёма
элитр и отрицательного computeFallDamage; бассейн перед лавой очищается
от воды, чтобы не создавать обсидиан/камень. Эти ошибки были в тестах;
изначальная игровая реализация прошла итоговую проверку без изменений.
Первый запуск Gradle из песочницы не получил доступ к общему wrapper zip.lck;
последующие проверки выполнены с разрешённым доступом к кэшу без удаления lock.

## Ревью этапа 22, 2026-11-05

Reviewed production HEAD: `f7720b12da5643569e36fc35fcd050ed9db74a38`.
Подтверждённых дефектов игрового кода не найдено; изменены только этот документ,
GravityTenGameTest и отдельный CLIENT probe. Итог `pass_with_limits`, механика
`pass`, расходы `no_issue_found / static`. Результаты 2026-10-27 выше исторические.

Свежие проверки:

| Команда | Результат | Доказательство |
|---|---|---|
| `build runGametest --offline -I build/gravity-ten-review-baseline.init.gradle` | exit 0, 46s; 149 JUnit / 54 suites, 2/2 gravity GameTest | build/gravity-ten-review-baseline.log; build/reports/gravity-ten-review-baseline.xml и baseline-junit/ |
| `build runGametest --offline -I build/gravity-ten-review-expanded.init.gradle` | exit 1, 33s; 149 JUnit, 3/4 gravity GameTest | expanded.log/XML: неверное новое ожидание Slow Falling в computeFallDamage |
| `build runGametest --offline -I build/gravity-ten-review-final.init.gradle` | exit 0, 36s; 149 JUnit, 8/8 GameTest (4 gravity + 4 heavy_body), normal shutdown | final.log/XML и final-junit/ |
| `runClient --offline -I src/clientprobe/gravity_ten/init.gradle` | attempt1 exit 1/3s: конфликт inherited fluidHeight; attempt2 exit 1/32s: Unsafe fixture не задавал swim-height; attempt3 exit 0/19s | build/gravity-ten-review-client-attempt1..3.log; marker GRAVITY_TEN_REVIEW_CLIENT_PASSED checks=2738 travel=2404 |
| `processGametestResources --offline` | exit 0, 1s; исходные 43 entrypoints восстановлены | build/gravity-ten-review-restore.log |
| `python build/gravity-ten-review-verify.py` | exit 0, GRAVITY_TEN_REVIEW_VERIFIED | build/gravity-ten-review-verifier.log; первый verifier exit 1 из-за UTF-16 PowerShell log, исправлено чтение BOM, attempt1.log сохранён |

Все Gradle-команды запускаются через `.\gradlew.bat`. Новые GameTest:

- `scopedSyncRepeatedActivationAndDiskKeepForeignData`: фактический S2C только
  PERSONAL-владельцу, codec, второй игрок без эффекта, enabled фильтрация;
  32 serial/duplicate/start/join/pair/end, velocity/fallDistance и no_sprint;
  Fabric DISCONNECT, playerdata + PersistentStateManager save/read/set,
  активная загрузка, offline end и пустой сервер. Реальный travel после
  Nether/End/Overworld, foreign gravity .04, named diamonds17/XP7+.625/HP17/
  Fire Resistance и чужой challengeData сохранены. Это одна JVM.
- `automaticAndHeavyJumpsAndFallModifiersCompose`: настоящий auto jump,
  последующий travel, оба окончания пары auto/gravity и heavy/gravity;
  safe fall distance 3/5, внешний damage attribute .5/1/1.5, block argument
  0/.2/.5/1 и distance 2/4.1/7/12. Только положительный округлённый результат
  ×3, Jump Boost safe distance сохранён. Slow Falling предотвращает накопление
  fallDistance в ванильном движении, а не обнуляет произвольно переданную
  computeFallDamage дистанцию: ошибочное ожидание 0 заменено сравнением с
  контрольным результатом. Production при исправлении fixture не менялся.

Изолированный final init (baseline/expanded используют только GravityTenGameTest
и свои phase/runDir/XML):

```groovy
allprojects {
    afterEvaluate {
        def resources = project.file('build/gravity-ten-review-final-resources')
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.GravityTenGameTest', 'com.cursecycle.HeavyBodyGameTest']
        resources.mkdirs()
        new File(resources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([resources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        tasks.named('test') {
            outputs.upToDateWhen { false }
            reports.junitXml.outputLocation = layout.buildDirectory.dir('reports/gravity-ten-review-final-junit')
        }
        loom.runs.gametest.runDir = 'build/gametest-gravity-ten-review-final-20261105'
        loom.runs.gametest.vmArg '-Dfabric-api.gametest.report-file=../reports/gravity-ten-review-final.xml'
    }
}
```

CLIENT probe сохранён в `src/clientprobe/gravity_ten/`, вне production sourceSets.
Новых test Mixin нет: исполняются настоящие transformed travel/computeFallDamage
и GravityTenChallenge.active. Unsafe создаёт ClientWorld/ClientPlayerEntity;
world block/fluid/chunk, logical movement, alive/modes/vehicle/effects/attributes,
swim-height, velocity и collision/input/limb методы подменены. CLIENT jump impulse
задаётся начальной скоростью; настоящий jump проверяет SERVER GameTest.
Матрица g=0/.005/.01/.04/.08/.12 × y=-.7/-.1/0/.2/.42 × Slow Falling × active:
воздух и horizontal drag, levitation, water/sprint, shallow/deep lava;
отдельно elytra pitch0, noGravity, positive/nonpositive rounded damage,
dead/removed/Creative/Spectator/passenger, 32 reset × 50 duplicate sync.
Sync вызывается вручную, игровое окно, настоящий мир/сеть и collision pipeline
не запущены. Настоящая передача S2C отдельно проверена EmbeddedChannel на сервере.

Verifier сверил SHA пяти входных документов/ТЗ, 6 metadata fields, multiplier3,
registration1/commonMixin1, все три XML и JUnit, CLIENT marker, восстановленные
43 entrypoints/source==built и два production JAR без GameTest/validation/Probe.
Обычный sandbox exec не создал процесс (ACL error5); разрешённый PowerShell
запуск работал. Два ошибочных пути чтения найдены через rg; locks не удалялись.

Static расходы: один PlayerEntity check на каждый подходящий travel LivingEntity,
затем gate O(G+A) server / O(K) client; g×3 O(1). Fall hook вызывается по событию,
с тем же gate; CallbackInfo/boxing имеют цену. Собственных tick/packet listeners,
NBT, maps, timers и retained Player/World нет. Shared dispatch/no-op callbacks,
PersistentState.Type lookup allocation и S2C раз в20ticks/activation/join остаются.
Время кадра, MSPT, allocations и retained memory не измерялись.

Ограничения ревью gravity_ten-L01..L04: обычный client/HUD/menu/аудио/полная
физика столкновений и mitigation; dedicated 2clients+late3/RTT/TPS;
full new-JVM active/ended/offlineend/emptyserver; одинаковый нагрузочный профиль.
Точные ручные шаги ниже; эти сценарии не объявлены пройденными.

## Ручные проверки — не выполнены

1. В живом клиенте выполнить `/fdc list`, `/fdc activate gravity_ten`,
   `/fdc active`. Проверить русское название, HUD и полные правила меню,
   стандартное уведомление без дополнительных сообщений/звуков.
2. Сравнить одинаковую высоту и начальную скорость: падение, обычный прыжок,
   спринт, Jump Boost, Slow Falling вверх/вниз, Levitation. Первый импульс
   прыжка прежний; последующее ускорение ×3 с прежним drag. Проверить
   столкновения, ступени, лестницы, лозы, приседание и внешний толчок.
3. Повторить в стоячей/текущей воде, под водой, при плавании и спринте,
   в мелкой/глубокой лаве с огнестойкостью; проверить всплытие и выход из воды.
   На элитрах проверить разные углы, горизонтальную скорость и фейерверк.
   Лодка, вагонетка и верховое животное должны сохранять свою физику.
4. Упасть на обычный блок, сено, слизь, кровать и в воду; проверить Feather
   Falling, Resistance, Slow Falling, тотем и смерть. Ванильная защита
   сохраняется, положительный исходный урон от падения ×3; другой урон прежний.
5. Завершить испытание в воздухе через `/fdc activate no_sprint`:
   скорость/fallDistance сохраняются, дальнейшая гравитация обычная.
   Проверить пары `double_challenge:gravity_ten,heavy_body`,
   `double_challenge:gravity_ten,no_jumping` и
   `double_challenge:gravity_ten,no_sprint`, затем замену пары на no_sprint.
6. Survival → Creative → Spectator → Adventure → Survival; поздний вход,
   два настоящих клиента, выход/вход, смерть/респаун при обоих keepInventory,
   порталы Nether/End. Оценить коррекции движения при реальном RTT.
7. Сохранить и полностью перезапустить JVM с активным испытанием. Повторить
   после завершения, в том числе с офлайн-игроком. Проверить включение/выключение
   в настройках, пустой сервер, естественную активацию цикла, паузу и низкий TPS.

GameTest и CLIENT preLaunch-стенд не заменяют эти проверки. Загрузка с диска
в том же сервере не доказывает полный рестарт JVM; стенд с подменённым миром
не доказывает отсутствие коррекций по сети, рендер и физические столкновения.
