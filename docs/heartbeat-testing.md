# heartbeat — проверка реализации и ревью

Дата: 2026-10-31. Minecraft 1.21.1, Yarn 1.21.1+build.3, Fabric API
0.102.0+1.21.1, Loader 0.16.0, Java 21.

## Ревью 2026-11-07 — свежие проверки

Исходный HEAD `0cbb9703882fe8b79417977c7dea16f85c57e160`, main. Подтверждённых
дефектов production-кода не найдено; игровой код и общие контракты не менялись.
Итог: `pass_with_limits`, механика `pass`, расходы `no_issue_found / static`.
Старые результаты ниже относятся к реализации и не являются доказательствами
этого ревью. Полная матрица ТЗ и ограничения: локальный
`Промпты/reviews/31-heartbeat.md`.

- `./gradlew.bat build runGametest --offline -I build/heartbeat-review-baseline.init.gradle`:
  exit 0, 149 JUnit / 54 suites, 2/2 предметных GameTest, без failures/errors/skipped.
- `./gradlew.bat -I src/clientprobe/heartbeat/init.gradle runClient --offline`:
  исходный probe exit 0, `HEARTBEAT_CLIENT_PROBE_PASSED checks=337`;
  после расширения exit 0, `checks=422`.
- `./gradlew.bat build runGametest --offline -I build/heartbeat-review-final.init.gradle`:
  exit 0, 149 JUnit / 54 suites, 4/4 предметных GameTest, без failures/errors/skipped.
  Два добавленных сценария: повторное исполнение существующей пары one_heart
  с настоящим пропуском, тотемом и смертью; 32 цикла double heartbeat/no_sprint
  для двух игроков, ранние 100 C2S за цикл, новые токены, отсутствие штрафов
  и освобождение всех серверных сеансов после завершения. Reflection только
  наблюдает карту SESSIONS; время, authority и active gates не подменяются.
- Повторные `runClient --offline` с отдельными `-I src/clientprobe/blind_hud/init.gradle`
  и `-I src/clientprobe/silent_world/init.gradle` проверяют соответствующие
  предметные пары и оба окончания. Blind HUD исполняет production overlay с
  захватом рисования; silent_world проверяет реальный OpenAL output и сохранение
  heartbeat state/age, без полного ритм-input/render/network цикла.

Расширенный heartbeat probe проверяет чужой window handle, потерю/возврат
фокуса, отсутствие доступного C2S, отсутствие искусственного PRESS при возврате
фокуса/канала, поздний S2C после завершения, unsynced connection, освобождение
удержания после disconnect и 32 цикла inactive/active с точным числом пакетов.
Настоящие transformed Keyboard/Mouse исполняются до test-only отмены vanilla
dispatch; canSend/send заменены управляемым fixture/capture. DrawContext и
игровые объекты по-прежнему fixtures. Это не проверка GLFW, framebuffer, реальной
сети или полного запуска обычного клиента.

Изоляция сервера: init-script копирует `src/gametest/resources/fabric.mod.json`
в `build/heartbeat-review-<phase>-resources`, оставляет entrypoint только
`com.cursecycle.HeartbeatGameTest`, принудительно обновляет resources/JUnit,
задаёт отдельные `build/gametest-heartbeat-review-<phase>-20261107` и
`build/reports/heartbeat-review-<phase>.xml`; production/classpath не меняются.
Вариант final получается заменой `baseline` на `final` в baseline-script.
Для воспроизведения baseline именно с 2 тестами нужен исходный HEAD; текущий
класс содержит 4 теста. После запуска восстановить обычные resources:
`./gradlew.bat processGametestResources --offline`; сравнить все 43 entrypoints
с исходным JSON. Binary/sources JAR не должны содержать validation/GameTest/Probe.

Логи ревью: `build/heartbeat-review-{baseline,final}.log`,
`build/heartbeat-review-client-{baseline,final}.log`,
`build/heartbeat-review-blind-hud-pair.log`, `build/heartbeat-review-silent-world-pair.log`.
JUnit: `build/reports/heartbeat-review-{baseline,final}-junit/TEST-*.xml`.
Финальная сверка: `python build/heartbeat-review-verify.py`; после передачи
тот же script с `--handoff`. Build-файлы локальные и в production не включаются.

Статический анализ: постоянный server tick проходит текущих игроков и сеансы,
включая inactive gates; active copy списка сеансов ограничена online players.
Входящий C2S не создаёт сеанса, не содержит клиентского времени и отправляет ack
не более одного на принятое окно. Образец отправляется каждые 5 тиков, при
переходе цели, принятии и общем sync. Ввод отслеживает физические keys даже
неактивным, чтобы активация не превращала удержание в нажатие. Render проверяет
active до рисования; text focus обходит дерево виджетов только при нужном пути.
Сканирований мира, собственных сохранений и очереди штрафов нет. FPS/MSPT,
allocation rate, retained memory и настоящая нагрузка не измерялись.

Ручные пункты в конце документа остаются невыполненными: heartbeat-L01 —
обычный ввод/GUI/визуализация/физический lifecycle и пары; L02 — два настоящих
клиента, транспорт/RTT/низкий TPS; L03 — полный новый JVM active/ended/offline;
L04 — сравнимый профиль inactive/active/double и повторных жизненных циклов.

## Поведение и технические решения

`heartbeat`, «Сердце на ручном приводе», HARD/GLOBAL. Название, краткое описание
и полные правила совпадают с `Испытания/heartbeat.json`.

- Персональный отсчёт использует `MinecraftServer.getTicks()`: подготовка 100
  тиков, первая цель на 120-м тике, следующие через 20. Окно ±5 включительно;
  на target+6 один запрос урона 2, если удар не принят. Повторный вызов в том
  же тике не дублирует результат. Пропущенные при скачке отсчёта окна не образуют
  очередь: `Rhythm.advance` сразу передвигает цель, возвращая не более одного
  запроса. Сон и изменение времени суток не меняют этот отсчёт.
- C2S содержит только activation serial и выданный сервером токен фазы. Нет
  клиентского времени, номера цели, размера урона или флага успеха. Сервер
  проверяет текущие объект игрока/подключение, мир, активацию и применимость;
  оценивает окно по времени получения на серверном потоке Fabric.
- Фазы хранятся только в памяти сервера. Смерть, респаун, измерение, выход,
  исключённый режим, disable и завершение освобождают собственный сеанс.
  Смена режима перехватывается в `ServerPlayerEntity.changeGameMode`, чтобы
  Creative → Survival даже внутри одного тика начинал подготовку заново.
  Повторный start/sync и Survival → Adventure существующую фазу не сбрасывают.
  Остановка сервера очищает карту. Активные ID сохраняет обычный CurseCycleState;
  фаза и пропуски в NBT не записываются.
- Собственный damage type входит только в `bypasses_armor`. Вызов обычного
  `player.damage` сохраняет сопротивление, поглощение, cooldown, тотем и общие
  обработчики фактически прошедшего урона. Exhaustion=0, дополнительного штрафа
  голода нет. Здоровье и предметы не возвращаются.
- B — стандартная переназначаемая KeyBinding. Client-only Mixin наблюдают
  физические PRESS/RELEASE до dispatch экрана, не отменяя ванильные события.
  REPEAT и повторный PRESS без RELEASE не отправляются. Поддержаны KEYSYM,
  SCANCODE и привязка к кнопке мыши. Чат, редактирование книги/таблички и
  сфокусированные TextFieldWidget/EditBoxWidget исключают удар; проверяются
  также поля, сфокусированные напрямую, без focus path родительского экрана.
- Полоса рисуется один раз в `GameRenderer.render` после экранов/tooltip,
  перед финальным `DrawContext.draw`. Она показывает окно, цель, текущую клавишу,
  подготовку и серверное подтверждение принятия. Общий HUD мода и actionbar
  не используются. F1, текстовый ввод, смерть и исключённые режимы скрывают
  полосу; нетекстовые меню и контейнеры её не закрывают. Звука метронома нет.
- Сервер отправляет образец каждые 5 тиков, при приёме удара, смене цели и
  обычном sync состояния. Клиент предсказывает не более 5 тиков между образцами,
  без компенсации сетевой задержки; integrated pause останавливает предсказание.
  CLIENT-стенд не доказывает удобство этого отображения при реальной задержке.

## Автоматически выполнено при реализации 2026-10-31 — история

1. `.\gradlew.bat compileJava compileGametestJava --offline` — BUILD SUCCESSFUL.
2. `.\gradlew.bat build runGametest --offline` — BUILD SUCCESSFUL; 144 JUnit,
   failures/errors/skipped=0, 71/71 GameTest. Сервер завершился штатно.
3. `.\gradlew.bat -I src/clientprobe/heartbeat/init.gradle runClient --offline`
   — BUILD SUCCESSFUL, `HEARTBEAT_CLIENT_PROBE_PASSED checks=337`.
4. После уточнения рисунка окна и серверного теста совместимости:
   `.\gradlew.bat -I src/clientprobe/heartbeat/init.gradle build runClient runGametest --offline`.
   BUILD SUCCESSFUL, 144 JUnit без failures/errors/skipped, 71/71 GameTest,
   `HEARTBEAT_CLIENT_PROBE_PASSED checks=337`, штатное завершение.
5. После финального выравнивания damage type с существующими собственными
   источниками урона (exhaustion=0): `.\gradlew.bat build runGametest --offline`.
   BUILD SUCCESSFUL, 144 JUnit без failures/errors/skipped, 71/71 GameTest,
   штатное завершение сервера. CLIENT-код после успешного стенда не менялся.

Первый sandbox-запуск остановился до компиляции на доступе к
`F:\Programs\GradleCache\wrapper\dists\...\gradle-8.10.2-bin.zip.lck`.
Следующие команды использовали разрешённый доступ к существующему кэшу;
lock-файлы, зависимости и настройки окружения не менялись.

### JUnit

Шесть новых тестов проверяют точные метаданные/единственную регистрацию,
границы 114/115/125/126, подготовку, штрафы 126/146, повторную обработку одного
тика, отсутствие бронирования будущего удара неверным нажатием, независимые
фазы и арифметический пропуск большой группы окон без очереди наказаний.

### Dedicated-server GameTest

Два новых теста исполняют production `tick`, `receive`, damage path, lifecycle
события и Mixin изменения режима на настоящем Fabric SERVER:

- Реальные 147 серверных тиков; ранняя и поздняя границы первого окна,
  отклонение на ±6, дубликат принятого удара, второй пропуск, поздний игрок
  с фазой на 10 тиков позже; двойной вызов обработчика в каждом тике.
- Старый activation serial и чужой токен проверяются **внутри открытого окна**.
  C2S codec → production receiver; S2C собирается из настоящих EmbeddedChannel
  после pending tasks/flush и проверяется своим codec.
- Фактические HP и absorption: броня не снижает 2, Resistance I оставляет 1.6,
  поглощение теряет 2, недавний равный урон сохраняет vanilla i-frames,
  тотем спасает при 1 HP. Наблюдатель ALLOW_DAMAGE отдельно считает запросы.
- Прошедший heartbeat-урон запускает PERSONAL `damage_slot_drop`: один настоящий
  ItemEntity с целым стеком. Замена испытания сохраняет потерю HP/предметов,
  `no_sprint` продолжает работать. Тестовые выпавшие предметы удаляются.
- GLOBAL/PERSONAL, дубли ID, disabled; повторный start без сброса; Creative,
  Spectator, Survival, Adventure; смерть/замена объекта при respawn; Nether,
  End и Overworld; reconnect UUID с новым токеном; double с `no_sprint`.
- Реальные save/readNbt с диска активного состояния, отсутствие фазы в данных;
  очистка in-memory сеансов моделирует старт новой JVM. Завершение сохраняется
  при офлайн-игроке; повторный вход не создаёт сеанс.
- OP 2: `/fdc list`, `/fdc activate heartbeat`, `/fdc active`; одна регистрация.

Это embedded-соединения, а не два настоящих клиента. C2S transport не проходит
через реальный сокет. Чтение сохранения в текущей JVM не равно перезапуску JVM.

### CLIENT preLaunch probe

`src/clientprobe/heartbeat/` собирается отдельным init-script в
`build/heartbeat-client-probe/probe.jar`. Игровые binary/sources JAR не содержат
validation-классы или тестовые Mixin.

- Исполняются настоящие transformed `Keyboard.onKey` и `Mouse.onMouseButton`.
  Тестовые Mixin останавливают дальнейший vanilla dispatch у первого
  `MinecraftClient.getWindow`, **после** production raw-edge hook. ASM отдельно
  подтверждает этот порядок и единственный production hook в каждом методе.
  GLFW callback/настоящий экранный dispatch этим не проверяются.
- Тестовый Mixin перехватывает только `ClientPlayNetworking.canSend/send`:
  production matching, PRESS/RELEASE/REPEAT, gates и генерация payload остаются.
  Проверены удержание/100 повторов, повторный PRESS, отпускание, нетекстовый
  Screen и container, чат/книга/табличка, сфокусированное поле AnvilScreen и
  EditBoxWidget, rebind B → N → SCANCODE → MOUSE. Смена образца не превращает
  удержание в новое нажатие. События текста не перехватываются production-кодом.
- Production injected renderer handler вызывается с DrawContext, захватывающим
  fill/text вместо GL. ASM подтверждает вызов после renderWithTooltip и до
  финального GUI flush. Проверены три размера, окно/цель, accepted color, скрытый
  общий HUD, F1, text/nontext screens, подготовка, pause, cap предсказания, четыре
  режима, смерть, смена объектов игрока/мира, clear, disconnect и окончание
  офлайн. Матрица восстанавливается и после исключения во время рисования.
- Мир, игрок, менеджер и окно — Unsafe fixtures после vanilla bootstrap;
  полного render loop, настоящего шрифта, OpenGL и сетевого lifecycle здесь нет.

Логи: `build/heartbeat-server-verification.log`, `build/heartbeat-client-probe.log`,
`build/heartbeat-verification.log`, `build/heartbeat-final-verification.log`.
XML: `build/test-results/test/TEST-*.xml`,
`build/reports/gametest.xml`.

## Ручные проверки, ещё не выполненные

1. Survival, 20 HP, без эффектов: активировать heartbeat, попасть в первое
   окно, пропустить второе, в третьем держать B с момента до открытия. Проверить
   только один запрос 2 за каждый пропуск и отсутствие автоматической игры.
   Сверить читаемость полосы, подсказку текущей клавиши и подтверждение сервера.
2. Два настоящих клиента с разными фазами: у первого контейнер, у второго чат.
   Первый продолжает ритм, второй пишет B как текст и получает только штрафы
   за пропуски. Закрыть чат, отпустить B и возобновить ритм. Повторить с полями
   наковальни, командного блока, книги и таблички; проверить сами введённые буквы.
3. Переназначить B, проверить экран настроек управления, клавиатурные раскладки,
   scancode, кнопку мыши и конфликт другой привязки; проверить потерю/возврат
   фокуса окна. Production не отменяет чужие действия той же кнопки.
4. Проверить настоящий framebuffer: HUD в мире, контейнер, пауза без текстового
   фокуса, F1, разные GUI scale/разрешения, выключенный общий HUD. Проверить
   отсутствие перекрытия важных элементов и сохранность общего actionbar.
5. Пауза одиночной игры должна останавливать серверный ритм. В сетевой игре
   открытое меню не останавливает сервер. Повторить при задержке и низком TPS:
   решение принимает сервер по получению пакета; компенсации задержки нет.
6. Два клиента, late join, настоящая смерть/respawn, выход/reconnect, портал,
   Creative/Spectator → Survival/Adventure: новая подготовка без накопленных
   штрафов. Проверить double `heartbeat,no_sprint`, затем `/fdc activate no_sprint`.
7. Проверить реальные пары с `damage_teleport`, `one_heart` и `silent_world`.
   Урон должен вызывать перенос только при фактическом прохождении; one_heart
   повышает риск смерти, silent_world не скрывает визуальную подсказку.
8. Сохранить мир с активным heartbeat, полностью остановить и запустить JVM:
   активность сохраняется, фаза начинается заново. Завершить через
   `/fdc activate no_sprint` при офлайн-игроке, сохранить/перезапустить и вернуть
   игрока: старые пакеты/подсказка не действуют, потерянные HP/предметы не возвращаются.

Команды `/fdc stop` нет. Ручные пункты не объявлены пройденными автоматикой.
