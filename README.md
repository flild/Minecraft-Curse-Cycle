# Curse-Cycle (Five Day Challenge)

**Curse-Cycle** — это мод для Minecraft (Fabric, 1.21.1), в котором каждые 5 игровых дней случайным образом выбирается и активируется новое испытание, меняющее привычный игровой процесс.

## Основные особенности и механики

1. **Интервал активации:**
   - Каждые 5 игровых дней (настраивается в конфиге `cursecycle.json`) активируется новое испытание.
   - По умолчанию новое испытание заменяет предыдущее.

2. **Колесо Фортуны:**
   - За 1 игровой день (за 24,000 тиков) до активации запускается плавное анимированное Колесо Фортуны (~5 секунд), которое определяет следующее испытание.
   - Игра **не ставится на паузу** во время вращения колеса — игрок должен заботиться о своей безопасности!
   - Результат заранее сохраняется на сервере и синхронизируется между всеми игроками в кооперативе.

3. **Баланс сложностей:**
   - Начальное соотношение шансов: 3 лёгких, 2 средних, 1 сложное.
   - С увеличением игровых дней доля сложных испытаний возрастает (настраивается этапами в конфиге).
   - Поддерживается избегание мгновенных повторов и обновление пула при исчерпании вариантов.

4. **Интерфейс (HUD & Меню):**
   - **HUD Overlay:** Показывает текущий день, время до следующего испытания, карточку с активными испытаниями и анонс следующего выпавшего испытания.
   - **Главное меню (Клавиша `H`):** Содержит вкладки с активными испытаниями, следующим испытанием, историей выпадений и подробными правилами каждого испытания.

5. **Команды для разработки и тестирования (`/fdc` или `/cursecycle`):**
   - `/fdc active` — показать список активных испытаний.
   - `/fdc next` — показать предварительно выбранное следующее испытание.
   - `/fdc trigger_spin` — принудительно запустить анимацию колеса фортуны и выбор испытания.
   - `/fdc activate <id>` — принудительно активировать испытание по его ID.
   - `/fdc list` — вывести полный список всех зарегистрированных испытаний.
   - `/fdc history` — посмотреть историю выпавших испытаний.
   - `/fdc setday <day>` — изменить текущий игровой день для проверки событий.
   - `/fdc reset` — сбросить состояние мода для повторного тестирования.

---

## Архитектура проекта

Код мода организован в единой понятной структуре в пакете `com.cursecycle`:

- `com.cursecycle.challenge` — абстрактный класс `Challenge`, перечисления `ChallengeDifficulty` и `ChallengeScope`, единый реестр `ChallengeRegistry` и точки регистрации.
- `com.cursecycle.config` — конфигурационный менеджер `CurseCycleConfig` (хранение в `config/cursecycle.json`).
- `com.cursecycle.state` — сохранение состояния мира `CurseCycleState` (Fabric `PersistentState`).
- `com.cursecycle.roulette` — алгоритм подбора кандидатов и генерации результатов колеса `RouletteSelector`.
- `com.cursecycle.network` — сетевые пакеты `CurseCycleSyncPayload` и `TriggerWheelPayload` (CustomPayload API).
- `com.cursecycle.client` & `com.cursecycle.client.gui` — клиентская логика, HUD overlay (`CurseCycleHudOverlay`), экраны `WheelScreen` и `MainMenuScreen`.
- `com.cursecycle.command` — команды администратора (`CurseCycleCommand`).

---

## Инструкция: Как добавить новое испытание

Для создания и добавления нового испытания в мод выполните 2 простых шага:

### Шаг 1: Создайте класс испытания
Создайте новый класс в пакете `com.cursecycle.challenge` (или подпакете), унаследовав его от `Challenge`:

```java
package com.cursecycle.challenge.custom;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class NoJumpingChallenge extends Challenge {
    public static final String ID = "no_jumping";

    public NoJumpingChallenge() {
        super(
            ID,                                    // Уникальный ID
            "Запрет прыжков",                       // Русское название
            "Игрокам запрещено прыгать",           // Краткое описание
            "При попытке подпрыгнуть игрок получает урон или сбрасывается в прыжке.", // Подробные правила
            ChallengeDifficulty.MEDIUM,            // Сложность: EASY, MEDIUM, HARD
            ChallengeScope.GLOBAL                  // Масштаб: GLOBAL или PERSONAL
        );
    }

    @Override
    public void onStart(MinecraftServer server, ServerPlayerEntity player) {
        // Логика при запуске испытания
    }

    @Override
    public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        // Логика при завершении испытания
    }

    @Override
    public void onTick(MinecraftServer server, ServerPlayerEntity player) {
        // Логика каждый тик, пока испытание активно
    }
}
```

### Шаг 2: Зарегистрируйте испытание
Добавьте одну строчку в класс `ModChallenges` (`com.cursecycle.challenge.ModChallenges`):

```java
public class ModChallenges {
    public static void registerAll() {
        ChallengeRegistry.register(new TestEasyChallenge());
        // ...
        ChallengeRegistry.register(new NoJumpingChallenge()); // <--- Новое испытание!
    }
}
```

Новое испытание автоматически появится в колесе фортуны, в списках, в меню и в командах без необходимости изменять другие менеджеры!

---

## Сборка и запуск

### Требования:
- Java 21 JDK
- Gradle 8.8+

### Команды:
- Сборка мода: `./gradlew build`
- Запуск тестового клиента: `./gradlew runClient`
- Запуск тестового сервера: `./gradlew runServer`
