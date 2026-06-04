package com.epic.engine.sim;

import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;

import java.util.Map;

public class NoOpPolicy implements Policy {
    @Override
    public Map<String, Object> selectCommand(Entity unit, String combatId, EntityStore store) {
        return null;
    }
}
