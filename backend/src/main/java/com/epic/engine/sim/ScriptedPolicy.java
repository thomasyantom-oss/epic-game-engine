package com.epic.engine.sim;

import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fixed skill sequence; after the sequence is consumed, falls back to basic_attack. */
public class ScriptedPolicy implements Policy {
    private final List<String> steps;
    private int index = 0;

    public ScriptedPolicy(List<String> steps) {
        this.steps = new ArrayList<>(steps == null ? List.of() : steps);
    }

    @Override
    public Map<String, Object> selectCommand(Entity unit, String combatId, EntityStore store) {
        Entity target = HeuristicPolicy.lowestHpEnemy(unit, combatId, store);
        if (target == null) return null;
        String type = index < steps.size() ? steps.get(index) : "basic_attack";
        index++;
        Map<String, Object> command = new HashMap<>();
        command.put("type", type);
        command.put("targetId", target.getId());
        return command;
    }
}
