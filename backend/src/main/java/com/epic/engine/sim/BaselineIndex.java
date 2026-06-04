package com.epic.engine.sim;

import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaselineIndex {
    private static final int DPS_ROUNDS = 20;
    private final CombatantBuilder builder;
    private final EntityStore store;
    private final EventBus bus;

    public BaselineIndex(CombatantBuilder builder, EntityStore store, EventBus bus) {
        this.builder = builder;
        this.store = store;
        this.bus = bus;
    }

    public record Result(double offenseIndex, double defenseIndex,
                         double baseDps, double variantDps,
                         double baseSurvival, double variantSurvival) {}

    public Result compare(String classId, int level, List<Map<String, Object>> variantSources, int iterations) {
        double baseDps = avgDps(classId, level, List.of(), iterations);
        double variantDps = avgDps(classId, level, variantSources, iterations);
        double baseSurvival = avgSurvival(classId, level, List.of(), iterations);
        double variantSurvival = avgSurvival(classId, level, variantSources, iterations);
        return new Result(div(variantDps, baseDps), div(variantSurvival, baseSurvival),
                baseDps, variantDps, baseSurvival, variantSurvival);
    }

    private double avgDps(String classId, int level, List<Map<String, Object>> sources, int iterations) {
        FightRunner runner = new FightRunner(store, bus);
        DamageTap tap = new DamageTap(bus, runner::round);
        long totalDamage = 0;
        for (int i = 0; i < iterations; i++) {
            String playerId = builder.buildPlayer(classId, level, sources);
            String dummyId = builder.makeRef("sim_dummy_" + System.nanoTime(), "dummy");
            String combatId = builder.makeCombat(playerId, dummyId);
            Map<String, Policy> policies = new HashMap<>();
            policies.put(playerId, new HeuristicPolicy());
            policies.put(dummyId, new NoOpPolicy());
            FightResult result = runner.run(combatId, List.of(playerId), List.of(dummyId), policies, DPS_ROUNDS, tap);
            totalDamage += result.trace().damageTakenByTarget().values().stream().mapToLong(Long::longValue).sum();
            cleanup(playerId, dummyId, combatId);
        }
        return (double) totalDamage / (iterations * DPS_ROUNDS);
    }

    private double avgSurvival(String classId, int level, List<Map<String, Object>> sources, int iterations) {
        long totalRounds = 0;
        for (int i = 0; i < iterations; i++) {
            FightRunner runner = new FightRunner(store, bus);
            String playerId = builder.buildPlayer(classId, level, sources);
            String attackerId = builder.makeRef("sim_attacker_" + System.nanoTime(), "attacker");
            String combatId = builder.makeCombat(playerId, attackerId);
            Map<String, Policy> policies = new HashMap<>();
            policies.put(playerId, new NoOpPolicy());
            policies.put(attackerId, new HeuristicPolicy());
            FightResult result = runner.run(combatId, List.of(playerId), List.of(attackerId), policies, 200);
            totalRounds += result.rounds();
            cleanup(playerId, attackerId, combatId);
        }
        return (double) totalRounds / iterations;
    }

    private void cleanup(String playerId, String enemyId, String combatId) {
        store.remove(enemyId);
        store.remove(combatId);
        Entity player = store.get(playerId);
        if (player != null) {
            player.removeTag("combat:" + combatId);
            store.reindexTags(player);
        }
        store.remove(playerId);
    }

    private static double div(double a, double b) {
        return b == 0 ? 0.0 : a / b;
    }
}
