# no_healing

Ревью этапа 6, 2026-11-02: свежие build, 149/149 JUnit и 5/5 предметных
GameTest прошли. Настоящий клиент, сеть двух клиентов, физические порталы,
полный перезапуск JVM и профили MSPT/FPS/памяти не проверялись.

Запрещено любое увеличение текущего здоровья живого игрока: естественная регенерация, эффекты регенерации и мгновенного лечения, еда, яблоки и прямое лечение. Голод, насыщение и золотые сердца поглощения работают обычно. Возрождение создаёт игрока с обычным здоровьем, а тотем спасает с 1 единицей, но его последующая регенерация здоровья блокируется.

Серверный setHealth отсекает прирост у живого подходящего игрока.
Десериализация vanilla NBT явно исключена scoped-флагом с finally.
Отдельный признак выставляется только в конце конструктора ServerPlayerEntity:
начальное здоровье и доступ к ещё не готовому interactionManager не затрагиваются.
После смертельного урона здоровье 0, штатный тотем может восстановить 1;
с этого момента игрок жив и последующее лечение снова запрещено.
Ванильные setHealth внутри ServerPlayerEntity.copyFrom отдельно исключены
через scoped-флаг с finally. Они инициализируют заменяющий объект; без этого
игрок с внешним base max health 40 и текущими 30 HP терял здоровье до 20
при живом переносе. После copy запрет немедленно действует снова. Флаг
healthInitialized, используемый one_heart, не выключается: его cap сохраняется.
Не изменяются naturalRegeneration, чужие эффекты, голод или поглощение.
Собственных сохраняемых изменений здоровья и снимков нет.

## Предметные автоматические проверки

NoHealingGameTest (реальные серверные классы с применёнными Mixin):

| Сценарий | Проверяемый путь |
|---|---|
| allGrowthPathsLoadingTotemAndRespawn | heal/setHealth/instant, еда, NBT/load, modes, lethal damage/тотем до 1 HP и 50 вызовов цикла эффектов без лечения, alive/dead respawn, окончание без компенсации |
| replacementCopiesHealthAboveDefaultMaximum | Base max 40/current 30 → alive copy сохраняет 30; новая жизнь получает 40; дальнейшее лечение блокируется; двойное one_heart сохраняет max2/HP1 при alive и HP2 при dead respawn |
| vanillaPotionFoodRegenerationAndDamagePaths | Быстрая/медленная natural regeneration, питьё/стеклянная бутылка, настоящий PotionEntity.onCollision для splash и lingering, созданный AreaEffectCloudEntity.tick; контроль disabled тем же путём лечит. Яблоко/голод/насыщение/absorption, Health Boost max28 без лечения, HP damage, non-player control, последующие импульсы после окончания |
| diskJoinModesPersonalDisableAndRepeatedActivation | OP2 fdc list/activate/active, 2 игрока + late third, Creative/Spectator/Adventure, Nether roundtrip, фактическое сохранение vanilla playerdata при remove и чтение при onPlayerConnect; PersistentState save/read/load active/ended, независимость другого игрока, PERSONAL/enabled, 32 start/stop, 100 heal без dirty/NBT/пустых UUID |
| independentSprintHealthAirAndRandomPotion | Два игрока no_sprint, сохранение второй механики после замены; one_heart/gills, seeded реальные выборы instant_health и regeneration у random_potion; own effect не удалён, после снятия no_healing продолжает лечить |

Игроки используют EmbeddedChannel, без настоящего клиента. tickEffects вызывает
tick + playerTick вручную: исполняет vanilla status/hunger paths, но не означает
столько же реальных server ticks/20 TPS. Возраст облака выставляется явно,
затем выполняется его настоящий tick/area lookup; полёт и столкновение по
физике не симулируются. Alive respawn идёт через PlayerManager, Nether —
teleport, без физического портала. Disk reload остаётся в одном процессе.
infinite_diet сопоставлен по коду (его HungerManager gate независим); ручная
пара остаётся непроверенной.

Воспроизведение итогового изолированного прогона (init-script под build):

```groovy
allprojects {
    afterEvaluate {
        def resources = project.file('build/no-healing-review-final-resources')
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.NoHealingGameTest']
        resources.mkdirs()
        new File(resources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([resources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        tasks.named('test') {
            outputs.upToDateWhen { false }
            reports.junitXml.outputLocation = layout.buildDirectory.dir('reports/no-healing-review-final-junit')
        }
        loom.runs.gametest.runDir = 'build/gametest-no-healing-review-final-20261102'
        loom.runs.gametest.vmArg '-Dfabric-api.gametest.report-file=../reports/no-healing-review-final.xml'
    }
}
```

```powershell
.\gradlew.bat build runGametest --offline -I build/no-healing-review-final.init.gradle
.\gradlew.bat processGametestResources --rerun-tasks --offline
```

Для нового запуска выбрать новый runDir/report. Вторая команда восстанавливает
все 43 стандартные GameTest entrypoints. Лог: build/no-healing-review-final.log;
XML: build/reports/no-healing-review-final.xml; JUnit: final-junit выше.
Test-only ресурсы и классы не входят в production binary/sources JAR.

До исправления red-прогон дал 2/5: подтверждённый дефект copy HP и две ошибки
новых fixtures (возраст cloud не продвигался; tick без playerTick не исполнял
status pulses). Их failed XML сохранён в build/reports/no-healing-review-red.xml.
После исправления: fixed5/5, затем final5/5 с дополнительными one_heart copy
и реальными totem regeneration assertions; оба build дали свежие 149 JUnit.
Первый sandbox запуск не получил доступ к Gradle wrapper zip.lck; lock не
удалялся. Первый повтор с доступом ошибочно поставил --tests после runGametest;
Gradle отклонил опцию до тестов. Исправленная команда baseline дала 1JUnit+1GT.

## Стоимость

Свой hook — setHealth/NBT, без таймера, UUID map, пакетов и снимков. Non-player
отсекается проверкой типа; server player gate выполняет enabled/mode/alive и
contains по GLOBAL/PERSONAL IDs, O(G+A). Два boolean на путь загрузки/copy,
конструкторный healthReady; copy scoped write O(1) только на инициализации,
после finally false. Общий scheduler всё ещё вызывает наследованный пустой
onTick для online игроков активного ID. Это static-анализ, не измеренное время
кадра/MSPT/allocations и не нулевой overhead.

## Ручные сценарии (ещё не выполнены)

Исходные условия: Survival, 10 из 20 здоровья, naturalRegeneration=true, полный голод; контроль без испытания подтверждает лечение. Действия: Активировать, подождать регенерацию, выпить/бросить лечение, выдать regeneration и съесть золотое яблоко. Ожидаемый результат: Текущее здоровье не возрастает; голод/насыщение восполняются, золотые сердца выдаются.

Исходные условия: Survival, тотем в руке, есть точка возрождения. Действия: Получить смертельный ванильный урон с тотемом, наблюдать последующую регенерацию; затем умереть без тотема и возродиться. Ожидаемый результат: Тотем оставляет 1 здоровье, дальнейшее лечение заблокировано; новый игрок получает обычное здоровье при респауне и снова не лечится после урона.

Исходные условия: Игрок ранен, активное испытание, включён длительный внешний regeneration. Действия: Завершить через no_sprint. Ожидаемый результат: Здоровье не восстанавливается мгновенно за пропуски, но следующий обычный импульс ещё действующего regeneration может лечить.

Исходные условия: Оператор OP 2, игрок Survival, испытание реализовано и включено. Действия: Выполнить /fdc list, /fdc activate no_healing, /fdc active. Ожидаемый результат: ID no_healing зарегистрирован ровно один раз, активен; русские название и описания совпадают с ТЗ.

Исходные условия: Два игрока Survival, испытание активно; третий подключается позже. Действия: Повторить основной сценарий обоими игроками и поздно вошедшим; затем последовательно проверить смерть/респаун, выход/вход и портал. Ожидаемый результат: Правила применяются к каждому подходящему игроку согласно player_lifecycle; нет зависимости от игрока, первым активировавшего испытание, и повторных срабатываний на один результат.

Исходные условия: Есть контрольное поведение без испытания; испытание активно. Действия: Повторить основной сценарий в Survival, затем Creative, Spectator и снова Survival; Adventure проверять с ванильно разрешёнными действиями. Ожидаемый результат: На игроков Survival/Adventure действует описанная механика, в Creative/Spectator собственное вмешательство в игрока отключено. Возврат режима не удваивает эффект. Уникальные мировые последствия проверяются по исключениям данного ТЗ.

Исходные условия: Новое испытание и существующее no_sprint включены; два игрока Survival. Действия: Выполнить /fdc activate "double_challenge:no_healing,no_sprint"; проверить основную механику и попытку спринта, затем /fdc activate no_sprint. Ожидаемый результат: Во время пары работают обе механики. После замены снимаются только временные изменения нового испытания, no_sprint продолжает блокировать спринт; здоровье, предметы и описанные мировые последствия не возвращаются.

Исходные условия: Испытание активно; один игрок отключился. Действия: Сохранить и перезапустить мир с активным испытанием, проверить основной эффект; затем завершить его через /fdc activate no_sprint, сохранить и перезапустить, вернуть офлайн-игрока. Ожидаемый результат: После первого запуска выполняется on_server_restart данного ТЗ. После второго новое испытание не действует; устаревшие собственные изменения очищены, чужие состояния сохранены. Не использовать несуществующую команду /fdc stop.
