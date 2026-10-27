package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.NoBlockPlacingChallenge;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NoBlockPlacingChallengeTest {
    @Test void registeredMetadataAndPersistedEffectiveIds() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var c = ChallengeRegistry.get(NoBlockPlacingChallenge.ID);
        assertInstanceOf(NoBlockPlacingChallenge.class, c);
        assertEquals("Ну ты же не строитель", c.getName());
        assertEquals("Нельзя ставить никакие блоки, включая жидкости и огонь.", c.getShortDescription());
        assertEquals(ChallengeDifficulty.EASY, c.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, c.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(x -> x.getId().equals(c.getId())).count());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.EASY).contains(c));
        var ids = ChallengeUtils.resolveToActiveIds("double_challenge:no_block_placing,no_sprint");
        assertEquals(List.of(c.getId(), "no_sprint"), ids);
        var state = new CurseCycleState();
        var uuid = UUID.randomUUID();
        state.beginActivation();
        state.getActiveChallengeIds().addAll(ids);
        state.getPersonalActiveChallengeIds(uuid).add(c.getId());
        var loaded = CurseCycleState.createFromNbt(state.writeNbt(new NbtCompound(), null), null);
        assertEquals(ids, loaded.getActiveChallengeIds());
        assertEquals(List.of(c.getId()), loaded.getPersonalActiveChallengeIds(uuid));
        assertTrue(loaded.getChallengeData().isEmpty());
    }
}
