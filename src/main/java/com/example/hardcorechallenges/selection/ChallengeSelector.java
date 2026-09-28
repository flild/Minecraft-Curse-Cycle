package com.example.hardcorechallenges.selection;

import com.example.hardcorechallenges.api.challenge.Challenge;
import com.example.hardcorechallenges.api.challenge.ChallengeDefinition;
import com.example.hardcorechallenges.api.registry.ChallengeRegistry;
import com.example.hardcorechallenges.manager.ChallengeManager;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public class ChallengeSelector {

    private final Random random = new Random();

    public List<Identifier> selectRandomChallenges(int count) {
        List<Identifier> available = getAvailableChallenges();
        if (available.isEmpty()) {
            return Collections.emptyList();
        }

        List<Identifier> selected = new ArrayList<>();
        List<Identifier> candidates = new ArrayList<>(available);

        for (int i = 0; i < count && !candidates.isEmpty(); i++) {
            Identifier id = candidates.remove(random.nextInt(candidates.size()));
            selected.add(id);

            // После выбора, мы должны удалить несовместимые испытания из пула кандидатов
            // В идеале мы должны инстанцировать dummy challenge или читать definition из registry.
            // Добавим в registry возможность получить definition
            Supplier<Challenge> supplier = ChallengeRegistry.getSupplier(id);
            if (supplier != null) {
                ChallengeDefinition def = supplier.get().getDefinition();
                candidates.removeIf(candidateId -> isConflict(candidateId, selected));
            }
        }
        return selected;
    }

    private List<Identifier> getAvailableChallenges() {
        List<Identifier> available = new ArrayList<>();
        List<Challenge> activeChallenges = ChallengeManager.getInstance().getActiveChallenges();

        for (Identifier id : ChallengeRegistry.getRegisteredIds()) {
            Supplier<Challenge> supplier = ChallengeRegistry.getSupplier(id);
            if (supplier == null) continue;

            Challenge dummy = supplier.get();
            ChallengeDefinition def = dummy.getDefinition();

            if (!def.allowMultiple() && isAlreadyActive(id, activeChallenges)) {
                continue; // Уже активно, а multiple не разрешено
            }

            if (isConflictWithActive(def, activeChallenges)) {
                continue;
            }

            if (!hasRequirements(def, activeChallenges)) {
                continue;
            }

            available.add(id);
        }

        return available;
    }

    private boolean isAlreadyActive(Identifier id, List<Challenge> activeChallenges) {
        return activeChallenges.stream().anyMatch(c -> c.getDefinition().id().equals(id));
    }

    private boolean isConflictWithActive(ChallengeDefinition def, List<Challenge> activeChallenges) {
        for (Challenge active : activeChallenges) {
            if (def.conflicts().contains(active.getDefinition().id())) {
                return true;
            }
            if (active.getDefinition().conflicts().contains(def.id())) {
                return true;
            }
        }
        return false;
    }

    private boolean isConflict(Identifier candidateId, List<Identifier> selected) {
        Supplier<Challenge> candidateSupplier = ChallengeRegistry.getSupplier(candidateId);
        if (candidateSupplier == null) return true;
        ChallengeDefinition candidateDef = candidateSupplier.get().getDefinition();

        for (Identifier selId : selected) {
            Supplier<Challenge> selSupplier = ChallengeRegistry.getSupplier(selId);
            if (selSupplier != null) {
                ChallengeDefinition selDef = selSupplier.get().getDefinition();
                if (candidateDef.conflicts().contains(selId) || selDef.conflicts().contains(candidateId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasRequirements(ChallengeDefinition def, List<Challenge> activeChallenges) {
        if (def.requirements().isEmpty()) return true;

        for (Identifier reqId : def.requirements()) {
            if (activeChallenges.stream().noneMatch(c -> c.getDefinition().id().equals(reqId))) {
                return false;
            }
        }
        return true;
    }
}
