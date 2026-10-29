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

## Автоматические проверки

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
   crafting_tax и offset_crosshair. Проверку heartbeat выполнить, когда он будет
   реализован; это испытание не входит в текущую работу.
6. Необязательно: два настоящих клиента на выделенном сервере; один выходит,
   второй продолжает независимо. Автоматические EmbeddedChannel не заменяют
   этот сценарий.
