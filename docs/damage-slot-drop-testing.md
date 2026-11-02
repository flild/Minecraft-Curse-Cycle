# damage_slot_drop

Ревью 2026-11-02: исправлено объединение вложенных принятых повреждений,
когда vanilla повторно использует один объект DamageSource. До исправления
оба вызова уменьшали HP, но стек выпадал только один раз. Теперь внешний
receipt основной melee-атаки заимствуется ровно один раз; рекурсивный вызов
получает собственный receipt даже при совпадении игрока и источника.
Регрессия: nested-red 7/8; после исправления verified 8/8, build и 149 JUnit.
Итоговая адресная перепроверка: build, 149/149 JUnit в54 suites и42/42
GameTest (8 предметных +34 общих/регрессионных); exit0, normal shutdown.
Лог `build/damage-slot-drop-review-recheck.log`, XML
`build/reports/damage-slot-drop-review-recheck.xml`, JUnit
`build/reports/damage-slot-drop-review-recheck-junit`.

Старые 72 JUnit и 17/17 GameTest относились к реализации и не являются
свежим подтверждением. Ручные сценарии ниже ещё не выполнены.

## Автоматический стенд ревью

Восемь методов DamageSlotDropGameTest, каждый в отдельном batch:

- `everyMainSlotExcludedStorageAndCurrentStacks`: каждый слот 0–35, пустой
  инвентарь, реальные armor/offhand/cursor/external chest; 128 операций с двумя
  стеками разного размера. Оба исхода доступны; равномерность следует из
  `nextInt(slots.size())`, статистический тест распределения не заявлен.
- `fullComponentsAndFortyRealTickPickup`: shulker с именем и вложенным
  зачарованным повреждённым инструментом; весь компонентный стек, отказ
  pickup до 40 тиков, успешный vanilla pickup после 40 настоящих server ticks,
  отсутствие возврата в старый слот при завершении.
- `actualCancellationShieldResistanceSourcesAndMeleeReceipt`: inactive,
  прямой setHealth, реальная Fabric ALLOW_DAMAGE cancellation, invulnerability,
  Resistance V, активный щит с PvP-контролем, fall/fire/magic/drown/generic;
  actual PlayerEntity.attack с внешним receipt attack_recoil, один дроп за
  обычный удар; nested same-source melee даёт два дропа и одну отдачу.
- `nestedAcceptedDamageWithSameOrDifferentSource`: тестовый ALLOW_DAMAGE
  callback вызывает вложенный урон1 перед внешним2; внутренний1 и принятая
  i-frame разница1 дают HP18 и два дропа, с тем же и с другим DamageSource.
  Callback ограничен UUID и снимает маркер до рекурсивного вызова.
- `modesPersonalLifecycleDiskAndIndependentSprint`: OP2 list/activate/active,
  два игрока и поздний третий, все режимы, enabled, PERSONAL, обе механики
  no_sprint-пары и замена, 32 цикла/same ID, Nether roundtrip, live replacement;
  actual playerdata и PersistentState disk reload active/ended, offline end,
  чужой Speed/его clock. Это один JVM с EmbeddedChannel и JOIN/DISCONNECT
  invoker, без настоящей сетевой сессии или физического портала.
- `actualShuffleThenDamageUsesCurrentSlot`: 101 настоящий server tick,
  actual hotbar_shuffle перемещает стек, damage выбирает актуальный слот;
  количество и world consequence после снятия drop сохраняются.
- `dropPrecedesTeleportRegardlessOfEncodedOrder`: обе последовательности
  damage_teleport/drop в encoded double, успешный перенос, один ItemEntity
  точно в исходной receipt position. Это проверка пары, не ревью teleport.
- `realDamageAbsorptionDeathAndLockedSlots`: поглощение, i-frames, полный
  стек, keepInventory death, PlayerManager respawn, реальный тотем и
  автоматический дроп из закрытого inventory_lockdown слота.

Предметный изолированный init-script (сохранить в build; для нового запуска
заменить `verified` на новый suffix, чтобы XML и мир не были старыми):

```groovy
allprojects {
    afterEvaluate {
        def resources = project.file('build/damage-slot-drop-review-verified-resources')
        def metadata = new groovy.json.JsonSlurper().parse(project.file('src/gametest/resources/fabric.mod.json'))
        metadata.entrypoints['fabric-gametest'] = ['com.cursecycle.DamageSlotDropGameTest']
        resources.mkdirs()
        new File(resources, 'fabric.mod.json').text = groovy.json.JsonOutput.toJson(metadata)
        sourceSets.gametest.resources.setSrcDirs([resources])
        tasks.named('processGametestResources') { outputs.upToDateWhen { false } }
        tasks.named('test') { outputs.upToDateWhen { false }; reports.junitXml.outputLocation = layout.buildDirectory.dir('reports/damage-slot-drop-review-verified-junit') }
        loom.runs.gametest.runDir = 'build/gametest-damage-slot-drop-review-verified-20261102'
        loom.runs.gametest.vmArg '-Dfabric-api.gametest.report-file=../reports/damage-slot-drop-review-verified.xml'
    }
}
```

```powershell
.\gradlew.bat build runGametest --offline -I build/damage-slot-drop-review-verified.init.gradle
.\gradlew.bat processGametestResources --offline --rerun-tasks
```

Последняя команда возвращает полный список GameTest entrypoints; production
resources не меняются. Для адресной перепроверки общего receipt контракта
используется тот же init с suffix `recheck` и entrypoints DamageSlotDrop,
Foundation, AttackRecoil, Gills, NoHealing, OneHeart, AllDebuffs, RandomPotion
(полные имена `com.cursecycle.<Name>GameTest`). Все неуспешные попытки,
числа и свежие XML перечислены в локальном отчёте08.

Статически: до gate нет обхода инвентаря/спавна/slot sync; активная операция
читает ровно36 слотов и создаёт максимум один ItemEntity/копию стека.
Глубокая стоимость copy зависит от компонентов стека, синхронизация — от
числа слотов открытого ScreenHandler. У receipt стек по глубине вложенности,
finally удаляет пустой ThreadLocal; один одноразовый флаг вместо распознавания
дубликата по одному source. Собственных UUID maps, tick timers и saved NBT
у damage_slot_drop нет. MSPT/FPS/allocations/memory не измерялись.
Spawn failure проверен чтением условия очистки, принудительно не вызывался.

В Survival и Adventure каждый фактически прошедший несмертельный урон выбрасывает весь стек из одного случайного непустого слота основного инвентаря или хотбара. Броня, вторая рука и предмет на курсоре не затрагиваются. Если подходящих предметов нет, ничего не происходит. Выпавший предмет можно подобрать снова.

Уменьшение HP/поглощения отмечается непосредственно у setters в
PlayerEntity.applyDamage, до тотема. Реакция вызывается после LivingEntity.damage
только для выжившего игрока. Вложенные вызовы имеют отдельные контексты.
Новый onPlayerDamageApplied не меняет старый предварительный void-хук.
Мировая позиция фиксируется при входе; порядок реакций — дроп до телепорта.
Сначала создаётся ItemEntity с копией полного стека; слот очищается лишь
при успешном spawnEntity. Автоматический дроп не является ручной операцией
закрытого слота inventory_lockdown.

## Ручные сценарии (ещё не выполнены)

Исходные условия: Survival, только один стак 64 камня в слоте 10; броня/offhand заполнены, есть поглощение. Действия: Получить несмертельный урон, затем полностью заблокировать второй удар щитом; подобрать стек после 40 тиков. Ожидаемый результат: Выпадает целиком 64 камня ровно один раз, броня/offhand остаются, заблокированный удар ничего не выбрасывает.

Исходные условия: Основные 36 слотов пусты, offhand и броня заполнены; затем есть предметы и keepInventory=true. Действия: Получить несмертельный урон пустым инвентарём; затем смертельный без тотема с предметами; отдельно несмертельный с предметами. Ожидаемый результат: Первый случай без дропа, смерть следует keepInventory без дополнительной потери; несмертельный удар выбрасывает стек даже при keepInventory.

Исходные условия: Оператор OP 2, игрок Survival, испытание реализовано и включено. Действия: Выполнить /fdc list, /fdc activate damage_slot_drop, /fdc active. Ожидаемый результат: ID damage_slot_drop зарегистрирован ровно один раз, активен; русские название и описания совпадают с ТЗ.

Исходные условия: Два игрока Survival, испытание активно; третий подключается позже. Действия: Повторить основной сценарий обоими игроками и поздно вошедшим; затем последовательно проверить смерть/респаун, выход/вход и портал. Ожидаемый результат: Правила применяются к каждому подходящему игроку согласно player_lifecycle; нет зависимости от игрока, первым активировавшего испытание, и повторных срабатываний на один результат.

Исходные условия: Есть контрольное поведение без испытания; испытание активно. Действия: Повторить основной сценарий в Survival, затем Creative, Spectator и снова Survival; Adventure проверять с ванильно разрешёнными действиями. Ожидаемый результат: На игроков Survival/Adventure действует описанная механика, в Creative/Spectator собственное вмешательство в игрока отключено. Возврат режима не удваивает эффект. Уникальные мировые последствия проверяются по исключениям данного ТЗ.

Исходные условия: Новое испытание и существующее no_sprint включены; два игрока Survival. Действия: Выполнить /fdc activate "double_challenge:damage_slot_drop,no_sprint"; проверить основную механику и попытку спринта, затем /fdc activate no_sprint. Ожидаемый результат: Во время пары работают обе механики. После замены снимаются только временные изменения нового испытания, no_sprint продолжает блокировать спринт; здоровье, предметы и описанные мировые последствия не возвращаются.

Исходные условия: Испытание активно; один игрок отключился. Действия: Сохранить и перезапустить мир с активным испытанием, проверить основной эффект; затем завершить его через /fdc activate no_sprint, сохранить и перезапустить, вернуть офлайн-игрока. Ожидаемый результат: После первого запуска выполняется on_server_restart данного ТЗ. После второго новое испытание не действует; устаревшие собственные изменения очищены, чужие состояния сохранены. Не использовать несуществующую команду /fdc stop.
