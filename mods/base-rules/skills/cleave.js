engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    if (cmd.get("type") !== "cleave") return;
    var actorId = event.get("actorId");
    var combatId = event.get("combatId");
    var caster = store.get(actorId);
    var attack = caster.hasComponent("CombatStats") ? caster.getComponent("CombatStats").getInt("attack") : 5;
    var damage = attack + 5;

    // Find all front row enemies
    var combatants = store.getByTagAsList("combat:" + combatId);
    var targets = [];
    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        if (!entity.hasTag("enemy")) continue;
        if (!entity.hasComponent("Health") || entity.getComponent("Health").getInt("hp") <= 0) continue;
        var pos = entity.hasComponent("CombatPosition") ? entity.getComponent("CombatPosition") : null;
        var row = pos !== null ? pos.getString("row") : "FRONT";
        if (row === "FRONT") {
            targets.push(entity.getId());
        }
    }

    // Deal damage to each target and fire events
    for (var t = 0; t < targets.length; t++) {
        var targetId = targets[t];
        var target = store.get(targetId);
        var health = target.getComponent("Health");
        health.set("hp", Math.max(0, health.getInt("hp") - damage));

        var dealEvent = engine.newEvent("combat.damage_dealt");
        dealEvent.set("attackerId", actorId);
        dealEvent.set("targetId", targetId);
        dealEvent.set("damage", damage);
        dealEvent.set("combatId", combatId);
        dealEvent.set("skillId", "cleave");
        engine.fire("combat.damage_dealt", dealEvent);
    }
});
