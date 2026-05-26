// Check for death after damage
engine.on("combat.damage_dealt", 100, function(event) {
    var target = store.get(event.get("targetId"));
    var health = target.getComponent("Health");

    if (health.getInt("hp") <= 0) {
        var deathEvent = engine.newEvent("combat.unit_death");
        deathEvent.set("deadId", target.getId());
        deathEvent.set("killerId", event.get("attackerId"));
        deathEvent.set("combatId", event.get("combatId"));
        engine.fire("combat.unit_death", deathEvent);
    }
});

// On death, remove all non-permanent buffs
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

// Check if combat is over
engine.on("combat.check_end", 100, function(event) {
    var combatId = event.get("combatId");
    var combatants = store.getByTagAsList("combat:" + combatId);

    var playersAlive = false;
    var enemiesAlive = false;

    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        if (entity.getComponent("Health").getInt("hp") > 0) {
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
