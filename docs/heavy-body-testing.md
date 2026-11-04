# heavy_body — проверка «На массе»

ТЗ: `Испытания/heavy_body.json`, SHA-256
`ccca1b7f70c5e865c7bba410e1723a38573512456e4e4f8942af818a25214b40`.
Дата реализации: 2026-10-27. EASY / GLOBAL. Тексты взяты из ТЗ.

## Реализация

- Общий Mixin `HeavyBodyLivingEntityMixin` умножает результат наземной ветки
  `LivingEntity.getMovementSpeed(float)` и локальный импульс `LivingEntity.jump`
  на 0,6. Импульс уже включает атрибут прыжка, блок под ногами и Jump Boost.
  Внешние атрибуты/эффекты не записываются, скорость/позиция при завершении
  не восстанавливаются. Воздушное управление, жидкости, лазание, транспорт
  и полёт пропускаются. Блокировка no_jumping остаётся на HEAD прыжка.
- Клиент сообщает только изменение наличия итогового горизонтального ввода,
  после inversion/movement_memory/blocked_direction. `HeavyBodyPayload`
  передаёт C2S намерение и S2C activationSerial после обычного sync_state.
  Повторная синхронизация той же активации не сбрасывает ввод.
- Сервер считает собственные тики при наличии ввода и фактического перемещения
  по горизонтали строго быстрее 0,02 блока/тик на земле. Толчок/инерция без
  ввода, жидкость, транспорт, лазание и полёт не накапливают время.
  До 100 тиков отдых сбрасывает накопление; после порога нужны 40 тиков отдыха.
  Звук начинается на сотом тике и повторяется не чаще одного раза в 80 тиков.
- Сервер посылает звук только самому игроку, громкость 0,35. Собственный
  `cursecycle:heavy_body_pant` ссылается на ванильный `entity.player.breath`:
  остановка по этому ID не прекращает чужие звуки. Это штатный звуковой путь,
  пригодный для будущего silent_world; само silent_world здесь не реализуется.
- Сеансы изолированы по серверу, UUID, объекту игрока, миру и activationSerial.
  JOIN/onStart, респаун, смена измерения, выход, деактивация, disabled и смерть
  очищают соответствующее временное состояние; SERVER_STOPPED освобождает карту.
  Таймер и намерение не сохраняются в NBT, активность сохраняется общей системой.

## Исторические результаты реализации (2026-10-27)

Все команды выполнялись с Java 21 и готовым кэшем, зависимости не менялись.
Песочница не могла открыть wrapper zip.lck в `F:\Programs\GradleCache`;
последующие команды получили разрешённый доступ к существующему кэшу.
Lock-файлы не удалялись.

| Команда | Результат |
|---|---|
| `.\gradlew.bat test compileGametestJava --offline` | BUILD SUCCESSFUL, 13s; 115 JUnit, failures/errors/skipped = 0 |
| `.\gradlew.bat build runGametest --offline` | Сборка прошла; 46/47 GameTest, включая оба heavy_body. Существующий BitterExperienceGameTest:253 увидел два алмазных дропа вместо одного |
| `.\gradlew.bat -I build/heavy-body-clean.init.gradle build runGametest --offline` | BUILD SUCCESSFUL, 58s; все 47/47 GameTest без ошибок в отдельном каталоге мира |
| `.\gradlew.bat -I build/heavy-body-client-probe/init.gradle runClient --offline` | BUILD SUCCESSFUL, 26s; HEAVY_BODY_CLIENT_PROBE_PASSED |
| `git diff --check`, `git diff --cached --check` | Без ошибок |

Первый сбой общего GameTest не повторился на отдельном мире
`build/heavy-body-clean-gametest`. Это указывает на загрязнение сохранённого
мира дропом; исходный тест и его механика не изменялись. Логи обоих прогонов:
`build/heavy-body-verification.log`, `build/heavy-body-clean-verification.log`.
XML: `build/test-results/test/TEST-*.xml`, `build/reports/gametest.xml`.

Добавлены четыре JUnit и два GameTest:

- Точная регистрация, EASY/GLOBAL, единственный ID и краткий текст.
- Пороги 99/100, 79/80, 39/40, сброс до порога, короткий отдых после порога.
  Клиентские переходы ввода, 500 повторных обновлений без спама, новый игрок,
  новый мир, новая активация и reset.
- Настоящие `travel` и `jump` с внедрённым Mixin: дистанция за 100 шагов
  физики и начальный импульс равны 60% контроля, в том числе при спринте,
  Speed II и Jump Boost II. Тест удерживает одинаковую опору, сохраняя
  горизонтальную скорость между шагами; это не живая пробежка по трассе.
- Creative/Spectator/Adventure/Survival; настоящая вода и полёт, воздушное
  управление, посадка в лодку; idempotent callbacks, PERSONAL, дубли и disabled.
  Пары с no_jumping/no_sprint, сохранение внешних эффектов после замены.
- Поздний вход, повторный UUID, смерть/новый объект респауна, Nether/Overworld,
  настоящее сохранение/чтение CurseCycleState с диска, вход после окончания.
- 320 настоящих серверных тиков: два игрока, сброс движения перед порогом,
  движение ниже порога, толчок без намерения, независимый старт, 80 тиков между
  звуками, прекращение на 40-м тике отдыха и завершение. Повторный tick в том же
  тике не удваивает результат. C2S кодируется и передаётся серверной функции;
  S2C PlaySound/StopSound читаются из настоящего EmbeddedChannel, звук проходит
  codec с прямой RegistryEntry. Живой сетевой клиент в GameTest не подключён.

Исторический CLIENT probe находился только в игнорируемом `build/heavy-body-client-probe`.
Он загружает настоящие CLIENT-преобразования и вызывает настоящий
`getMovementSpeed(float)`: 16 комбинаций клавиш и 9 исключений, GUI,
blocked_direction, movement_memory и reset. Мир и методы тестового игрока
подменены; реальные KeyboardInput и общие Mixin работают через Fabric.
Игрового мира, окна и воспроизведения звука нет. Первый запуск стенда потребовал
добавить Bootstrap.initialize перед созданием тестового ClientWorld;
продуктовый код для этого не менялся. Стенд не включён в игровой JAR.

## Ручные сценарии, ещё не выполненные

1. В живом Survival/Adventure измерить трассу за 100 тиков, начальную Y-скорость
   прыжка, затем 100 тиков ходьбы и 40 тиков отдыха. Прослушать громкость,
   короткую длительность и остановку отдышки; проверить отсутствие изменения
   модели, хитбокса, здоровья и дополнительного расхода голода.
2. Сравнить те же условия со Speed II/Jump Boost, спринтом и приседанием;
   проверить воду/лаву, лестницы/лианы, лодку/вагонетку/лошадь и элитры.
   Инерция и толчки при отпущенном вводе не должны запускать отдышку.
3. Проверить реальные HUD, меню правил и `/fdc list`, `/fdc activate heavy_body`,
   `/fdc active`, включая естественный цикл и оба порядка двойной активации.
   Завершать активацией другого испытания; команды `/fdc stop` нет.
4. Два реальных клиента и поздний вход: независимые таймеры, локальный звук,
   смерть при обоих keepInventory, GUI, выход/вход, настоящие порталы и End,
   сброс накопления при респауне/измерении/входе, смена четырёх режимов.
5. Полный рестарт JVM с активным испытанием; окончание при офлайн-игроке,
   второй рестарт и возврат. GameTest проверил диск, но не полный рестарт.
6. TPS/пауза/сон/time, реальная задержка/доставка C2S и отсутствие звукового
   спама. Отсчёт использует серверные тики; RTT может задержать распознавание
   начала/остановки намерения. Silent_world проверить после его реализации.

Сборка, headless GameTest и CLIENT probe не заменяют эти проверки.


## Ревью этапа 21 — 2026-11-05

Входной production HEAD: `b88577b8ddcea32ac8cc68539b2934b68b48e30f`, main.
JSON/SHA прежние. Подтверждённых дефектов механики не найдено; production,
общие контракты и сборка не менялись. Результат `pass_with_limits`, механика
`pass`, расходы `no_issue_found / static`. Старые числа выше — история.

Свежие проверки:

| Команда | Результат и артефакты |
|---|---|
| `.\gradlew.bat build runGametest --offline -I build/heavy-body-review-baseline.init.gradle` | exit 0, 42s; 149 JUnit/54 suites, 2/2 heavy GameTest; `build/heavy-body-review-baseline.log`, `build/reports/heavy-body-review-baseline.xml`, `-baseline-junit/` |
| Та же команда с `build/heavy-body-review-expanded.init.gradle` | exit 0, 34s; 149 JUnit, 4/4 heavy GameTest; одноимённые expanded log/XML/JUnit |
| Та же команда с `build/heavy-body-review-final.init.gradle` | exit 0, 44s; 149 JUnit, 9/9 GameTest (4 heavy + 5 movement_memory), штатное завершение сервера; одноимённые final log/XML/JUnit |
| `.\gradlew.bat runClient --offline -I src/clientprobe/heavy_body/init.gradle` | attempts 1/2: exit 1, 27s/43s; ошибки новой fixture: inherited client shadow и неинициализированный sprint DataTracker. Исправлены только accessors fixture. Attempt 3: exit 0, 25s, HEAVY_BODY_REVIEW_CLIENT_PASSED, 15522 checks / 3075 real input ticks; `build/heavy-body-review-client-attempt1..3.log` |
| `.\gradlew.bat processGametestResources --offline` | exit 0, 2s; восстановлены все 43 стандартных entrypoints, source == built; `build/heavy-body-review-restore.log` |
| `python build/heavy-body-review-verify.py` | exit 0, HEAVY_BODY_REVIEW_VERIFIED; проверяет SHA5/metadata6/parameters7/registration1/Mixin/resource, все свежие XML/JUnit, CLIENT marker и два production JAR без тестов/probe |

Init scripts выбирают entrypoints через временные ресурсы, форсируют свежий
JUnit XML и задают отдельные `build/gametest-heavy-body-review-<phase>-20261105`.
Их повторное воспроизведение — скопировать baseline init для нужной фазы,
заменить её имя и список entrypoints (final: HeavyBodyGameTest и
MovementMemoryGameTest); стандартный build.gradle не изменён.

T1 `realPhysicsEffectsExclusionsAndLifecycle`: настоящие SERVER `travel`/`jump`,
100 шагов с одной опорой/накопленной горизонтальной velocity, ×.6 с внешними
Speed II/Jump Boost II и спринтом, вода/воздух/flight/boat, режимы, no_jumping,
no_sprint, PERSONAL/duplicate/disabled, late/rejoin/respawn/Nether/disk.
Серверный fixture разрешает логическую симуляцию движения; это не пробежка
реального клиента по трассе. `/fdc list/activate/active` исполняются dispatcher
от server source, а не обычным сетевым OP2-клиентом.

T2 `realTicksInputIndependentPlayersAndSoundCleanup`: 320 настоящих server ticks,
100/40/80 и independent2UUID, .019 исключён/.04 принят, толчок без intent,
покой перед порогом, повторный tick, повторные текущие C2S/устаревший serial,
скачок world time не меняет clock. Sound/StopSound получают из EmbeddedChannel;
codec/volume .35 проверены. C2S codec вызывает receive напрямую, не реальную сеть.

T3 `sessionsScopesLifecycleAndDiskNeverReplayBreathing`: actual PERSONAL S2C
только владельцу; 32 serial/duplicate/start/join/disable tick/end/no_sprint,
карта не превышает online UUID и освобождает пустой server bucket; Creative,
Nether/Overworld, respawn, настоящий Fabric DISCONNECT, actual playerdata и
PersistentStateManager save/read/set active/ended/offline-end/empty-server.
Старые intent/breathing не восстанавливаются; foreign named diamond17/XP7+.625/
HP17/FireResistance/world data сохраняются. Одна JVM, без полного рестарта.

T4 `realJumpGravityAndAutoJumpKeepIndependentPhysics`: реальный обычный прыжок
×.6 при gravity; при снятии heavy gravity travel остаётся, при снятии gravity
heavy air ветка ванильная. AutoJump.tick вызывает настоящий jump один раз
с ×.6; после снятия heavy автоматический jump обычный, после снятия auto
остаётся heavy manual jump. Silent pair не снимает тяжёлую физику.
Самостоятельные ревью этих парных испытаний не выполнялись.

Сохранённый CLIENT probe в `src/clientprobe/heavy_body/` имеет только preLaunch,
без test Mixin и подмены production gates. Он исполняет transformed
KeyboardInput.tick и private LivingEntity.getMovementSpeed(float): 128 masks ×
3 scales × inversion2 × block4; исключения, Adventure/air/GUI, real memory
release/end, silent pair и оба окончания. Silent listener проверен вызовом
реального injected handler (reflection), без OpenAL/воспроизведения; это не
полный SoundSystem pipeline. CLIENT jump handler загружается, actual jump
исполняется SERVER GameTest. 32 reset/context цикла × 500 повторных sync
проверяют отсутствие лишних intent и освобождение refs. Intent.report получает
локальный capture Consumer, а не ClientPlayNetworking.send. ClientDataHolder
sync/receive ручные, client/world/player/options/manager созданы через Unsafe,
accessor conditions/sprint флаг искусственные. Нет окна, live network,
клиентского travel, options-файла и прослушивания звука.

Накладные расходы: common hooks на каждом getMovementSpeed/jump для living
сначала делают instanceof PlayerEntity; server gate O(G+A), client gate O(K).
Отдельный server tick делает O((S+P)×(G+A)), включая неактивный O(P×(G+A));
S — retained sessions, P — online players, G/A — global/personal IDs, K —
synced IDs. Session ≤ один per UUID/server; refs освобождают stop/join/respawn/
disconnect/context-invalid/disabled tick/SERVER_STOPPED. Map/tick/Vec3d,
shared dispatch/sync, vanilla travel и packet machinery имеют стоимость.
Intent штатно шлётся при смене boolean/context; C2S receive O(G+A) на пакет,
не создаёт задач/ответов на каждый пакет и не продлевает таймер. Собственного
hard rate cap нет; звук ≤1/80 ticks после100, stop адресован только own ID.
FPS/MSPT/allocations/retained memory/load profile не измерялись.

Служебные попытки: sandbox-bin ACL access5 не создала процесс; разрешённый
escalated exec работал, locks не удалялись. Неверные предположения о read paths
исправлены по rg. Один Python stdin snapshot потерял кириллицу (OSError),
снимок сделан Copy-Item; нужные UTF-8 файлы прочитаны. Первый verifier остановился
на неуспешном CLIENT attempt2, не был объявлен успехом; лог сохранён в
`build/heavy-body-review-verifier-attempt1.log`. Итоговый verifier прошёл.

Открытые ограничения `heavy_body-L01..L04` (точные условия в отчёте21):
обычный клиент/physical travel/звук/HUD/menu/OP2/lifecycle и среды;
настоящие два клиента/late третьего/RTT/TPS/pause/intent delivery;
полный новый JVM active/ended/offline-end/empty-server;
сравнительный FPS/MSPT/memory/packet профиль в одинаковых условиях.
Все старые ограничения других испытаний сохранены, shared contracts не
менялись, stale/recheck не назначены. Автоматика не закрывает эти сценарии.
