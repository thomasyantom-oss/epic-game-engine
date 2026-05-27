engine.on("combat.damage_dealt", 100, function(event) {
    var target = store.get(event.get("targetId"));
    var health = target.getComponent("Health");
    if (health.getInt("hp") <= 0) {
        var hpZeroEvent = engine.newEvent("entity.hp_zero");
        hpZeroEvent.set("entity", target);
        hpZeroEvent.set("combatId", event.get("combatId"));
        hpZeroEvent.set("killerId", event.get("attackerId"));
        engine.fire("entity.hp_zero", hpZeroEvent);
    }
});

engine.on("entity.hp_zero", 100, function(event) {
    if (event.isCancelled()) return;
    var entity = event.get("entity");
    var ls = entity.getComponent("LifeState");
    if (ls === null) {
        var deathEvent = engine.newEvent("combat.unit_death");
        deathEvent.set("deadId", entity.getId());
        deathEvent.set("killerId", event.get("killerId"));
        deathEvent.set("combatId", event.get("combatId"));
        engine.fire("combat.unit_death", deathEvent);
        return;
    }
    var deathCount = ls.has("deathCount") ? ls.getInt("deathCount") : 0;
    if (deathCount === 0) {
        ls.set("state", "downed");
    } else {
        ls.set("state", "dead");
    }
    ls.set("deathCount", deathCount + 1);

    var stateEvent = engine.newEvent("entity.state_change");
    stateEvent.set("entity", entity);
    stateEvent.set("newState", ls.getString("state"));
    stateEvent.set("combatId", event.get("combatId"));
    engine.fire("entity.state_change", stateEvent);
});

engine.on("entity.state_change", 50, function(event) {
    var combatId = event.get("combatId");
    if (combatId === null) return;
    var checkEvent = engine.newEvent("combat.check_end");
    checkEvent.set("combatId", combatId);
    engine.fire("combat.check_end", checkEvent);
});

engine.on("combat.unit_death", 50, function(event) {
    var deadId = event.get("deadId");
    var entity = store.get(deadId);
    if (entity === null) return;

    var components = entity.getAllComponents();
    for (var i = 0; i < components.size(); i++) {
        var comp = components.get(i);
        if (!comp.getType().startsWith("Buff_")) continue;
        var permanent = comp.has("permanent") && comp.getBoolean("permanent");
        if (!permanent) {
            var buffId = comp.getType().substring(5);
            buffs.removeBuff(deadId, buffId);
        }
    }
});

engine.on("combat.check_end", 100, function(event) {
    var combatId = event.get("combatId");
    var combatants = store.getByTagAsList("combat:" + combatId);

    var playersAlive = false;
    var enemiesAlive = false;

    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        var ls = entity.getComponent("LifeState");
        var alive = ls !== null
            ? (ls.getString("state") === "alive")
            : (entity.getComponent("Health").getInt("hp") > 0);

        if (alive) {
            if (entity.hasTag("player")) playersAlive = true;
            if (entity.hasTag("enemy")) enemiesAlive = true;
        }
    }

    if (!enemiesAlive) {
        event.set("ended", true);
        event.set("result", "VICTORY");
    } else if (!playersAlive) {
        event.set("ended", true);
        event.set("result", "DEFEAT");
    } else {
        event.set("ended", false);
    }
});
