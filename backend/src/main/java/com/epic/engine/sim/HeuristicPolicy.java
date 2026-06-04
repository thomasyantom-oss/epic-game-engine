package com.epic.engine.sim;

import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;

import java.util.HashMap;
import java.util.Map;

/** V1 heuristic: basic_attack the living opposing unit with the lowest HP. */
public class HeuristicPolicy implements Policy {
    @Override
    public Map<String, Object> selectCommand(Entity unit, String combatId, EntityStore store) {
        Entity target = lowestHpEnemy(unit, combatId, store);
        if (target == null) return null;
        Map<String, Object> command = new HashMap<>();
        command.put("type", "basic_attack");
        command.put("targetId", target.getId());
        return command;
    }

    static Entity lowestHpEnemy(Entity unit, String combatId, EntityStore store) {
        boolean unitIsPlayer = unit.hasTag("player");
        Entity best = null;
        int bestHp = Integer.MAX_VALUE;
        for (Entity entity : store.getByTagAsList("combat:" + combatId)) {
            boolean opposing = unitIsPlayer ? !entity.hasTag("player") : entity.hasTag("player");
            if (!opposing || !entity.hasComponent("Health")) continue;
            int hp = entity.getComponent("Health").getInt("hp");
            if (hp <= 0) continue;
            if (hp < bestHp) {
                bestHp = hp;
                best = entity;
            }
        }
        return best;
    }
}
