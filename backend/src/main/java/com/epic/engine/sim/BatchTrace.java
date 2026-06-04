package com.epic.engine.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BatchTrace {
    private final Map<String, Long> totalDamageBySkill = new HashMap<>();
    private final Map<String, Long> totalDamageByType = new HashMap<>();
    private final Map<String, Long> totalMitigationSaved = new HashMap<>();
    private final Map<String, Integer> totalKillSource = new HashMap<>();
    private final List<Integer> allDeathRounds = new ArrayList<>();

    public void merge(FightTrace trace) {
        if (trace == null) return;
        trace.damageBySkill().forEach((k, v) -> totalDamageBySkill.merge(k, v, Long::sum));
        trace.damageByType().forEach((k, v) -> totalDamageByType.merge(k, v, Long::sum));
        trace.mitigationSavedByTarget().forEach((k, v) -> totalMitigationSaved.merge(k, v, Long::sum));
        trace.killSource().forEach((k, v) -> totalKillSource.merge(k, v, Integer::sum));
        allDeathRounds.addAll(trace.deathRounds());
    }

    public Map<String, Long> totalDamageBySkill() { return totalDamageBySkill; }
    public Map<String, Long> totalDamageByType() { return totalDamageByType; }
    public Map<String, Long> totalMitigationSaved() { return totalMitigationSaved; }
    public Map<String, Integer> totalKillSource() { return totalKillSource; }

    public double avgDeathRound() {
        return allDeathRounds.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }
}
