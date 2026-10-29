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

## Автоматические проверки

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
   и существующими испытаниями, меняющими экипировку. `silent_world` и
   `netherite_mobs` в этом чате не реализуются.
6. При необязательной сетевой проверке сравнить два настоящих клиента:
   Survival скрывает мобов, Creative видит обычный рендер; отключение одного
   не влияет на второго. Embedded-пакеты GameTest не заменяют эту проверку.
