engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    var cmdType = cmd.get("type");
    if (cmdType !== "basic_attack") return;

    var actorId = event.get("actorId");
    var combatId = event.get("combatId");
    var targetId = cmd.get("targetId");
    if (targetId === null || targetId === undefined) return;

    var target = store.get(targetId);
    if (target === null || !target.hasComponent("Health") || target.getComponent("Health").getInt("hp") <= 0) return;

    var calcEvent = engine.newEvent("combat.damage_calc");
    calcEvent.set("attackerId", actorId);
    calcEvent.set("targetId", targetId);
    calcEvent.set("combatId", combatId);
    engine.fire("combat.damage_calc", calcEvent);

    var damage = calcEvent.get("damage");
    var health = target.getComponent("Health");
    health.set("hp", Math.max(0, health.getInt("hp") - damage));

    var dealEvent = engine.newEvent("combat.damage_dealt");
    dealEvent.set("attackerId", actorId);
    dealEvent.set("targetId", targetId);
    dealEvent.set("damage", damage);
    dealEvent.set("combatId", combatId);
    engine.fire("combat.damage_dealt", dealEvent);
});
