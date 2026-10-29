# tiny_fov — «Тесновато»

Спецификация: EASY/GLOBAL, итоговый вертикальный FOV сцены 15 градусов.
Регистрация и оба русских описания соответствуют `Испытания/tiny_fov.json`.

## Реализация

- `TinyFovGameRendererMixin` — только client-раздел `cursecycle.mixins.json`.
  `getFov(Camera, float, boolean)` перехватывается на RETURN после ванильных
  модификаторов. По байткоду Yarn 1.21.1+build.3 `renderWorld` передаёт `true`,
  `renderHand` — `false`; изменяется только первый путь. Panorama сохраняет
  ванильные 90 градусов. Матрицы проекции, aspect ratio, GUI, прицел, дистанция
  камеры, перспектива и использование подзорной трубы не изменяются.
- `TinyFovCamera` читает актуальные synced ID, наличие мира, живого неудалённого
  игрока и interactionManager, Survival/Adventure при каждом запросе FOV.
  Снимков настроек, ссылок на игрока, таймеров, новых пакетов и собственного
  NBT нет. Повторные ID не умножают эффект. После снятия действует текущий
  ванильный FOV; чужие модификаторы не откатываются.
- Активность сохраняется существующим `CurseCycleState`. Сервер синхронизирует
  включённые GLOBAL/PERSONAL ID без дублей. Существующий reset JOIN/DISCONNECT
  исключает использование прежней активации после смены подключения.

## Автоматические проверки 2026-10-29

- `.\gradlew.bat test compileGametestJava --offline` — BUILD SUCCESSFUL;
  126 JUnit, failures/errors/skipped = 0, включая три новых теста tiny_fov.
  Проверяются точные метаданные, регистрация один раз, double, режимы,
  смерть, свежая синхронизация и reset без сохранения старого ограничения.
- `.\gradlew.bat -I build/tiny-fov-clean.init.gradle build runGametest --offline`
  — BUILD SUCCESSFUL, 57/57 GameTest, включая два новых; сервер штатно завершился.
  Init содержит только `allprojects { afterEvaluate { loom.runs.gametest {
  runDir 'build/tiny-fov-final-gametest' } } }`, чтобы использовать отдельный мир.
- Два новых `TinyFovGameTest`: реальные S2C для двух игроков и late join,
  смена режимов, смерть/респаун, Nether/End/Overworld, переподключение того же
  UUID, double с no_sprint и сохранение запрета спринта после снятия tiny_fov,
  настоящая запись и чтение активных/завершённых ID с диска, завершение офлайн,
  PERSONAL, дедупликация и disabled. Выполняются `/fdc list`,
  `/fdc activate tiny_fov`, `/fdc active`. Это проверка серверного пути и S2C,
  а не рендера подключённого клиента или полного перезапуска JVM.
- `.\gradlew.bat -I src/clientprobe/tiny_fov/init.gradle runClient --offline`
  — BUILD SUCCESSFUL, `TINY_FOV_CLIENT_PROBE_PASSED`: 2700 комбинаций настоящего
  transformed `GameRenderer.getFov`. База 30/70/80/90/110, все перспективы,
  пять множителей .1/.85/1/1.3/1.5, три tickDelta, все CameraSubmersionType,
  три значения FOV effect scale. Каждый случай: ванильный контроль, 15 для
  сцены, неизменный FOV рук, немедленный возврат к ванильному контролю.
  Дополнительно: изменение настройки 90 → 80 во время активации, режимы,
  смерть/удаление/новый игрок, null world/player/interactionManager/mode,
  новый мир, reset/завершение офлайн/свежий sync, panorama и оба действующих
  forced_third_person через настоящий `GameOptions.setPerspective` Mixin.
- CLIENT-стенд воспроизводим из `src/clientprobe/tiny_fov`; его классы/манифест
  не входят в игровой JAR. Использованы Unsafe client/player/world, подменённые
  camera/player accessors и заданные поля итоговых ванильных множителей.
  Настоящие спринт, атрибуты, лук, труба, окно, GUI и сетевой клиент этим
  стендом не запускаются. Их живое поведение остаётся в списке ниже.
- Логи: `build/tiny-fov-focused.log`, `build/tiny-fov-verification.log`,
  `build/tiny-fov-client-probe.log`. JUnit XML: `build/test-results/test/`;
  GameTest XML: `build/reports/gametest.xml`.
- Проверен `build/libs/curse-cycle-1.0.0.jar`: содержит три production-класса
  tiny_fov, без `validation/` и манифеста стенда.

В песочнице wrapper не смог открыть lock существующего внешнего кэша
`F:\Programs\GradleCache`. Повторный запуск с разрешённым доступом прошёл;
lock не удалялся, зависимости и настройки окружения не менялись.

## Ручные проверки — не выполнены

1. Survival, FOV 90, контрольный кадр из фиксированной точки. Активировать
   tiny_fov; проверить покой, спринт, Speed/Slowness, натяжение лука, подзорную
   трубу, воду/лаву и F5. Сцена остаётся 15; рамка трубы, руки, HUD, меню,
   прицел, aspect ratio и GUI scale работают штатно. Сравнить forced front/rpg.
2. При активном испытании изменить настройку FOV с 90 на 80 и GUI scale.
   Выполнить `/fdc activate no_sprint`, выйти в меню, открыть мир заново.
   После завершения используется 80 с текущими ванильными модификаторами;
   проверить `options.txt`, что временные 15 туда не записались.
3. Проверить русские тексты в HUD/меню и стандартное единственное уведомление.
   Выполнить `/fdc list`, `/fdc activate tiny_fov`, `/fdc active` с OP 2.
4. Проверить два живых клиента и поздний вход; Survival → Creative → Spectator
   → Adventure → Survival, смерть/респаун с обоими keepInventory, реальные
   порталы, отключение/повторный вход, смену сервера и задержку сети.
5. Выполнить `/fdc activate "double_challenge:tiny_fov,no_sprint"`, затем
   `/fdc activate no_sprint`: FOV возвращается, запрет спринта сохраняется.
6. Сохранить и полностью перезапустить JVM с активным tiny_fov. Затем завершить
   через no_sprint при отключённом игроке, сохранить и снова перезапустить.
   После входа используются только актуальные ID. Проверить естественную смену
   цикла. Команды `/fdc stop` нет.

`upside_down_camera` и `offset_crosshair` ещё не реализованы: совместимость
с их будущими перехватами не объявлена проверенной.
