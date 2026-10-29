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
- `SilentWorldSoundSystemMixin` повторно применяет текущую логическую громкость
  на HEAD `SoundSystem.tick(boolean)`, `play(SoundInstance)` и
  `updateListenerPosition(Camera)`, только при запущенном движке. Это охватывает
  уже звучащие источники, паузу, новые звуки и смену контекста между тиками.

Байткод Yarn 1.21.1 проверен локально. `SoundSystem.play` уведомляет
`SoundInstanceListener`/`SubtitlesHud`, затем читает `SoundListener.getVolume`
для решения о запуске источника. Поэтому обнуление самого поля или отмена
`play` не подходят: здесь источники продолжают обычную временную шкалу.
Финальный listener gain охватывает все категории, включая MASTER, MUSIC,
RECORDS, VOICE, UI и звуки мода. Таймеры/изображение/HUD не перехватываются.
Настройки и `options.txt` не записываются. После снятия используются последние
пользовательские громкости, завершившиеся звуки не запускаются повторно.

## Автоматические проверки

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
