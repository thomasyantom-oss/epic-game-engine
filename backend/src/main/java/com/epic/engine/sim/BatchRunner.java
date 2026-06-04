package com.epic.engine.sim;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class BatchRunner {
    public BatchMetrics run(int iterations, Supplier<FightResult> oneFight) {
        List<FightResult> results = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            results.add(oneFight.get());
        }
        return BatchMetrics.of(results);
    }

    public BatchMetrics runSimulation(int iterations, SimSetup setup,
                                      CombatantBuilder builder,
                                      EntityStore store,
                                      EventBus bus) {
        return run(iterations, () -> runOne(setup, builder, store, bus, null));
    }

    public record Aggregated(BatchMetrics metrics, BatchTrace trace) {}

    public Aggregated runSimulationWithTrace(int iterations, SimSetup setup,
                                             CombatantBuilder builder,
                                             EntityStore store,
                                             EventBus bus) {
        FightRunner runner = new FightRunner(store, bus);
        DamageTap tap = new DamageTap(bus, runner::round);
        List<FightResult> results = new ArrayList<>(iterations);
        BatchTrace trace = new BatchTrace();
        for (int i = 0; i < iterations; i++) {
            FightResult result = runOne(setup, builder, store, bus, runner, tap);
            results.add(result);
            trace.merge(result.trace());
        }
        return new Aggregated(BatchMetrics.of(results), trace);
    }

    private FightResult runOne(SimSetup setup, CombatantBuilder builder,
                               EntityStore store, EventBus bus, DamageTap tap) {
        return runOne(setup, builder, store, bus, null, tap);
    }

    private FightResult runOne(SimSetup setup, CombatantBuilder builder,
                               EntityStore store, EventBus bus,
                               FightRunner reusableRunner, DamageTap tap) {
        String playerId = builder.buildPlayer(setup.playerClass(), setup.playerLevel(), setup.sources());
        CombatantBuilder.Spawn spawn = builder.spawnEncounter(playerId, setup.encounterId());
        FightRunner runner = reusableRunner != null ? reusableRunner : new FightRunner(store, bus);
        Map<String, Policy> policies = new HashMap<>();
        policies.put(playerId, setup.newPlayerPolicy());
        for (String enemyId : spawn.enemyIds()) {
            policies.put(enemyId, new HeuristicPolicy());
        }
        FightResult result = runner.run(spawn.combatId(), List.of(playerId), spawn.enemyIds(),
                policies, setup.maxTurns(), tap);
        builder.cleanup(playerId, spawn);
        store.remove(playerId);
        return result;
    }
}
