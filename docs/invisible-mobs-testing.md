# invisible_mobs — проверка

Испытание №29: «Их здесь нет», MEDIUM/GLOBAL. Название и описания взяты из
актуального `Испытания/invisible_mobs.json`.

## Реализация

`InvisibleMobs` проверяет текущую синхронизацию, effective ID, мир, живого
неудалённого локального игрока и режим Survival/Adventure. Подходят типы
`SpawnGroup.MONSTER`, кроме `ENDER_DRAGON` и `WITHER`: нейтральность эндермена
и дневного паука не меняет категорию. Проверка не хранит ссылки или снимки.

Client-only `InvisibleMobsEntityRenderDispatcherMixin` оборачивает два вызова
в `EntityRenderDispatcher.render` через MixinExtras v2 `WrapWithCondition`:

- `EntityRenderer.render`: целиком модель, features экипировки/глаз, имя и
  вершины обычного glowing-outline;
- `renderShadow`: тень того же моба.

Байткод Yarn 1.21.1 проверен локально: `LivingEntityRenderer.render` вызывает
`EntityModel.render`, `FeatureRenderer.render` и родительский рендер имени.
Огонь и F3+B вызываются отдельно в диспетчере и продолжают выполняться.
Флаг невидимости, эффекты, AI, звук, частицы, снаряды и серверная физика
не перехватываются. После завершения снова исполняется обычный рендер с
актуальным состоянием, включая чужую естественную невидимость.

Общая система сохраняет активные ID и рассылает enabled GLOBAL/PERSONAL ID.
Новых NBT, пакетов, обработчиков тика или обходов мира нет. Клиентский reset
JOIN/DISCONNECT уже очищает синхронизацию; собственное состояние очищать не нужно.

## Исторические проверки реализации (2026-10-29)

Результаты этого раздела относятся к реализации, не к свежему ревью.
Актуальные команды и доказательства этапа 29 приведены ниже.

Воспроизводимый CLIENT-стенд:

```powershell
.\gradlew.bat -I src/clientprobe/invisible_mobs/init.gradle test compileGametestJava runClient --offline
.\gradlew.bat -I src/clientprobe/invisible_mobs/init.gradle build runClient runGametest --offline
```

Init-script помещает отдельный test JAR в `build/invisible-mobs-client-probe`,
задаёт отдельные runDir клиента и GameTest. В игровой JAR стенд не входит.
Первый запуск в песочнице остановился до компиляции из-за доступа к
`F:\Programs\GradleCache\wrapper\dists\...\gradle-8.10.2-bin.zip.lck`.
Проверки повторены с разрешённым доступом к существующему кэшу; lock не удалялся.

- JUnit: регистрация без дубликатов, точные метаданные, double_challenge,
  living/synced/mode gate, reset и завершение офлайн.
- CLIENT: `INVISIBLE_MOBS_CLIENT_PROBE_PASSED checks=9499` в обоих прогонах.
  Настоящий transformed `EntityRenderDispatcher.render` исполняется для всех
  типов реестра, кроме отдельного skin-dispatch игрока; классификация игрока
  проверена отдельно. Есть положительный контроль без испытания и с Creative/
  Spectator, Survival/Adventure, обычным и outline-провайдером. Проверяются
  вход в renderer, тень, огонь, F3+B, восстановление стека матриц, чужая
  невидимость, смерть/respawn, removed player, новый мир, null-контексты,
  потеря синхронизации, окончание офлайн и неизменность настроек.
  ASM подтверждает ровно два production-перехвата только в `render` и границы
  модели/features/имени в ванильном LivingEntityRenderer.
- Три новых GameTest: настоящий урон от вооружённого зомби игроку и обратно;
  сохранность цели, glowing, экипировки и чужой невидимости; effective S2C
  двум игрокам и late join; режимы, respawn, Nether/End/Overworld, reconnect UUID;
  реальная запись/чтение диска active/ended; GLOBAL/PERSONAL, disabled/дубли,
  double с no_sprint; `/fdc list`, `/fdc activate invisible_mobs`, `/fdc active`.

Итоговая команда: `BUILD SUCCESSFUL`, exit 0, штатное завершение сервера;
136 JUnit, failures/errors/skipped=0; 67/67 GameTest; CLIENT checks=9499.
Начальный прогон тоже прошёл. Единственное изменение после него — переход
с deprecated WrapWithCondition на composable v2; итоговый прогон проверяет v2.
Binary/sources JAR содержат три production-класса и не содержат validation/Probe.
`git diff --check` и `git diff --cached --check` пройдены перед коммитом.
Логи: `build/invisible-mobs-client-probe.log`,
`build/invisible-mobs-verification.log`; XML: `build/test-results/test/TEST-*.xml`,
`build/reports/gametest.xml`.

## Границы стенда и ручные проверки

CLIENT-стенд использует Unsafe fixtures client/player/world/dispatcher и
считающий renderer. Отдельный probe-only Mixin считает terminal shadow/fire/
hitbox, заменяя их GL/world работу. Production-gate и вызовы диспетчера не
подменяются. Настоящие модели, отдельные armor/eyes/name shaders, framebuffer
outline и полный WorldRenderer/OpenGL стенд не рисует; их охват основан на
проверенной границе renderer, а не на сравнении пикселей.

Не выполнены следующие проверки настоящим клиентом:

1. В Survival показать вооружённого бронированного зомби, паука, нейтрального
   эндермена, корову, жителя, голема и обоих боссов; добавить имена/glowing.
   Активировать испытание без повторного спавна. Проверить отсутствие всех
   требуемых слоёв/теней/контуров MONSTER, обычный вид исключений и bossbar.
2. Проверить звук/субтитры, частицы зелий/попадания, огонь, F3+B, снаряды,
   столкновения, обнаружение и реальные атаки в игре. Отдельно проверить
   уже загруженных мобов и новый спавн во всех измерениях.
3. Повторить в Adventure, Creative, Spectator и снова Survival. Завершить
   через `/fdc activate no_sprint`: естественная невидимость сохраняется,
   остальные сущности возвращаются по текущим ванильным правилам.
4. Проверить смерть/respawn, порталы, reconnect, полный restart JVM при
   активном испытании и вход после окончания офлайн. Disk reload в GameTest
   не является полным перезапуском клиента или сервера.
5. Проверить `double_challenge:invisible_mobs,no_sprint`, сочетание с blind_hud
   и испытаниями, меняющими экипировку. Отдельно проверить пару с
   `silent_world`: звук исчезает, частицы и коллизии остаются.
6. При необязательной сетевой проверке сравнить два настоящих клиента:
   Survival скрывает мобов, Creative видит обычный рендер; отключение одного
   не влияет на второго. Embedded-пакеты GameTest не заменяют эту проверку.

## Ревью этапа 29 (2026-11-06)

Проверен production HEAD `3268526881dc4b7562210317fb4e1560d33ca9b4`.
Подтверждённых дефектов production не найдено; изменены только предметные
GameTest, CLIENT probe и этот документ. Итог `pass_with_limits`: механика
`pass`, расходы `no_issue_found / static`. JSON и shared contracts прежние.

```powershell
.\gradlew.bat build runGametest --offline -I build/invisible-mobs-review-baseline.init.gradle
.\gradlew.bat runClient --offline -I src/clientprobe/invisible_mobs/init.gradle
.\gradlew.bat build runGametest --offline -I build/invisible-mobs-review-final.init.gradle
.\gradlew.bat runClient --offline -I src/clientprobe/invisible_mobs/init.gradle
.\gradlew.bat processGametestResources --offline
python build/invisible-mobs-review-verify.py
```

Review init-script заменяет только fabric-gametest entrypoints на
`com.cursecycle.InvisibleMobsGameTest` во временном resource dir, задаёт
уникальный runDir `build/gametest-invisible-mobs-review-<phase>-20261106`,
XML `../reports/invisible-mobs-review-<phase>.xml` и JUnit reports dir
`build/reports/invisible-mobs-review-<phase>-junit`; test и обработка
resources принудительно выполняются заново. Production/classpath не меняются.
CLIENT init по-прежнему запускает preLaunch probe без игрового окна.

Свежие попытки:

| Проверка | Результат | Лог в build/ |
|---|---|---|
| Baseline build/GT | exit 0, 35s; 149 JUnit / 54 suites, 3/3 GameTest | invisible-mobs-review-baseline.log |
| Baseline CLIENT | exit 0, 27s; 9499 checks | invisible-mobs-review-client-baseline.log |
| Final build/GT | exit 0, 39s; 149 JUnit / 54 suites, 5/5 GameTest | invisible-mobs-review-final.log |
| Expanded CLIENT attempt 1 | exit 1, 43s; NoSuchMethodException в новом fixture: ошибочно указан renderHealthBar | invisible-mobs-review-client-final-attempt1.log |
| Final CLIENT | exit 0, 24s; 10318 checks, 1696 dispatcher calls | invisible-mobs-review-client-final.log |
| Final artifact build после выравнивания отступов try/finally | exit 0, 6s; JUnit up-to-date, нового прогона тестов нет | invisible-mobs-review-artifact-build.log |
| Restore / verifier | exit 0 / 0; 43 entrypoints восстановлены, SHA5/metadata6/registration/Mixin/XML/JUnit/2 production JAR проверены | invisible-mobs-review-restore.log / invisible-mobs-review-verifier.log |

JUnit failures/errors/skipped = 0; GameTest завершились штатно. Ошибка CLIENT
исправлена выбором фактического `renderStatusBars(DrawContext)` в test-only
reflection. Failed log сохранён; production ради стенда не менялся.

GameTest дополнительно проверяет wire codec каждого полученного S2C;
32 смены activationSerial, duplicate ID и double; обе очередности окончания
с no_sprint, предметы/HP/XP/чужой эффект; Fabric DISCONNECT, активацию и
окончание без игроков, same-UUID join с настоящим playerdata. Адресная пара
netherite_mobs исполняет настоящий spawnEntity → ServerWorld.tickEntity:
четыре issued armor slots остаются после обоих окончаний, новые комплекты
после окончания companion не выдаются. Мобы fixture удаляются в finally.
Это проверка границы совместимости, не самостоятельное ревью netherite_mobs.

CLIENT исполняет оба production WrapWithCondition на настоящем transformed
EntityRenderDispatcher.render: все типы реестра, все режимы, normal/outline
provider, death/removed/new player/world/null/reset, чужая невидимость,
fire/F3+B/shadow, непустая матрица и восстановление стека. 32 reset/start/end
с 50 повторными sync на цикл не оставляют фильтр. Пары netherite_mobs и
blind_hud проверены с обоими окончаниями; для blind_hud дополнительно вызван
настоящий transformed renderStatusBars с активным gate (HEAD cancellation).
Неактивная ветка HUD в этом стенде не рисуется. ASM проверяет ровно два
production call sites и границы model/features/name в LivingEntityRenderer.

Свежий javap mapped 1.21.1 сохранён в
`build/invisible-mobs-review-{EntityRenderDispatcher,LivingEntityRenderer,WorldRenderer}.javap.txt`:
WorldRenderer выбирает outline provider перед тем же renderEntity/dispatcher;
LivingEntityRenderer вызывает model/features/parent name внутри закрываемой
границы, fire и F3+B находятся отдельно от неё. Вместо GPU-пикселей probe
считает входы: NORMAL/OUTLINE — разные recording providers без реального
framebuffer. Armor/eyes/name shaders и реальный outline пока не отрисованы.

Ограничения остаются открыты:

- L01: настоящий клиент/OpenGL/fullframe, UI/options.txt, все manual_tests[0,1,2,4]
  и локальные [3,5]; модели/броня/оружие/глаза/name/glowing/tень скрыты только у
  MONSTER кроме боссов, fire/частицы/звук/снаряды/коллизии/F3+B и bossbar сохранны;
  no_sprint/netherite_mobs/blind_hud/silent_world с обоими окончаниями.
- L02: manual_tests[7] и сетевые [1,3,5,6]; dedicated два Survival + Creative
  + late join, actual receiver/network/reset/server switch/latency-lowTPS-pause.
  EmbeddedChannel/codec и ручной ClientDataHolder.updateState не доказывают сеть.
- L03: manual_tests[6] и restart [3]; полный новый JVM при active, offline end,
  empty server и same UUID. Disk reload в одной JVM не заменяет restart.
- L04: одинаковые мир/версии/P/E/K/дальность/прогрев/длительность, inactive-active-
  double/PERSONAL и 32 reconnect; FPS/MSPT/allocations/retained memory не измерены.

Стоимость статически ограничена O(E × K) на кадр для подходящих видимых мобов,
где E — число dispatcher calls, K — число effective ID. На моб два gate при
достижении shadow ветки; для нетаргетных типов ранний O(1). До gate остаются
vanilla renderer lookup/position offset/matrix и сам вызов wrapper. Неактивный
target тоже оплачивает context/mode/alive/contains. Своих коллекций, таймеров,
пакетов, NBT, retained Player/World нет; shared callbacks, sync/HUD/history и
ванильный рендер имеют цену. Счётчики probe не являются benchmark.
