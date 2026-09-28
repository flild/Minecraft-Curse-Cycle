# Hardcore Challenges

Мод для Minecraft Java Edition (на базе Fabric), реализующий хардкорные испытания.
Проект предоставляет гибкую архитектуру для добавления новых испытаний с серверной обработкой логики и клиентской частью для UI.

## Архитектура

Архитектура мода спроектирована таким образом, чтобы каждое новое испытание было отдельным классом, не требующим изменений в ядре системы. Сервер является единым источником истины.

### Основные компоненты

*   `Challenge` (интерфейс) / `BaseChallenge` (абстрактный класс): Базовые абстракции испытаний. Они определяют методы жизненного цикла (`onActivate`, `onDeactivate`, `tick`), обработку событий по умолчанию (например, `onDamage`, `onJump`) и механизмы сохранения/чтения NBT.
*   `ChallengeDefinition`: Содержит метаданные испытания (ID, название, описание, сложность и область действия `GLOBAL`/`PLAYER`).
*   `ChallengeManager`: Управляет жизненным циклом (запуск, остановка), отслеживает активные испытания, ведет подсчет тиков и маршрутизирует системные события (`ON_DAMAGE`, `ON_MOVE` и т.д.) только к активным испытаниям.
*   `ChallengeRegistry`: Реестр испытаний. Позволяет добавлять новые испытания путем регистрации их фабрики (`Supplier<Challenge>`).
*   `ChallengePersistentState`: Класс, расширяющий `PersistentState`. Отвечает за сохранение и восстановление состояния менеджера (включая активные испытания и таймер) в NBT данные мира (Overworld).
*   `ChallengeEvents`: Система событий. Вместо одного огромного обработчика мы предоставляем отдельные специализированные события (`ON_DAMAGE`, `ON_MOVE`, `ON_JUMP`, `ON_SPRINT`). Вызовы этих событий внедрены через Mixin.

## Как добавить новое испытание

Добавление нового испытания сводится к созданию класса, наследующего `BaseChallenge`, и его регистрации. Ниже приведен пример создания испытания, которое запрещает игрокам прыгать.

### 1. Создание класса испытания

Создайте новый класс в вашем пакете `challenge`, который расширяет `BaseChallenge`.

```java
package com.example.hardcorechallenges.challenge;

import com.example.hardcorechallenges.api.challenge.BaseChallenge;
import com.example.hardcorechallenges.api.challenge.ChallengeContext;
import com.example.hardcorechallenges.api.challenge.ChallengeDefinition;
import com.example.hardcorechallenges.api.challenge.ChallengeScope;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class NoJumpChallenge extends BaseChallenge {

    public static final Identifier ID = Identifier.of("hardcorechallenges", "no_jump");

    public NoJumpChallenge() {
        super(new ChallengeDefinition(
                ID,
                "No Jump",
                "You cannot jump! If you jump, you will receive a message.",
                "movement",
                2,
                12000, // 10 minutes
                ChallengeScope.GLOBAL
        ));
    }

    @Override
    public void onActivate(ChallengeContext context) {
        super.onActivate(context);
        if (context.server() != null) {
            context.server().getPlayerManager().broadcast(Text.literal("§c[!] No Jump challenge activated!"), false);
        }
    }

    @Override
    public void onDeactivate(ChallengeContext context) {
        super.onDeactivate(context);
        if (context.server() != null) {
            context.server().getPlayerManager().broadcast(Text.literal("§a[!] No Jump challenge deactivated!"), false);
        }
    }

    @Override
    public void onJump(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("§cYou jumped! That's bad in this challenge!"), true);
    }
}
```

### 2. Регистрация испытания

В главном классе мода (`HardcoreChallenges.java`) в методе `onInitialize` зарегистрируйте испытание через `ChallengeRegistry`.

```java
import com.example.hardcorechallenges.challenge.NoJumpChallenge;
import com.example.hardcorechallenges.api.registry.ChallengeRegistry;

// Внутри onInitialize():
ChallengeRegistry.register(() -> new NoJumpChallenge());
```

Готово! Испытание теперь доступно в игре. Вы можете запустить его командой `/challenge start hardcorechallenges:no_jump`.

## Команды

Мод предоставляет команду `/challenge` с подкомандами (требует уровень прав оператора = 2):

*   `/challenge current` - Показывает активные испытания.
*   `/challenge start <id>` - Запускает испытание по его ID.
*   `/challenge stop` - Останавливает все испытания.
*   `/challenge stop <id>` - Останавливает конкретное испытание.
*   `/challenge next` - Останавливает все и запускает следующее (базовая реализация).
*   `/challenge list` - Список всех зарегистрированных испытаний.
*   `/challenge reload` - Заглушка для перезагрузки.
