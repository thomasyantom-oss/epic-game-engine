// Handle ATTACK and DEFEND commands
engine.on("combat.unit_action", 100, function(event) {
    var cmd = event.get("command");
    var actorId = event.get("actorId");
    var combatId = event.get("combatId");

    var cmdType = cmd.get("type");
    var cmdTypeStr = (typeof cmdType === "string") ? cmdType : cmdType.toString();

    if (cmdTypeStr === "ATTACK") {
        var targetId = cmd.get("targetId");

        var calcEvent = engine.newEvent("combat.damage_calc");
        calcEvent.set("attackerId", actorId);
        calcEvent.set("targetId", targetId);
        calcEvent.set("combatId", combatId);
        engine.fire("combat.damage_calc", calcEvent);

        var damage = calcEvent.get("damage");

        var target = store.get(targetId);
        var health = target.getComponent("Health");
        var newHp = Math.max(0, health.getInt("hp") - damage);
        health.set("hp", newHp);

        var dealEvent = engine.newEvent("combat.damage_dealt");
        dealEvent.set("attackerId", actorId);
        dealEvent.set("targetId", targetId);
        dealEvent.set("damage", damage);
        dealEvent.set("combatId", combatId);
        engine.fire("combat.damage_dealt", dealEvent);
    } else if (cmdTypeStr === "DEFEND") {
        var actor = store.get(actorId);
        actor.addComponent(engine.newComponent("Defending"));
    }
});

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
