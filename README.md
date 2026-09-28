# Hardcore Challenges

Мод для Minecraft Java Edition (на базе Fabric), реализующий хардкорные испытания.
Проект предоставляет гибкую архитектуру для добавления новых испытаний с серверной обработкой логики и полноценным клиентским UI (HUD, меню, рулетка).

## Архитектура

Архитектура мода разделена на серверную логику (Source of Truth) и клиентское отображение. Сервер управляет всем состоянием, а клиент только отрисовывает UI, реагируя на кастомные пакеты.

### Основные компоненты

*   `Challenge` / `BaseChallenge`: Базовые абстракции испытаний. Они определяют методы жизненного цикла (`onActivate`, `onDeactivate`, `tick`) и обработку событий.
*   `ChallengeManager`: Управляет запуском/остановкой испытаний, таймерами, отслеживает активные испытания и ведет маршрутизацию глобальных событий (`ON_DAMAGE`, `ON_MOVE` и т.д.).
*   `ChallengeSelector`: Механизм выбора (рулетка). Может выбирать сразу несколько неконфликтующих испытаний, учитывая их сложность, вес и требования.
*   `ConfigManager`: Система конфигурации (GSON). Позволяет настраивать режимы, длительность, повторы и включение HUD'а (`hardcorechallenges.json`).
*   `ChallengePersistentState` & `ChallengeStatistics`: Сохраняют активные испытания, время, глобальный GameMode и историю/статистику игрока в NBT (данные мира Overworld).
*   **Сетевой слой (`CustomPayload`)**: Для обмена данными между сервером и клиентом зарегистрированы пакеты `SyncStatePayload` (периодическое обновление активных челленджей и времени), `RouletteStartPayload` (команда на анимацию рулетки) и `NotificationPayload` (предупреждения о нарушениях).

### Пользовательский интерфейс (Client UI)

*   **HUD**: Минималистичный интерфейс, показывающий активный режим, текущий игровой день и список активных испытаний. Включается/выключается через конфиг.
*   **Рулетка (`RouletteScreen`)**: Вызывается при старте случайного испытания (`/challenge start-random`). Показывает визуальную анимацию "колеса" и победителей.
*   **Меню (`ChallengeMenuScreen`)**: Открывается по нажатию клавиши `H`. Отображает текущий режим и активные испытания (в будущем — историю и статистику).
*   Уведомления: Выводятся в чат (или в будущем через Toast-уведомления) при нарушении условий испытания.

## Как добавить новое испытание

Добавление нового испытания сводится к созданию класса, наследующего `BaseChallenge`, и его регистрации.

### 1. Создание класса испытания

Создайте новый класс, который расширяет `BaseChallenge`.

```java
package com.example.hardcorechallenges.challenge;

import com.example.hardcorechallenges.api.challenge.BaseChallenge;
import com.example.hardcorechallenges.api.challenge.ChallengeDefinition;
import com.example.hardcorechallenges.api.challenge.ChallengeDifficulty;
import com.example.hardcorechallenges.api.challenge.ChallengeScope;
import com.example.hardcorechallenges.api.violation.ChallengePenalty;
import com.example.hardcorechallenges.api.violation.ChallengeViolation;
import com.example.hardcorechallenges.api.violation.DeathPenalty;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.Collections;
import java.util.List;

public class NoSprintChallenge extends BaseChallenge {

    public static final Identifier ID = Identifier.of("hardcorechallenges", "no_sprint");

    public NoSprintChallenge() {
        super(new ChallengeDefinition(
                ID,
                "No Sprinting",
                "Do not sprint, or face the consequences!",
                "movement",
                ChallengeDifficulty.MEDIUM,
                12000,
                ChallengeScope.GLOBAL,
                List.of("movement", "sprint"), // Tags
                Collections.emptyList(), // Conflicts
                Collections.emptyList(), // Requirements
                false // Allow multiple
        ));
    }

    @Override
    public void onSprintStart(ServerPlayerEntity player) {
        if (getState() == com.example.hardcorechallenges.api.challenge.ChallengeState.ACTIVE) {
            triggerViolation(player, "Sprinting is not allowed!");
        }
    }

    @Override
    public ChallengePenalty getPenalty(ChallengeViolation violation) {
        return new DeathPenalty(); // Наказание применяется с учетом текущего GameMode
    }
}
```

### 2. Регистрация испытания

В главном классе мода (`HardcoreChallenges.java`) в методе `onInitialize` зарегистрируйте испытание через `ChallengeRegistry`.

```java
import com.example.hardcorechallenges.challenge.NoSprintChallenge;
import com.example.hardcorechallenges.api.registry.ChallengeRegistry;

// Внутри onInitialize():
ChallengeRegistry.register(() -> new NoSprintChallenge());
```

Готово! Испытание теперь доступно в игре. Оно может быть выбрано случайным образом рулеткой или активировано командой `/challenge start hardcorechallenges:no_sprint`.

## Команды

Мод предоставляет команду `/challenge` с подкомандами (требует уровень прав оператора = 2):

*   `/challenge current` - Показывает активные испытания.
*   `/challenge start <id>` - Запускает испытание по его ID.
*   `/challenge start-random [count]` - Запускает рулетку и выбирает указанное количество испытаний.
*   `/challenge roulette` - Выводит в чат несколько случайных совместимых испытаний (без запуска).
*   `/challenge stop` - Останавливает все испытания.
*   `/challenge stop <id>` - Останавливает конкретное испытание.
*   `/challenge next` - Останавливает все и запускает следующее (через рулетку).
*   `/challenge list` - Список всех зарегистрированных испытаний.
