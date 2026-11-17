# Разработка и проверки

[Документация](README.md) · [Конфигурация](configuration.md) · [Сценарии быстрой проверки](quick-testing.md)

## Среда и сборка

Текущая версия мода — 1.0.5. Параметры среды задаются в `gradle.properties` и `build.gradle`: Minecraft 1.21.1, Yarn 1.21.1+build.3, Fabric Loader 0.16.0, Fabric API 0.102.0+1.21.1, JDK 21, Fabric Loom 1.7. Gradle Wrapper использует Gradle 8.10.2.

Команды ниже выполняются из корня репозитория в PowerShell. На Linux/macOS замените `.\gradlew.bat` на `./gradlew`.

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
```

`build` компилирует мод, запускает JUnit и собирает `build/libs/curse-cycle-1.0.5.jar` и JAR исходников. Он не запускает серверные GameTest и клиентские пробы. `runClient`/`runServer` запускают среду разработки; для установки нужен обычный JAR. Первый запуск требует загрузки зависимостей. Флаг `--offline` используйте, когда зависимости уже есть в кэше.

## Устройство проекта

| Путь внутри `src/main` | Назначение |
| --- | --- |
| `java/com/cursecycle/CurseCycleMod.java` | Серверная и общая инициализация, регистрация событий |
| `java/com/cursecycle/CurseCycleClient.java` | Клиентская инициализация, клавиши и сетевые обработчики |
| `java/com/cursecycle/challenge/` | Базовый `Challenge`, реестр, метаданные и реализации правил |
| `java/com/cursecycle/challenge/effect/` | Учёт собственных и внешних вкладов эффектов |
| `java/com/cursecycle/state/` | `CurseCycleState` в PersistentState и расписание `CycleManager` |
| `java/com/cursecycle/roulette/` | Пул, кандидаты колеса и выбор победителя |
| `java/com/cursecycle/event/` | Доставка событий активным испытаниям и обработка урона |
| `java/com/cursecycle/config/` | Конфигурация и применение расписания с проверкой прав |
| `java/com/cursecycle/network/` | Payload, кодеки и синхронизация состояния |
| `java/com/cursecycle/client/` | Ввод, камеры, звуки и клиентское состояние |
| `java/com/cursecycle/client/gui/` | HUD, колесо и журнал испытаний |
| `java/com/cursecycle/mixin/` | Перехваты ванильной логики |
| `java/com/cursecycle/diagnostics/` | Логи среды и отчёт об ошибке инициализации |
| `resources/cursecycle.mixins.json` | Общие и клиентские Mixin |
| `resources/assets/cursecycle/` | Переводы, текстуры и звуки |
| `resources/data/cursecycle/` | Типы урона и другие данные мода |

Сервер определяет активные испытания и результат колеса. Клиент применяет синхронизированные правила ввода, камеры и интерфейса. Все текущие зарегистрированные испытания имеют область GLOBAL; инфраструктура состояния и событий также поддерживает PERSONAL.

## Изменение испытания

1. Проверьте текущий `Challenge`, похожие реализации, регистрацию в `ModChallenges` и существующие обработчики событий.
2. Для нового испытания задайте уникальный ID, название, короткое описание, полные правила, сложность и область; зарегистрируйте его один раз.
3. Предпочитайте имеющиеся события. Если необходим Mixin, сверяйте цель с текущими Yarn mappings; клиентский код размещайте в client-разделе конфигурации Mixin.
4. Обработайте завершение, выход, смерть/респаун, режимы игры, отключённую регистрацию и сочетание с двойным испытанием. Собственные ограничения снимайте без отмены чужих эффектов и без возврата потраченных ресурсов.
5. Обновите [список испытаний](challenges.md), соответствующий `*-testing.md` и ссылки в [оглавлении](README.md). Это правило закреплено в [AGENTS.md](../AGENTS.md).
6. Выполните проверки, соответствующие изменению, и отдельно укажите оставшиеся ручные сценарии.

Локальные JSON-ТЗ в `Испытания/` и старый `challenge-implementation-plan.md` не являются полным текущим справочником: они исключены из Git и могут отсутствовать в свежем клоне. Текущее поведение сверяйте с реализацией и отслеживаемой документацией.

## Автоматические проверки

### JUnit

```powershell
.\gradlew.bat test
.\gradlew.bat test --tests com.cursecycle.state.CycleManagerTest
```

Исходники: `src/test/java`. HTML-отчёт: `build/reports/tests/test/index.html`; XML: `build/test-results/test/TEST-*.xml`. Эти тесты проверяют алгоритмы и контракты, но сами по себе не подтверждают применение Mixin в Minecraft.

### Серверные GameTest

```powershell
.\gradlew.bat runGametest
```

Исходники и регистрация: `src/gametest/java` и `src/gametest/resources/fabric.mod.json`. Полный запуск использует `build/gametest`; XML-отчёт — `build/reports/gametest.xml`. GameTest запускаются отдельно от `build` и применяют игровые Mixin.

Для команд, настроек, жизненного цикла и расписания есть отслеживаемый изолированный запуск:

```powershell
.\gradlew.bat runGametest -I src/gametest/manual-controls.init.gradle
```

Он выбирает `ManualCycleGameTest` и `FoundationGameTest`, хранит мир в `build/manual-controls-gametest/run`, отчёт — в `build/manual-controls-gametest/report.xml`. Включает проверки сна и восстановления пустого цикла из [сценария расписания](cycle-scheduling-testing.md).

Запуски GameTest используют сохранённый каталог мира. При подозрении на остатки предыдущего запуска задайте отдельный свежий `runDir`. Не запускайте Gradle-задачи с общими ресурсами и миром параллельно. После временного ограничения списка entrypoints восстановите штатные ресурсы:

```powershell
.\gradlew.bat processGametestResources
```

Затем сравните сгенерированный `build/resources/gametest/fabric.mod.json` с исходным списком. Init-скрипты внутри `build/`, упомянутые в старых отчётах, — локальные артефакты, в свежем клоне их нет.

### Клиентские пробы

Пробы хранятся в `src/clientprobe/<имя>/` и подключаются отдельным init-скриптом, например:

```powershell
.\gradlew.bat runClient -I src/clientprobe/visual/init.gradle
.\gradlew.bat runClient -I src/clientprobe/floor_feedback/init.gradle
.\gradlew.bat runClient -I src/clientprobe/inventory_lockdown/init.gradle
```

Разные пробы дают разное покрытие: одни проверяют преобразованные методы на синтетических объектах, другие открывают настоящий рендерер и сохраняют кадры. Успех пробы без игрового мира не означает проверку механики в обычной игре. Условия, маркеры успеха и пути логов приведены в документах конкретных испытаний. GameTest и клиентские пробы не включаются в основной JAR.

## Как записывать результаты

Указывайте дату, выполненную команду, итог актуального отчёта и границы проверки. Сохранение и загрузка NBT в одной JVM не заменяют полный перезапуск сервера; EmbeddedChannel не заменяет два настоящих сетевых клиента; проверка пакетов HUD не подтверждает отрисовку.

Числа тестов и пути в датированных разделах `*-testing.md` относятся к тем запускам. Не переносите их на новую версию без повторного выполнения. При изменении только документации достаточно проверить факты по коду, ссылки, полноту списка испытаний и `git diff --check`; пересборка не подтверждает качество текста.
