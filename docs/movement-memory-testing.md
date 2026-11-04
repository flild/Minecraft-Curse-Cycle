# Ноги помнят — movement_memory

Реализовано по `Испытания/movement_memory.json`: MEDIUM/GLOBAL, 20 серверных
тиков после перехода итогового горизонтального ввода в ноль. Название и оба
описания взяты из ТЗ; используются существующие HUD, меню и команды.

## Путь ввода и времени

- Настоящий `KeyboardInput.tick` каждый раз читает текущие назначения клавиш.
  Его RETURN вызывает `InvertedKeyboardInput.apply` → `MovementMemoryInput.apply`
  → `BlockedDirectionInput.apply`. Память хранит две знаковые компоненты
  относительно взгляда, до блокировки направления. Нормализация диагонали,
  поворот относительно yaw и физическое перемещение остаются ванильными.
- Запоминается направление без старого sneak-множителя: подставляется текущий
  коэффициент замедления. Jump/sneak читаются заново, KeyBinding не меняются.
  При удержании без текущей клавиши sprint снимается ванильная защёлка спринта.
  no_sprint/no_jumping продолжают работать своими существующими перехватами.
- Новый ненулевой ввод сразу отменяет старое удержание и заменяет память.
  Встречные клавиши с нулевой итоговой осью считаются нулём. Нулевой ввод
  после истечения не запускает повторный интервал.
- `MovementMemoryPayload` зарегистрирован в обе стороны. При штатной
  синхронизации активному получателю передаётся activationSerial (sequence=-1).
  Только отпускание/отмена посылают C2S; сервер отвечает состоянием интервала
  и посылает его окончание. На каждом тике пакеты не отправляются.
- Сервер проверяет активность, включённость, игрока и условия; интервалы
  независимы по серверу/UUID/activationSerial. Начало — принятие отпускания,
  конец — `server.getTicks() - startedAt >= 20`. Повтор того же sequence
  не продлевает срок. Сутки, сон и `/time` в расчёте не участвуют.
- Клиент немедленно предсказывает удержание и заканчивает его по серверному
  ответу. При сетевой задержке видимое клиентское продолжение включает доставку
  запроса/ответа; точные 20 тиков относятся к серверному интервалу. Стенд не
  измеряет задержку сети или фактическую дистанцию на живом клиенте.
  Устаревшие ответы другой активации/sequence не меняют новое движение.
- Любой открываемый Screen очищает память в HEAD `MinecraftClient.setScreen`,
  в том числе перед паузой. Проверки перед подстановкой и на END_CLIENT_TICK
  очищают её в GUI, воде/лаве, на лестнице, при полёте/элитрах, в транспорте,
  при смерти, смене режима, объекта игрока, мира или input. Survival/Adventure
  подходят, в том числе в обычном прыжке/падении. Creative/Spectator исключены.
- Клиентские JOIN/DISCONNECT и отсутствие ID в sync_state очищают память и
  ссылки. Серверные disconnect, stop, respawn, смена контекста и SERVER_STOPPED
  удаляют свои сеансы. В NBT сохраняется общая активность штатным CurseCycleState;
  направление и незавершённое удержание не записываются.
- Скорость, позиция, внешние эффекты и настройки пользователя не восстанавливаются.
  После окончания ввода ванильная инерция и slippery_hands остаются независимыми.

## Исторические проверки реализации (2026-10-27)

Следующие результаты сохранены как история реализации, а не доказательства ревью.

- `.\gradlew.bat test compileGametestJava --offline` — BUILD SUCCESSFUL, 12s.
  Пять новых JUnit: точные метаданные/регистрация/double_challenge; пустая
  активация; один запрос при длительном нулевом вводе; серверное окончание;
  немедленная замена; старые ответы и serial; все 16 сочетаний горизонтальных
  клавиш с инверсией и без; актуальные jump/sneak и разные масштабы;
  композиция с blocked_direction; исключения и очистка.
- `.\gradlew.bat build runGametest --offline` — BUILD SUCCESSFUL, 1m 3s.
  XML: 111 JUnit и 45/45 GameTest, failures/errors/skipped = 0.
  Лог: `build/movement-memory-verification.log`.
- Два новых GameTest в отдельных batch: реальные 19/20/21 серверные тики,
  отсутствие раннего/повторного окончания и продления дубликатом; два независимых
  игрока; отмена; смена активации и no_sprint; PERSONAL/дубликаты/чужой получатель;
  четыре режима, Nether/Overworld, смерть/новый объект и disabled;
  NBT сохраняет активность без локальной памяти; возврат UUID после окончания
  офлайн; `fdc list`, `fdc activate movement_memory`, `fdc active` через dispatcher.
  C2S codec вызывает зарегистрированную серверную функцию напрямую, ответы
  проходят настоящий S2C transport в EmbeddedChannel и codec round trip.
  Это не тест живого сетевого клиента или клиентской физики.
- `.\gradlew.bat -I build/movement-memory-client-probe/init.gradle runClient --offline`
  — BUILD SUCCESSFUL, 18s, `MOVEMENT_MEMORY_CLIENT_PROBE_PASSED`.
  Fabric CLIENT применил настоящие Mixin. 3424 матричных вызова
  `KeyboardInput.tick`: 16 сочетаний, инверсия, все три запрета/без запрета,
  длительное удержание, единственный запрос, свежие jump/sneak, разные масштабы
  и серверное окончание. Дополнительно: немедленный реверс, вызов настоящего
  внедрённого GUI cleanup-handler, пропуск/возврат применимости, reset и
  переназначенная клавиша. Gates применимости и сетевой транспорт подменены
  тестовыми Mixin; живой мир, игрок и окно не создавались. Стенд и лог в
  игнорируемом `build/movement-memory-client-probe`, в игровой JAR не входят.
- Начальный запуск Gradle в песочнице не смог открыть общий wrapper zip.lck.
  С разрешённым доступом к существующему кэшу проверки прошли; lock не удалялся,
  зависимости не менялись.
- `git diff --check` и `git diff --cached --check` проверяются перед коммитом.

## Ручные сценарии — не выполнены

1. Один Survival-игрок, ровный пол без льда и других эффектов:
   `/fdc list`, `/fdc activate movement_memory`, `/fdc active`. Проверить одно
   штатное уведомление, точные тексты HUD/меню и отсутствие движения при активации.
   Пройти вперёд, отпустить; проверить серверные границы 19/20/21 и отдельно
   обычное физическое торможение. Повторить боком, диагональю, встречными
   клавишами, с поворотом камеры и немедленным нажатием назад.
2. Во время удержания отдельно менять jump, sneak и sprint. Проверить обычный
   прыжок/падение, удар/предмет в руке и текущие переназначения клавиш. Sprint
   без текущего ввода не должен удерживаться вместе с направлением.
3. Во время удержания открыть инвентарь, чат, меню паузы, контейнер и меню мода;
   закрыть без движения. Повторить с водой, лавой, лестницей/лианой, лодкой,
   лошадью, элитрами и полётом. После исключения без нового ввода движения нет.
4. Survival → Creative → Spectator → Adventure → Survival, смерть с обоими
   keepInventory, настоящие порталы Nether/End и возврат из End; выход/вход,
   другой сервер и полный рестарт JVM. После каждого переноса память пуста.
   Проверить завершение офлайн и отключение в серверных настройках.
5. Пары с inverted_keyboard, blocked_direction, no_sprint, no_jumping,
   slippery_hands. Порядок ввода соответствует ТЗ; блокировка не пропускает
   запомнённый запрещённый знак, чужая инерция/отбрасывание сохраняются.
   После `/fdc activate no_sprint` память прекращается, запрет спринта остаётся.
6. Естественное завершение по циклу, пауза, сон, `/time set/add`, снижение TPS
   и задержка сети. Отдельно оценить видимое предсказание и серверный интервал.
   Необязательно: два живых клиента, поздний вход и выход одного игрока.

Живые игровые сценарии выше, отрисовка HUD, физика движения, сетевой RTT и полный
рестарт JVM в этой работе не выполнялись. Сборка и стенды их не подменяют.

## Ревью этапа 20 — 2026-11-04

Исходный HEAD `ef5780eb128f3d6ca6ef96f66170683421ce4f35`, main, чистое дерево.
Полный JSON SHA-256 `70928a92b6f77c9345e359cd49de5c5e8e3d77810d7e94c8fd6059081798c036`.
Постоянные документы и ТЗ не менялись. Ревью только movement_memory;
heavy_body изучен и проверен только как предметная пара памяти.

### Подтверждённое исправление

`movement_memory-F01`: при уже действующем удержании каждый C2S с новым
sequence повторно присваивал startedAt и отправлял S2C. Это позволяло продлевать
один интервал и порождало лишние ответы без новой смены ввода. Red GameTest
отправил 32 таких запроса на десятом тике: получил повторные подтверждения,
2/3 теста прошли. Перенос deadline также непосредственно виден в прежнем receive.

Теперь новый sequence обновляет адресуемую версию ответа, а startedAt меняется
только при переходе false→true. Повторные true не отвечают и не сдвигают срок;
на исходном 20-м тике приходит одно окончание для последнего sequence. Отмена
true→false и новый false→true остаются немедленными. Запрос удержания при
неподходящих серверных условиях получает отрицательный ответ, чтобы прекратить
клиентское предсказание. Сетевой формат, общий sync и клиентский код не менялись.

### Свежие команды и артефакты

Server-команда для каждой фазы:
`.\gradlew.bat build runGametest --offline -I build/movement-memory-review-<phase>.init.gradle`.
Каждая использует отдельный новый runDir
`build/gametest-movement-memory-review-<phase>-20261104`, лог
`build/movement-memory-review-<phase>.log`, GameTest XML
`build/reports/movement-memory-review-<phase>.xml`, JUnit XML
`build/reports/movement-memory-review-<phase>-junit/`.

| Фаза | Exit / результат | Что исполнялось |
|---|---|---|
| baseline | 0 / 38s; 149 JUnit/54 suites + 2/2 GT | Исходный код, прежние предметные тесты |
| red | 1 / 40s; 149 JUnit + 2/3 GT | Новый regression до исправления F01 |
| fixed | 0 / 1m19s; 149 JUnit + 3/3 GT | Исправление + исходный deadline/новый sequence/отмена/повторный старт |
| final | 1 / 57s; 149 JUnit + 9/11 GT | Расширенные lifecycle/disk/пары; два сбоя нового fixture |
| verified | 0 / 50s; 149 JUnit/54 suites + 11/11 GT | Итоговые 5 memory + 3 inverted_keyboard + 3 blocked_direction |

В failed final проверка всех пакетов ошибочно учитывала периодический context
sync с sequence=-1 как повторный hold-ответ; фильтр теперь проверяет sequence>=0.
Парный jump-тест не устанавливал isOnGround=true, необходимый heavy_body;
исходные условия исправлены. Эти два сбоя не объявлены production-дефектами.

Предметные серверные тесты:

- `exactServerIntervalAndIndependentPlayers`: реальные 19/20/21 тики, два
  независимых игрока, дубликат sequence, одна expiry, отмена, новый serial,
  double/no_sprint. Таймер не использует timeOfDay. Tick20 вызывает тот же
  handler вручную до END_SERVER_TICK; повторный вызов идемпотентен.
- `lifecyclePersonalAndPersistence`: PERSONAL/дубликаты/чужой получатель,
  четыре режима, реальный Nether teleport/смерть/respawn/disabled, codec,
  OP-команды list/activate/active. Его старый write/read NBT отдельно от disk.
- `newerRepeatedHoldCannotExtendDeadlineOrSpamReplies`: 32 новых sequence на
  десятом тике, ноль дополнительных hold-ответов, исходный deadline20,
  финальное окончание для sequence33, cancel/rearm.
- `repeatedSessionsDisconnectAndActualDiskWithoutInputReplay`: 32 цикла
  sameID/double/start/join/disabled/re-enable/end, размер sessions1 и удаление
  bucket; stale/negative/duplicate packets без ответа; no_sprint продолжает
  работать. Настоящий Fabric DISCONNECT, PlayerManager save/load playerdata,
  PersistentStateManager save/read/set active/ended/offlineend/emptyserver,
  тот же UUID, нет направления/интервала в NBT и нет replay-сессии после входа.
  Именованные diamonds17, XP7+.625, HP17, FireResistance и чужие worlddata
  сохраняются. Один JVM-процесс; не full restart.
- `intervalNeverReplacesVelocityOrHeavyJumpPhysics`: настоящий vanilla jump с
  heavy_body×.6 при памяти; cancel/end оставляют внешний velocity и действие
  heavy_body. Это не клиентское travel после физического отпускания клавиши.

CLIENT-команда:
`.\gradlew.bat runClient --offline -I src/clientprobe/movement_memory/init.gradle`.
Логи `build/movement-memory-review-client-attempt<N>.log`, отдельный probe JAR
в `build/movement-memory-review-client-probe/`. Стенд сохранён в src/clientprobe;
production sourceSets его не включают.

Попытки1/2/3/4 завершились exit1 (29s/1m/34s/32s). Причины исключительно нового
fixture: ожидание сброса при том же режиме; проверка ссылок после нового input;
в паре heavy_body обращение к отсутствующему network player-list у Unsafe player.
Четвёртая попытка выявила в getWorld fixture скрытое унаследованным полем имя
client: оно уточнено как MovementMemoryProbe.client. Исправления остальных:
фактический переход режима, проверка сразу после reset и synthetic
isCreative/isSpectator/getWorld плюс client-world flag для наблюдателя heavy_body.
Неуспешные логи сохранены. Попытка5: exit0/39s,
`MOVEMENT_MEMORY_REVIEW_CLIENT_PASSED checks=307473 ticks=92485`.

Стенд исполняет настоящий transformed KeyboardInput.tick, shouldRemember,
Memory.apply и порядок inversion→memory→block без тестовых Mixin/gate-stubs.
128 masks × 3 initial scales × 2 inversion × 4 blocked choices; activation0,
24 повторных tick с актуальными jump/sneak/scale, неизменный sequence,
stale serial/sequence, authoritative end. Дополнительно reverse/sprint latch,
все modes/liquids/climbing/flying/vehicle/dead/removed/player-world-input change,
null contexts/foreign input/GUI/validate/reset32/переназначения, обе стороны
окончания пар memory-block и окончание memory оставляет inversion. HeavyBodyInput
наблюдает итоговый синтетический ввод. GUI HEAD-handler вызывается отражением;
полный window-dependent setScreen не запускается. Client/world/options/player/
manager создаются Unsafe; accessor-условия искусственные, sync_state/S2C
receive вызываются вручную; реального соединения и client travel нет.

Восстановить стандартные resources:
`.\gradlew.bat processGametestResources --offline`.
`python build/movement-memory-review-verify.py` проверяет SHA5, metadata6,
регистрацию1, memory20, client-only Mixin2, все свежие XML/логи включая failures,
43 entrypoints/source==built, отсутствие стендов в binary/sources JAR.

### Расходы и ограничения

Static: клиент имеет один Memory/три context-ссылки, gate O(A), подстановка O(1),
без world scan/позиционных откатов/записи KeyBinding. Cancel при неактивности
остаётся O(1) и меняет sequence; hooks работают и после окончания.
Сервер хранит максимум одну сессию на UUID/сервер, включая ссылку на Player/World;
tick O(S×(G+A)), getPlayer по UUID, без полного обхода мира. Ineligible requests
могут создать transient session и отрицательный ответ, удаляемый следующим tick;
sender привязан к настоящему networking context. Возрастающий sequence сам
по себе больше не продлевает holding и не генерирует повторный true-ответ.
Cancel/rearm — реальные переходы; произвольного лимита input-rate/античита не
добавлено, обработка входного трафика остаётся O(R). Сессии удаляются на
stop/disconnect/respawn/context mismatch/disabled tick/SERVER_STOPPED. Направление
не сериализуется. Общие sync/dispatch/HUD/vanilla travel имеют собственную цену.

MSPT/FPS/allocations/retained-memory profile не измерялись. Открыты:
movement_memory-L01 обычный клиент/физическое движение/HUD/GUI/пары;
L02 реальный network/RTT/два клиента; L03 full new-JVM restart;
L04 нагрузка и профиль. Точные шаги — ручной список выше и отчёт20.
Все прежние ограничения других этапов остаются открытыми.
