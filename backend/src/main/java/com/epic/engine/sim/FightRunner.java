package com.epic.engine.sim;

import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Runs one combat by feeding policy-selected commands into the real combat.resolve_round event. */
public class FightRunner {
    private final EntityStore store;
    private final EventBus bus;
    private int currentRound;

    public FightRunner(EntityStore store, EventBus bus) {
        this.store = store;
        this.bus = bus;
    }

    public FightResult run(String combatId, List<String> playerIds, List<String> enemyIds,
                           Map<String, Policy> policies, int maxTurns) {
        return run(combatId, playerIds, enemyIds, policies, maxTurns, null);
    }

    public FightResult run(String combatId, List<String> playerIds, List<String> enemyIds,
                           Map<String, Policy> policies, int maxTurns, DamageTap tap) {
        Entity combat = store.get(combatId);
        Component state = combat.getComponent("CombatState");
        currentRound = 0;
        FightTrace trace = tap != null ? tap.begin() : null;
        String phase = state.getString("phase");
        while (currentRound < maxTurns && !"VICTORY".equals(phase) && !"DEFEAT".equals(phase)) {
            Map<String, Map<String, Object>> commands = new HashMap<>();
            for (String id : allUnits(playerIds, enemyIds)) {
                Entity unit = store.get(id);
                if (!isAlive(unit)) continue;
                Policy policy = policies.get(id);
                if (policy == null) continue;
                Map<String, Object> command = policy.selectCommand(unit, combatId, store);
                if (command != null) commands.put(id, command);
            }
            GameEvent event = new GameEvent("combat.resolve_round");
            event.set("combatId", combatId);
            event.set("commands", commands);
            bus.fire("combat.resolve_round", event);
            currentRound++;
            phase = state.getString("phase");
        }
        if (tap != null) tap.end();
        FightOutcome outcome = "VICTORY".equals(phase) ? FightOutcome.WIN
                : "DEFEAT".equals(phase) ? FightOutcome.LOSS : FightOutcome.TIMEOUT;
        return new FightResult(outcome, currentRound, hpFraction(playerIds), hpFraction(enemyIds), trace);
    }

    public int round() {
        return currentRound;
    }

    private static List<String> allUnits(List<String> playerIds, List<String> enemyIds) {
        List<String> all = new ArrayList<>(playerIds);
        all.addAll(enemyIds);
        return all;
    }

    private static boolean isAlive(Entity entity) {
        return entity != null && entity.hasComponent("Health") && entity.getComponent("Health").getInt("hp") > 0;
    }

    private double hpFraction(List<String> ids) {
        int hp = 0;
        int maxHp = 0;
        for (String id : ids) {
            Entity entity = store.get(id);
            if (entity == null || !entity.hasComponent("Health")) continue;
            Component health = entity.getComponent("Health");
            hp += Math.max(0, health.getInt("hp"));
            maxHp += health.getInt("maxHp");
        }
        return maxHp == 0 ? 0.0 : (double) hp / maxHp;
    }
}
