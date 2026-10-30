package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.EndlessRaidChallenge;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EndlessRaidChallengeTest {
    @Test void registrationMatchesSpecification() {
        ChallengeRegistry.clear(); ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get("endless_raid");
        assertInstanceOf(EndlessRaidChallenge.class, challenge);
        assertEquals("Рейд без выходных", challenge.getName());
        assertEquals("У деревень рейды возобновляются после завершения предыдущих.", challenge.getShortDescription());
        assertEquals("Пока испытание активно, игроки Survival/Adventure постоянно провоцируют ванильные рейды при посещении подходящих деревень. После завершения одного рейда следующий запускается через обычную ванильную подготовку. Вдали от деревень искусственные волны не создаются. Одну деревню обслуживает один рейд; при окончании испытания его собственные незавершённые рейды прекращаются, но уже появившиеся разбойники остаются в мире.", challenge.getDetailedRules());
        assertEquals(ChallengeDifficulty.MEDIUM, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals("endless_raid")).count());
        assertEquals(List.of("endless_raid", "no_sprint"), ChallengeUtils.resolveToActiveIds("double_challenge:endless_raid,no_sprint"));
    }
}
