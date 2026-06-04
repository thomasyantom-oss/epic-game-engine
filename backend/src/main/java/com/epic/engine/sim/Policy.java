package com.epic.engine.sim;

import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;

import java.util.Map;

/** Selects one command for a living unit in a combat round. */
public interface Policy {
    Map<String, Object> selectCommand(Entity unit, String combatId, EntityStore store);
}
