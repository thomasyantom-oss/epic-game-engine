package com.epic.engine.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mutable per-fight explainability accumulator. */
public class FightTrace {
    private final Map<String, Long> damageBySkill = new HashMap<>();
    private final Map<String, Long> damageTakenByTarget = new HashMap<>();
    private final Map<String, Long> mitigationSavedByTarget = new HashMap<>();
    private final Map<String, Long> damageByType = new HashMap<>();
    private final Map<String, Integer> killSource = new HashMap<>();
    private final List<Integer> deathRounds = new ArrayList<>();

    void addDamage(String skillId, String targetId, long damage) {
        damageBySkill.merge(skillId == null ? "?" : skillId, damage, Long::sum);
        if (targetId != null) damageTakenByTarget.merge(targetId, damage, Long::sum);
    }

    void addMitigation(String targetId, String type, long raw, long fin) {
        if (targetId != null) mitigationSavedByTarget.merge(targetId, Math.max(0, raw - fin), Long::sum);
        if (type != null) damageByType.merge(type, fin, Long::sum);
    }

    void addDeath(String killerId, int round) {
        if (killerId != null) killSource.merge(killerId, 1, Integer::sum);
        deathRounds.add(round);
    }

    public Map<String, Long> damageBySkill() { return damageBySkill; }
    public Map<String, Long> damageTakenByTarget() { return damageTakenByTarget; }
    public Map<String, Long> mitigationSavedByTarget() { return mitigationSavedByTarget; }
    public Map<String, Long> damageByType() { return damageByType; }
    public Map<String, Integer> killSource() { return killSource; }
    public List<Integer> deathRounds() { return deathRounds; }
}
