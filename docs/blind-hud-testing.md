# blind_hud — «На ощупь»

## Реализация

EASY/GLOBAL, название и оба описания совпадают с актуальным JSON.
`BlindHudInGameHudMixin` зарегистрирован только в client-разделе. В Minecraft
1.21.1 `renderStatusBars` рисует личные сердца (включая поглощение, poison,
wither, frozen и hardcore), броню, еду и воздух. Отмена этого метода не затрагивает
отдельные `renderMountHealth`, `renderMountJumpBar` и `renderHotbar`.
`renderExperienceBar` и `renderExperienceLevel` отменяются отдельно.
Общий `render`/`renderMainHud` и отрисовка остальных элементов не перехватываются.

`BlindHud.shouldApply` читает текущие synced effective ID, world/player/manager,
режим Survival/Adventure и живого неудалённого игрока. Нет снимков ресурсов,
настроек, ссылок на старый объект, таймеров, пакетов или собственного NBT.
Сохранение активности и GLOBAL/PERSONAL/double используют существующую систему;
JOIN/DISCONNECT сбрасывают `ClientDataHolder`. Восстановление рендера всегда
использует актуальные значения, F1 и остальные пользовательские настройки.

## Исторические проверки реализации (не доказательства ревью 28)

```powershell
.\gradlew.bat -I src/clientprobe/blind_hud/init.gradle test compileGametestJava runClient --offline
.\gradlew.bat -I src/clientprobe/blind_hud/init.gradle runClient --offline
.\gradlew.bat -I src/clientprobe/blind_hud/init.gradle build runGametest --offline
git diff --check
git diff --cached --check
```

Init-script помещает CLIENT probe в отдельный JAR и задаёт отдельный GameTest
runDir `build/blind-hud-gametest`. Проверочные классы не входят в игровые JAR.
Существующий Gradle-кэш `F:\Programs\GradleCache` требует доступа вне песочницы;
первый запуск остановился на `gradle-8.10.2-bin.zip.lck` с отказом доступа.
Lock-файлы не удалялись, зависимости не менялись.

- JUnit: 134 теста, failures/errors/skipped = 0. Два новых проверяют регистрацию,
  точные метаданные и gate при сменах ID/режима/жизни/синхронизации.
- CLIENT: `BLIND_HUD_CLIENT_PROBE_PASSED checks=270`, `BUILD SUCCESSFUL`.
  Исполняются настоящие transformed `renderStatusBars`, `renderExperienceBar`,
  `renderExperienceLevel`. DrawContext перехватывает texture/text draw;
  отдельный probe-only Mixin убирает только GL blend side effects.
  Сравнение с контролем: четыре режима; poison/wither/regeneration/hunger,
  frozen/hardcore; поглощение, неполные броня/голод/воздух/XP. Все личные draw
  исчезают в Survival/Adventure, в исключённых режимах вывод равен контролю.
  Положительные контроли отдельно подтверждают сердца, absorption, броню,
  голод, воздух, полосу и текст XP. После изменения ресурсов и завершения
  рисуются новые значения. Проверены смерть, новый player/world, removal,
  отсутствующие контексты, reset и завершение офлайн; F1-флаг не меняется.
  Настоящие `renderMountHealth` и `renderMountJumpBar` имеют непустой контроль
  и идентичный вывод при активном испытании; личные ресурсы на транспорте скрыты.
  ASM exported InGameHud подтверждает ровно три вызова production-фильтра,
  исключительно в выбранных методах.
- Три новых GameTest: реальные effective S2C для двух игроков и late join,
  GLOBAL/PERSONAL, дубли, disabled, режимы, смерть/респаун, три измерения,
  reconnect с тем же UUID, double с no_sprint, сохранение/чтение active/ended
  состояния с диска и завершение офлайн. `/fdc list`, `/fdc activate blind_hud`,
  `/fdc active` выполнены через dispatcher с OP 2. Отдельный тест подтверждает
  настоящую потерю HP от урона, пополнение еды и получение XP; vanilla
  HealthUpdateS2CPacket и ExperienceBarUpdateS2CPacket доходят до EmbeddedChannel
  с текущими значениями. Завершение не возвращает ресурсы из снимка.
- Итоговый полный GameTest: 64/64, failures = 0; `BUILD SUCCESSFUL`.
  `git diff --check` и `git diff --cached --check` без ошибок. В binary и sources
  JAR присутствуют только три production-класса blind_hud; validation/Probe нет.

Первый полный GameTest-прогон: 63/64; ошибочное утверждение нового теста
путало Yarn-getters XP-пакета. Локальный байткод конструктора/ServerPlayer/
ClientPlayNetworkHandler подтвердил: getExperienceLevel() возвращает total XP,
getExperience() — уровень. Исправлены только утверждения теста, проверяются
оба поля. Production-код из-за этого сбоя не менялся.

Первые два CLIENT-прогона обнаружили ошибки только Unsafe-fixture: обращение к
неинициализированному унаследованному client и вызов setRemoved без passenger
list. Исправлены fixture и установка removalReason для проверки vanilla isRemoved;
production gate/рендер в стенде не подменяются.

Логи: `build/blind-hud-client-probe.log`, `build/blind-hud-verification.log`.
JUnit XML: `build/test-results/test/TEST-*.xml`; GameTest: `build/reports/gametest.xml`.
Локальный байткод для выбора методов: `build/blind-hud-InGameHud.javap.txt`.

## Ограничения и оставшиеся ручные проверки

CLIENT стенд работает в preLaunch с фиктивными client/world/player/options/font;
OpenGL, полный HUD кадр и реальное подключение клиента не выполняются.
Переключение F1 проверено как неизменность флага, а полный layered render при F1
остаётся ручным сценарием. Чтение NBT с диска не является полным restart JVM.
Совместные ID gills/one_heart/crafting_tax/offset_crosshair в CLIENT-стенде проверяют
gate; полный одновременный геймплей этих испытаний здесь не запускался.

1. В живом Survival с бронёй, absorption, эффектом, неполными ресурсами и XP:
   `/fdc activate blind_hud`; получить урон, поесть, подобрать XP, погрузиться
   в воду и выйти на сушу. Не должно быть личных ресурсов или заменяющих подсказок.
2. Проверить хотбар/выбор/число предметов/offhand, прицел и оба варианта attack
   indicator, чат, эффекты, bossbar, HUD испытаний, меню H, инвентарь/контейнеры,
   экран смерти. На лошади здоровье и прыжок остаются видимыми.
3. Переключить F1, размер окна/GUI scale; завершить через `/fdc activate no_sprint`
   при F1, затем вернуть HUD. Должны показаться актуальные значения; options.txt
   должен сохранить пользовательские настройки. Проверить Creative/Spectator,
   возврат в Survival и Adventure.
4. Смерть/респаун, порталы, disconnect/reconnect и полный перезапуск JVM в активном
   состоянии; затем окончание офлайн и вход. Проверить обычные drop/keepInventory.
5. Проверить double с no_sprint и по очереди gills (воздух на суше), one_heart,
   crafting_tax, offset_crosshair и heartbeat. Проверить обе механики и оба
   окончания; полный совместный геймплей остаётся ручной проверкой.
6. Необязательно: два настоящих клиента на выделенном сервере; один выходит,
   второй продолжает независимо. Автоматические EmbeddedChannel не заменяют
   этот сценарий.

## Свежие проверки ревью 28 — 2026-11-06

Входной production HEAD `b0febf3ffa436e63e8c67257d53c3f5deeadf6d8`.
Production/shared contracts не менялись. Старые числа выше — история реализации.

```powershell
.\gradlew.bat build runGametest --offline -I build/blind-hud-review-baseline.init.gradle
.\gradlew.bat runClient --offline -I src/clientprobe/blind_hud/init.gradle
.\gradlew.bat build runGametest --offline -I build/blind-hud-review-final.init.gradle
.\gradlew.bat processGametestResources --offline
python build/blind-hud-review-verify.py
```

Review init ограничивает metadata одним `BlindHudGameTest`, задаёт отдельные
JUnit/XML и свежие runDir; production/classpath не подменяются. Baseline runDir:
`build/gametest-blind-hud-review-baseline-20261106`; финальный:
`build/gametest-blind-hud-review-final-attempt3-20261106`.
После проверок обычный processGametestResources восстанавливает 43 entrypoints.

- Baseline build/GameTest exit 0/35 s: 149 JUnit, 54 suites, 3/3 предметных
  GameTest. Baseline CLIENT exit 0/27 s: 270 checks.
- Expanded CLIENT attempt1 exit 1/2 s: отсутствующий `AttackIndicator.CODEC`
  в новом fixture. Исправлен только тестовый codec. Attempt2 exit 0/24 s:
  2044 checks/5615 calls. Добавлены настоящие F1 predicates; final exit 0/29 s:
  **2053 checks/5615 render calls**.
- Первый expanded build/GameTest exit 1/50 s: JUnit 149/149, GameTest 1/4.
  Новый assertion сравнивал обычный `CurseCycleSyncPayload` по object identity.
  Исправлен только assertion: все шесть decoded полей + полное потребление
  buffer. Неуспешные log/XML/JUnit сохранены с суффиксом `final-attempt1`.
- Второй expanded build/GameTest exit 1/45 s: JUnit149, GameTest3/4.
  Absorption fixture ванильно ограничивался нулём без max absorption эффекта.
  Добавлены AbsorptionII и positive assertion перед активацией. Production
  не менялся; log/XML/JUnit сохранены с суффиксом `final-attempt2`.
- Финальный build/GameTest exit 0/33 s: **149 JUnit/54 suites +4/4 GameTest**,
  failures/errors/skipped0, normal shutdown. Полный локальный отчёт:
  `Промпты/reviews/28-blind_hud.md`.

GameTest T1: настоящий damage, HungerManager.add, addExperience, playerTick,
health/food/XP пакеты с текущими значениями доходят до EmbeddedChannel;
конец не откатывает ресурсы. Физическое употребление еды не моделируется.
T2: actual S2C+codec двух и late третьего, modes/respawn/три измерения/sameUUID,
no_sprint во время/после, три OP2 dispatcher commands, настоящий
PersistentStateManager.save/readNbt/createFromNbt/set active/ended/offline end.
T3: PERSONAL owner/other, GLOBAL+PERSONAL dedup, disabled/enabled.
T4: 32 actual serial activation/sameID/double/end/no_sprint; inventory17/HP17/
XP7/food11/absorption5 не меняются, нет собственного NBT. Actual Fabric
DISCONNECT invoker, empty server start/end, sameUUID join получает текущие ID.
Каждый S2C assert теперь проверяет действительный codec всех полей.

CLIENT сохраняет исходную матрицу resource render (четыре режима, эффекты,
frozen/hardcore/absorption/неполные ресурсы), позитивные draw-контроли,
изменение ресурсов/stop/death/new/null/removed/reset и transport health/jump.
ASM подтверждает ровно три injection call только в ресурсных методах.
Добавлен полный настоящий nondebug renderCrosshair с partial attack indicator:
размеры 640×360, 101×73, 320×180, 15×15; control/blind/both/offset, независимые
+80/+40/clamp координаты, одинаковые texture counts, nonidentity matrix/stack
restore и оба окончания пары. `HeartbeatInput.receive` + настоящий
`HeartbeatOverlay.render`: grace/beat/accepted/F1 и оба окончания, rhythm draw
сохраняется с blind_hud. 32 reset × 50 duplicate sync проверяют идемпотентность
и latest F1. Оба настоящих vanilla layer F1 predicates исполняются active/inactive.

Пределы CLIENT: preLaunch без окна, Unsafe client/world/player/options/font/
manager/hud/horse/debug и overrides ресурсных/mode/alive accessors;
recording DrawContext, GL blend отменён только test-only Mixin отдельного JAR.
Sync/reset и heartbeat samples ручные, overlay вызван напрямую; полный
GameRenderer/receiver/frame/OpenGL/options.txt/input/server rhythm не проверены.
Labels фиксируются без проверки перевода. Disk reload — одна JVM.

Логи: `build/blind-hud-review-{baseline,final-attempt1,final-attempt2,final}.log`,
`build/blind-hud-review-client-{baseline,final-attempt1,final-attempt2,final}.log`.
XML/JUnit: `build/reports/blind-hud-review-<phase>.xml`, `-<phase>-junit/`.
Свежий javap: `build/blind-hud-review-{InGameHud,GameRenderer}.javap.txt`.
Verifier/restore: `build/blind-hud-review-{verifier,restore}.log`.

## Статическая стоимость и оставшиеся ограничения ревью

До трёх gates на кадр: O(K) contains по synced IDs, cancel O(1).
Inactive тоже выполняет injected callback/context/mode/alive checks;
Cancellable CallbackInfo имеет цену. Нет своих maps/timers/packet/save/NBT/
player-world refs или сканирования entities/chunks. Active пропускает vanilla
resource draw; ускорение не измерялось. Shared tick/event dispatch, 20 tick
sync/history/HUD и vanilla render/network имеют отдельную цену.
FPS/MSPT/allocations/retained memory не измерены, оценка **static**.

Открытые проверки: обычный клиент/full HUD/menu/wheel/F1/resize/GUIscale/
options.txt/physical food-XP-water/death-drop-bothkeepInventory/portals/OP2 тексты
и одна нотификация; обе механики и оба окончания no_sprint/gills/one_heart/
crafting_tax/offset_crosshair/heartbeat. Dedicated+2Survival+late3, реальные
receiver/reset/network/server switch/latency-lowTPS/pause. Новый JVM active/
ended/offline end/emptyserver/sameUUID с сохранными чужими ресурсами/options.
Профиль при одинаковых версиях/мире/P/K/H/window/drawrate/warmup/duration,
inactive-active-double-PERSONAL/32start-stop-reconnect, стоимость gate отдельно
от shared HUD/history/sync и vanilla. Точные шаги и L01–L04 — в отчёте 28.
