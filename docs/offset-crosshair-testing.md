# offset_crosshair — проверка реализации

«Конфига симпла», MEDIUM/GLOBAL. С 2026-11-20 по запросу пользователя
смещается и игровой прицел: добыча, удары и выстрелы направлены в отметку
справа и снизу. Исходное JSON-ТЗ описывает прежнюю ложную отметку;
текущий код и `docs/challenges.md` отражают новое поведение.

## Реализация

Client-only `OffsetCrosshairInGameHudMixin` оборачивает только ванильный
`InGameHud.renderCrosshair`. На время вызова матрица `DrawContext` получает
перенос +80/+40 единиц текущего GUI. Координата центра ограничена отступом 8
по каждой оси; при размере меньше 16 смещение этой оси равно нулю. Используется
тот же целочисленный центр, что у ванильных прицела и индикаторов.

Переносятся стандартная текстура и все её индикаторы атаки, включая полный
и заполняющийся. Ванильный 3D-прицел сам умножает эту матрицу на model-view
перед своей камерной ротацией, поэтому смещение остаётся экранным. Матрица
восстанавливается в `finally`, в том числе при раннем возврате или исключении.
Решения ванили о видимости/перспективе/отладке и выборе индикатора сохранены.
HOTBAR-индикатор не связан с прицелом и остаётся в своём месте.

`OffsetCrosshair.shouldApply` каждый раз проверяет текущие synced ID, мир,
живого неудалённого игрока и Survival/Adventure.

`OffsetCrosshairGameRendererMixin` согласует игровой прицел с отметкой через
off-axis проекцию: перед проекционной матрицей умножается перенос в clip space.
Перенос одинаков для всех расстояний и FOV, поэтому ванильный луч выбора
блоков/сущностей и направление стрелы отображаются под смещённой отметкой.
В центре экрана теперь другая часть мира; для прицеливания надо подвести цель
к отметке справа/снизу. Это меняет экранное положение сцены и руки.
Настройки FOV и yaw/pitch игрока, серверные пакеты действий и raycast сохранены.
Разброс стрел, гравитация и прочая баллистика остаются ванильными.

Перехватываются ровно три вызова `getBasicProjectionMatrix`: два в `renderWorld`
(сцена и frustum отсечения) и один в `renderHand`. Меню, панорама и GUI-проекция
не затрагиваются. Для мира и руки используются актуальные scaledWidth/Height
окна, те же +80/+40 и clamp, что для HUD. На нечётном размере учтён
целочисленный центр отметки; при нулевом размере перенос проекции пропускается.
Перенос применяется слева, а roll `upside_down_camera` справа: смещение
остаётся экранным при обоих порядках Mixin. `tiny_fov` сохраняет свой FOV.

Нет сохранённых настроек, размеров окна, ссылок на игрока, новых пакетов,
таймеров и собственного NBT. Активные GLOBAL/PERSONAL ID сохраняет и
синхронизирует существующая система; JOIN/DISCONNECT сбрасывают
`ClientDataHolder`. Снятие испытания сразу возвращает обычную проекцию.
В третьем лице сохраняются ванильные видимость прицела и параллакс камеры.

Сигнатура проверена по локальному байткоду Minecraft 1.21.1 и
[Yarn 1.21.1+build.3](https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/client/gui/hud/InGameHud.html#renderCrosshair(net.minecraft.client.gui.DrawContext,net.minecraft.client.render.RenderTickCounter)).

## Проверки изменения игрового прицела — 2026-11-20

- `.\gradlew.bat build --offline --console=plain` — BUILD SUCCESSFUL,
  220 JUnit, failures/errors/skipped = 0. Новая проверка проецирует луч действий
  на отметку и восстанавливает его обратной проекцией; центр экрана указывает
  в другую сторону. Размеры GUI: 640×360, 321×181, 160×100, 17×16 и 15×9;
  FOV 15/70/110, roll 0/180, расстояния 1/4.5/32/1000. Проверены clamp,
  нечётный центр, неизменная глубина и нулевые размеры окна.
- `.\gradlew.bat runClient --offline --console=plain -I src/clientprobe/offset_crosshair/init.gradle`
  — BUILD SUCCESSFUL;
  `OFFSET_CROSSHAIR_CLIENT_PROBE_PASSED comparisons=3131 checks=16241`.
  Сохранены прежние проверки настоящего transformed `renderCrosshair`.
  Новый стенд проверяет три места внедрения в transformed `GameRenderer`,
  вызывает настоящий merged handler поверх ванильной матрицы и проверяет
  экранную точку луча для той же сетки размеров/FOV/roll/дистанций. Проверены
  отключение, режимы, смерть, panorama, reset и минимизированное окно.
  Лог: `build/offset-crosshair-aim-client-probe.log`.
- Свежий локальный javap подтверждает цепочку vanilla projection → roll →
  offset для мира, frustum и руки; остальные методы не вызывают offset handler.
  Вывод: `build/offset-crosshair-aim-transformed.javap.txt`.

Это геометрия и клиентские fixtures без полного OpenGL-кадра. Реальная добыча
и стрельба, полный кадр с bob/hurt, параллакс третьего лица и два настоящих
сетевых клиента вручную не проверялись. Новые серверные хуки отсутствуют;
серверные GameTest в этой задаче не запускались. Исторические результаты ниже
не считаются свежими проверками. Добавленная стоимость: gate O(K) и один
перенос матрицы O(1) на каждый из трёх вызовов проекции; замеров FPS нет.

## Исторические проверки реализации (не новое ревью)

Результаты до 2026-11-20 относятся к прежней визуальной механике и не доказывают
новую off-axis проекцию. Ниже они сохранены как история.

- 132 JUnit, failures/errors/skipped = 0. Три новых теста проверяют регистрацию,
  точный текст, double, текущую активность, режимы/жизнь/reset и границы координат
  для всех размеров 0–4000.
- `.\gradlew.bat -I build/offset-crosshair-clean.init.gradle build runGametest --offline`
  — BUILD SUCCESSFUL; 132 JUnit без ошибок, 61/61 GameTest, штатный выход.
  Init меняет только `loom.runs.gametest.runDir` на
  `build/offset-crosshair-final-gametest` для изоляции от старого мира.
- Два новых серверных GameTest: реальные S2C пакеты двум игрокам и late join,
  все режимы, смерть/респаун, Nether/End/Overworld, reconnect с тем же UUID,
  double с no_sprint и снятие только нового испытания, реальная запись и чтение
  активного/завершённого состояния с диска, завершение офлайн, PERSONAL,
  дубли/disabled и команды `/fdc list`, `/fdc activate offset_crosshair`,
  `/fdc active` с уровнем разрешений 2. Серверный тест не проверяет изображение.
- `.\gradlew.bat -I src/clientprobe/offset_crosshair/init.gradle runClient --offline`
  — BUILD SUCCESSFUL; `OFFSET_CROSSHAIR_CLIENT_PROBE_PASSED comparisons=1443`.
  Стенд вызывает целиком настоящий преобразованный `renderCrosshair`,
  не подменяет production gate/обёртку. Проверяет экспортированный байткод:
  ровно один вызов production-обёртки, только внутри `renderCrosshair`.
  Сравнивает текстуры, их число и координаты без/с активацией: три перспективы,
  четыре режима, обычный/3D-прицел, все варианты AttackIndicator, частичная/полная
  перезарядка и пять размеров GUI, включая нечётные и меньше 16.
  Для 3D-прицела проверяется настоящая model-view матрица при yaw=73/pitch=-42;
  после каждого вызова обе матрицы восстановлены. Отдельно проверены смерть,
  удаление/замена игрока, замена/отсутствие мира, отсутствие player/manager,
  reset, завершение офлайн, неизменные yaw/pitch и вектор взгляда, а также
  восстановление матрицы после намеренной ошибки отрисовки.
  Клиент/игрок/мир/options — fixtures; текстурные вызовы перехватываются
  DrawContext-приёмником. Только отдельный probe JAR заменяет GL blend/apply
  и отрисовку 3D-линий; production рендер их не заменяет. Это не OpenGL/скриншот.
  Совместные ID tiny_fov/upside_down_camera/inverted_mouse проверяют gate;
  настоящий рендер сцены и реальный ввод этих испытаний не запускались.
  Исходники/init находятся в `src/clientprobe/offset_crosshair` и не входят
  в игровые JAR.
- `git diff --check`, `git diff --cached --check` — без ошибок. В игровом JAR
  присутствуют три production-класса OffsetCrosshair; validation/Probe отсутствуют.

Логи: `build/offset-crosshair-compile.log`, `build/offset-crosshair-verification.log`,
`build/offset-crosshair-client-probe.log`; отчёты JUnit: `build/test-results/test`,
GameTest: `build/reports/gametest.xml`. Gradle использует существующий кэш
`F:\Programs\GradleCache` с разрешённым доступом; lock-файлы и зависимости
не менялись. При разработке исправлены неверное ожидаемое число одного JUnit
и неполный Unsafe fixture удаления игрока в стенде.

## Ревью этапа 27 — 2026-11-06

Production-дефектов не обнаружено. Изменены только предметные GameTest,
CLIENT probe и этот документ. Механика `pass`, расходы `no_issue_found / static`,
этап `pass_with_limits`. Отчёт: `Промпты/reviews/27-offset_crosshair.md`.
Исторические числа выше не используются как новое доказательство.

Свежие команды (все exit 0):

```powershell
.\gradlew.bat build runGametest --offline -I build/offset-crosshair-review-baseline.init.gradle
.\gradlew.bat runClient --offline -I src/clientprobe/offset_crosshair/init.gradle
.\gradlew.bat build runGametest --offline -I build/offset-crosshair-review-final.init.gradle
.\gradlew.bat runClient --offline -I src/clientprobe/offset_crosshair/init.gradle
.\gradlew.bat processGametestResources --offline
python build/offset-crosshair-review-verify.py
```

- Baseline build/GT: 36 s, 149 JUnit / 54 suites, 2/2 предметных GameTest.
  Final build/GT: 37 s, 149 JUnit / 54 suites, 3/3 предметных GameTest.
  Failures/errors/skipped = 0; штатное завершение сервера.
  Логи `build/offset-crosshair-review-<phase>.log`, XML
  `build/reports/offset-crosshair-review-<phase>.xml` и `-<phase>-junit/`.
  Init меняет только metadata/resource input, runDir и расположение отчётов;
  не меняет production/classpath. Мир каждой фазы новый:
  `build/gametest-offset-crosshair-review-<phase>-20261106`.
- T1: два embedded игрока и поздний третий; реальные исходящие S2C и codec,
  режимы, respawn, Nether/End/Overworld, same-UUID reconnect; настоящие
  `PersistentStateManager.save/readNbt/createFromNbt/set` active/ended/offline end;
  no_sprint во время пары и после замены; три OP2-команды и регистрация.
  T2: PERSONAL owner/other, GLOBAL, дубли, disabled/enabled.
  Новый T3: 32 цикла новой activationSerial, duplicate-ID, double и снятия;
  инвентарь 17 diamonds, HP 17, XP level 7 и server yaw/pitch 37/-23 сохранны;
  нет собственного settings NBT; настоящий Fabric DISCONNECT invoker,
  start/end при пустом сервере и повторный вход с тем же UUID.
- CLIENT baseline: 31 s, 1443 вызова renderCrosshair. CLIENT final: 25 s,
  `OFFSET_CROSSHAIR_CLIENT_PROBE_PASSED comparisons=3131 checks=15837`.
  Логи `build/offset-crosshair-review-client-<phase>.log`, свежий export
  `build/offset-crosshair-client-probe/run/.mixin.out/`.
  Исполняется полный настоящий transformed `InGameHud.renderCrosshair`
  с production gate; ASM проверяет ровно один вызов обёртки только из него.
  Матрица исходного контекста не единичная; обе матрицы и глубина DrawContext
  восстановлены. Есть намеренное исключение textured draw для `finally`.
  Ожидание координат теперь независимо от `OffsetCrosshair.translation`.
  Сетка: перспективы 3 × режимы 4 × debug 2 × AttackIndicator 3 × cooldown 2
  × GUI sizes 5 (нечётные, clamp и <16); независимо проверены число текстур,
  отсутствие дубля и число 3D-вызовов. Дополнительно reducedDebugInfo игрока
  и options во всех 4 сочетаниях, настоящие два vanilla F1 layer predicates,
  32 reset/resize × 50 duplicate sync и завершение по актуальному размеру.
  Реальные merged roll/FOV handlers + полный renderCrosshair проверены
  для обеих пар и обоих окончаний: FOV 15/80, roll 180/0, HUD +80/+40/0.
  Это вызовы границ handlers, а не полный renderWorld/getFov/кадр пары.
- Свежий javap текущего mapped Minecraft: vanilla 3D model-view умножает
  DrawContext до экранного центра и camera rotation; CROSSHAIR attack textures
  внутри метода, HOTBAR отдельно; vanilla layer predicates читают hudHidden,
  InGameHud.render идёт до Screen.renderWithTooltip.
  Файлы `build/offset-crosshair-review-{InGameHud,GameRenderer}.javap.txt`.
- Обычный `processGametestResources` возвращает 43 исходных entrypoints.
  Verifier сверяет SHA5/metadata6/80-40-8/регистрацию/client Mixin, свежие
  JUnit/GameTest/CLIENT результаты и отсутствие validation/Probe/GameTest
  в production binary и sources JAR.

Пределы: Unsafe MinecraftClient/world/player/options/renderer и текущие
mode/alive/debug/cooldown — fixtures; размер GUI задаёт Context. Текстурные
вызовы записываются Context, GL blend/apply и debug line draw отключены
отдельным probe JAR. Sync/reset вызываются вручную. Нет настоящего окна,
OpenGL, полного LayeredDrawer/HUD/GameRenderer кадра, нажатий F1/F5, GUI scale
через окно, options.txt, настоящей сети или двух процессов клиента. F1 predicates
не доказывают полный F1 frame. Серверный disk reload в одной JVM не full restart.
Пары inverted_mouse/forced cameras/blind_hud имеют только статические границы
и проверки ID/перспектив здесь; их полная совместная игра остаётся ручной.

Историческая оценка стоимости до добавления проекции: gate O(K) по текущим synced ID на вызов renderCrosshair (F кадров
даёт O(F×K)); inactive тоже проверяет контекст/режим/жизнь и вызывает Operation.
Active добавляет два расчёта O(1), push/copy/translate/pop матриц, Object[]
вызова Operation; даже при vanilla early return перспективы эта часть выполнена.
Собственных tick/timer/packet/map/UUID/NBT нет; общий dispatch вызывает пустые
callbacks, sync каждые 20 server ticks/при активации/входе, HUD/history имеют
свою стоимость. FPS/MSPT/allocations/retained memory не измерялись. Счётчики
assertions и повторных вызовов проверяют корректность, не производительность.

Открыты offset_crosshair-L01–L04: ordinary client/render/aim/UI и пары,
настоящий co-op/сеть, полный JVM restart, профиль FPS/MSPT/памяти.
Ручные шаги ниже сохранены и в этом ревью **не выполнялись**.

## Ручные проверки — не выполнены

1. Survival, первое лицо, две мишени в центре и под будущей отметкой:
   `/fdc activate offset_crosshair`. Проверить удар, стрелу, добычу и использование:
   действие/подсветка блока идут в отметку справа/снизу. Подвести туда цель,
   добыть блок и попасть из лука; цель в центре экрана не выбирается.
   Стрела сохраняет ванильные разброс и падение. Изменить окно и GUI
   scale, включая маленькое окно; край центра отметки не ближе 8 единиц GUI.
2. Проверить обычный и F3 3D-прицел, уменьшенную отладку, полный/частичный
   индикатор атаки, HOTBAR/OFF. Нет второго центрального прицела. Проверить F1,
   F5, инвентарь, меню и курсор: видимость следует ванили, новый прицел не
   навязывается. Завершить через `/fdc activate no_sprint`: обычное расположение
   возвращается по актуальным настройкам.
3. Два настоящих клиента и поздний вход: основной сценарий, смерть/респаун,
   reconnect, порталы всех измерений. Повторить в Adventure, Creative, Spectator
   и после возврата в Survival. Серверные embedded игроки не заменяют эту проверку.
4. `/fdc activate "double_challenge:offset_crosshair,no_sprint"`, затем
   `/fdc activate no_sprint`: смещение исчезает, запрет спринта остаётся.
5. Пары с tiny_fov, upside_down_camera, inverted_mouse и обоими
   forced_third_person: смещение остаётся вправо/вниз экрана, когда ваниль
   показывает прицел; мир и управление следуют собственным правилам пары.
6. Сохранить и полностью перезапустить JVM с активным испытанием. Затем завершить
   его, сохранить и перезапустить снова, вернуть офлайн-игрока: устаревшего
   смещения нет. Автоматическое чтение диска не является полным перезапуском JVM.
