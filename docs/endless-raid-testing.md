# endless_raid — проверка

«Рейд без выходных», MEDIUM/GLOBAL. Название и оба описания совпадают с ТЗ.

## Реализация

- При активации и затем раз в 20 серверных тиков проверяются онлайн-игроки.
  Живой Survival/Adventure без обоих предзнаменований и рейда рядом получает
  собственный bad_omen I. Он бесконечен, чтобы ждать настоящей деревни;
  интенсивность и ванильные таймеры не меняются. Время мира/сон не ускоряют проверку.
- Peaceful, disableRaids и DimensionType.hasRaids соблюдаются. Собственные
  предзнаменования не могут запустить рейд в исключённом режиме даже между
  проверками. Чужое предзнаменование сохраняет ванильное поведение.
- BadOmenStatusEffect выполняет настоящую конверсию в raid_omen (600 тиков).
  Mixin переносит происхождение только при исключительно собственном bad_omen
  и отсутствии уже существовавшего raid_omen. Смешанный с чужим эффект не даёт
  права присвоить рейд. Молоко очищает вклад через существующий EffectLedger.
- EffectLedger.adopt учитывает происхождение уже созданного ванильного эффекта,
  не публикует его заново и не ведёт параллельный отсчёт его длительности.
  consumed забывает целиком потреблённую ванилью цепочку. Для обычных эффектов
  прежние отсчёт, учёт внешнего вклада и очистка сохранены.
- RaidManager.getOrCreateRaid проверяется до добавления нового ID в реестр
  ванили. Помечается только новый рейд от исключительно собственного raid_omen;
  существующий рейд не присваивается, собственное предзнаменование второго игрока
  не увеличивает его интенсивность. Поиск центра, условия, подготовка, волны,
  победа/поражение, награды, bossbar и появление мобов остаются ванильными.
- Пока getRaidAt возвращает предыдущий рейд, новое предзнаменование не выдаётся,
  включая ванильное празднование победы/поражения. После его остановки/удаления
  следующая проверка разрешает обычную новую подготовку.
- Происхождение эффектов сохраняется существующим ledger в NBT игрока.
  Собственные (измерение, raid ID, activationSerial) сохраняются отдельно в
  overworld PersistentState cursecycle_endless_raid. beginActivation не стирает
  этот реестр. Серверный singleton не хранит ссылки на игроков/миры.
- Обычный onStop останавливает собственные незавершённые рейды. onPlayerLeave
  ничего не останавливает. До каждого RaidManager.tick сверяются активность,
  включённость и serial: осиротевшие рейды останавливаются до новых волн/наград,
  в том числе после смены испытания на пустом сервере и после загрузки мира.
  Проверяются только записанные IDs, сущности/чанки мира не обходятся.
- Остановка — Raid.invalidate (STOPPED), без победы и без удаления рейдеров,
  брони или обычных последствий. Уже законченные рейды не отменяются.
  Числовой ID другого измерения не считается собственным. Поддержаны effective
  GLOBAL/PERSONAL ID и double_challenge; PERSONAL не распространяет выдачу на других игроков.
- Клиентского кода, новых пакетов, HUD, сообщений, настроек и зависимостей нет.

## Автоматизация

- JUnit проверяет единственную регистрацию, точные русские тексты, MEDIUM/GLOBAL
  и разрешение double_challenge:endless_raid,no_sprint.
- Пять GameTest исполняют production-код с применёнными Mixin на выделенном сервере.
  Создаются настоящие занятые HOME POI через PointOfInterestStorage, без подмены
  isNearOccupiedPointOfInterest. Эффекты тикаются настоящим tickStatusEffects
  через reflection: полный 600-тиковый отсчёт исполняется синхронно, без ожидания
  30 секунд реального времени; проверка поддержки вызывается каждые 20 таких тиков.
- Проверяются реальное начало рейда, отсутствие мгновенного запуска и продления
  countdown, два игрока/одна деревня без усиления, две независимые деревни,
  возобновление после invalidate, отсутствие волн в поле, собственные/чужие
  эффекты и рейды до активации, молоко, сохранение внешнего amplifier/duration,
  Survival/Adventure/Creative/Spectator, Peaceful, disableRaids, enabled,
  Nether/возврат, смерть/респаун, два варианта double и PERSONAL.
- Проверяются OP 2 /fdc list, /fdc activate endless_raid, /fdc active;
  NBT игрока с происхождением и ванильной raid_omen_position; запись/чтение
  собственного PersistentState с диска; NBT ванильного RaidManager; продолжение
  активной активации без дубля; смена при пустом сервере и остановка до vanilla tick;
  очистка устаревшего офлайн-эффекта; сохранение живого рейдера и его незеритовой
  брони; отсутствие искусственной победы.
- Совпадающий чужой raid ID другого измерения вставляется только тестом через
  reflection в RaidManager, чтобы проверить именно разграничение измерений.
  Это не проверка ванильного запуска рейдов в Nether.

## Исторические результаты реализации (не доказательство текущего ревью)

- .\gradlew.bat compileJava --offline и compileGametestJava --offline — успешно.
  Первый sandbox-запуск wrapper получил отказ доступа к zip.lck в существующем
  F:\Programs\GradleCache; повтор с разрешённым доступом успешен. Lock не удалялся.
- Итог: .\gradlew.bat build runGametest --offline -I build/endless-raid-tests.init.gradle
  — BUILD SUCCESSFUL за 1m 13s; 146 JUnit, failures/errors/skipped=0;
  79/79 GameTest, failure/error=0, включая пять новых. XML обоих наборов проверен.
  Игровой JAR содержит реализацию, не содержит GameTest/Probe.
- Изолированный runDir: build/gametest-endless-raid-clean. Локальный init-script
  в build содержит:

```groovy
allprojects {
    afterEvaluate {
        loom.runs.gametest.runDir = 'build/gametest-endless-raid-clean'
    }
}
```

- Первый игровой прогон обнаружил раннее истечение ledger-таймера raid_omen;
  это исправлено vanillaTimed. Также подсчёт тестовых рейдов исключает уже
  остановленные fixtures. Следующий прогон прошёл четыре новых сценария,
  но старый NetheriteMobsGameTest.birthsIncludingPostSpawnInitialization (:63)
  в повторно использованном мире обнаружил damage=1 у нового нагрудника.
  В итоговом свежем мире все тесты прошли; посторонний код теста не менялся.
- git diff --check пройден; staged diff и git diff --cached --check проверены
  перед отдельным локальным коммитом.

## Ручные проверки — не выполнены

1. В настоящей деревне с жителями/кроватями: весь бой и ванильная победа либо
   поражение, празднование, следующий рейд после удаления предыдущего; реальные
   поиск spawn-позиций, группы волн, количество волн по сложности и награды.
   Автотест возобновляет рейд после invalidate, а не после полного боя.
2. Настоящий клиент: HUD, меню с русскими текстами, bossbar, звуки; совместно
   с silent_world проверить тишину при сохранении bossbar.
3. Сетевой кооператив: два игрока в одной деревне, третий в другой, поздний вход,
   отключение и повторное подключение, молоко обоими игроками. Embedded-клиенты
   в GameTest не подтверждают реальную сеть.
4. Полный restart JVM в ходе raid_omen, хода рейда и после окончания испытания
   при офлайн-игроке. Дисковая сериализация в тесте не заменяет restart процесса.
5. Настоящие порталы, смерть в бою и респаун, Creative/Spectator до последнего
   тика raid_omen, возврат Survival/Adventure. Тест использует серверные teleport,
   respawn и смену режима, а не пользовательские действия в клиенте.
6. Двойные испытания с no_sprint/netherite_mobs, а также ограничения строительства
   no_block_placing/no_block_breaking в настоящей деревне; чужой рейд до активации
   и независимый рейд из внешнего предзнаменования во время активации.
7. Peaceful, disableRaids, отсутствие деревни, сон, /time set/add; восстановить
   исходные условия и убедиться, что подготовка идёт ванильно, без кастомных волн.

Команды: /fdc list, /fdc activate endless_raid, /fdc active.
Для завершения: /fdc activate no_sprint. Команды /fdc stop нет.

## Ревью этапа 33 — 2026-11-07

Входной HEAD `50be981774ecc8bf005a0feafc1a0e3c69f05362`. Production-код,
общие контракты, зависимости и ТЗ не менялись. Подтверждённых дефектов нет;
добавлены четыре предметных GameTest, всего девять:

- `vanillaVictoryLossCelebrationAndNextPreparation`: настоящий omen/countdown
  создаёт рейд. Тест задаёт только границу окончания боя (`started`, число
  завершённых волн, пустой состав); ванильный `RaidManager.tick` сам определяет
  победу либо поражение после удаления занятой HOME POI. Проверены награда,
  все 600 тиков празднования, отсутствие раннего omen, новый countdown/ID после
  окончания и сохранение уже полученного Hero of the Village при очистке.
  Это не прохождение боя и не проверка естественного поиска spawn-позиций.
- `cadenceSerialCleanupAndIndependentPairs`: tick1–19 без выдачи, tick20 с
  выдачей, 50 повторов того же тика не продлевают countdown; только тест временно
  задаёт `MinecraftServer.ticks` и восстанавливает его в finally. Проверены
  32 активации с двумя игроками, один реестр/рейд на деревню, смена serial,
  оба окончания пары no_sprint, отсутствие нового dirty при 50 неизменных
  reconcile и накопления ownership, сохранение HP17/предметов17. Пара с
  all_debuffs проверяет независимую очистку poison/omen в обе стороны.
- `sameUuidDiskRejoinAndEmptyServerSameId`: настоящий Fabric DISCONNECT,
  PlayerManager.remove/save и повторный connect того же UUID читают playerdata
  с неизменным countdown/owner. PersistentStateManager.save/readNbt восстанавливает
  текущую активацию и реестр; менеджер сохраняет актуальный рейд. Повтор того же
  ID на пустом сервере меняет serial, и следующий manager tick останавливает
  прежний рейд без победы. Проверен offline-end, дисковая загрузка ended-state
  и очистка старого omen настоящим JOIN. Это одна JVM, не перезапуск процесса;
  vanilla RaidManager NBT roundtrip отдельно покрыт существующим тестом.
- `actualOwnedWavesAndBothNetheriteEndings`: после настоящего village countdown
  вызывается ванильный private spawnNextWave через reflection. Созданные им
  рейдеры получают четыре issued armor slots. Проверены оба окончания пары:
  оставшееся netherite_mobs экипирует нового моба; оставшееся endless_raid
  создаёт следующий рейд/волну уже без выдачи брони. Существующие мобы и их
  снаряжение остаются; временные рейдеры удаляются по окончании сценариев
  и в finally теста.

Свежие команды выполнялись с отдельными init/runDir/XML/JUnit output:

```powershell
.\gradlew.bat build runGametest --offline -I build/endless-raid-review-baseline.init.gradle
.\gradlew.bat build runGametest --offline -I build/endless-raid-review-expanded-attempt1.init.gradle
.\gradlew.bat build runGametest --offline -I build/endless-raid-review-expanded-attempt2.init.gradle
.\gradlew.bat build runGametest --offline -I build/endless-raid-review-final.init.gradle
.\gradlew.bat processGametestResources --offline
python build/endless-raid-review-verify.py
```

Baseline: exit0/49s, 149 JUnit (54 suites), 5/5 GameTest. Первая попытка до
baseline получила exit1/1s из-за UTF-8 BOM в локальном Groovy init; лог сохранён
как `build/endless-raid-review-baseline-init-failed.log`, BOM исправлен только
в ignored init. Expanded-attempt1: exit1/47s, 149 JUnit, 7/8 GameTest; новый
assertion ошибочно относил BAD_OMEN к harmful-набору all_debuffs. Исправлен
только тест: ванильные предзнаменования не принадлежат этому companion.
Expanded-attempt2: exit0/45s, 149 JUnit, 9/9 GameTest.
Final: exit0/45s, 149 JUnit, 9/9 GameTest; failures/errors/skipped=0,
normal shutdown. Восстановление 43 entrypoints: exit0/1s. Проверка артефактов
и всех XML — exit0; первая попытка ignored verifier дала exit1, поскольку
PowerShell перенёс слово Unexpected в сохранённом сообщении BOM. Проверка
уточнена по startup failed/символу BOM, игровые assertions не менялись.
Логи `build/endless-raid-review-<phase>.log`, XML
`build/reports/endless-raid-review-<phase>.xml`, JUnit
`build/reports/endless-raid-review-<phase>-junit/`; failure-артефакты сохранены.
Init сужает только GameTest entrypoints/resources, задаёт свежий runDir
`build/gametest-endless-raid-review-<phase>-20261107`, отдельные reports и
форсирует свежий JUnit; production classpath/Mixin не подменяются.

Оценка расходов статическая. Каждые 20 серверных тиков проверяются P игроков
с effective-ID/ledger gate; активный getRaidAt обходит V рейдов измерения.
BadOmen wrapper делает дополнительный getRaidAt каждый тик исключительно
собственного bad_omen, включая ожидание деревни: учитывать O(Pown × V),
а не приписывать всем перехватам интервал20. RaidManager HEAD каждого измерения
обходит только R записанных ownership; при R>0 также проверяет GLOBAL/PERSONAL
списки. Нет обхода сущностей/чанков и принудительной загрузки. На остановке
R IDs проверяются/удаляются, markDirty только при изменении реестра; игроки
и миры не удерживаются в собственном singleton. Даже inactive есть modulo,
проход P/пустые ledger gates, lookup PersistentState на manager tick и обычные
shared callbacks/sync/HUD/history. Время CPU/FPS/MSPT, allocations, retained
memory и длительная нагрузка не измерялись.

Вердикт: pass_with_limits; механика pass, расходы no_issue_found / static.
Обычный клиент/HUD/bossbar/audio, весь естественный бой и полный набор волн,
настоящая сеть/кооператив, порталы/сон и full JVM restart остаются ручными
проверками выше. Локальный отчёт `Промпты/reviews/33-endless_raid.md` и
индекс содержат ограничения endless_raid-L01–L04 и результаты финального
прогона/проверки артефактов. Исторические 146/79 не засчитываются как новое ревью.
