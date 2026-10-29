# offset_crosshair — проверка реализации

«Конфига симпла», MEDIUM/GLOBAL. Название и оба описания совпадают с актуальным
`Испытания/offset_crosshair.json` (SHA-256
`928958557c5319fe213a783687f50783bf56b5849e646b8ba4c254063c72307b`).

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
живого неудалённого игрока и Survival/Adventure. Нет сохранённых настроек,
размеров окна, ссылок на игрока, новых пакетов, таймеров и собственного NBT.
Активные GLOBAL/PERSONAL ID сохраняет и синхронизирует существующая система;
JOIN/DISCONNECT сбрасывают `ClientDataHolder`. Серверные действия, raycast,
снаряды, камера, проекция мира и остальные методы HUD не перехватываются.

Сигнатура проверена по локальному байткоду Minecraft 1.21.1 и
[Yarn 1.21.1+build.3](https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/client/gui/hud/InGameHud.html#renderCrosshair(net.minecraft.client.gui.DrawContext,net.minecraft.client.render.RenderTickCounter)).

## Автоматические проверки

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

## Ручные проверки — не выполнены

1. Survival, первое лицо, две мишени в центре и под будущей отметкой:
   `/fdc activate offset_crosshair`. Проверить удар, стрелу, добычу и использование:
   действие/подсветка блока центральные, прицел справа/снизу. Изменить окно и GUI
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
