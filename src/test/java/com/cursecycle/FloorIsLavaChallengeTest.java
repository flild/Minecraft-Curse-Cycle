package com.cursecycle;

import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.challenge.ChallengeUtils;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.challenge.impl.FloorIsLavaChallenge;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FloorIsLavaChallengeTest {
    @BeforeEach
    void register() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
    }

    @Test
    void registeredAndSelectableAsHardGlobalChallenge() {
        var challenge = ChallengeRegistry.get("the_floor_is_lava");
        assertInstanceOf(FloorIsLavaChallenge.class, challenge);
        assertEquals("Пол — это лава!", challenge.getName());
        assertEquals("Стоять на твёрдых блоках вредно для здоровья. Очень вредно.", challenge.getShortDescription());
        assertEquals(ChallengeDifficulty.HARD, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.HARD).contains(challenge));
        challenge.setEnabled(false);
        assertFalse(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.HARD).contains(challenge));
    }

    @Test
    void doubleAndPersonalSelectionsSurviveSerialization() {
        var ids = List.of(FloorIsLavaChallenge.ID, "no_sprint");
        assertEquals(ids, ChallengeUtils.resolveToActiveIds("double_challenge:the_floor_is_lava,no_sprint"));
        var state = new CurseCycleState();
        state.getActiveChallengeIds().addAll(ids);
        UUID player = UUID.randomUUID();
        state.getPersonalActiveChallengeIds(player).add(FloorIsLavaChallenge.ID);
        state.addHistory("double_challenge:the_floor_is_lava,no_sprint", 1);
        var restored = CurseCycleState.createFromNbt(state.writeNbt(new NbtCompound(), null), null);
        assertEquals(ids, restored.getActiveChallengeIds());
        assertEquals(List.of(FloorIsLavaChallenge.ID), restored.getPersonalActiveChallengeIds(player));
        assertEquals("double_challenge:the_floor_is_lava,no_sprint", restored.getHistory().getFirst().challengeId);
    }
}
