# auto_jump — проверка «Попрыгунчик»

ТЗ: `Испытания/auto_jump.json`, EASY/GLOBAL, интервал 10 серверных тиков.

## Реализация и технические решения

- `AutoJumpChallenge.tick` выполняется после обновления цикла. Сервер вызывает
  обычный `PlayerEntity.jump()`, сохраняя Jump Boost, атрибут прыжка, спринт,
  статистику и расход голода. `no_jumping` отменяет этот же путь, `heavy_body`
  меняет его штатный импульс, `gravity_ten` продолжает менять только гравитацию.
- У каждого UUID есть временный сеанс текущего сервера, объекта игрока и
  activationSerial. Первая попытка немедленная; попытка, даже неуспешная,
  расходует интервал. Успешный ручной прыжок начинает тот же интервал, но
  интервал не запрещает ручные прыжки в последующие тики.
- Mixin фиксирует успешный прыжок только у записи `LivingEntity.velocityDirty`:
  отмена и ранний выход при нулевой силе до неё не доходят. Повторный вызов
  `PlayerEntity.jump()` в том же серверном тике отменяется до статистики,
  голода и событий. Автоматические повторные вызовы дедуплицирует тот же сеанс.
- После успешной автоматической попытки сервер снимает `onGround`, чтобы
  пакет движения не засчитал этот отрыв как ещё один ручной прыжок.
  Один S2C `AutoJumpPayload` несёт ID объекта, итоговый вертикальный импульс
  и горизонтальную прибавку обычного спринта. Клиент сохраняет собственную
  горизонтальную скорость; уже начавшийся подъём не перезапускается.
  Пакет для старого объекта, неактивного испытания или исключённого состояния
  не применяется. C2S, удержания клавиши, смены настроек и постоянного ввода нет.
- Вода/лава, лестницы, пассажиры, полёт способностями/элитрами, воздух,
  Creative/Spectator и мёртвые/удалённые игроки исключены. GUI и приседание
  не выключают механику. GLOBAL/PERSONAL и включённость проверяет общий gate.
- Собственного NBT нет: сохраняется общая активация, интервал после нового
  объекта/входа/рестарта начинается заново. Повторные onStart/onJoin не сбрасывают
  текущий интервал. Смена измерения на том же объекте сохраняет его.
  Выход, респаун, завершение, disabled и остановка сервера освобождают сеансы.
  Деактивация не меняет текущую скорость, падение, предметы или чужие эффекты.

## Ревью 2026-11-05 — свежие проверки

Reviewed production HEAD: `8907c0dff255f23ded260f962b3e8e4fcc9dcf4b`.
Production/shared contracts не менялись. Механика: pass; расходы:
no_issue_found / static; итог: pass_with_limits.

- `.\gradlew.bat build runGametest --offline -I build/auto-jump-review-<phase>.init.gradle`:
  baseline exit 0, 30s, 149 JUnit / 54 suites и 4/4 auto_jump GameTest;
  expanded exit 0, 31s, 149 JUnit и 5/5 auto_jump;
  final exit 0, 34s, 149 JUnit и 13/13 GameTest: 5 auto_jump, 4 heavy_body,
  4 gravity_ten. Все failure/error/skipped равны 0, normal server shutdown.
  Логи: `build/auto-jump-review-<phase>.log`; XML:
  `build/reports/auto-jump-review-<phase>.xml` и `-<phase>-junit/`.
  Каждый init меняет только GameTest resources/entrypoints, fresh runDir
  `build/gametest-auto-jump-review-<phase>-20261105`, JUnit/report paths;
  gameplay settings, зависимости и production source sets прежние.
- Предметные пути: настоящий PlayerEntity.jump, hunger/stat/sprint/Jump Boost,
  no_jumping и остальные исключения, collision потолка/пола, fall damage,
  GUI handler, режимы, смерть/respawn, Nether и disk reload. У Clock-теста
  теперь три независимых игрока: успешные попытки 1/11/21, ручной прыжок 13
  и следующая автоматика 23; неудача с нулевой силой на 1 тоже откладывает
  следующую попытку до 11, даже если сила восстановлена на 2. Проверяются
  реальные server ticks, повторный callback, изменение day time.
- Новый `personalPacketsRepeatedCleanupAndDiskKeepForeignData`: фактический
  адресный S2C и codec, PERSONAL не влияет на второго игрока; 32 активации
  duplicate ID/serial, повторные start/join, disabled tick, stop/no_sprint,
  размер session map 1/2/0; реальный Fabric DISCONNECT и playerdata read/write,
  PersistentStateManager save/read/set active/offline-ended/empty-server end.
  Named 17 diamonds, XP 7 + .625, HP 17, Fire Resistance и чужие world data
  не откатываются. Сохранение проверено в одной JVM. Парные GameTest отдельно
  исполняют actual auto/heavy/gravity jump/travel и оба порядка окончания пары.
- `.\gradlew.bat runClient --offline -I src/clientprobe/auto_jump/init.gradle`:
  attempt 1 exit 1, 2s: новый fixture пытался переопределить final isRemoved;
  исправлен только fixture — реальное поле removalReason задаётся reflection.
  Attempt 2 exit 0, 19s: `AUTO_JUMP_REVIEW_CLIENT_PASSED checks=2486`.
  Сохранённый стенд исполняет production receiver/codec и реальные gates,
  проверяет загрузку обоих common jump Mixin в CLIENT; 32 reset/start/end,
  знаки motion/ground/manual ascent, 50 повторов approval без сложения,
  removed/old entity/null/death/modes/fluid/vehicle/flight/no_jumping/no_sprint.
  Unsafe ClientWorld/ClientPlayer; подменены world, alive/modes, ground,
  velocity, vehicle/fluid/climbing/flight accessors. Синхронизация вызывается
  вручную. Нет окна, input sampling, реальной сети, client jump/travel/HUD.
  Actual jump/travel исполняются SERVER GameTest, не этим probe.
- Restore `.\gradlew.bat processGametestResources --offline`: exit 0, 1s;
  штатные 43 entrypoints восстановлены. Verifier:
  `python build/auto-jump-review-verify.py`, лог `build/auto-jump-review-verifier.log`:
  hashes/metadata/registration/Mixin/current XML/CLIENT/restored resources/clean JARs.
  Первая попытка verifier exit 1 искала слово does, разорванное переносом
  PowerShell-лога; исправлен только поиск, сохранён verifier-attempt1.log.
- Ошибки подготовки: sandbox helper ACL5 не создал процесс; разрешённый
  escalated exec работает. Unicode в Python stdin исправлен поиском paths;
  первый baseline exit 1, 903ms, не нашёл ещё не созданный init и тестов не
  выполнял (`build/auto-jump-review-baseline-setup-failed.log`). Lock-файлы,
  кэш и зависимости не менялись. Эти попытки не засчитаны успешными тестами.

Расходы оценены статически. P — online players, S — own sessions <= P,
G/A — GLOBAL/PERSONAL IDs, K — client effective IDs. Каждый server tick:
prune O(S*(G+A)) и player loop O(P*(G+A)); собственный Clock O(1).
Session.matches/active вызывают PersistentState lookup с выделением Type,
горячие jump Mixin/CallbackInfo, boxing Integer и lambda/removeIf имеют цену.
При inactive остаётся обход P/gate, пустой bucket удаляется; Player/server
references удерживаются только до cleanup/следующего tick, SERVER_STOPPED
освобождает весь bucket. Один S2C на успешную auto attempt, не чаще одного
в 10 server ticks на UUID; 24 bytes doubles + VarInt entity ID без framing,
без C2S/tick-broadcast/собственного NBT. CLIENT receive O(K), только по пакету.
Общие cycle dispatch/sync/HUD и vanilla jump/travel/сеть оцениваются отдельно.
FPS/MSPT, allocations и retained memory не измерены; функциональные счётчики
не являются нагрузочным профилем.

Открыты auto_jump-L01 (обычный клиент/физический ввод/HUD/меню/lifecycle/пары),
L02 (реальные клиенты/сеть/RTT/TPS), L03 (полный новый JVM active/ended/offline),
L04 (одинаковые inactive/active/load профили). Точные шаги ниже и в локальном
`Промпты/reviews/23-auto_jump.md`. Прежние результаты не являются fresh evidence.

## Исторические автоматические проверки реализации

Дата: 2026-10-27. Minecraft 1.21.1, Yarn 1.21.1+build.3, Java 21.

- `.\gradlew.bat test compileGametestJava --offline` — BUILD SUCCESSFUL.
  120 JUnit, failures/errors/skipped = 0; четыре новых теста регистрации,
  точного интервала, ручного прыжка и переполнения серверного счётчика тиков.
- `.\gradlew.bat -I build/auto-jump-clean.init.gradle build runGametest --offline`
  — BUILD SUCCESSFUL, 53/53 GameTest, включая четыре новых, failures/errors = 0.
  Init задаёт только отдельный runDir
  `build/auto-jump-final-gametest`, не меняя зависимости или игровые настройки.
  Отчёт: `build/reports/gametest.xml`; лог: `build/auto-jump-verification.log`.
- Четыре новых GameTest: обычный реальный jump, голод/статистика, единичный
  S2C и оба порядка серверных вызовов, спринт, приседание, Jump Boost,
  heavy_body/gravity_ten/no_jumping/no_sprint, нулевая сила прыжка,
  реальные вода/лава/лестница, лодка, воздух, полёт, режимы и открытый handler;
  реальные тики с повторными попытками на 1/11/21 и независимыми игроками,
  ручной прыжок на 13 и следующая автоматическая попытка на 23,
  изменение day time без ускорения интервала; настоящие travel и коллизия
  потолка/опоры, неизменный computeFallDamage; lifecycle, PERSONAL/дубли,
  disabled, reconnect UUID, смерть/респаун, Nether/Overworld,
  фактическая запись/чтение состояния с диска и завершение офлайн.
- `.\gradlew.bat -I build/auto-jump-client-probe/init.gradle runClient --offline`
  — BUILD SUCCESSFUL, `AUTO_JUMP_CLIENT_PROBE_PASSED`. CLIENT preLaunch проверяет
  настоящий codec и `AutoJumpMotion.receive`: сохранение горизонтального
  движения, прибавку спринта, снятие ground, запоздалый ручной подъём,
  повтор, ID объекта, synced/active, режимы/смерть, вода/лава/лазание/транспорт/
  полёт, приоритет no_jumping и reset. Игрок и ground/world-accessors подменены;
  настоящего сетевого обмена и живого мира в этом стенде нет.
  Первый запуск стенда потребовал исправить его неинициализированный bounding
  box при Unsafe-игроке; игровая реализация причиной этого сбоя не была.
- Стенд, init, исходники, `probe.jar` и `verification.log` находятся только в
  игнорируемом `build/auto-jump-client-probe`; в игровой JAR они не включаются.
- Исходный sandbox-запуск wrapper не получил доступ к zip.lck в существующем
  `F:\Programs\GradleCache`. Проверки выполнены с разрешённым доступом к кэшу;
  lock-файлы не удалялись, зависимости и конфигурация сборки не менялись.

## Ручная проверка — не выполнена

Headless GameTest и CLIENT-стенд не заменяют эти сценарии:

1. В живом одиночном Survival-мире выполнить `/fdc list`, `/fdc activate auto_jump`,
   `/fdc active`; сверить название, EASY, HUD, полные правила и единичное
   стандартное уведомление. Повторить стоя, идя, спринтуя и присев без Space.
2. Проверить обычную траекторию/голод/падение, ручной прыжок одновременно с
   автоматическим, удержание Space, низкий потолок, ступени и края блоков.
   В GUI автоматика продолжается, если сервер тикает; пауза одиночного мира
   при остановленном сервере ничего не продвигает.
3. Проверить реальные водоёмы, лаву, лестницы/лианы, лодку/другой транспорт,
   элитры и смену режимов Survival → Creative → Spectator → Adventure/Survival.
4. Через `/fdc activate "double_challenge:auto_jump,no_jumping"` проверить
   отмену; аналогично проверить heavy_body, gravity_ten и no_sprint.
   После `/fdc activate no_sprint` автоматические прыжки прекращаются,
   текущая траектория и чужое ограничение сохраняются. Проверить реальное
   движение, замечаемое adhd_no_standing_still, и урон the_floor_is_lava
   при касании земли: собственной защиты от него нет.
5. Умереть/возродиться с обоими keepInventory, выйти/войти, пройти реальные
   порталы Nether/End. Проверить активное сохранение и полный рестарт JVM;
   затем завершить испытание офлайн через активацию другого и вернуться.
6. Необязательно: два настоящих клиента на выделенном сервере, поздний вход,
   отключение одного. Проверить независимость прыжков, оба порядка ручного
   ввода и получения S2C при RTT, потере/задержке сети и низком TPS.
7. Дождаться естественной смены цикла: не остаются интервалы или принуждение
   к прыжку. Команды `/fdc stop` нет.

## Остаточные ограничения

Движение реального клиента и его пакеты при RTT не запускались. GameTest
вызывает настоящий jump и проверяет S2C, но не эмулирует полный обмен движения
двух игровых клиентов. Сохранение с диска проверено в том же JVM; полный
рестарт и визуальная плавность требуют ручной проверки. Ранее отмеченные
риски изоляции NoBlockPlacing/BitterExperience не исправлялись в этой задаче.
