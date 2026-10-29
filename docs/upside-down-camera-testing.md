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

## Автоматические проверки

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
