# no_block_breaking

Актуальное ТЗ: EASY / GLOBAL, «Не ломается, баг наверно». Только ванильное
Утомление X (amplifier 9), без отмены добычи. Собственный вклад выдаётся на
40 тиков, проверяется каждый тик, продлевается при остатке не более 20.

## Устройство

EffectLedger прикреплён к серверному игроку. Он хранит собственные вклады
по ID/активации и отдельную актуальную внешнюю цепочку затронутого эффекта.
Длительности внешних, включая скрытые, уменьшаются без повторного применения
их игровой механики. Внешние добавления/снятия и молоко учитываются Mixin.
Публикуется ванильное объединение вкладов; очистка сохраняет текущее внешнее
состояние. Другие типы эффектов не затрагиваются.

Данные вкладов сериализуются вместе с игроком через реальные методы
LivingEntity.writeCustomDataToNbt/readCustomDataFromNbt. Серийный номер
активации хранится CurseCycleState; старые сохранения используют 0.
При входе и тиках устаревшие вклады удаляются. Выход снимает собственный
вклад без удаления чужого эффекта; новая активация не наследует старую.

## Проверки

JUnit: регистрация ровно один раз, EASY/GLOBAL и описание amplifier 9.
GameTest: немедленная выдача, продление, более слабые/сильные внешние эффекты,
скрытая цепочка и её истечение, молоко, Creative, повторная загрузка NBT
игрока, сохранение маркера активации, очистка устаревшего NBT и двойной режим.
Результаты запуска фиксируются в challenge-implementation-plan.md.

## Ручные сценарии

Нужен OP 2. Выполнить /fdc list, /fdc activate no_block_breaking, /fdc active.
Проверить HUD и меню H, постоянный amplifier 9, молоко и следующий тик.
Проверить добычу камня, мгновенно разрушаемого цветка, дроп/износ и сундук:
есть только ванильное действие Утомления, отдельной отмены нет.
Проверить внешнее Утомление III/XI до и во время действия и после завершения.
Повторить в Adventure с CanBreak, Creative, Spectator и после возврата.
Проверить реальную смерть/респаун, порталы, поздний вход второго клиента,
переподключение и полный перезапуск процесса сервера. Завершить через
/fdc activate no_sprint, включая окончание во время отсутствия игрока.
Полный рестарт процесса и клиентское отображение не заменяются NBT-тестом.

## Ревью 2026-11-01

Игровых дефектов при предметном ревью не выявлено. Production-код не менялся.
Расширен NoBlockBreakingGameTest: теперь пять сценариев с применёнными Mixin
на выделенном GameTest-сервере. Новые сценарии проверяют:

- 240 ванильных тиков эффектов через playerTick и production CycleManager,
  точные 40/20 тиков собственного вклада, повторный dispatch без раннего
  продления, настоящий MilkBucketItem.finishUsing и 32 start/stop цикла
  с проверкой NBT на отсутствие накопленных вкладов;
- Survival/Adventure/Creative/Spectator, enabled, мёртвый объект, настоящий
  PlayerManager.respawnPlayer, живую замену объекта с внешней скрытой цепочкой,
  teleport в Nether/обратно, PERSONAL, поздний JOIN и DISCONNECT callback,
  два независимых игрока и возврат после завершения;
- OP 2 dispatcher /fdc list/activate/active, замедление ванильного mining delta,
  START_DESTROY_BLOCK для мгновенного цветка, его статистику и дроп,
  успешный tryBreakBlock и износ кирки, Adventure с/без CAN_BREAK и сундук;
- no_sprint и сохранение второго ограничения после снятия Утомления X;
  независимую очистку вкладов all_debuffs/random_potion;
- настоящий END_SERVER_TICK: восстановление после молока уже на следующем
  тике и автоматическое разрушение опоры fragile_floor при действующем X.

Синхронные сценарии используют EmbeddedChannel вместо настоящего клиента.
240 итераций playerTick — отсчёт ванильных эффектов, не 240 реальных server
ticks. Отдельный асинхронный сценарий действительно ждёт 42 server ticks.
TestServer по умолчанию подключает игроков в Creative; тест позднего входа
временно задаёт default Survival и восстанавливает прежний режим в finally.
Блоки/дроп новых сценариев очищаются; стандартный ChallengeScenario удаляет
игроков, освобождает EmbeddedChannel и восстанавливает общее состояние.

Воспроизводимый изолирующий init-script (сохранить в build/no-block-breaking-review-final.init.gradle):

```groovy
allprojects {
    afterEvaluate {
        def isolatedResources = project.file('build/no-block-breaking-review-final-resources')
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.NoBlockBreakingGameTest']
        isolatedResources.mkdirs()
        new File(isolatedResources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([isolatedResources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        tasks.named('test') { outputs.upToDateWhen { false } }
        loom.runs.gametest.runDir = 'build/gametest-no-block-breaking-review-final-20261101'
        loom.runs.gametest.vmArg '-Dfabric-api.gametest.report-file=../reports/no-block-breaking-review-final.xml'
    }
}
```

```powershell
.\gradlew.bat build runGametest --offline -I build/no-block-breaking-review-final.init.gradle
.\gradlew.bat processGametestResources --offline
```

Для нового независимого мира изменить runDir и имя XML, сохранив прежние
результаты. Вторая команда возвращает все стандартные entrypoints.
Итоговый запуск: build/no-block-breaking-review-closeout.log,
build/reports/no-block-breaking-review-final.xml; 149 JUnit и 5/5 GameTest,
exit 0. Предшествующий успешный прогон до добавления cleanup: final.log и
reports/no-block-breaking-review-pre-cleanup.xml, также 149 и 5/5.

История предметных попыток, не подменяющая итог:

- baseline.log: exit 1, sandbox не получил доступ к Gradle cache lock;
  baseline-retry.log: exit 0, 1 JUnit и исходный 1/1 GameTest,
  reports/no-block-breaking-review-original-test.xml;
- expanded.log: exit 1, build/JUnit успешны (149), ошибка компиляции нового
  теста: executeWithPrefix возвращает void; заменён на dispatcher.execute;
- expanded-retry.log: exit 1 до задач, неверный относительный apply-путь
  build/build; исправлен init-script;
- expanded-run.log: exit 1, 149 JUnit, 3/4 GameTest; late JOIN был Creative,
  reports/no-block-breaking-review-fixture-failure.xml;
- lifecycle.log: exit 1, 149 JUnit, 4/5 GameTest; новый async callback
  проверял эффект на tick 0, до назначенной на tick 1 активации; теперь
  пропускает tick 0, reports/no-block-breaking-review-zero-tick-failure.xml.

Все имена логов выше имеют префикс build/no-block-breaking-review-, XML —
build/reports/. Ошибки новых fixtures устранены без изменения игровых правил.
Полный общий GameTest набор в этом этапе не запускался.

Накладные расходы проверены статически: один эффект/владелец на игрока,
проверка каждый tick, публикация обычно раз в 20 ticks, плюс необходимое
восстановление/очистка. Нет поиска блоков/сущностей, собственного render hook,
C2S или ручной рассылки каждый tick. Общий ledger продолжает пустую проверку
и после деактивации; выделения и ванильные пакеты при publish имеют цену.
FPS/MSPT, профиль памяти и нагрузка не измерялись.

Остаются ручные сценарии выше: обычный клиент/HUD/меню, фактическое питьё и
ввод добычи, сеть с двумя клиентами, порталы, смерть/keepInventory и полный
restart active/ended с отсутствовавшим игроком. NBT и EmbeddedChannel не
являются их выполнением. Локальный подробный отчёт ревью хранится в
Промпты/reviews/01-no_block_breaking.md (планирование игнорируется Git).
