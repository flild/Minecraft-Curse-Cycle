# bitter_experience — «Горький опыт»

MEDIUM / GLOBAL. Реализация относится только к этому ID.

## Механика и точка перехвата

`BitterExperienceOrbMixin` оборачивает ванильный
`ExperienceOrbEntity.onPlayerCollision(PlayerEntity)`. До вызова сохраняется
число порций `pickingCount`; после нормального завершения проверяется его
уменьшение ровно на одну порцию. Уже удалённая на входе сфера не является новым
источником запросов. Это не событие притяжения или общего начисления XP.

В актуальном байткоде Minecraft 1.21.1/Yarn build.3 порядок такой: проверка
`experiencePickUpDelay`, `sendPickup`, `repairPlayerGears`, начисление остатка XP,
уменьшение `pickingCount`, удаление исчерпанной сущности. Перехват выполняет
`BitterExperienceChallenge.afterOrbPickup` после всего этого пути. Поэтому
полная починка без начисления XP тоже учитывается. Публичные сигнатуры:
[Yarn ExperienceOrbEntity](https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/entity/ExperienceOrbEntity.html).
Порядок инструкций дополнительно проверен локальным `javap -c -p` для jar,
который использует текущая сборка.

Источник `cursecycle:bitter_experience` имеет базовый урон 1, scaling `never`
и exhaustion 0. В damage-type tags он добавлен только в `bypasses_armor`.
Сопротивление, защитные чары, поглощение, тотемы, обычная неуязвимость после
удара и защита при входе сохраняются. Прямого изменения HP и сброса i-frames
в реализации нет. Обычные подтверждённые реакции `DamageHooks` переиспользуются.

Применимость проверяет `ChallengeActivity.active`: включённость, GLOBAL и
PERSONAL ID, живой ServerPlayer, Survival/Adventure. Собственных таймеров,
счётчиков, ссылок на игроков и сохраняемого состояния нет; повторный вход,
замена сущности и загрузка мира используют актуальную общую активацию.
Завершение не откатывает уже полученный урон, опыт и починку.

## Ревью этапа 10 (2026-11-02)

Подтверждённых дефектов не найдено; игровой код не менялся. Расширен только
`BitterExperienceGameTest`. Итог pass_with_limits, механика pass,
расходы no_issue_found / static. Полная матрица и журнал запусков:
`Промпты/reviews/10-bitter_experience.md` (локальный ignored документ).
Старые числа реализации не засчитываются как доказательства этого ревью.

| Тест | Доказательство |
|---|---|
| consumedPortionsMendingAndVanillaDamage (T1) | OP2 fdc list/activate/active; inactive control; номиналы1/7/2477; XP до запроса; command/level spending; delay/count3; Mending all/partial; armor, ResistanceI, absorption, tags, totem/death |
| activityPersistenceAndDamageReactions (T2) | no_sprint обе механики/end; modes/enabled; late player/PERSONAL; dead/live replacement; PersistentState disk; DSD/DT accepted vs i-frames; no_healing/one_heart пары |
| nestedPickupsCancelledDamageAndRemovedOrb (T3) | reentrant collision той же Count2 сферы внутри ALLOW_DAMAGE: XP14/requests2/HP−1/drop1; stale removed orb requests0; cancelled pickup XP11/loss0/drop0; pickup nested in generic damage: two accepted results/two drops; ResistanceV request1/loss0/drop0 |
| realServerTicksKeepVanillaPickupDelayAndIFrames (T4) | 25 реальных server ticks, attempts offsets0..4 → requests0/2/4, XP21/HP−1; offset25 ещё HP−1. Server вызывает tick, fixture вызывает vanilla playerTick вместо play-network; точный countdown20..16/0 проверяется,food17 исключает natural regen. Delay/i-frames после начала не сбрасываются. Сфера unspawned,притяжение/сеть не проверяются |
| repeatedActivationsAndActualPlayerDiskReload (T5) | 32цикла repeated/double same ID/GLOBAL+PERSONAL/end, XP once/HP−1; actual playerdata save/rejoin и PersistentState disk load в одном JVM active/ended/offlineend; чужие Speed777/items7/XP/HP сохранены, no_sprint продолжает работать |

Все тесты используют настоящие ServerPlayerEntity и transformed orb/damage path.
Наблюдатель ALLOW_DAMAGE находится только в gametest source set; REQUESTS пишет
amount/XP/tool damage до mitigation, одноразовый NESTED и CANCELLED нужны T3.
UUID/closures удаляются в finally. T4 закрывает scenario на успехе/assertion failure.
Общие damage/health/effect contracts и production JAR не включают новый стенд.

Воспроизводимый изолированный запуск из корня (PowerShell):

```powershell
.\gradlew.bat build runGametest --offline -I build/bitter-experience-review-final.init.gradle
.\gradlew.bat processGametestResources --offline --rerun-tasks
python build/bitter-experience-review-verify.py
```

Содержимое final init-script; для baseline/subject/verified/cadence/clocks/validated заменить `final`
во всех путях и оставить entrypoint только BitterExperienceGameTest:

```groovy
allprojects {
    afterEvaluate {
        def resources = project.file('build/bitter-experience-review-final-resources')
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.BitterExperienceGameTest', 'com.cursecycle.DamageTeleportGameTest', 'com.cursecycle.DamageSlotDropGameTest', 'com.cursecycle.FoundationGameTest']
        resources.mkdirs()
        new File(resources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([resources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        tasks.named('test') { outputs.upToDateWhen { false }; reports.junitXml.outputLocation = layout.buildDirectory.dir('reports/bitter-experience-review-final-junit') }
        loom.runs.gametest.runDir = 'build/gametest-bitter-experience-review-final-20261102'
        loom.runs.gametest.vmArg '-Dfabric-api.gametest.report-file=../reports/bitter-experience-review-final.xml'
    }
}
```

Baseline: sandbox exit1 до тестов (zip.lck access denied), повтор с разрешённым
существующим cache exit0,149JUnit и2/2GameTest. Lock не удалялся.
Первая расширенная subject попытка: build/JUnit прошли,4/5GameTest;
T4 timeout100 из-за рекурсивного waitAndRun в новой fixture. Заменён только
тестовый scheduler на runAtEveryTick. Verified:4/5GT, vanilla pickup delay не
продвигался, поскольку EmbeddedChannel не вызывает normal play-network updates.
Cadence и clocks:4/5GT, абсолютный HP18 ошибочно ожидался без учёта vanilla
saturated regeneration. Попытка вручную добавить tick дала validated4/5GT:
точный countdown выявил двойной вызов,actual18 вместо ожидаемых19 на offset1.
В final удалён дополнительный tick,оставлен playerTick вместо play-network,
food17 исключает natural regen. Все failed XML сохранены;это исправления
fixture, production не менялся. Финальные результаты — в отчёте этапа10 и XML.
Final: exit0, свежие build+149/149JUnit в54suites,24/24GameTest
(5BE+7DT+8DSD+4Foundation),штатный shutdown. Restore exit0 вернул43entrypoints;
verifier exit0 сверил все семь XML/JUnit наборов,SHA5/5,metadata6/6,
registration1,единственный custom tag bypasses_armor и два чистых production JAR.
Логи `build/bitter-experience-review-<suffix>.log`, XML
`build/reports/bitter-experience-review-<suffix>.xml`, отдельные JUnit
`build/reports/bitter-experience-review-<suffix>-junit/TEST-*.xml`.

Повторный локальный javap текущего mapped Minecraft JAR сохранён в
`build/bitter-experience-review-orb-bytecode.txt`: server-only/cooldown gate,
repairPlayerGears → addExperience remainder → pickingCount−1 → discard.
Это статическое подтверждение инструкций; игровые assertions — отдельно.

Расходы: wrapper на каждый collision сохраняет boolean/int и проверяет count.
Только consumed portion доходит до eligibility O(G+A); активная порция создаёт
один DamageSource/один damage request. Нет собственных tick/render/packet/save
hooks, maps, UUID history, timers/world scan. Inherited no-op callbacks и общие
scheduler/sync/damage gates имеют цену (foundation-L02). В паре DSD/DT цена
receipt/drop/teleport относится к соответствующей реакции, vanilla XP/Mending/
tracking — к ванили. MSPT/FPS/allocations/load не измерялись.

## Оставшиеся ручные сценарии

Не выполнены настоящим клиентом и не объявляются пройденными:

1. В одиночном Survival-мире: `/fdc list`, `/fdc activate bitter_experience`,
   `/fdc active`; проверить название, описание HUD и полные правила меню.
   Подбирать разнесённые сферы разных номиналов с интервалом более 20 тиков;
   затем `/experience add @s 10 points`. Ожидается полсердца за отдельную
   успешную порцию, XP без изменений, команда без урона.
2. Плотная группа/объединённые сферы и предмет с Mending: проверить настоящее
   притяжение, звук и анимацию, отсутствие дублирования опыта/починки,
   сохранение i-frames при естественных задержках подбора.
3. Смерть с опытом, keepInventory выключен/включён, возврат к выпавшим сферам.
   Собственные посмертные сферы не освобождаются от механики.
4. Настоящие порталы во все измерения, выход/вход и полный перезапуск процесса
   мира с активным испытанием. Тест замены сущности и чтения NBT не заменяет
   эти проверки и не проверяет клиентскую синхронизацию.
5. Окончание офлайн: выйти, на выделенном сервере выполнить
   `/fdc activate no_sprint`, вернуться. Старое испытание не должно восстановиться.
6. В одном клиенте повторить режимы и
   `/fdc activate "double_challenge:bitter_experience,no_sprint"`, затем
   `/fdc activate no_sprint`. Будущие подборы больше не наносят урон,
   запрет спринта сохраняется; последствия подбора не откатываются.
7. Необязательно: два настоящих клиента на выделенном сервере; каждый
   подбирает опыт самостоятельно, один отключается. Общего персонального
   счётчика, требования напарника и зависимости от количества игроков нет.

Обменов, новой конфигурации, клиентских перехватов и отдельной обратной связи
испытание не добавляет. Следующий ID этой эстафеты здесь не реализован.
