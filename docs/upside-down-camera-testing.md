# upside_down_camera — проверка

## Реализация

«С ног на голову», MEDIUM/GLOBAL. Название и оба описания совпадают с JSON.
`UpsideDownGameRendererMixin` находится только в client-разделе Mixin config.
MixinExtras меняет результат `getBasicProjectionMatrix` только внутри
`GameRenderer.renderWorld` и `renderHand`. В Yarn 1.21.1 это три места:
проекция мира, отдельная проекция frustum и независимая проекция рук.
Каждый вызов создаёт новую матрицу; к каждой добавляется один крен 180° по Z.
Frustum при 180° имеет ту же область, его проекция также согласована с креном.

Порядок сцены: `projection * roll * hurt * bob * view`; ванильные эффекты
сохраняются. Матрица вида камеры, shared model-view, yaw/pitch, луч взаимодействия,
перспектива, FOV и пользовательские настройки не меняются. Руки получают свой
единственный крен, без переноса уже повёрнутой матрицы мира. HUD/меню используют
отдельную GUI-проекцию. Panorama исключена.

`UpsideDownCamera` читает текущее synced состояние, мир, живого неудалённого
игрока и режим Survival/Adventure на каждом вызове. Нет сохранённой матрицы,
снимка настроек, ссылки на игрока, таймера, новых пакетов или дополнительного NBT.
Активность сохраняет общий `CurseCycleState`; включённые GLOBAL/PERSONAL ID
приходят существующим sync_state. Reset при JOIN/DISCONNECT уже есть.

## Ревью 2026-11-06 — свежие проверки этапа 26

Production-код и общие контракты не менялись; подтверждённых дефектов не найдено.
Расширены только предметный GameTest и изолированный CLIENT probe.

- `.\gradlew.bat build runGametest --offline -I build/upside-down-camera-review-baseline.init.gradle`
  — exit 0, 1m 1s; 149 JUnit, 2/2 GameTest, штатное завершение сервера.
- `.\gradlew.bat build runGametest --offline -I build/upside-down-camera-review-final.init.gradle`
  — exit 0, 48s; 149 JUnit, 3/3 GameTest, штатное завершение сервера.
  Изоляция: только `UpsideDownCameraGameTest`, отдельные resources/runDir/XML,
  `build/gametest-upside-down-camera-review-<phase>-20261106`,
  `build/reports/upside-down-camera-review-<phase>.xml` и `-<phase>-junit/`.
  Init не меняет production/classpath; после проверки восстановить штатные
  ресурсы через `.\gradlew.bat processGametestResources --offline` (43 entrypoints).
- Существующие lifecycle/scopes/enabled/disk/no_sprint тесты исполнены заново.
  Команды теперь проверяются с OP 2. Реальный S2C payload дополнительно проходит
  CODEC encode/decode с проверкой полного потребления буфера.
  Новый тест выполняет 32 цикла duplicate-ID → double с no_sprint → окончание:
  новый serial, единственный ID, сохранные yaw/pitch/17 предметов/17 HP/7 XP,
  нет собственного camera/settings NBT. Настоящий Fabric DISCONNECT invoker,
  пустой сервер, активный и завершённый same-UUID join. EmbeddedChannel и диск
  одной JVM не заменяют настоящую сеть и полный restart.
- CLIENT команда: `.\gradlew.bat runClient --offline -I src/clientprobe/upside_down_camera/init.gradle`.
  Логи `build/upside-down-camera-review-client-baseline.log` и `-client-final.log`.
  Оба запуска exit 0: baseline 50s, final 51s;
  `UPSIDE_DOWN_CAMERA_CLIENT_PROBE_PASSED: 810`, final `checks=14189`.
  Сохранены 810 сравнений при 3 aspect × 3 perspective × 5 FOV × 9 yaw/pitch ×
  2 hurt/bob fixtures и аудит ровно трёх transformed call sites.
  Расширение исполняет настоящий transformed getFov и merged roll/offset handlers:
  оба окончания пар tiny_fov/offset_crosshair/forced front/RPG,
  HUD translate(+80,+40) без крена, один callback, восстановление GUI matrix
  при успешной отрисовке и исключении; 32 reset × 50 duplicate sync,
  актуальные перспектива/yaw/pitch/вектор взгляда после каждого окончания.
  Gate production, без test Mixin. Unsafe client/options/player/world/window/
  manager/renderer; alive/mode/camera accessors, заданные размеры окна и матрицы,
  ручной updateState, recording Operation. Нет полного renderWorld/renderHand,
  OpenGL, настоящей геометрии рук/map, raycast/выстрела, сети или options.txt.
- Свежий `javap` текущего mapped GameRenderer:
  `build/upside-down-camera-review-GameRenderer.javap.txt`.
  Каждый basic projection создаёт новую матрицу; мир и руки получают отдельные
  проекции перед vanilla hurt/bob, frustum — отдельную conservative projection.
  UI использует другой путь; raycast/Camera rotation не переписываются.

Накладные расходы оценены **static**: до gate выполняется вызов handler,
проверки контекста и alive/mode; contains по K effective IDs — O(K).
До трёх вызовов за обычный world/hand проход; активный rotateZ меняет свежую
матрицу на месте, O(1), без собственного кэша/пакетов/таймеров/NBT.
Общий server tick-dispatch работает каждый тик, sync — каждые 20 ticks;
списки/история и HUD имеют цену.
FPS/MSPT/allocations/retained memory не измерены; счётчики probe не профиль.
Ручные сценарии ниже остаются невыполненными, в том числе реальные транспорт,
элитры, hurt/bob, culling/lighting и центральное попадание. Полный отчёт и
точные ограничения: локальный `Промпты/reviews/26-upside_down_camera.md`.

## Исторические автоматические проверки реализации

Следующие результаты относятся к первоначальной реализации, не к ревью 2026-11-06.

- `.\gradlew.bat test compileGametestJava --offline` — BUILD SUCCESSFUL,
  129 JUnit, failures/errors/skipped = 0; три новых теста регистрации,
  режимов и очистки synced состояния.
- `.\gradlew.bat -I build/upside-down-camera-clean.init.gradle build runGametest --offline`
  — BUILD SUCCESSFUL, 129 JUnit без ошибок, 59/59 GameTest; штатное завершение.
  Init меняет только runDir на `build/upside-down-camera-final-gametest`.
- Два новых GameTest: реальные S2C пакеты двум игрокам и late join;
  режимы, смерть/респаун, Nether/End/Overworld, UUID/reconnect, double с no_sprint,
  завершение офлайн, реальные запись/чтение активного и завершённого состояния
  с диска, PERSONAL, дубли и серверная включённость; команды `/fdc list`,
  `/fdc activate upside_down_camera`, `/fdc active`; одна регистрация.
- `.\gradlew.bat -I src/clientprobe/upside_down_camera/init.gradle runClient --offline`
  — BUILD SUCCESSFUL, `UPSIDE_DOWN_CAMERA_CLIENT_PROBE_PASSED`, 810 сравнений.
  Стенд проверяет экспортированный transformed GameRenderer: сразу после каждого
  из трёх указанных вызовов находится ровно один production-handler, в остальных
  методах его вызовов нет. Затем вызывает этот настоящий внедрённый handler
  на настоящих `getBasicProjectionMatrix`, без подмены production gate/методов.
  Матрицы проверены при трёх aspect ratio, трёх перспективах, пяти FOV (включая 15),
  девяти yaw/pitch и с/без заданной матрицы hurt/bob. Проверены отдельный проход
  рук, переворот асимметричной точки, неизменные глубина и центральный луч,
  неизменные yaw/pitch/перспектива, завершение, режимы, смерть/удаление/новый объект,
  отсутствие мира/игрока/interactionManager/режима, замена мира, reset,
  завершение офлайн, panorama, оба forced_third_person через настоящий setter Mixin
  и завершение только крена при сохранении forced-перспективы.
  Клиент, мир, окно и игрок — Unsafe fixtures; размеры окна заданы полями,
  hurt/bob/view — матричные fixtures. Стенд не выполняет renderWorld/renderHand
  целиком, OpenGL, реальные ввод, взаимодействие и сетевое соединение.
  Исходники/init закоммичены в `src/clientprobe/upside_down_camera`; стенд не входит
  в игровые JAR. Воспроизводимый запуск не требует создания игрового мира.
- `git diff --check`, `git diff --cached --check` — без ошибок; состав игрового
  JAR проверен: production-классы присутствуют, validation/Probe отсутствуют.

Логи: `build/upside-down-compile.log`, `build/upside-down-camera-verification.log`,
`build/upside-down-camera-client-probe.log`. Отчёты: `build/test-results/test/TEST-*.xml`,
`build/reports/gametest.xml`. Экспорт клиента: `build/upside-down-camera-client-probe/run/.mixin.out`.
Gradle потребовал разрешённого доступа к существующему `F:\Programs\GradleCache`;
lock-файлы не удалялись, зависимости не менялись.

## Ручные проверки — не выполнены

1. Survival, асимметричная стена с центральной целью: `/fdc activate upside_down_camera`.
   Мир/руки перевёрнуты; сломать центральную цель и выстрелить в неё. F5 во всех
   перспективах, транспорт, элитры, вода, hurt/bob, портал: крен сохраняется один раз.
   Открыть инвентарь/чат/меню: HUD, текст, прицел и курсор обычные, ввод совпадает
   с контролем. Проверить реальные геометрию рук, held map, освещение и culling.
2. Два настоящих клиента и поздний вход: основной сценарий, смерть/респаун,
   reconnect и измерения. Серверные embedded игроки не заменяют эту проверку.
3. Creative/Spectator без крена, Adventure с креном; возврат в Survival без удвоения.
4. `/fdc activate "double_challenge:upside_down_camera,forced_third_person_front"`,
   затем `/fdc activate forced_third_person_front`: исчезает только крен.
   Повторить с RPG; отдельно пары с inverted_mouse/inverted_keyboard и tiny_fov.
5. `/fdc activate "double_challenge:upside_down_camera,no_sprint"`, затем
   `/fdc activate no_sprint`: крен снимается, запрет спринта остаётся.
6. Сохранить и полностью перезапустить JVM с активным испытанием. Затем завершить
   через `/fdc activate no_sprint`, сохранить/перезапустить и вернуть офлайн-игрока:
   крен отсутствует, чужие правила сохранены. Автоматический тест диска не заменяет
   полный перезапуск клиента/сервера.

Следующее испытание здесь не реализуется. Ранее отмеченные риски изоляции чужих
GameTest не исправляются в рамках этой задачи.
