# silent_world — проверка

Испытание №30: «Тихий час», EASY/GLOBAL. Название и оба описания совпадают
с актуальным `Испытания/silent_world.json`.

## Реализация

`SilentWorld` проверяет текущие synced effective ID, мир, живого неудалённого
локального игрока и режим Survival/Adventure. Состояния, снимков громкости,
ссылок на игрока, новых пакетов и NBT нет. Общий `CurseCycleState` сохраняет
активность, `NetworkHandler` объединяет включённые GLOBAL/PERSONAL ID,
JOIN/DISCONNECT очищают клиентскую синхронизацию.

Два Mixin объявлены только в client-разделе:

- `SilentWorldSoundListenerMixin` меняет только аргумент gain вызова
  `AL10.alListenerf(AL_GAIN, volume)` внутри `SoundListener.setVolume`.
  Поле `SoundListener.volume` и его getter сохраняют логическую громкость.
- `SilentWorldSoundSystemMixin` согласует текущую логическую громкость
  на HEAD `SoundSystem.tick(boolean)`, `play(SoundInstance)` и
  `updateListenerPosition(Camera)`, только при запущенном движке. Это охватывает
  уже звучащие источники, паузу, новые звуки и смену контекста между тиками.
  Через `SilentWorldOutput` listener сравнивает требуемое подавление с последним
  действительно отправленным состоянием; при совпадении не вызывает setVolume
  и OpenAL. Каждый обычный setVolume, включая ползунок и запуск устройства,
  обновляет этот boolean в финальном фильтре. Снимка громкости/игрока нет.

Байткод Yarn 1.21.1 проверен локально. `SoundSystem.play` уведомляет
`SoundInstanceListener`/`SubtitlesHud`, затем читает `SoundListener.getVolume`
для решения о запуске источника. Поэтому обнуление самого поля или отмена
`play` не подходят: здесь источники продолжают обычную временную шкалу.
Финальный listener gain охватывает все категории, включая MASTER, MUSIC,
RECORDS, VOICE, UI и звуки мода. Таймеры/изображение/HUD не перехватываются.
Настройки и `options.txt` не записываются. После снятия используются последние
пользовательские громкости, завершившиеся звуки не запускаются повторно.

## Историческая проверка реализации

```powershell
.\gradlew.bat test compileGametestJava --offline
.\gradlew.bat -I src/clientprobe/silent_world/init.gradle build runClient runGametest --offline
```

Первый sandbox-запуск остановился до компиляции на доступе к
`F:\Programs\GradleCache\wrapper\dists\...\gradle-8.10.2-bin.zip.lck`.
Повторы используют разрешённый доступ к существующему кэшу; lock не удалялся.
Первичная компиляция и 138 JUnit прошли. Две первые CLIENT-итерации выявили
ошибки только стенда: `Channel.tick` уже удалил завершённый OpenAL source;
Unsafe-игрок не имеет списка пассажиров для полного `Entity.setRemoved`.
Проверка учитывает удаление source, а removalReason задаётся fixture напрямую.
Production-логика ради этих ошибок не менялась.

- JUnit: точные метаданные и единственная регистрация, double_challenge,
  synced/alive/mode gate, reset и завершение офлайн.
- CLIENT: `SILENT_WORLD_CLIENT_PROBE_PASSED checks=175`. Настоящие transformed
  `SoundListener.setVolume`, `SoundSystem.play/tick/updateListenerPosition`,
  `SubtitlesHud.onSoundPlayed` и настоящий OpenAL Soft device `No Output`.
  Проверяется AL_GAIN через `alGetListenerf`, реальные AL_SOURCE_STATE и
  пользовательские `SimpleOption` для всех категорий. Уже звучащая музыка
  остаётся PLAYING, новые звуки идут со своей обычной длительностью, короткий
  звук успевает STOPPED во время тишины и удаляется без повторного запуска.
  SubtitlesHud получает все категории. MASTER 0/1/промежуточные значения и
  другие ползунки не обходят подавление, последние значения сохраняются.
  Покрыты четыре режима, смерть/replacement/removed, новый/null мир, отсутствие
  игрока/interactionManager, reset/повторная синхронизация, окончание офлайн,
  немедленное обновление при play, повторная инициализация listener и menu gate.
  ASM-аудит подтверждает один финальный filter и три refresh call sites.
- Два GameTest: effective S2C двум игрокам и late join, четыре режима,
  respawn, Nether/End/Overworld, reconnect того же UUID; GLOBAL/PERSONAL,
  disabled/дубли; double с no_sprint и сохранение его запрета после снятия
  silent_world. Реальная запись/чтение диска active/ended и окончание офлайн;
  `/fdc list`, `/fdc activate silent_world`, `/fdc active` с OP 2.

Итоговая команда завершилась `BUILD SUCCESSFUL`, exit 0: 138 JUnit,
failures/errors/skipped=0; 69/69 GameTest; CLIENT checks=175. Сервер штатно
сохранил мир и завершился. Binary/sources JAR содержат четыре production-класса
SilentWorld и не содержат validation/Probe. `git diff --check` пройден;
перед коммитом также проверяется `git diff --cached --check`.

Init-script собирает изолированный probe.jar в `build/silent-world-client-probe`,
задаёт отдельные client/GameTest runDir. `ALSOFT_DRIVERS=null` задаётся только
процессу стенда, чтобы не выводить тестовые сигналы в колонки. Мир, игрок и
interactionManager — Unsafe fixtures; аудио состоит из синтетического PCM,
SoundLoader возвращает готовые буферы, Channel выполняет задачи синхронно.
Production gate/Mixin, OpenAL и получатель субтитров не подменены. Отдельного
test Mixin нет. Стенд не входит в игровой JAR.

Логи: `build/silent-world-compile.log`, `build/silent-world-client-probe-first.log`,
`build/silent-world-client-probe-second.log`, `build/silent-world-verification.log`.
XML: `build/test-results/test/TEST-*.xml`, `build/reports/gametest.xml`.
Байткод: `build/silent-world-{SoundSystem,SoundListener,SoundManager,SubtitlesHud}.javap.txt`.

## Оставшиеся ручные проверки

Автоматика не слушает физическое устройство, не рисует HUD/субтитры и не
проверяет весь клиентский сетевой цикл, декодирование OGG/streaming и полный
restart JVM. Disk reload не равен перезапуску процесса.

1. Начать музыку и проигрыватель, включить субтитры, активировать
   `/fdc activate silent_world`. Проверить шаги, сундук, мобов, UI, колесо
   и отдышку heavy_body: звук отсутствует, субтитры/изображение продолжаются.
   Изменять все ползунки, MASTER в том числе; отдельно проверить паузу.
2. Завершить через `/fdc activate no_sprint`. Слышны актуальные звуки с новыми
   громкостями, старые короткие сигналы не воспроизводятся очередью. Выйти
   в меню и на другой сервер: подавление не переносится, options.txt хранит
   выбранные пользователем значения. Проверить resource reload и смену устройства.
3. Повторить Survival → Creative → Spectator → Adventure → Survival, смерть/
   respawn, порталы и reconnect в настоящем клиенте. Неподходящие контексты
   снимают только подавление звука; после синхронизации оно применяется заново.
4. Два настоящих клиента и поздний вход третьего; пара
   `/fdc activate "double_challenge:silent_world,no_sprint"`, затем замена
   на no_sprint. Спринт остаётся запрещён, звук восстанавливается.
5. Сохранить и полностью перезапустить JVM с активным испытанием; затем
   закончить, сохранить, перезапустить и вернуть офлайн-игрока. Сверить
   сохранённую активность, очистку подавления и отсутствие отката настроек.
   Голос вне звукового движка Minecraft и внешние приложения не затрагиваются.

## Ревью этапа 30 — 2026-11-07

Исходный HEAD `1dec570dd1171cec965524e88d0af254c4a82e83`, main, clean.
Результаты выше относятся к реализации и не засчитаны как новое подтверждение.
В ревью исправлен подтверждённый расход: раньше каждый tick/play/frame даже
без silent_world повторно отправлял неизменный AL_GAIN. Игровая семантика
подавления сохранена; общая синхронизация/другие испытания не менялись.

Свежие команды (PowerShell; полные логи под build):

```powershell
.\gradlew.bat build runGametest --offline -I build/silent-world-review-baseline.init.gradle
.\gradlew.bat runClient --offline -I src/clientprobe/silent_world/init.gradle
.\gradlew.bat build runGametest --offline -I build/silent-world-review-final.init.gradle
.\gradlew.bat processGametestResources --offline
python build/silent-world-review-verify.py
```

Baseline до исправления: build exit 0/45s, 149 JUnit/54 suites и 2/2 subject
GameTest; исходный CLIENT exit 0/23s, 175 assertions. Расширенный счётчик до
исправления: exit 0/22s, 203 assertions. После исправления: build exit 0/37s,
149 JUnit/54 suites и 4/4 subject GameTest; CLIENT exit 0/24s, 5440 assertions.
XML failures/errors/skipped=0. Общий набор GameTest не запускался: init заменяет
только metadata entrypoints на SilentWorldGameTest, задаёт отдельные отчёты
и новые runDir `build/gametest-silent-world-review-{baseline,final}-20261107`.
После предметного запуска стандартные 43 entrypoints восстановлены.

Счётчик — test-only HEAD injection в настоящий SoundListener.setVolume без
подмены gate/native call. По текущему vanilla байткоду один setter = один
alListenerf. В одинаковом OpenAL Soft No Output fixture выполнены по 200
paused tick/frame/cannot-play triplets для inactive/active/double. Каждая серия
из 600 callbacks давала до исправления 600 native gain writes, после — 0.
Изменение подавления даёт ровно один write; ползунки сохраняют обычный путь.
Это измерение числа вызовов, без прогрева и временного окна; не FPS/MSPT,
не стоимость драйвера и не нагрузочный профиль. До исправления для повтора
счётчика на старых production Mixin использовать `-PsilentWorldExpectUncachedRefresh`.
Флаг меняет только ожидание счётчика; последующие регрессии повторного sync
в текущем probe требуют исправленного production-кода.
Обычный запуск текущего probe по умолчанию требует исправленного поведения.

CLIENT дополнительно исполняет 32 reset/start/end × 50 duplicate sync,
текущий MASTER после промежуточного unmute/slider/reactivation, настоящее
пересоздание OpenAL context и vanilla start/setVolume. Audio/phase boundary
heartbeat проверена receive/valid/state/age с обоими окончаниями: звук не
меняет фазу. Отрисовка overlay/физический ввод не исполняются. GameTest
добавляет 32 активации, activationSerial/dedup, оба окончания no_sprint,
чужие inventory/HP/XP/effect, пустой сервер, Fabric DISCONNECT и actual
same-UUID playerdata; настоящий wire codec. Пара heavy_body проверена
реальным transformed Player.jump: 60% при обоих, 60% после mute end,
vanilla после heavy_body end. Реальная отдышка/OGG остаётся ручной.

Сохранены промежуточные попытки: счётчик baseline exit 0/21s с ошибкой cleanup
fixture (синтетические буферы закрывались после уничтожения контекста); исправлен
порядок close до stop, чистый повтор указан выше. Первый cached CLIENT exit
1/26s: audio/counters прошли, но ASM-аудит ошибочно посчитал вызов интерфейса
внутри merged handler дополнительным injection site. Аудит теперь различает
владельца/дескриптор CallbackInfo и подтверждает ровно три реальные точки.
Production ради этих fixture ошибок не ослаблялся.
Проверка обычной команды без дополнительных флагов: exit 0/23s, те же 5440
assertions; `build/silent-world-review-client-default.log`. Restore exit 0/1s,
verifier exit 0: свежие XML/счётчики, metadata/SHA и оба production JAR чистые.

Логи: `build/silent-world-review-{baseline,final}.log`,
`build/silent-world-review-client-{baseline,count-baseline,count-baseline-clean,cached,final}.log`.
XML: `build/reports/silent-world-review-{baseline,final}.xml` и
`build/reports/silent-world-review-{baseline,final}-junit/TEST-*.xml`.
Свежий javap: `build/silent-world-review-{SoundSystem,SoundListener,SoundManager,SoundEngine,SubtitlesHud}.javap.txt`.

Вердикт: pass_with_limits; механика pass; расходы fixed / static+measured
(measured = только точное число native gain writes). Нет собственных таймеров,
NBT, пакетов, коллекций или сохранённых Player/World; один boolean на listener.
Для T tick, F frame, S play и K effective IDs проверка стоит O((T+F+S)×K),
menu/null context выходит до contains; неизменный контекст не обращается к AL.
Shared sync/HUD/history и vanilla sound/source work имеют отдельную стоимость.

Открыты silent_world-L01..L04: обычный клиент/физический звук/HUD/subtitles/
options.txt/streaming-OGG/resource reload/смена устройства; настоящая сеть и
два клиента с поздним третьим; полный новый JVM active/ended/offline end;
FPS/MSPT/allocations/память и долгий reconnect/load. Шаги 1–5 выше остаются
невыполненными ручными сценариями, дополнительно проверить heartbeat overlay
и обе очередности окончания пар heavy_body/heartbeat. Static и синтетический
No Output fixture не доказывают эти сценарии. Foundation-L02 остаётся открыт.
