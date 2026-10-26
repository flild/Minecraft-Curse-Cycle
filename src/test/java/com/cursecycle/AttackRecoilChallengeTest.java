package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.AttackRecoilChallenge;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AttackRecoilChallengeTest {
    @BeforeEach void register() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
    }
    @Test void registeredOnceWithSpecifiedMetadataAndSelection() {
        var challenge = ChallengeRegistry.get(AttackRecoilChallenge.ID);
        assertInstanceOf(AttackRecoilChallenge.class, challenge);
        assertEquals("Как в контрстрайке", challenge.getName());
        assertEquals("Успешный удар в ближнем бою отбрасывает самого игрока назад.", challenge.getShortDescription());
        assertEquals(ChallengeDifficulty.MEDIUM, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(challenge.getId())).count());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(challenge));
        challenge.setEnabled(false);
        assertFalse(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(challenge));
    }
    @Test void doubleAndPersonalActivationPersistWithoutPerAttackState() {
        var ids = List.of(AttackRecoilChallenge.ID, "no_sprint");
        assertEquals(ids, ChallengeUtils.resolveToActiveIds("double_challenge:attack_recoil,no_sprint"));
        var state = new CurseCycleState();
        var uuid = UUID.randomUUID();
        state.getActiveChallengeIds().addAll(ids);
        state.getPersonalActiveChallengeIds(uuid).add(AttackRecoilChallenge.ID);
        var loaded = CurseCycleState.createFromNbt(state.writeNbt(new NbtCompound(), null), null);
        assertEquals(ids, loaded.getActiveChallengeIds());
        assertEquals(List.of(AttackRecoilChallenge.ID), loaded.getPersonalActiveChallengeIds(uuid));
        assertTrue(loaded.getChallengeData().isEmpty());
    }
}
